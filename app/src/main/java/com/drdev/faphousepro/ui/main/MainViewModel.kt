package com.drdev.faphousepro.ui.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drdev.faphousepro.AppGraph
import com.drdev.faphousepro.data.LicenseState
import com.drdev.faphousepro.network.LicenseVerifyResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

sealed interface MainEvent {
    data class RouteToLicense(val reason: String) : MainEvent
    data class ShowToast(val message: String) : MainEvent
}

data class HeartbeatState(
    val lastSuccessMs: Long = 0L,
    val lastAttemptMs: Long = 0L,
    val consecutiveFailures: Int = 0,
    val licenseValid: Boolean = true
)

class MainViewModel : ViewModel() {

    companion object {
        private const val TAG = "FHP-Main"
        private const val HEARTBEAT_INTERVAL_MS = 60_000L
        private const val OFFLINE_GRACE_MS = 5 * 60_000L
    }

    private val _events = MutableSharedFlow<MainEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<MainEvent> = _events.asSharedFlow()

    private val _heartbeat = MutableStateFlow(HeartbeatState(lastSuccessMs = System.currentTimeMillis()))
    val heartbeat: StateFlow<HeartbeatState> = _heartbeat.asStateFlow()

    private var heartbeatJob: kotlinx.coroutines.Job? = null

    fun startHeartbeat() {
        if (heartbeatJob?.isActive == true) return
        heartbeatJob = viewModelScope.launch {
            delay(3_000L)
            while (isActive) {
                runHeartbeat()
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private suspend fun runHeartbeat() {
        val key = withContext(Dispatchers.IO) { AppGraph.storage.getLicenseKey() } ?: run {
            _events.tryEmit(MainEvent.RouteToLicense("no_license"))
            return
        }
        _heartbeat.update { it.copy(lastAttemptMs = System.currentTimeMillis()) }
        try {
            val resp = withContext(Dispatchers.IO) {
                AppGraph.apiClient.verifyLicense(key, heartbeat = true)
            }
            applyHeartbeatResult(resp)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.w(TAG, "heartbeat network failure: ${t.message}")
            _heartbeat.update { it.copy(consecutiveFailures = it.consecutiveFailures + 1) }
            val age = System.currentTimeMillis() - _heartbeat.value.lastSuccessMs
            if (age > OFFLINE_GRACE_MS) {
                _events.tryEmit(MainEvent.RouteToLicense("offline_grace_expired"))
            }
        }
    }

    private fun applyHeartbeatResult(resp: LicenseVerifyResponse) {
        when {
            resp.success && resp.license?.status == "active" -> {
                _heartbeat.update {
                    it.copy(
                        lastSuccessMs = System.currentTimeMillis(),
                        consecutiveFailures = 0,
                        licenseValid = true
                    )
                }
                resp.license.let { info ->
                    AppGraph.appState.setLicense(LicenseState.Valid(info, System.currentTimeMillis()))
                    viewModelScope.launch(Dispatchers.IO) {
                        AppGraph.storage.setExpiresAt(info.expiresAt)
                        AppGraph.storage.setPlanLabel(info.plan)
                    }
                }
            }
            resp.code == "LICENSE_EXPIRED" -> {
                _heartbeat.update { it.copy(licenseValid = false) }
                AppGraph.appState.setLicense(LicenseState.Expired(resp.expiredAt))
                viewModelScope.launch(Dispatchers.IO) { AppGraph.storage.setLicenseKey(null) }
                _events.tryEmit(MainEvent.RouteToLicense("expired"))
            }
            resp.code == "USER_BLOCKED_24H" -> {
                _heartbeat.update { it.copy(licenseValid = false) }
                val now = System.currentTimeMillis() / 1000L
                val until = now + (resp.blockedUntilRemaining ?: 86400L)
                AppGraph.appState.setLicense(LicenseState.Blocked(until, resp.message))
                _events.tryEmit(MainEvent.RouteToLicense("blocked"))
            }
            resp.code == "LICENSE_REVOKED" || resp.code == "LICENSE_NOT_FOUND" ||
                    resp.code == "DEVICE_MISMATCH" -> {
                _heartbeat.update { it.copy(licenseValid = false) }
                AppGraph.appState.setLicense(LicenseState.Invalid(resp.code, resp.triesLeft))
                viewModelScope.launch(Dispatchers.IO) { AppGraph.storage.setLicenseKey(null) }
                _events.tryEmit(MainEvent.RouteToLicense("invalid"))
            }
            else -> {
                Log.w(TAG, "heartbeat unexpected code=${resp.code}")
                _heartbeat.update { it.copy(consecutiveFailures = it.consecutiveFailures + 1) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        heartbeatJob?.cancel()
    }
}