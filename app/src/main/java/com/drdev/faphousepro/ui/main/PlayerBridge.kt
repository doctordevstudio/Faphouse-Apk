package com.drdev.faphousepro.ui.main

import android.webkit.JavascriptInterface

class PlayerBridge(
    private val onFullscreenChanged: (Boolean) -> Unit,
    private val onDownloadRequested: (String) -> Unit,
    private val onSessionExpired: () -> Unit,
    private val onSpaNavigation: (String) -> Unit
) {
    @JavascriptInterface
    fun onFullscreenChanged(isFullscreen: Boolean) { onFullscreenChanged(isFullscreen) }

    @JavascriptInterface
    fun onDownloadRequested(videoUrl: String) { onDownloadRequested(videoUrl) }

    @JavascriptInterface
    fun onSessionExpired() { onSessionExpired() }

    @JavascriptInterface
    fun onSpaNavigation(url: String) { onSpaNavigation(url) }

    @JavascriptInterface
    fun log(msg: String) { android.util.Log.d("FHP-Player", msg) }
}