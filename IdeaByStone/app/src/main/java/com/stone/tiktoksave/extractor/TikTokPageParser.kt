package com.stone.tiktoksave.extractor

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Đọc dữ liệu video từ khối JSON nhúng của trang TikTok. Dùng chung cho OkHttp và WebView. */
object TikTokPageParser {

    private val DETAIL_KEYS = listOf("webapp.video-detail", "webapp.reflow.video.detail")

    /** Từ toàn bộ HTML (đường OkHttp). */
    fun parseHtml(
        html: String,
        expectedId: String,
        requestHeaders: Map<String, String>,
        pageUrl: String? = null
    ): VideoInfo {
        val json = extractRehydrationJson(html)
            ?: throw if (looksBlocked(html)) {
                ExtractorException.Blocked("trang xác minh, tiêu đề: ${pageTitle(html)}")
            } else {
                ExtractorException.Parse("không có dữ liệu nhúng; trang ${html.length} ký tự, tiêu đề: ${pageTitle(html)}")
            }
        return parseJson(json, expectedId, requestHeaders, pageUrl)
    }

    /** Từ nội dung khối __UNIVERSAL_DATA_FOR_REHYDRATION__ (đường WebView lấy thẳng bằng JavaScript). */
    fun parseJson(
        json: String,
        expectedId: String,
        requestHeaders: Map<String, String>,
        pageUrl: String? = null
    ): VideoInfo {
        val root = try {
            JSONObject(json)
        } catch (e: JSONException) {
            throw ExtractorException.Parse("JSON không hợp lệ")
        }

        // Trang máy tính dùng "webapp.video-detail"; trang điện thoại (reflow) dùng "webapp.reflow.video.detail".
        val scope = root.optJSONObject("__DEFAULT_SCOPE__")
        val detail = DETAIL_KEYS.firstNotNullOfOrNull { scope?.optJSONObject(it) }
            ?: throw ExtractorException.Parse(missingDetailMessage(root, scope, pageUrl))

        val item = detail.optJSONObject("itemInfo")?.optJSONObject("itemStruct")
            ?: detail.optJSONObject("itemStruct")
        if (item == null) {
            // statusCode khác 0: video đã xóa, riêng tư hoặc bị giới hạn khu vực.
            throw if (detail.optInt("statusCode", 0) != 0) {
                ExtractorException.Unavailable()
            } else {
                ExtractorException.Parse("thiếu itemStruct; khóa trong phần video: ${keyNames(detail)}")
            }
        }

        // Bài ảnh có imagePost.images; bài video phải có luồng phát.
        val images = readImages(item)
        val video = item.optJSONObject("video")
        val formats = if (video != null) readFormats(video) else emptyList()
        if (images.isEmpty() && formats.isEmpty()) throw ExtractorException.NoMedia()

        val author = item.optJSONObject("author")
        val authorName = author.str("uniqueId") ?: author.str("nickname") ?: "unknown"

        return VideoInfo(
            id = item.str("id") ?: expectedId,
            title = item.str("desc") ?: "Video của @$authorName",
            author = authorName,
            durationSec = video?.optInt("duration", 0) ?: 0,
            posterUrl = video.str("originCover") ?: video.str("cover") ?: video.str("dynamicCover")
                ?: images.firstOrNull()?.urls?.firstOrNull(),
            videoFormats = formats,
            audioUrl = item.optJSONObject("music").str("playUrl"),
            requestHeaders = requestHeaders,
            images = images
        )
    }

    /** TikTok: imagePost.images[].imageURL.urlList (kèm imageWidth, imageHeight). */
    private fun readImages(item: JSONObject): List<ImageItem> {
        val list = item.optJSONObject("imagePost")?.optJSONArray("images") ?: return emptyList()
        val out = ArrayList<ImageItem>()
        for (i in 0 until list.length()) {
            val entry = list.optJSONObject(i) ?: continue
            val urls = entry.optJSONObject("imageURL")?.optJSONArray("urlList").toUrlList().map { httpsOf(it) }
            if (urls.isEmpty()) continue
            out += ImageItem(urls, entry.optInt("imageWidth", 0), entry.optInt("imageHeight", 0))
        }
        return out.distinctBy { it.urls.first() }
    }

    /** App không cho phép kết nối không mã hóa nên đổi http thành https. */
    private fun httpsOf(url: String): String =
        if (url.startsWith("http://")) "https://" + url.removePrefix("http://") else url

    private fun readFormats(video: JSONObject): List<MediaFormat> {
        val out = mutableListOf<MediaFormat>()

        val infos = video.optJSONArray("bitrateInfo")
        if (infos != null) {
            for (i in 0 until infos.length()) {
                val entry = infos.optJSONObject(i) ?: continue
                val play = entry.optJSONObject("PlayAddr") ?: continue
                val urls = play.optJSONArray("UrlList").toUrlList()
                if (urls.isEmpty()) continue
                out += MediaFormat(
                    urls = urls,
                    width = play.optInt("Width", 0),
                    height = play.optInt("Height", 0),
                    codec = entry.str("CodecType"),
                    bitrate = entry.optLong("Bitrate", 0L),
                    sizeBytes = play.optLong("DataSize", 0L).takeIf { it > 0 }
                )
            }
        }

        if (out.isEmpty()) {
            val play = video.urlValue("playAddr")
            if (play != null) {
                out += MediaFormat(
                    urls = listOf(play),
                    width = video.optInt("width", 0),
                    height = video.optInt("height", 0),
                    codec = null,
                    bitrate = video.optLong("bitrate", 0L),
                    sizeBytes = null
                )
            }
        }
        return out
    }

    private fun missingDetailMessage(root: JSONObject, scope: JSONObject?, pageUrl: String?): String {
        val where = if (scope != null) "trong __DEFAULT_SCOPE__" else "ở gốc dữ liệu"
        val page = pageUrl?.let { "; trang: ${it.take(90)}" }.orEmpty()
        return "thiếu video-detail; khóa $where: ${keyNames(scope ?: root)}$page"
    }

    /** Chỉ liệt kê tên khóa (tối đa 10), không đưa nội dung, để chẩn đoán cấu trúc trang. */
    private fun keyNames(obj: JSONObject): String {
        val names = ArrayList<String>()
        val iter = obj.keys()
        while (iter.hasNext()) names += iter.next()
        if (names.isEmpty()) return "(trống)"
        val shown = names.take(10).joinToString(", ") { it.take(40) }
        return if (names.size > 10) "$shown, … (+${names.size - 10})" else shown
    }

    private fun extractRehydrationJson(html: String): String? {
        val marker = html.indexOf("__UNIVERSAL_DATA_FOR_REHYDRATION__")
        if (marker < 0) return null
        val start = html.indexOf('>', marker)
        if (start < 0) return null
        val end = html.indexOf("</script>", start)
        if (end < 0) return null
        return html.substring(start + 1, end).trim()
    }

    private fun looksBlocked(html: String): Boolean =
        html.contains("captcha", ignoreCase = true) ||
            html.contains("tiktok-verify-page") ||
            html.contains("verify-ele") ||
            html.contains("Please wait", ignoreCase = true)

    private fun pageTitle(html: String): String {
        val tag = html.indexOf("<title", ignoreCase = true)
        if (tag < 0) return "(không có)"
        val open = html.indexOf('>', tag)
        if (open < 0) return "(không có)"
        val close = html.indexOf("</title>", open, ignoreCase = true)
        if (close < 0) return "(không có)"
        return html.substring(open + 1, close).trim().take(60).ifEmpty { "(trống)" }
    }

    private fun JSONObject?.str(name: String): String? {
        if (this == null || isNull(name)) return null
        return optString(name, "").takeIf { it.isNotBlank() }
    }

    /** Giá trị là chuỗi URL, hoặc đối tượng có UrlList (dạng bitrateInfo). */
    private fun JSONObject.urlValue(name: String): String? {
        if (isNull(name)) return null
        return when (val value = opt(name)) {
            is String -> value.takeIf { it.startsWith("http") }
            is JSONObject -> value.optJSONArray("UrlList").toUrlList().firstOrNull()
            else -> null
        }
    }

    private fun JSONArray?.toUrlList(): List<String> {
        if (this == null) return emptyList()
        val list = ArrayList<String>(length())
        for (i in 0 until length()) {
            val s = optString(i, "")
            if (s.startsWith("http")) list += s
        }
        return list
    }
}
