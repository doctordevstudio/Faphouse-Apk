package com.drdev.faphousepro.data.proxy

import android.net.Uri
import android.util.Log
import android.webkit.WebResourceResponse
import com.drdev.faphousepro.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object StreamProxy {

    private const val TAG = "FHP-Proxy"
    const val FAKE_HOST = "fhp.local"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val sessions = ConcurrentHashMap<String, String>()

    fun registerSession(sessionId: String) {
        sessions[sessionId] = BuildConfig.API_BASE_URL.trimEnd('/')
        Log.i(TAG, "registered session=$sessionId")
    }

    fun unregisterSession(sessionId: String) { sessions.remove(sessionId) }

    fun intercept(uri: Uri): WebResourceResponse? {
        if (uri.host != FAKE_HOST) return null
        val segs = uri.pathSegments
        if (segs.size < 3 || segs[0] != "hls") return null
        val sessionId = segs[1]
        val backendBase = sessions[sessionId] ?: BuildConfig.API_BASE_URL.trimEnd('/')

        val rest = segs.drop(1).joinToString("/")
        val query = uri.query?.let { "?$it" } ?: ""
        val backendUrl = "$backendBase/api/v1/stream/$rest$query"

        return try {
            val req = Request.Builder()
                .url(backendUrl)
                .header("User-Agent", "FHP-Proxy/1.0")
                .build()
            val resp = client.newCall(req).execute()
            val body = resp.body
            if (!resp.isSuccessful || body == null) {
                resp.close()
                val msg = "upstream ${resp.code}".toByteArray()
                return WebResourceResponse(
                    "text/plain", "utf-8", resp.code,
                    "Upstream error", emptyMap(), ByteArrayInputStream(msg)
                )
            }
            val mime = resp.header("Content-Type")?.substringBefore(';') ?: guessMime(segs.last())
            val headers = mutableMapOf<String, String>()
            resp.header("Content-Length")?.let { headers["Content-Length"] = it }
            resp.header("Content-Range")?.let { headers["Content-Range"] = it }
            resp.header("Accept-Ranges")?.let { headers["Accept-Ranges"] = it }
            val status = resp.code
            val reason = if (status == 206) "Partial Content" else "OK"

            WebResourceResponse(
                mime,
                if (mime.startsWith("text/") || mime.contains("mpegurl")) "utf-8" else null,
                status,
                reason,
                headers,
                body.byteStream()
            )
        } catch (t: Throwable) {
            Log.w(TAG, "intercept failed for $uri: ${t.message}")
            val msg = "proxy error: ${t.message}".toByteArray()
            WebResourceResponse(
                "text/plain", "utf-8", 502, "Proxy error",
                emptyMap(), ByteArrayInputStream(msg)
            )
        }
    }

    private fun guessMime(name: String): String = when {
        name.endsWith(".m3u8") -> "application/vnd.apple.mpegurl"
        name.endsWith(".ts") -> "video/mp2t"
        name.endsWith(".mp4") -> "video/mp4"
        name.endsWith(".m4s") || name.endsWith(".m4v") -> "video/iso.segment"
        name.endsWith(".aac") -> "audio/aac"
        name.endsWith(".vtt") -> "text/vtt"
        else -> "application/octet-stream"
    }
}