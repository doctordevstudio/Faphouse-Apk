package com.drdev.faphousepro.data.download

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

class HlsDownloader(
    private val client: OkHttpClient,
    private val onProgress: suspend (downloaded: Long, total: Long) -> Unit
) {
    companion object {
        private const val TAG = "FH-HLS"
        private const val UA =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36"
    }

    suspend fun download(
        mediaPlaylistUrl: String,
        outDir: File
    ): Result<HlsResult> = withContext(Dispatchers.IO) {
        try {
            outDir.mkdirs()
            val playlistResp = client.newCall(
                Request.Builder().url(mediaPlaylistUrl).header("User-Agent", UA).build()
            ).execute()
            playlistResp.use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IOException("playlist HTTP ${resp.code}"))
                }
                val playlistText = resp.body?.string()
                    ?: return@withContext Result.failure(IOException("empty playlist"))
                val baseUrl = mediaPlaylistUrl.substringBeforeLast('/') + "/"

                val rewritten = StringBuilder()
                var downloadedBytes = 0L
                var segIndex = 0
                var keyIndex = 0
                var isEncrypted = false
                var allDownloaded = true

                for (line in playlistText.lines()) {
                    val trimmed = line.trim()
                    when {
                        trimmed.startsWith("#EXT-X-KEY") -> {
                            isEncrypted = true
                            val uri = extractUriAttr(trimmed)
                            if (uri != null) {
                                val keyUrl = resolveUrl(baseUrl, uri)
                                val keyName = "key_${keyIndex++}.bin"
                                val keyFile = File(outDir, keyName)
                                if (!downloadTo(keyUrl, keyFile)) {
                                    allDownloaded = false
                                    break
                                }
                                rewritten.append(trimmed.replace(uri, keyName))
                            } else {
                                rewritten.append(trimmed)
                            }
                        }
                        trimmed.isEmpty() || trimmed.startsWith("#") -> {
                            rewritten.append(trimmed)
                        }
                        else -> {
                            val segUrl = resolveUrl(baseUrl, trimmed)
                            val ext = segUrl.substringAfterLast('.', "ts").substringBefore('?')
                            val segName = "seg_%05d.%s".format(segIndex++, ext)
                            val segFile = File(outDir, segName)
                            val size = downloadToWithSize(segUrl, segFile)
                            if (size <= 0) {
                                allDownloaded = false
                                break
                            }
                            downloadedBytes += size
                            rewritten.append(segName)
                            onProgress(downloadedBytes, downloadedBytes + 5_000_000L)
                        }
                    }
                    rewritten.append('\n')
                }

                if (!allDownloaded) {
                    return@withContext Result.failure(IOException("segment download failed"))
                }

                val localPlaylist = File(outDir, "local.m3u8")
                localPlaylist.writeText(rewritten.toString())

                val mergedFile = if (!isEncrypted) mergeSegments(outDir) else null

                Result.success(HlsResult(localPlaylist, mergedFile, downloadedBytes))
            }
        } catch (t: Throwable) {
            Log.w(TAG, "hls download failed: ${t.message}")
            Result.failure(t)
        }
    }

    private fun downloadTo(url: String, dest: File): Boolean = downloadToWithSize(url, dest) > 0

    private fun downloadToWithSize(url: String, dest: File): Long {
        return try {
            val resp = client.newCall(
                Request.Builder().url(url).header("User-Agent", UA).build()
            ).execute()
            resp.use {
                if (!it.isSuccessful) return 0L
                val body = it.body ?: return 0L
                dest.outputStream().use { out ->
                    body.byteStream().use { input -> input.copyTo(out, 64 * 1024) }
                }
                dest.length()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "segment failed $url: ${t.message}")
            0L
        }
    }

    private fun mergeSegments(dir: File): File? {
        return try {
            val segments = dir.listFiles { f -> f.name.startsWith("seg_") && f.length() > 0 }
                ?.sortedBy { it.name } ?: return null
            if (segments.isEmpty()) return null
            val out = File(dir, "merged.ts")
            out.outputStream().use { os ->
                for (seg in segments) {
                    seg.inputStream().use { it.copyTo(os, 128 * 1024) }
                }
            }
            if (out.length() > 0) out else null
        } catch (t: Throwable) {
            Log.w(TAG, "merge failed: ${t.message}")
            null
        }
    }

    private fun extractUriAttr(line: String): String? {
        return Regex("""URI="([^"]+)"""").find(line)?.groupValues?.getOrNull(1)
    }

    private fun resolveUrl(base: String, ref: String): String {
        if (ref.startsWith("http://") || ref.startsWith("https://")) return ref
        return if (ref.startsWith("/")) {
            val root = base.substringBefore("://") + "://" + base.substringAfter("://").substringBefore('/')
            root + ref
        } else {
            base + ref
        }
    }

    data class HlsResult(
        val playbackFile: File,
        val mergedFile: File?,
        val bytesTotal: Long
    )
}