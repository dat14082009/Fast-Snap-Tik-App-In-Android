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
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Trích xuất Douyin bằng WebView ẩn, không cần thuật toán ký: trình duyệt thật tự chạy JavaScript
 * và tự xử lý các bước kiểm tra. Lấy video từ ba nguồn theo thứ tự ưu tiên: dữ liệu nhúng trong
 * trang (RENDER_DATA, _ROUTER_DATA), nguồn của thẻ <video>, và các yêu cầu mạng giống luồng video.
 *
 * Lớp này độc lập với [WebViewExtractor] của TikTok để sửa Douyin không ảnh hưởng TikTok.
 */
class DouyinWebExtractor(context: Context) : Extractor {

    private val appContext = context.applicationContext

    private class Loaded(
        val snapshot: DouyinSnapshot?,
        val sniffed: List<String>,
        val httpStatus: Int,
        val userAgent: String,
        val cookies: String
    )

    override suspend fun extract(input: String): VideoInfo {
        val found = LinkParser.findDouyinUrl(input) ?: throw ExtractorException.InvalidLink()
        val url = DouyinUrls.canonical(found)

        // WebView chỉ dùng được trên luồng chính.
        val loaded = withContext(Dispatchers.Main) { load(url) }

        val headers = HashMap<String, String>()
        headers["User-Agent"] = loaded.userAgent
        headers["Referer"] = DOUYIN_REFERER
        if (loaded.cookies.isNotEmpty()) headers["Cookie"] = loaded.cookies
        return DouyinOutcome.resolve(loaded.snapshot, loaded.sniffed, loaded.httpStatus, url, headers)
    }

    /** Phải gọi trên luồng chính. */
    private suspend fun load(url: String): Loaded = suspendCancellableCoroutine { cont ->
        val handler = Handler(Looper.getMainLooper())

        val webView = try {
            WebView(appContext)
        } catch (e: Exception) {
            cont.resumeWithException(ExtractorException.Parse("không khởi tạo được WebView: ${e.message}", "Douyin"))
            return@suspendCancellableCoroutine
        }
        WebViewSupport.configure(webView)
        val userAgent = webView.settings.userAgentString.orEmpty()
        val sniffed = CopyOnWriteArrayList<String>()

        var finished = false
        var httpStatus = 0
        var polls = 0
        var directStreak = 0
        var verifyStreak = 0
        var dataStreak = 0
        var lastSnapshot: DouyinSnapshot? = null

        fun finish(snapshot: DouyinSnapshot?, error: Throwable?) {
            if (finished) return
            finished = true
            handler.removeCallbacksAndMessages(null)
            val cookies = WebViewSupport.cookieHeader(DOUYIN_REFERER)
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
                cont.resume(Loaded(snapshot, sniffed.toList(), httpStatus, userAgent, cookies))
            }
        }

        val poll = object : Runnable {
            override fun run() {
                if (finished) return
                // Sau một lúc vẫn chưa có gì: cho phép trình phát tự chạy để bắt được yêu cầu video.
                if (polls == AUTOPLAY_AFTER_POLLS) webView.settings.mediaPlaybackRequiresUserGesture = false
                webView.evaluateJavascript(DouyinOutcome.SNAPSHOT_JS) { raw ->
                    if (finished) return@evaluateJavascript
                    val snapshot = DouyinSnapshotParser.parse(raw)
                    polls++
                    if (snapshot != null) lastSnapshot = snapshot
                    val state = DouyinOutcome.progress(snapshot, sniffed.toList())
                    directStreak = if (state == DouyinProgress.DIRECT) directStreak + 1 else 0
                    verifyStreak = if (state == DouyinProgress.VERIFY) verifyStreak + 1 else 0
                    dataStreak = if (snapshot?.renderJson != null || snapshot?.routerJson != null) dataStreak + 1 else 0
                    when {
                        state == DouyinProgress.DONE -> finish(snapshot, null)
                        directStreak >= DIRECT_STREAK_LIMIT -> finish(snapshot, null)
                        verifyStreak >= VERIFY_STREAK_LIMIT -> finish(snapshot, null)
                        // Có dữ liệu nhưng không đọc được: không chờ hết thời gian, báo lỗi kèm chẩn đoán.
                        dataStreak >= DATA_STREAK_LIMIT -> finish(snapshot, null)
                        polls >= MAX_POLLS -> finish(lastSnapshot, null)
                        else -> handler.postDelayed(this, POLL_INTERVAL_MS)
                    }
                }
            }
        }

        webView.setWebViewClient(object : WebViewSupport.SafeClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val requested = request.url.toString()
                if (sniffed.size < MAX_SNIFFED && MediaUrlSniffer.isVideoRequest(requested) && requested !in sniffed) {
                    sniffed.add(requested)
                }
                return null
            }

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
                finish(null, ExtractorException.Parse("tiến trình WebView bị dừng", "Douyin"))
                return true
            }
        })

        cont.invokeOnCancellation { handler.post { finish(null, null) } }
        webView.loadUrl(url)
        handler.postDelayed(poll, FIRST_POLL_DELAY_MS)
    }

    private companion object {
        const val FIRST_POLL_DELAY_MS = 1500L
        const val POLL_INTERVAL_MS = 500L
        const val MAX_POLLS = 60
        const val AUTOPLAY_AFTER_POLLS = 12
        const val DIRECT_STREAK_LIMIT = 3
        const val VERIFY_STREAK_LIMIT = 4
        const val DATA_STREAK_LIMIT = 6
        const val MAX_SNIFFED = 20
        val NETWORK_ERRORS = setOf(
            WebViewClient.ERROR_HOST_LOOKUP,
            WebViewClient.ERROR_CONNECT,
            WebViewClient.ERROR_TIMEOUT,
            WebViewClient.ERROR_IO
        )
    }
}
