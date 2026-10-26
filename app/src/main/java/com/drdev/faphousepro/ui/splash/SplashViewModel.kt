package com.drdev.faphousepro.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drdev.faphousepro.AppGraph
import com.drdev.faphousepro.data.LicenseState
import com.drdev.faphousepro.network.LicenseInfoDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

sealed interface SplashDestination {
    data object Loading : SplashDestination
    data object ToMain : SplashDestination
    data object ToLicense : SplashDestination
}

class SplashViewModel : ViewModel() {

    private val _dest = MutableStateFlow<SplashDestination>(SplashDestination.Loading)
    val dest: StateFlow<SplashDestination> = _dest.asStateFlow()

    private val minDisplayMs = 1_300L

    init { run() }

    private fun run() {
        viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            var route: SplashDestination = SplashDestination.ToLicense

            try {
                val initResp = withContext(Dispatchers.IO) { AppGraph.apiClient.init() }
                if (initResp.success) {
                    AppGraph.appState.setUpdate(initResp.update)
                    AppGraph.appState.setShadowBanned(initResp.shadow)
                }

                val key = withContext(Dispatchers.IO) { AppGraph.storage.getLicenseKey() }
                if (key.isNullOrBlank()) {
                    AppGraph.appState.setLicense(LicenseState.NoKey)
                    route = SplashDestination.ToLicense
                } else {
                    val result = withContext(Dispatchers.IO) {
                        AppGraph.apiClient.verifyLicense(key, heartbeat = false)
                    }
                    when {
                        result.success && result.license?.status == "active" -> {
                            AppGraph.appState.setLicense(
                                LicenseState.Valid(result.license, System.currentTimeMillis())
                            )
                            persistLicense(result.license, key)
                            route = SplashDestination.ToMain
                        }
                        result.code == "LICENSE_EXPIRED" -> {
                            AppGraph.appState.setLicense(LicenseState.Expired(result.expiredAt))
                            route = SplashDestination.ToLicense
                        }
                        result.code == "USER_BLOCKED_24H" -> {
                            val until = System.currentTimeMillis() / 1000L +
                                    (result.blockedUntilRemaining ?: 86400L)
                            AppGraph.appState.setLicense(LicenseState.Blocked(until, result.message))
                            route = SplashDestination.ToLicense
                        }
                        else -> {
                            AppGraph.appState.setLicense(LicenseState.Invalid(result.code, result.triesLeft))
                            route = SplashDestination.ToLicense
                        }
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                val cachedExp = withContext(Dispatchers.IO) { AppGraph.storage.getExpiresAt() }
                val now = System.currentTimeMillis() / 1000L
                if (cachedExp > now + 60L) {
                    val cachedInfo = LicenseInfoDto(
                        status = "active",
                        plan = withContext(Dispatchers.IO) { AppGraph.storage.getPlanLabel() } ?: "",
                        expiresAt = cachedExp,
                        remainingSeconds = cachedExp - now
                    )
                    AppGraph.appState.setLicense(LicenseState.Valid(cachedInfo, System.currentTimeMillis()))
                    route = SplashDestination.ToMain
                } else {
                    AppGraph.appState.setLicense(LicenseState.Error(t.message ?: "network error"))
                    route = SplashDestination.ToLicense
                }
            }

            val elapsed = System.currentTimeMillis() - startedAt
            if (elapsed < minDisplayMs) delay(minDisplayMs - elapsed)

            _dest.value = route
        }
    }

    private suspend fun persistLicense(info: LicenseInfoDto?, key: String) {
        if (info == null) return
        withContext(Dispatchers.IO) {
            AppGraph.storage.setLicenseKey(key)
            AppGraph.storage.setPlanLabel(info.plan)
            AppGraph.storage.setExpiresAt(info.expiresAt)
            AppGraph.storage.setLastVerifiedMs(System.currentTimeMillis())
            AppGraph.storage.setFailedAttempts(0)
            AppGraph.storage.setUnlockAtMs(0L)
        }
    }
}