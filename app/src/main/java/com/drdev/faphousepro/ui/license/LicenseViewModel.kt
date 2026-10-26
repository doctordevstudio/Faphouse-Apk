package com.drdev.faphousepro.ui.license

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drdev.faphousepro.AppGraph
import com.drdev.faphousepro.data.LicenseState
import com.drdev.faphousepro.network.LicenseVerifyResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

data class LicenseUiState(
    val key: String = "",
    val loading: Boolean = false,
    val errorMessage: String? = null,
    val blockedUntilSec: Long? = null,
    val expired: Boolean = false,
    val success: Boolean = false
)

class LicenseViewModel : ViewModel() {

    private val _ui = MutableStateFlow(LicenseUiState())
    val ui: StateFlow<LicenseUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val cachedKey = withContext(Dispatchers.IO) { AppGraph.storage.getLicenseKey() }
            val unlockAt = withContext(Dispatchers.IO) { AppGraph.storage.getUnlockAtMs() }
            val nowSec = System.currentTimeMillis() / 1000L
            _ui.update {
                it.copy(
                    key = cachedKey.orEmpty(),
                    blockedUntilSec = if (unlockAt > nowSec) unlockAt else null
                )
            }
            (AppGraph.appState.current.license as? LicenseState.Blocked)?.let { b ->
                _ui.update { it.copy(blockedUntilSec = b.untilSec) }
            }
        }
    }

    fun onKeyChange(v: String) {
        val cleaned = v.uppercase().replace(" ", "").take(64)
        _ui.update { it.copy(key = cleaned, errorMessage = null) }
    }

    fun clearError() = _ui.update { it.copy(errorMessage = null) }

    fun activate() {
        val key = _ui.value.key.trim()
        if (key.isEmpty()) {
            _ui.update { it.copy(errorMessage = "Please enter your license key.") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, errorMessage = null, expired = false) }
            try {
                val resp = withContext(Dispatchers.IO) {
                    AppGraph.apiClient.verifyLicense(key, heartbeat = false)
                }
                handleResponse(key, resp)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                _ui.update {
                    it.copy(
                        loading = false,
                        errorMessage = "Network error. Check your connection and try again."
                    )
                }
            }
        }
    }

    private suspend fun handleResponse(key: String, resp: LicenseVerifyResponse) {
        when {
            resp.success && resp.license?.status == "active" -> {
                withContext(Dispatchers.IO) {
                    AppGraph.storage.setLicenseKey(key)
                    AppGraph.storage.setPlanLabel(resp.license.plan)
                    AppGraph.storage.setExpiresAt(resp.license.expiresAt)
                    AppGraph.storage.setLastVerifiedMs(System.currentTimeMillis())
                    AppGraph.storage.setFailedAttempts(0)
                    AppGraph.storage.setUnlockAtMs(0L)
                }
                AppGraph.appState.setLicense(LicenseState.Valid(resp.license, System.currentTimeMillis()))
                _ui.update { it.copy(loading = false, success = true, expired = false) }
            }
            resp.code == "LICENSE_EXPIRED" -> {
                withContext(Dispatchers.IO) { AppGraph.storage.setLicenseKey(null) }
                AppGraph.appState.setLicense(LicenseState.Expired(resp.expiredAt))
                _ui.update {
                    it.copy(
                        loading = false,
                        expired = true,
                        errorMessage = "Your license expired. Purchase a new key from the Telegram bot."
                    )
                }
            }
            resp.code == "USER_BLOCKED_24H" -> {
                val now = System.currentTimeMillis() / 1000L
                val until = now + (resp.blockedUntilRemaining ?: 86400L)
                withContext(Dispatchers.IO) { AppGraph.storage.setUnlockAtMs(until) }
                AppGraph.appState.setLicense(LicenseState.Blocked(until, resp.message))
                _ui.update { it.copy(loading = false, blockedUntilSec = until, errorMessage = null) }
            }
            resp.code == "LICENSE_NOT_FOUND" ||
                    resp.code == "LICENSE_REVOKED" ||
                    resp.code == "DEVICE_MISMATCH" -> {
                val tries = resp.triesLeft ?: 0
                withContext(Dispatchers.IO) { AppGraph.storage.setFailedAttempts(3 - tries) }
                AppGraph.appState.setLicense(LicenseState.Invalid(resp.code, tries))
                _ui.update {
                    it.copy(
                        loading = false,
                        errorMessage = "Invalid license key. $tries attempt(s) remaining."
                    )
                }
            }
            else -> {
                _ui.update {
                    it.copy(
                        loading = false,
                        errorMessage = resp.message ?: "Verification failed. Please try again."
                    )
                }
            }
        }
    }
}