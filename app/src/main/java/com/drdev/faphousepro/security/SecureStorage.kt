package com.drdev.faphousepro.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SecureStorage(context: Context) {

    private val appCtx = context.applicationContext
    private val prefs: SharedPreferences by lazy { buildPrefs() }

    private fun buildPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(appCtx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            appCtx,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getLicenseKey(): String? = prefs.getString(KEY_LICENSE, null)
    fun setLicenseKey(v: String?) = edit { if (v.isNullOrBlank()) remove(KEY_LICENSE) else putString(KEY_LICENSE, v) }

    fun getLastKnownDeviceId(): String? = prefs.getString(KEY_DEVICE_ID, null)
    fun setLastKnownDeviceId(v: String) = edit { putString(KEY_DEVICE_ID, v) }

    fun getLastKnownDeviceName(): String? = prefs.getString(KEY_DEVICE_NAME, null)
    fun setLastKnownDeviceName(v: String) = edit { putString(KEY_DEVICE_NAME, v) }

    fun getPlanLabel(): String? = prefs.getString(KEY_PLAN, null)
    fun setPlanLabel(v: String?) = edit { if (v.isNullOrBlank()) remove(KEY_PLAN) else putString(KEY_PLAN, v) }

    fun getExpiresAt(): Long = prefs.getLong(KEY_EXPIRES_AT, 0L)
    fun setExpiresAt(v: Long) = edit { putLong(KEY_EXPIRES_AT, v) }

    fun getLastVerifiedMs(): Long = prefs.getLong(KEY_LAST_VERIFIED, 0L)
    fun setLastVerifiedMs(v: Long) = edit { putLong(KEY_LAST_VERIFIED, v) }

    fun getUnlockAtMs(): Long = prefs.getLong(KEY_UNLOCK_AT, 0L)
    fun setUnlockAtMs(v: Long) = edit { putLong(KEY_UNLOCK_AT, v) }

    fun getFailedAttempts(): Int = prefs.getInt(KEY_FAILED_ATTEMPTS, 0)
    fun setFailedAttempts(v: Int) = edit { putInt(KEY_FAILED_ATTEMPTS, v) }

    fun getLastUpdatePromptVersion(): Int = prefs.getInt(KEY_LAST_UPDATE_PROMPT, 0)
    fun setLastUpdatePromptVersion(v: Int) = edit { putInt(KEY_LAST_UPDATE_PROMPT, v) }

    fun getOnboardingSeen(): Boolean = prefs.getBoolean(KEY_ONBOARDING, false)
    fun setOnboardingSeen(v: Boolean) = edit { putBoolean(KEY_ONBOARDING, v) }

    suspend fun clearAll() = withContext(Dispatchers.IO) { prefs.edit().clear().apply() }

    private inline fun edit(block: SharedPreferences.Editor.() -> Unit) {
        val e = prefs.edit()
        e.block()
        e.apply()
    }

    companion object {
        private const val FILE_NAME = "fh_secure_prefs"
        private const val KEY_LICENSE = "license_key"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_PLAN = "plan_label"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_LAST_VERIFIED = "last_verified_ms"
        private const val KEY_UNLOCK_AT = "unlock_at_ms"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LAST_UPDATE_PROMPT = "last_update_prompt_code"
        private const val KEY_ONBOARDING = "onboarding_seen"
    }
}