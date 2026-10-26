package com.drdev.faphousepro.data.download

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.drdev.faphousepro.AppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class DownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "FH-Worker"
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_TITLE = "title"
        const val PROGRESS_PCT = "progress_pct"
        const val PROGRESS_BYTES = "progress_bytes"
        const val PROGRESS_TOTAL = "progress_total"
        const val STATE = "state"
        const val ERROR = "error"
        private const val UA =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: return@withContext Result.failure()
        val entityJson = inputData.getString("entity") ?: return@withContext Result.failure()
        val entity = try {
            Json { ignoreUnknownKeys = true }
                .decodeFromString(DownloadEntity.serializer(), entityJson)
        } catch (t: Throwable) {
            Log.e(TAG, "bad entity: ${t.message}")
            return@withContext Result.failure()
        }

        setForegroundSafe(buildProgressForeground(entity, 0, true, "Preparing…"))

        val outDir = File(applicationContext.filesDir, "downloads/$downloadId").apply { mkdirs() }
        val client = AppGraph.apiClient.streamingClient()

        val result = try {
            if (entity.isHls) downloadHls(client, entity, outDir)
            else downloadMp4(client, entity, outDir)
        } catch (t: Throwable) {
            Log.w(TAG, "download failed: ${t.message}")
            DownloadResult.Failure(t.message ?: "unknown")
        }

        when (result) {
            is DownloadResult.Success -> {
                val completed = entity.copy(
                    state = DownloadState.COMPLETED,
                    filePath = result.playbackPath,
                    playbackPath = result.playbackPath,
                    sharePath = result.sharePath ?: result.playbackPath,
                    bytesTotal = result.bytesTotal,
                    bytesDownloaded = result.bytesTotal,
                    completedAt = System.currentTimeMillis()
                )
                AppGraph.downloads.upsert(completed)
                setProgress(workDataOf(
                    PROGRESS_PCT to 100,
                    PROGRESS_BYTES to result.bytesTotal,
                    PROGRESS_TOTAL to result.bytesTotal,
                    STATE to DownloadState.COMPLETED.name
                ))
                DownloadNotification.notifyDone(applicationContext, downloadId.hashCode(), entity.title)
                Result.success()
            }
            is DownloadResult.Failure -> {
                val failed = entity.copy(state = DownloadState.FAILED, errorMessage = result.message)
                AppGraph.downloads.upsert(failed)
                setProgress(workDataOf(
                    STATE to DownloadState.FAILED.name,
                    ERROR to result.message
                ))
                Result.failure()
            }
            DownloadResult.Cancelled -> {
                AppGraph.downloads.upsert(entity.copy(state = DownloadState.CANCELLED))
                Result.failure()
            }
        }
    }

    private suspend fun downloadHls(
        client: OkHttpClient,
        entity: DownloadEntity,
        outDir: File
    ): DownloadResult {
        val downloader = HlsDownloader(client) { done, total ->
            val pct = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 99) else 0
            setProgress(workDataOf(
                PROGRESS_PCT to pct,
                PROGRESS_BYTES to done,
                PROGRESS_TOTAL to total,
                STATE to DownloadState.DOWNLOADING.name
            ))
            setForegroundSafe(
                buildProgressForeground(
                    entity, pct, false,
                    "${entity.qualityLabel} · %.1f MB".format(done / 1024.0 / 1024.0)
                )
            )
        }

        return downloader.download(entity.qualityUrl, outDir).fold(
            onSuccess = { res ->
                DownloadResult.Success(
                    playbackPath = res.playbackFile.absolutePath,
                    sharePath = res.mergedFile?.absolutePath,
                    bytesTotal = res.bytesTotal
                )
            },
            onFailure = { DownloadResult.Failure(it.message ?: "hls failed") }
        )
    }

    private suspend fun downloadMp4(
        client: OkHttpClient,
        entity: DownloadEntity,
        outDir: File
    ): DownloadResult = withContext(Dispatchers.IO) {
        val dest = File(outDir, "video.mp4")
        val req = Request.Builder()
            .url(entity.qualityUrl)
            .header("User-Agent", UA)
            .header("Referer", "https://faphouse2.com/")
            .build()
        val resp = try { client.newCall(req).execute() } catch (t: Throwable) {
            return@withContext DownloadResult.Failure(t.message ?: "network")
        }
        resp.use {
            if (!it.isSuccessful) return@withContext DownloadResult.Failure("HTTP ${it.code}")
            val body = it.body ?: return@withContext DownloadResult.Failure("empty body")
            val total = body.contentLength()
            var downloaded = 0L
            val buf = ByteArray(128 * 1024)
            try {
                dest.outputStream().use { out ->
                    body.byteStream().use { input ->
                        while (true) {
                            if (isStopped) {
                                dest.delete()
                                return@withContext DownloadResult.Cancelled
                            }
                            val n = input.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            downloaded += n
                            val pct = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 99) else 0
                            setProgress(workDataOf(
                                PROGRESS_PCT to pct,
                                PROGRESS_BYTES to downloaded,
                                PROGRESS_TOTAL to total,
                                STATE to DownloadState.DOWNLOADING.name
                            ))
                            if (downloaded % (2L * 1024 * 1024) < buf.size) {
                                setForegroundSafe(
                                    buildProgressForeground(
                                        entity, pct, false,
                                        "${entity.qualityLabel} · %.1f / %.1f MB".format(
                                            downloaded / 1024.0 / 1024.0,
                                            if (total > 0) total / 1024.0 / 1024.0 else 0.0
                                        )
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                try { dest.delete() } catch (_: Throwable) {}
                return@withContext DownloadResult.Failure(t.message ?: "write")
            }
            if (downloaded <= 0) return@withContext DownloadResult.Failure("0 bytes")
            DownloadResult.Success(
                playbackPath = dest.absolutePath,
                sharePath = dest.absolutePath,
                bytesTotal = downloaded
            )
        }
    }

    private suspend fun setForegroundSafe(info: ForegroundInfo) {
        try { setForeground(info) } catch (t: Throwable) {
            Log.w(TAG, "setForeground failed: ${t.message}")
        }
    }

    private fun buildProgressForeground(
        entity: DownloadEntity,
        progress: Int,
        indeterminate: Boolean,
        contentText: String
    ): ForegroundInfo {
        val notification = DownloadNotification.buildProgress(
            applicationContext, entity.title, progress, indeterminate, contentText
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                entity.id.hashCode(), notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(entity.id.hashCode(), notification)
        }
    }

    private sealed interface DownloadResult {
        data class Success(val playbackPath: String, val sharePath: String?, val bytesTotal: Long) : DownloadResult
        data class Failure(val message: String) : DownloadResult
        data object Cancelled : DownloadResult
    }
}