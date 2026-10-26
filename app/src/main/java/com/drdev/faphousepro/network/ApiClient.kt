package com.drdev.faphousepro.network

import android.util.Log
import com.drdev.faphousepro.BuildConfig
import com.drdev.faphousepro.security.NativeSecurityBridge
import com.drdev.faphousepro.security.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CertificatePinner
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.IOException
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable
data class EnvelopeDto(val d: String, val s: String, val t: String, val n: String)

@Serializable
data class InitPayload(
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_name") val deviceName: String,
    @SerialName("apk_sig") val apkSig: String,
    @SerialName("app_version") val appVersion: String,
    @SerialName("version_code") val versionCode: Int,
    @SerialName("env_flags") val envFlags: Int
)

@Serializable
data class UpdateConfigDto(
    val enabled: Boolean = false,
    @SerialName("version_name") val versionName: String = "1.0.0",
    @SerialName("version_code") val versionCode: Int = 1,
    val features: String = "",
    @SerialName("update_url") val updateUrl: String = "",
    @SerialName("force_update") val forceUpdate: Boolean = true
)

@Serializable
data class InitResponse(
    val success: Boolean,
    val code: String,
    val shadow: Boolean = false,
    val update: UpdateConfigDto = UpdateConfigDto(),
    @SerialName("server_time") val serverTime: Long = 0L
)

@Serializable
data class LicenseVerifyPayload(
    @SerialName("license_key") val licenseKey: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_name") val deviceName: String,
    @SerialName("app_version") val appVersion: String,
    @SerialName("apk_sig") val apkSig: String,
    @SerialName("version_code") val versionCode: Int,
    @SerialName("env_flags") val envFlags: Int,
    @SerialName("is_heartbeat") val isHeartbeat: Boolean = false
)

@Serializable
data class LicenseUserDto(@SerialName("full_name") val fullName: String? = null)

@Serializable
data class LicenseInfoDto(
    val status: String = "",
    val plan: String = "",
    @SerialName("expires_at") val expiresAt: Long = 0L,
    @SerialName("remaining_seconds") val remainingSeconds: Long = 0L,
    val user: LicenseUserDto = LicenseUserDto()
)

@Serializable
data class LicenseVerifyResponse(
    val success: Boolean,
    val code: String,
    val message: String? = null,
    val license: LicenseInfoDto? = null,
    val update: UpdateConfigDto? = null,
    @SerialName("server_time") val serverTime: Long = 0L,
    @SerialName("tries_left") val triesLeft: Int? = null,
    @SerialName("blocked_until_remaining") val blockedUntilRemaining: Long? = null,
    @SerialName("retry_after") val retryAfter: Long? = null,
    @SerialName("expired_at") val expiredAt: Long? = null,
    @SerialName("bot_url") val botUrl: String? = null,
    @SerialName("telegram_url") val telegramUrl: String? = null,
    @SerialName("purchase_text") val purchaseText: String? = null
)

@Serializable
data class VideoFetchPayload(
    @SerialName("license_key") val licenseKey: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_name") val deviceName: String,
    @SerialName("app_version") val appVersion: String,
    @SerialName("video_url") val videoUrl: String,
    @SerialName("apk_sig") val apkSig: String,
    @SerialName("version_code") val versionCode: Int,
    @SerialName("env_flags") val envFlags: Int
)

@Serializable
data class QualityDto(
    val quality: String,
    val url: String,
    val type: String = "m3u8",
    val bandwidth: Long = 0L,
    @SerialName("size_mb") val sizeMb: Double = 0.0
)

@Serializable
data class SubtitleDto(
    val lang: String,
    val label: String,
    val url: String
)

@Serializable
data class VideoInfoDto(
    @SerialName("video_url") val videoUrl: String,
    val title: String,
    val studio: String,
    val thumbnail: String,
    @SerialName("duration_seconds") val durationSeconds: Int,
    @SerialName("duration_formatted") val durationFormatted: String,
    @SerialName("preview_url") val previewUrl: String,
    @SerialName("auto_best_url") val autoBestUrl: String,
    @SerialName("master_m3u8") val masterM3u8: String,
    val qualities: List<QualityDto> = emptyList(),
    val subtitles: List<SubtitleDto> = emptyList(),
    @SerialName("related_html") val relatedHtml: String = "",
    @SerialName("dom_injection_css") val domInjectionCss: String = "",
    @SerialName("stream_session_id") val streamSessionId: String = ""
)

@Serializable
data class VideoFetchResponse(
    val success: Boolean,
    val code: String,
    val message: String? = null,
    val video: VideoInfoDto? = null,
    @SerialName("retry_after") val retryAfter: Long? = null,
    @SerialName("bot_url") val botUrl: String? = null
)

class ApiClient(
    private val bridge: NativeSecurityBridge,
    private val storage: SecureStorage,
    private val baseUrl: String = BuildConfig.API_BASE_URL
) {
    companion object {
        private const val TAG = "FH-Api"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val baseHttpUrl: HttpUrl = baseUrl.toHttpUrl()

    private val certPinner: CertificatePinner by lazy {
        val pins = bridge.certPins().filter { it.startsWith("sha256/") && !it.contains("AAAA") }
        val builder = CertificatePinner.Builder()
        for (p in pins) {
            try { builder.add(baseHttpUrl.host, p) } catch (t: Throwable) {
                Log.w(TAG, "bad pin dropped: $p")
            }
        }
        builder.build()
    }

    private val dispatcher = Dispatcher().apply {
        maxRequests = 32
        maxRequestsPerHost = 8
    }

    private val client: OkHttpClient by lazy {
        val bootstrap = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()

        val doh = DnsOverHttps.Builder()
            .client(bootstrap)
            .url("https://1.1.1.1/dns-query".toHttpUrl())
            .includeIPv6(false)
            .build()

        OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .certificatePinner(certPinner)
            .dns(doh)
            .proxy(Proxy.NO_PROXY)
            .build()
    }

    private suspend fun postEnvelope(path: String, plaintextPayload: String): String =
        withContext(Dispatchers.IO) {
            val envJson = bridge.encrypt(plaintextPayload)
            if (envJson.isBlank() || envJson.contains("\"error\"")) {
                throw IOException("envelope_encrypt_failed: $envJson")
            }

            val url = baseHttpUrl.newBuilder().addPathSegments(path.trimStart('/')).build()
            val req = Request.Builder()
                .url(url)
                .post(envJson.toRequestBody(JSON_MEDIA))
                .header("Accept", "application/json")
                .header("X-Client", "fhp-android/${BuildConfig.VERSION_NAME}")
                .header("User-Agent", "FHP/${BuildConfig.VERSION_NAME} (Android)")
                .build()

            val call = client.newCall(req)
            val resp = call.await()
            resp.use {
                if (!it.isSuccessful) {
                    val body = it.body?.string().orEmpty()
                    throw IOException("http ${it.code}: ${body.take(200)}")
                }
                val body = it.body?.string() ?: throw IOException("empty body")
                val plaintext = bridge.decrypt(body)
                if (plaintext.isBlank() || plaintext.contains("\"error\"")) {
                    throw IOException("envelope_decrypt_failed: ${plaintext.take(120)}")
                }
                plaintext
            }
        }

    suspend fun init(): InitResponse = withContext(Dispatchers.IO) {
        val payload = InitPayload(
            deviceId = bridge.deviceId(),
            deviceName = bridge.deviceName(),
            apkSig = bridge.apkSignatureSha256(),
            appVersion = bridge.versionName(),
            versionCode = bridge.versionCode(),
            envFlags = bridge.envFlags()
        )
        val pt = json.encodeToString(InitPayload.serializer(), payload)
        val raw = postEnvelope("/api/v1/sec/init", pt)
        json.decodeFromString(InitResponse.serializer(), raw)
    }

    suspend fun verifyLicense(licenseKey: String, heartbeat: Boolean = false): LicenseVerifyResponse =
        withContext(Dispatchers.IO) {
            val payload = LicenseVerifyPayload(
                licenseKey = licenseKey,
                deviceId = bridge.deviceId(),
                deviceName = bridge.deviceName(),
                appVersion = bridge.versionName(),
                apkSig = bridge.apkSignatureSha256(),
                versionCode = bridge.versionCode(),
                envFlags = bridge.envFlags(),
                isHeartbeat = heartbeat
            )
            val pt = json.encodeToString(LicenseVerifyPayload.serializer(), payload)
            val raw = postEnvelope("/api/v1/sec/license/verify", pt)
            json.decodeFromString(LicenseVerifyResponse.serializer(), raw)
        }

    suspend fun fetchVideo(licenseKey: String, videoUrl: String): VideoFetchResponse =
        withContext(Dispatchers.IO) {
            val payload = VideoFetchPayload(
                licenseKey = licenseKey,
                deviceId = bridge.deviceId(),
                deviceName = bridge.deviceName(),
                appVersion = bridge.versionName(),
                videoUrl = videoUrl,
                apkSig = bridge.apkSignatureSha256(),
                versionCode = bridge.versionCode(),
                envFlags = bridge.envFlags()
            )
            val pt = json.encodeToString(VideoFetchPayload.serializer(), payload)
            val raw = postEnvelope("/api/v1/sec/video/fetch", pt)
            json.decodeFromString(VideoFetchResponse.serializer(), raw)
        }

    fun streamingClient(): OkHttpClient = OkHttpClient.Builder()
        .dispatcher(dispatcher)
        .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) cont.resume(response) else response.close()
            }
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }
        })
        cont.invokeOnCancellation { try { cancel() } catch (_: Throwable) {} }
    }
}