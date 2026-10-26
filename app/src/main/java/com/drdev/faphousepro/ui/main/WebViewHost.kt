package com.drdev.faphousepro.ui.main

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.drdev.faphousepro.R
import com.drdev.faphousepro.data.proxy.StreamProxy
import com.drdev.faphousepro.ui.theme.FhBg
import com.drdev.faphousepro.ui.theme.FhGold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "FHP-WebView"
private const val HOME_URL = "https://faphouse2.com/"

@SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
@Composable
fun WebViewHost(
    playerBridge: PlayerBridge?,
    onUrlChanged: (String) -> Unit,
    onMainFrameError: (String) -> Unit,
    onDownloadAttempt: (String) -> Unit,
    onPageFinished: (String) -> Unit,
    onWebViewReady: (WebView) -> Unit
) {
    val ctx = LocalContext.current
    var progress by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var crashed by remember { mutableStateOf(false) }

    var injectedCss by remember { mutableStateOf("") }
    var injectedJs by remember { mutableStateOf("") }
    var injectedPlayerJs by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        injectedCss = withContext(Dispatchers.IO) { readRawFile(ctx, R.raw.inject_css) }
        injectedJs = withContext(Dispatchers.IO) { readRawFile(ctx, R.raw.inject_js) }
        injectedPlayerJs = withContext(Dispatchers.IO) { readRawFile(ctx, R.raw.inject_player) }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(android.graphics.Color.parseColor("#0a0b0e"))
                    configureSettings(settings)

                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    playerBridge?.let { addJavascriptInterface(it, "FHPBridge") }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress
                            if (newProgress >= 100) loading = false
                        }
                    }

                    webViewClient = object : WebViewClient() {

                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest
                        ): WebResourceResponse? {
                            val url = request.url
                            if (url.host == StreamProxy.FAKE_HOST) {
                                return StreamProxy.intercept(url)
                            }
                            return null
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean {
                            val url = request.url.toString()
                            return handleUrl(
                                url = url,
                                onTelegram = { u -> openExternal(view.context, u) },
                                onExternal = { u -> openExternal(view.context, u) },
                                allowInWebView = { UrlInspector.isFapHouse(it) }
                            )
                        }

                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            loading = true
                            progress = 0
                            canGoBack = view.canGoBack()
                            url?.let { onUrlChanged(it) }
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            super.onPageFinished(view, url)
                            loading = false
                            canGoBack = view.canGoBack()

                            if (injectedCss.isNotBlank()) injectCss(view, injectedCss)
                            if (injectedJs.isNotBlank()) injectJs(view, injectedJs)
                            if (injectedPlayerJs.isNotBlank()) injectJs(view, injectedPlayerJs)

                            url?.let { onUrlChanged(it); onPageFinished(it) }
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: android.webkit.WebResourceError
                        ) {
                            super.onReceivedError(view, request, error)
                            if (request.isForMainFrame) {
                                onMainFrameError(error.description?.toString() ?: "Network error")
                            }
                        }

                        override fun onReceivedHttpError(
                            view: WebView,
                            request: WebResourceRequest,
                            errorResponse: WebResourceResponse
                        ) {
                            super.onReceivedHttpError(view, request, errorResponse)
                            if (request.isForMainFrame) {
                                val code = errorResponse.statusCode
                                val msg = when (code) {
                                    403 -> "Access denied (403)"
                                    451 -> "Unavailable in your region (451)"
                                    404 -> "Not found (404)"
                                    else -> "HTTP $code"
                                }
                                onMainFrameError(msg)
                            }
                        }

                        override fun onReceivedSslError(
                            view: WebView,
                            handler: SslErrorHandler,
                            error: android.net.http.SslError
                        ) {
                            handler.cancel()
                            onMainFrameError("TLS verification failed")
                        }

                        override fun onRenderProcessGone(
                            view: WebView,
                            detail: RenderProcessGoneDetail
                        ): Boolean {
                            Log.e(TAG, "WebView render process gone, crashed=${detail.didCrash()}")
                            crashed = true
                            return true
                        }
                    }

                    setDownloadListener { url, _, _, _, _ -> onDownloadAttempt(url) }

                    settings.javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(false)

                    webView = this
                    onWebViewReady(this)
                    loadUrl(HOME_URL)
                }
            },
            update = { }
        )

        if (loading && progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                color = FhGold,
                trackColor = FhBg,
                modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().height(2.dp)
            )
        }

        if (crashed) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = FhGold)
            }
        }
    }

    BackHandler(enabled = canGoBack) {
        webView?.let { it.goBack(); canGoBack = it.canGoBack() }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                webView?.apply {
                    loadUrl("about:blank")
                    stopLoading()
                    webChromeClient = null
                    webViewClient = null
                    destroy()
                }
            } catch (_: Throwable) { }
        }
    }
}

private fun configureSettings(s: WebSettings) {
    s.javaScriptEnabled = true
    s.domStorageEnabled = true
    s.databaseEnabled = true
    s.cacheMode = WebSettings.LOAD_DEFAULT
    s.loadsImagesAutomatically = true
    s.blockNetworkImage = false
    s.mediaPlaybackRequiresUserGesture = false
    s.javaScriptCanOpenWindowsAutomatically = false
    s.setSupportZoom(false)
    s.builtInZoomControls = false
    s.displayZoomControls = false
    s.useWideViewPort = true
    s.loadWithOverviewMode = true
    s.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    s.userAgentString = s.userAgentString.replace("; wv)", ")")
    s.setGeolocationEnabled(false)
    s.allowFileAccess = false
    s.allowContentAccess = false
    s.savePassword = false
    s.saveFormData = false
    s.textZoom = 100
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        s.safeBrowsingEnabled = true
    }
}

private fun handleUrl(
    url: String,
    onTelegram: (String) -> Unit,
    onExternal: (String) -> Unit,
    allowInWebView: (String) -> Boolean
): Boolean {
    if (!UrlInspector.isHttpOrHttps(url)) {
        if (UrlInspector.isAllowedExternalScheme(url)) onExternal(url)
        return true
    }
    if (UrlInspector.isFapHouse(url)) return !allowInWebView(url)
    if (UrlInspector.isTelegram(url)) { onTelegram(url); return true }
    onExternal(url)
    return true
}

private fun openExternal(ctx: android.content.Context, url: String) {
    try {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    } catch (_: ActivityNotFoundException) { }
}

private fun injectCss(view: WebView, css: String) {
    val escaped = css.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ").replace("\r", " ")
    val script = """
        (function(){
            var id='__fhp_style__';
            var old=document.getElementById(id);
            if(old) old.parentNode.removeChild(old);
            var s=document.createElement('style');
            s.id=id;
            s.type='text/css';
            s.appendChild(document.createTextNode('$escaped'));
            (document.head || document.documentElement).appendChild(s);
        })();
    """.trimIndent()
    view.evaluateJavascript(script, null)
}

private fun injectJs(view: WebView, js: String) {
    view.evaluateJavascript(js, null)
}

private fun readRawFile(ctx: android.content.Context, resId: Int): String = try {
    ctx.resources.openRawResource(resId).bufferedReader().use { it.readText() }
} catch (_: Throwable) { "" }