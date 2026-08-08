package moe.chenxy.huaweipods.ui.pages

import android.annotation.SuppressLint
import android.net.http.SslError
import android.util.Log
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import moe.chenxy.huaweipods.R
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DocumentationPage(
    onOpenExternalUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var loadFailed by remember { mutableStateOf(false) }
    val surfaceColor = MiuixTheme.colorScheme.surface.toArgb()

    fun updateNavigationState(view: WebView) {
        canGoBack = view.canGoBack()
    }

    fun handleNavigation(rawUrl: String): Boolean = when (
        DocumentationUrlPolicy.destination(rawUrl)
    ) {
        DocumentationUrlDestination.IN_APP -> false
        DocumentationUrlDestination.EXTERNAL -> {
            onOpenExternalUrl(rawUrl)
            true
        }
        DocumentationUrlDestination.BLOCKED -> true
    }

    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                stopLoading()
                loadUrl("about:blank")
                clearHistory()
                removeAllViews()
                destroy()
            }
            webView = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = contentPadding.calculateBottomPadding()),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    val navigationTracker = DocumentationNavigationTracker()
                    setBackgroundColor(surfaceColor)
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = false
                        allowContentAccess = false
                        setGeolocationEnabled(false)
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        setSupportMultipleWindows(false)
                        javaScriptCanOpenWindowsAutomatically = false
                        safeBrowsingEnabled = true
                    }
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) {
                            progress = newProgress.coerceIn(0, 100) / 100f
                            updateNavigationState(view)
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean {
                            if (!request.isForMainFrame) {
                                return DocumentationUrlPolicy.destination(request.url.toString()) !=
                                    DocumentationUrlDestination.IN_APP
                            }
                            return handleNavigation(request.url.toString())
                        }

                        override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                            navigationTracker.onPageStarted(url)
                            loadFailed = false
                            progress = 0f
                            updateNavigationState(view)
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            if (!navigationTracker.onPageFinished(url)) return
                            loadFailed = false
                            progress = 1f
                            updateNavigationState(view)
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError,
                        ) {
                            if (!request.isForMainFrame) return
                            val failedUrl = request.url.toString()
                            if (!navigationTracker.onPageError(failedUrl)) {
                                Log.d(
                                    "HuaweiPods-Docs",
                                    "Ignored stale/late main-frame error code=${error.errorCode} url=$failedUrl",
                                )
                                return
                            }
                            Log.w(
                                "HuaweiPods-Docs",
                                "Main-frame load failed code=${error.errorCode} url=$failedUrl",
                            )
                            loadFailed = true
                        }

                        override fun onReceivedHttpError(
                            view: WebView,
                            request: WebResourceRequest,
                            errorResponse: WebResourceResponse,
                        ) {
                            if (!request.isForMainFrame || errorResponse.statusCode < 400) return
                            val failedUrl = request.url.toString()
                            if (!navigationTracker.onPageError(failedUrl)) return
                            Log.w(
                                "HuaweiPods-Docs",
                                "Main-frame HTTP ${errorResponse.statusCode} url=$failedUrl",
                            )
                            loadFailed = true
                        }

                        override fun onReceivedSslError(
                            view: WebView,
                            handler: SslErrorHandler,
                            error: SslError,
                        ) {
                            handler.cancel()
                            if (navigationTracker.onPageError(error.url)) {
                                Log.w(
                                    "HuaweiPods-Docs",
                                    "Main-frame SSL error primary=${error.primaryError} url=${error.url}",
                                )
                                loadFailed = true
                            }
                        }
                    }
                    setDownloadListener { url, _, _, _, _ ->
                        if (DocumentationUrlPolicy.destination(url) ==
                            DocumentationUrlDestination.EXTERNAL
                        ) {
                            onOpenExternalUrl(url)
                        }
                    }
                    loadUrl(DocumentationUrlPolicy.HOME_URL)
                    webView = this
                }
            },
            update = { it.setBackgroundColor(surfaceColor) },
        )

        if (progress < 1f && !loadFailed) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.coerceAtLeast(0.06f))
                        .background(MiuixTheme.colorScheme.primary),
                )
            }
        }

        if (loadFailed) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MiuixTheme.colorScheme.surface)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.documentation_load_failed),
                    color = MiuixTheme.colorScheme.onSurface,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.documentation_load_failed_summary),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                )
                TextButton(
                    text = stringResource(R.string.retry),
                    onClick = {
                        loadFailed = false
                        progress = 0f
                        webView?.reload()
                    },
                )
            }
        }
    }
}
