package com.stone.tiktoksave.extractor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Trích xuất bằng OkHttp (dự phòng cho WebView): tải HTML trang video và đọc khối JSON nhúng.
 * Nếu bị chặn hoặc không có dữ liệu với giao diện máy tính, thử lại một lần với giao diện điện thoại.
 * Thông báo lỗi kèm chi tiết (mã HTTP, tiêu đề trang) để chẩn đoán.
 */
class TikTokWebExtractor(private val client: OkHttpClient) : Extractor {

    private class Page(val finalUrl: String, val html: String)

    override suspend fun extract(input: String): VideoInfo = withContext(Dispatchers.IO) {
        val found = LinkParser.findTikTokUrl(input) ?: throw ExtractorException.InvalidLink()
        // Bỏ tham số theo dõi (?is_from_webapp=1&sender_device=pc...), không cần cho việc trích xuất.
        val url = found.substringBefore('#').substringBefore('?')
        try {
            val firstFailure: ExtractorException = try {
                return@withContext attempt(BROWSER_USER_AGENT, url)
            } catch (e: ExtractorException.Blocked) {
                e
            } catch (e: ExtractorException.Parse) {
                e
            }
            try {
                attempt(MOBILE_USER_AGENT, url)
            } catch (e: ExtractorException.Blocked) {
                throw ExtractorException.Blocked("máy tính: ${firstFailure.shortDetail()}; điện thoại: ${e.shortDetail()}")
            } catch (e: ExtractorException.Parse) {
                throw ExtractorException.Parse("máy tính: ${firstFailure.shortDetail()}; điện thoại: ${e.shortDetail()}")
            }
        } catch (e: IOException) {
            throw ExtractorException.Network(e)
        }
    }

    private suspend fun attempt(userAgent: String, url: String): VideoInfo {
        val headers = mapOf("User-Agent" to userAgent, "Referer" to TIKTOK_REFERER)
        // Bước 1: theo dõi redirect (vt./vm.tiktok.com) và lấy video ID từ URL cuối.
        val first = fetchPage(url, userAgent)
        val id = LinkParser.videoId(first.finalUrl)
            ?: LinkParser.videoId(url)
            ?: throw ExtractorException.InvalidLink()
        // Bước 2: đọc dữ liệu; nếu trang đầu không có dữ liệu thì thử lại bằng URL chuẩn.
        return try {
            TikTokPageParser.parseHtml(first.html, id, headers, first.finalUrl)
        } catch (e: ExtractorException.Parse) {
            val retry = fetchPage("https://www.tiktok.com/@_/video/$id", userAgent)
            // Ghi địa chỉ sau redirect của trang đầu (có ích khi chẩn đoán); URL chuẩn chỉ là chuỗi dựng từ ID.
            TikTokPageParser.parseHtml(retry.html, id, headers, first.finalUrl)
        }
    }

    private fun pageHeaders(userAgent: String): Headers = Headers.Builder()
        .add("User-Agent", userAgent)
        .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
        .add("Accept-Language", "en-US,en;q=0.9")
        .add("Upgrade-Insecure-Requests", "1")
        .add("Sec-Fetch-Dest", "document")
        .add("Sec-Fetch-Mode", "navigate")
        .add("Sec-Fetch-Site", "none")
        .add("Sec-Fetch-User", "?1")
        .build()

    private suspend fun fetchPage(url: String, userAgent: String): Page {
        val request = Request.Builder().url(url).headers(pageHeaders(userAgent)).build()
        return client.newCall(request).await().use { response ->
            when (response.code) {
                403, 429 -> throw ExtractorException.Blocked("HTTP ${response.code}")
                404, 410 -> throw ExtractorException.Unavailable()
            }
            if (!response.isSuccessful) throw IOException("TikTok trả về HTTP ${response.code}")
            Page(response.request.url.toString(), response.body?.string().orEmpty())
        }
    }
}
