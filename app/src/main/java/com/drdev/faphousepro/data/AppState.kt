package com.drdev.faphousepro.data

import com.drdev.faphousepro.network.LicenseInfoDto
import com.drdev.faphousepro.network.UpdateConfigDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface LicenseState {
    data object Unknown : LicenseState
    data object NoKey : LicenseState
    data class Valid(val info: LicenseInfoDto, val verifiedAtMs: Long) : LicenseState
    data class Expired(val expiredAtSec: Long?) : LicenseState
    data class Blocked(val untilSec: Long, val reason: String?) : LicenseState
    data class Invalid(val code: String, val triesLeft: Int?) : LicenseState
    data class Error(val message: String) : LicenseState
}

data class AppRuntimeState(
    val license: LicenseState = LicenseState.Unknown,
    val update: UpdateConfigDto? = null,
    val shadowBanned: Boolean = false,
    val lastHeartbeatMs: Long = 0L
)

class AppState {
    private val _state = MutableStateFlow(AppRuntimeState())
    val state: StateFlow<AppRuntimeState> = _state.asStateFlow()

    fun setLicense(s: LicenseState) { _state.value = _state.value.copy(license = s) }
    fun setUpdate(u: UpdateConfigDto?) { _state.value = _state.value.copy(update = u) }
    fun setShadowBanned(v: Boolean) { _state.value = _state.value.copy(shadowBanned = v) }
    fun markHeartbeat() { _state.value = _state.value.copy(lastHeartbeatMs = System.currentTimeMillis()) }

    val current: AppRuntimeState get() = _state.value
}