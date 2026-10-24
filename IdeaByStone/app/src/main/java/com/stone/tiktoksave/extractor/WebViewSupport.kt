package com.stone.tiktoksave.extractor

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/** Cấu hình WebView dùng chung cho trích xuất ẩn và màn Xác minh (hai nơi dùng chung kho cookie). */
object WebViewSupport {

    @SuppressLint("SetJavaScriptEnabled")
    fun configure(webView: WebView) {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = true
        settings.userAgentString = UserAgents.cleanWebView(settings.userAgentString)
        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(webView, true)
    }

    /** Chỉ cho http/https; bỏ qua các chuyển hướng mở app (snssdk://, intent://). */
    open class SafeClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val scheme = request.url.scheme?.lowercase()
            return scheme != "http" && scheme != "https"
        }
    }

    fun cookieHeader(url: String): String = CookieManager.getInstance().getCookie(url).orEmpty()

    fun flushCookies() {
        CookieManager.getInstance().flush()
    }
}
