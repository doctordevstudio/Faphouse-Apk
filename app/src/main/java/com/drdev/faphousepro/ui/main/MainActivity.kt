package com.drdev.faphousepro.ui.main

import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.WindowManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.drdev.faphousepro.AppGraph
import com.drdev.faphousepro.data.proxy.StreamProxy
import com.drdev.faphousepro.network.QualityDto
import com.drdev.faphousepro.network.SubtitleDto
import com.drdev.faphousepro.network.VideoInfoDto
import com.drdev.faphousepro.ui.download.WatchDownloadActivity
import com.drdev.faphousepro.ui.license.LicenseActivity
import com.drdev.faphousepro.ui.theme.FapHouseProTheme
import com.drdev.faphousepro.ui.theme.FhBg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    private var currentVideoUrl: String = ""
    private var currentWebView: WebView? = null
    private var lastInjectedUrl: String = ""
    private var injectJob: Job? = null
    private var currentSessionId: String = ""
    private var currentVideoInfo: VideoInfoDto? = null

    private var playerBridge: PlayerBridge? = null

    private var isFullscreen: Boolean = false
    private var isPlayerPresent: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (AppGraph.isReady) {
            val lic = AppGraph.appState.current.license
            if (lic is com.drdev.faphousepro.data.LicenseState.NoKey ||
                lic is com.drdev.faphousepro.data.LicenseState.Invalid ||
                lic is com.drdev.faphousepro.data.LicenseState.Expired ||
                lic is com.drdev.faphousepro.data.LicenseState.Blocked) {
                startActivity(Intent(this, LicenseActivity::class.java))
                finish()
                return
            }
        }

        playerBridge = PlayerBridge(
            onFullscreenChanged = { fs ->
                isFullscreen = fs
                runOnUiThread { updateUiState() }
            },
            onDownloadRequested = { url ->
                val target = if (UrlInspector.isVideoUrl(url)) url else currentVideoUrl
                if (target.isNotBlank()) runOnUiThread { openWatchDownload(target) }
            },
            onSessionExpired = {
                runOnUiThread { currentVideoInfo?.let { info -> refreshSession(info) } }
            },
            onSpaNavigation = { url ->
                runOnUiThread {
                    currentVideoUrl = url
                    if (UrlInspector.isVideoUrl(url) && url != lastInjectedUrl) {
                        handleVideoPage(url)
                    } else if (!UrlInspector.isVideoUrl(url)) {
                        destroyPlayer()
                    }
                }
            }
        )

        setContent {
            FapHouseProTheme {
                val lifecycle = LocalLifecycleOwner.current.lifecycle
                val snackbar = remember { SnackbarHostState() }

                var fullscreenState by remember { mutableStateOf(false) }
                var playerPresentState by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    while (true) {
                        fullscreenState = isFullscreen
                        playerPresentState = isPlayerPresent
                        kotlinx.coroutines.delay(120)
                    }
                }

                LaunchedEffect(Unit) {
                    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        vm.startHeartbeat()
                        vm.events.collect { ev ->
                            when (ev) {
                                is MainEvent.RouteToLicense -> {
                                    startActivity(
                                        Intent(this@MainActivity, LicenseActivity::class.java)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                    )
                                    finish()
                                }
                                is MainEvent.ShowToast -> snackbar.showSnackbar(ev.message)
                            }
                        }
                    }
                }

                DisposableEffect(Unit) { onDispose { vm.stopHeartbeat() } }

                MainScreen(
                    snackbarHostState = snackbar,
                    fullscreen = fullscreenState,
                    playerPresent = playerPresentState,
                    playerBridge = playerBridge,
                    onUrlChanged = { },
                    onPageFinished = { url ->
                        currentVideoUrl = url
                        if (UrlInspector.isVideoUrl(url) && url != lastInjectedUrl) {
                            handleVideoPage(url)
                        } else if (!UrlInspector.isVideoUrl(url)) {
                            destroyPlayer()
                        }
                    },
                    onWebViewReady = { wv -> currentWebView = wv },
                    onWatchAndDownloadClick = {
                        if (currentVideoUrl.isNotBlank() && UrlInspector.isVideoUrl(currentVideoUrl)) {
                            openWatchDownload(currentVideoUrl)
                        } else {
                            openWatchDownload(null)
                        }
                    },
                    onMyDownloadsClick = { openWatchDownload(null, downloadsOnly = true) },
                    onMainFrameError = { msg -> snackbar.showSnackbar(msg) },
                    onDownloadAttempt = { url ->
                        if (UrlInspector.isVideoUrl(url) || UrlInspector.isDirectMediaLink(url)) {
                            openWatchDownload(url)
                        }
                    }
                )
            }
        }
    }

    private fun updateUiState() {
        runOnUiThread { isPlayerPresent = currentSessionId.isNotBlank() }
    }

    private fun handleVideoPage(url: String) {
        lastInjectedUrl = url
        injectJob?.cancel()
        injectJob = lifecycleScope.launch {
            val wv = currentWebView ?: return@launch
            withContext(Dispatchers.Main) {
                wv.evaluateJavascript(placeholderJs(), null)
                isPlayerPresent = true
            }
            try {
                val key = withContext(Dispatchers.IO) { AppGraph.storage.getLicenseKey() }
                if (key.isNullOrBlank()) return@launch
                val resp = withContext(Dispatchers.IO) { AppGraph.apiClient.fetchVideo(key, url) }
                val info = resp.video ?: return@launch
                val sessionId = info.streamSessionId
                if (sessionId.isBlank() || sessionId == "shadow_trap") {
                    withContext(Dispatchers.Main) { wv.evaluateJavascript(errorJs("no session"), null) }
                    return@launch
                }
                StreamProxy.registerSession(sessionId)
                currentSessionId = sessionId
                currentVideoInfo = info
                withContext(Dispatchers.Main) {
                    wv.evaluateJavascript(buildMountJs(sessionId, info), null)
                    isPlayerPresent = true
                }
            } catch (t: Throwable) {
                Log.w("FHP-Main", "inject failed: ${t.message}")
                withContext(Dispatchers.Main) {
                    currentWebView?.evaluateJavascript(errorJs(t.message ?: "fetch failed"), null)
                }
            }
        }
    }

    private fun refreshSession(info: VideoInfoDto) {
        injectJob = lifecycleScope.launch {
            try {
                val key = withContext(Dispatchers.IO) { AppGraph.storage.getLicenseKey() } ?: return@launch
                val resp = withContext(Dispatchers.IO) { AppGraph.apiClient.fetchVideo(key, info.videoUrl) }
                val newInfo = resp.video ?: return@launch
                val newSession = newInfo.streamSessionId
                if (newSession.isBlank() || newSession == "shadow_trap") return@launch
                StreamProxy.registerSession(newSession)
                currentSessionId = newSession
                currentVideoInfo = newInfo
                val qualitiesJs = buildQualitiesJson(newSession, newInfo.qualities)
                currentWebView?.evaluateJavascript(
                    "if (window.FHPPlayer) window.FHPPlayer.refresh('$newSession', $qualitiesJs);",
                    null
                )
            } catch (t: Throwable) {
                Log.w("FHP-Main", "refresh failed: ${t.message}")
            }
        }
    }

    private fun destroyPlayer() {
        currentWebView?.evaluateJavascript(
            "if (window.FHPPlayer) window.FHPPlayer.destroy();", null
        )
        currentSessionId = ""
        currentVideoInfo = null
        isPlayerPresent = false
    }

    private fun placeholderJs(): String = """
        (function(){
            if (!document.getElementById('fhp-player-wrap')) {
                var host = document.querySelector('.video__main')
                    || document.querySelector('.video__wrap')
                    || document.querySelector('.video')
                    || document.body;
                if (host) {
                    var p = document.createElement('div');
                    p.id = 'fhp-player-wrap';
                    p.innerHTML = '<div id="fhp-placeholder"><div class="fhp-ph-inner"><div class="fhp-ph-spinner"></div><div>Preparing player…</div></div></div>';
                    host.insertBefore(p, host.firstChild);
                }
            }
        })();
    """.trimIndent()

    private fun errorJs(msg: String): String {
        val safe = msg.replace("'", "\\'")
        return """
            (function(){
                var w = document.getElementById('fhp-player-wrap');
                if (!w) return;
                w.classList.add('error');
                var ph = document.getElementById('fhp-placeholder');
                if (ph) ph.remove();
                var err = document.createElement('div');
                err.id = 'fhp-error-msg';
                err.style.display = 'flex';
                err.innerHTML = '<div class="fhp-err-title">Stream unavailable</div><div class="fhp-err-body">$safe</div>';
                w.appendChild(err);
            })();
        """.trimIndent()
    }

    private fun b64url(s: String): String {
        return Base64.encodeToString(
            s.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }

    private fun buildQualitiesJson(sessionId: String, qualities: List<QualityDto>): String {
        return qualities.joinToString(prefix = "[", postfix = "]") { q ->
            val label = q.quality.replace("'", "\\'")
            val proxy = "https://${StreamProxy.FAKE_HOST}/hls/$sessionId/var/${b64url(q.url)}"
            "{label:'$label',sizeMb:${q.sizeMb},proxyUrl:'$proxy'}"
        }
    }

    private fun buildMountJs(sessionId: String, info: VideoInfoDto): String {
        val qualitiesJs = buildQualitiesJson(sessionId, info.qualities)
        val subsJs = info.subtitles.joinToString(prefix = "[", postfix = "]") { s: SubtitleDto ->
            val label = s.label.replace("'", "\\'")
            val lang = s.lang.replace("'", "\\'").lowercase().take(2)
            val proxy = "https://${StreamProxy.FAKE_HOST}/hls/$sessionId/seg/${b64url(s.url)}"
            "{label:'$label',lang:'$lang',proxyUrl:'$proxy'}"
        }
        val videoUrl = info.videoUrl.replace("'", "\\'")
        return """
            (function(){
                var payload = {
                    sessionId: '$sessionId',
                    videoUrl: '$videoUrl',
                    qualities: $qualitiesJs,
                    subs: $subsJs
                };
                if (window.FHPPlayer && window.FHPPlayer.mount) {
                    window.FHPPlayer.mount(payload);
                }
            })();
        """.trimIndent()
    }

    private fun openWatchDownload(videoUrl: String?, downloadsOnly: Boolean = false) {
        val i = Intent(this, WatchDownloadActivity::class.java)
        if (!videoUrl.isNullOrBlank() && UrlInspector.isVideoUrl(videoUrl)) {
            i.putExtra(WatchDownloadActivity.EXTRA_VIDEO_URL, videoUrl)
        }
        if (downloadsOnly) {
            i.putExtra(WatchDownloadActivity.EXTRA_MODE, WatchDownloadActivity.MODE_DOWNLOADS)
        }
        startActivity(i)
    }
}

@Composable
private fun MainScreen(
    snackbarHostState: SnackbarHostState,
    fullscreen: Boolean,
    playerPresent: Boolean,
    playerBridge: PlayerBridge?,
    onUrlChanged: (String) -> Unit,
    onPageFinished: (String) -> Unit,
    onWebViewReady: (WebView) -> Unit,
    onWatchAndDownloadClick: () -> Unit,
    onMyDownloadsClick: () -> Unit,
    onMainFrameError: (String) -> Unit,
    onDownloadAttempt: (String) -> Unit
) {
    Scaffold(
        containerColor = FhBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0)
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).background(FhBg)
        ) {
            WebViewHost(
                playerBridge = playerBridge,
                onUrlChanged = onUrlChanged,
                onMainFrameError = onMainFrameError,
                onDownloadAttempt = onDownloadAttempt,
                onPageFinished = onPageFinished,
                onWebViewReady = onWebViewReady
            )

            if (!fullscreen) {
                FloatingActions(
                    modifier = Modifier
                        .align(if (playerPresent) Alignment.BottomEnd else Alignment.TopEnd)
                        .then(if (playerPresent) Modifier.padding(16.dp) else Modifier.statusBarsPadding()),
                    onWatchAndDownloadClick = onWatchAndDownloadClick,
                    onMyDownloadsClick = onMyDownloadsClick
                )
            }
        }
    }
}