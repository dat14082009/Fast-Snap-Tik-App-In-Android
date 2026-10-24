package com.stone.tiktoksave.extractor

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Trích xuất bằng WebView ẩn: là trình duyệt thật (chạy JavaScript, có vân tay kết nối của Chromium)
 * nên qua được các trang kiểm tra mà OkHttp bị chặn. Đọc khối JSON nhúng bằng JavaScript rồi dùng
 * chung bộ đọc với đường OkHttp. Cookie và User-Agent của WebView được trả về trong requestHeaders
 * để lớp tải xuống dùng khi tải từ CDN.
 */
class WebViewExtractor(context: Context) : Extractor {

    private val appContext = context.applicationContext

    private class Loaded(
        val snapshot: WebSnapshot?,
        val httpStatus: Int,
        val userAgent: String,
        val cookies: String
    )

    override suspend fun extract(input: String): VideoInfo {
        val found = LinkParser.findTikTokUrl(input) ?: throw ExtractorException.InvalidLink()
        val url = found.substringBefore('#').substringBefore('?')

        // WebView chỉ dùng được trên luồng chính.
        val loaded = withContext(Dispatchers.Main) { load(url) }

        val headers = HashMap<String, String>()
        headers["User-Agent"] = loaded.userAgent
        headers["Referer"] = TIKTOK_REFERER
        if (loaded.cookies.isNotEmpty()) headers["Cookie"] = loaded.cookies
        return WebViewOutcome.resolve(loaded.snapshot, loaded.httpStatus, url, headers)
    }

    /** Phải gọi trên luồng chính. Tải trang rồi kiểm tra định kỳ cho tới khi có dữ liệu hoặc hết thời gian. */
    private suspend fun load(url: String): Loaded = suspendCancellableCoroutine { cont ->
        val handler = Handler(Looper.getMainLooper())

        val webView = try {
            WebView(appContext)
        } catch (e: Exception) {
            cont.resumeWithException(ExtractorException.Parse("không khởi tạo được WebView: ${e.message}"))
            return@suspendCancellableCoroutine
        }
        WebViewSupport.configure(webView)
        val userAgent = webView.settings.userAgentString.orEmpty()

        var finished = false
        var httpStatus = 0
        var polls = 0
        var verifyStreak = 0
        var lastSnapshot: WebSnapshot? = null

        fun finish(snapshot: WebSnapshot?, error: Throwable?) {
            if (finished) return
            finished = true
            handler.removeCallbacksAndMessages(null)
            val cookies = WebViewSupport.cookieHeader(TIKTOK_REFERER)
            WebViewSupport.flushCookies()
            try {
                webView.stopLoading()
                webView.destroy()
            } catch (e: Exception) {
                // WebView đã được giải phóng, bỏ qua.
            }
            if (!cont.isActive) return
            if (error != null) {
                cont.resumeWithException(error)
            } else {
                cont.resume(Loaded(snapshot, httpStatus, userAgent, cookies))
            }
        }

        val poll = object : Runnable {
            override fun run() {
                if (finished) return
                webView.evaluateJavascript(WebViewOutcome.SNAPSHOT_JS) { raw ->
                    if (finished) return@evaluateJavascript
                    val snapshot = WebSnapshotParser.parse(raw)
                    polls++
                    if (snapshot != null) lastSnapshot = snapshot
                    verifyStreak = if (snapshot?.verify == true) verifyStreak + 1 else 0
                    when {
                        snapshot?.json != null -> finish(snapshot, null)
                        verifyStreak >= VERIFY_STREAK_LIMIT -> finish(snapshot, null)
                        polls >= MAX_POLLS -> finish(lastSnapshot, null)
                        else -> handler.postDelayed(this, POLL_INTERVAL_MS)
                    }
                }
            }
        }

        webView.setWebViewClient(object : WebViewSupport.SafeClient() {
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame && error.errorCode in NETWORK_ERRORS) {
                    finish(null, ExtractorException.Network(IOException(error.description?.toString())))
                }
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse
            ) {
                if (request.isForMainFrame) httpStatus = errorResponse.statusCode
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                finish(null, ExtractorException.Parse("tiến trình WebView bị dừng"))
                return true
            }
        })

        cont.invokeOnCancellation { handler.post { finish(null, null) } }
        webView.loadUrl(url)
        handler.postDelayed(poll, FIRST_POLL_DELAY_MS)
    }

    private companion object {
        const val FIRST_POLL_DELAY_MS = 1000L
        const val POLL_INTERVAL_MS = 500L
        const val MAX_POLLS = 50
        const val VERIFY_STREAK_LIMIT = 4
        val NETWORK_ERRORS = setOf(
            WebViewClient.ERROR_HOST_LOOKUP,
            WebViewClient.ERROR_CONNECT,
            WebViewClient.ERROR_TIMEOUT,
            WebViewClient.ERROR_IO
        )
    }
}
