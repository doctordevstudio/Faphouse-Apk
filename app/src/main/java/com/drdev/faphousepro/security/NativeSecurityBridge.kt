package com.drdev.faphousepro.security

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaDrm
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.security.MessageDigest
import java.util.UUID

@SuppressLint("PrivateApi", "HardwareIds")
class NativeSecurityBridge(private val context: Context) {

    companion object {
        private const val TAG = "FH-Bridge"
        private const val LIB_NAME = "native-lib"
        private val WIDEVINE_UUID: UUID = UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed")

        @Volatile private var loaded = false

        fun ensureLoaded() {
            if (loaded) return
            synchronized(this) {
                if (loaded) return
                System.loadLibrary(LIB_NAME)
                loaded = true
            }
        }

        @JvmStatic external fun nativeInit(bridge: NativeSecurityBridge, context: Context)
        @JvmStatic external fun setWidevineId(bytes: ByteArray?)
        @JvmStatic external fun getDeviceId(): String
        @JvmStatic external fun getDeviceModel(): String
        @JvmStatic external fun getApkSignature(): String
        @JvmStatic external fun getVersionName(): String
        @JvmStatic external fun getVersionCode(): Int
        @JvmStatic external fun getEnvFlags(): Int
        @JvmStatic external fun encryptEnvelope(plaintextJson: String): String
        @JvmStatic external fun decryptEnvelope(envelopeJson: String): String
        @JvmStatic external fun getCertPins(): Array<String>
        @JvmStatic external fun runIntegritySelfCheck(
            expectedApkSha256: String,
            expectedVerName: String,
            expectedVerCode: Int
        ): Boolean
    }

    @Volatile private var cachedApkSig: String? = null
    @Volatile private var cachedVersionName: String? = null
    @Volatile private var cachedVersionCode: Int = 0
    @Volatile private var cachedDeviceModel: String? = null
    @Volatile private var cachedPackageName: String? = null
    @Volatile private var cachedInstaller: String? = null

    private fun sha256Hex(bytes: ByteArray): String {
        val d = MessageDigest.getInstance("SHA-256").digest(bytes)
        val sb = StringBuilder(d.size * 2)
        for (b in d) sb.append(String.format("%02x", b))
        return sb.toString()
    }

    fun javaGetApkSignatureSha256(): String {
        cachedApkSig?.let { return it }
        val sig = try {
            val pm = context.packageManager
            val pkg = context.packageName
            val der: ByteArray? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
                val signers = info.signingInfo?.apkContentsSigners
                if (signers.isNullOrEmpty()) null else signers[0].toByteArray()
            } else {
                @Suppress("DEPRECATION")
                val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                info.signatures?.firstOrNull()?.toByteArray()
            }
            der
        } catch (t: Throwable) {
            Log.w(TAG, "apk sig extract failed: ${t.message}")
            null
        }
        val hex = if (sig != null) sha256Hex(sig) else ""
        cachedApkSig = hex
        return hex
    }

    fun javaGetVersionName(): String {
        cachedVersionName?.let { return it }
        val v = try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (_: Throwable) { "" }
        cachedVersionName = v
        return v
    }

    fun javaGetVersionCode(): Int {
        if (cachedVersionCode != 0) return cachedVersionCode
        val v = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionCode
            }
        } catch (_: Throwable) { 0 }
        cachedVersionCode = v
        return v
    }

    fun javaGetDeviceModel(): String {
        cachedDeviceModel?.let { return it }
        val m = listOf(Build.MANUFACTURER, Build.MODEL)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
        cachedDeviceModel = m
        return m
    }

    fun javaGetPackageName(): String {
        cachedPackageName?.let { return it }
        val p = context.packageName ?: ""
        cachedPackageName = p
        return p
    }

    fun javaGetInstallerPackage(): String {
        cachedInstaller?.let { return it }
        val i = try {
            context.packageManager.getInstallerPackageName(context.packageName) ?: ""
        } catch (_: Throwable) { "" }
        cachedInstaller = i
        return i
    }

    fun javaIsDebuggerAttached(): Boolean = android.os.Debug.isDebuggerConnected()

    fun bootstrap() {
        ensureLoaded()
        try { nativeInit(this, context) } catch (t: Throwable) {
            Log.e(TAG, "nativeInit failed: ${t.message}")
            return
        }
        try {
            val drm = MediaDrm(WIDEVINE_UUID)
            try {
                val wv = drm.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)
                if (wv != null && wv.isNotEmpty()) setWidevineId(wv)
            } finally {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) drm.close()
                else @Suppress("DEPRECATION") drm.release()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "widevine id unavailable: ${t.message}")
            setWidevineId(null)
        }
    }

    private fun fallbackDeviceHash(): String {
        val androidId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        } catch (_: Throwable) { "" }
        val serial = try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION") Build.SERIAL
            } else "unknown"
        } catch (_: Throwable) { "unknown" }
        val material = "aid=$androidId|serial=$serial|model=${javaGetDeviceModel()}|pkg=${javaGetPackageName()}"
        return sha256Hex(material.toByteArray(Charsets.UTF_8))
    }

    fun deviceId(): String {
        val nativeId = try { getDeviceId() } catch (_: Throwable) { "" }
        return if (nativeId.isNotBlank() && nativeId.length == 64) nativeId else fallbackDeviceHash()
    }

    fun deviceName(): String = javaGetDeviceModel()
    fun apkSignatureSha256(): String = javaGetApkSignatureSha256()
    fun versionName(): String = javaGetVersionName()
    fun versionCode(): Int = javaGetVersionCode()
    fun envFlags(): Int = try { getEnvFlags() } catch (_: Throwable) { -1 }

    fun integritySelfCheck(expectedSig: String, expectedVer: String, expectedCode: Int): Boolean =
        try { runIntegritySelfCheck(expectedSig, expectedVer, expectedCode) } catch (_: Throwable) { false }

    fun encrypt(plaintext: String): String = encryptEnvelope(plaintext)
    fun decrypt(envelope: String): String = decryptEnvelope(envelope)
    fun certPins(): List<String> = try { getCertPins().toList() } catch (_: Throwable) { emptyList() }
}