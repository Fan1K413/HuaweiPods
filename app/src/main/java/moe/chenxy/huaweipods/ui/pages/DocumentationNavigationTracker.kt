package moe.chenxy.huaweipods.ui.pages

import java.net.URI

/** Associates asynchronous WebView callbacks with the latest main-frame navigation. */
internal class DocumentationNavigationTracker {
    private var activeDocumentKey: String? = null
    private var state = NavigationState.IDLE

    fun onPageStarted(url: String?) {
        activeDocumentKey = url?.let(::documentationDocumentKey)
        state = NavigationState.LOADING
    }

    fun onPageFinished(url: String?): Boolean {
        if (!isCurrent(url) || state == NavigationState.FAILED) return false
        state = NavigationState.FINISHED
        return true
    }

    fun onPageError(url: String?): Boolean {
        if (!isCurrent(url) || state == NavigationState.FINISHED) return false
        state = NavigationState.FAILED
        return true
    }

    private fun isCurrent(url: String?): Boolean {
        val candidate = url?.let(::documentationDocumentKey) ?: return false
        return candidate == activeDocumentKey
    }

    private enum class NavigationState {
        IDLE,
        LOADING,
        FINISHED,
        FAILED,
    }
}

private fun documentationDocumentKey(rawUrl: String): String? = runCatching {
    val uri = URI(rawUrl).normalize()
    val scheme = uri.scheme?.lowercase() ?: return null
    val host = uri.host?.lowercase() ?: return null
    val port = when {
        uri.port == -1 -> ""
        scheme == "https" && uri.port == 443 -> ""
        else -> ":${uri.port}"
    }
    val path = uri.rawPath.orEmpty().ifEmpty { "/" }
    val query = uri.rawQuery?.let { "?$it" }.orEmpty()
    "$scheme://$host$port$path$query"
}.getOrNull()
