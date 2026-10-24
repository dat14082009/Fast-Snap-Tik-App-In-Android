package com.stone.tiktoksave.extractor

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

const val DOUYIN_REFERER = "https://www.douyin.com/"

/** Ảnh chụp trạng thái trang Douyin trong WebView tại một thời điểm. */
class DouyinSnapshot(
    /** Nội dung thẻ #RENDER_DATA đã giải mã URL (trang máy tính). */
    val renderJson: String?,
    /** window._ROUTER_DATA (trang chia sẻ dành cho điện thoại). */
    val routerJson: String?,
    /** Nguồn của các thẻ <video> và <source>. */
    val videos: List<String>,
    val videoWidth: Int,
    val videoHeight: Int,
    val title: String,
    val url: String,
    val htmlLength: Int,
    val verify: Boolean
)

object DouyinSnapshotParser {
    /** evaluateJavascript trả về chuỗi JSON bọc trong dấu nháy; giải mã hai lớp như bên TikTok. */
    fun parse(raw: String?): DouyinSnapshot? {
        if (raw.isNullOrBlank() || raw == "null") return null
        return try {
            val inner = JSONTokener(raw).nextValue() as? String ?: return null
            val o = JSONObject(inner)
            val videos = ArrayList<String>()
            o.optJSONArray("videos")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i, "")
                    if (s.isNotBlank()) videos += s
                }
            }
            DouyinSnapshot(
                renderJson = o.nonBlank("render"),
                routerJson = o.nonBlank("router"),
                videos = videos,
                videoWidth = o.optInt("vw", 0),
                videoHeight = o.optInt("vh", 0),
                title = o.optString("title", ""),
                url = o.optString("url", ""),
                htmlLength = o.optInt("len", 0),
                verify = o.optBoolean("verify", false)
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun JSONObject.nonBlank(name: String): String? =
        if (isNull(name)) null else optString(name, "").takeIf { it.isNotBlank() }
}

object DouyinUrls {
    /**
     * Link trang tìm kiếm/chia sẻ có thể mang ID trong ?modal_id=: đưa về link video chuẩn.
     * Các link khác chỉ bỏ phần tham số theo dõi và fragment.
     */
    fun canonical(url: String): String {
        val modal = Regex("""[?&]modal_id=(\d+)""").find(url)?.groupValues?.get(1)
        if (modal != null) return "https://www.douyin.com/video/$modal"
        return url.substringBefore('#').substringBefore('?')
    }

    /**
     * Chuẩn hóa URL lấy từ dữ liệu Douyin: thêm https cho dạng "//host", đổi http sang https
     * (app không cho phép kết nối không mã hóa), đổi /playwm/ (có logo) thành /play/ (không logo),
     * bỏ các loại không tải được (blob:, javascript:...).
     */
    fun normalize(raw: String?): String? {
        var url = raw?.trim().orEmpty()
        if (url.isEmpty()) return null
        if (url.startsWith("//")) url = "https:$url"
        if (url.startsWith("http://")) url = "https://" + url.removePrefix("http://")
        if (!url.startsWith("https://")) return null
        return url.replace("/playwm/", "/play/")
    }
}

object MediaUrlSniffer {
    private val STATIC_EXTENSIONS = listOf(".js", ".css", ".png", ".jpg", ".jpeg", ".webp", ".gif", ".svg", ".json", ".woff", ".woff2", ".ico")

    /** Yêu cầu mạng có vẻ là luồng video của Douyin (dùng làm nguồn dự phòng cuối cùng). */
    fun isVideoRequest(url: String): Boolean {
        val lower = url.lowercase()
        if (!lower.startsWith("http")) return false
        val path = lower.substringAfter("://").substringAfter('/', "").substringBefore('?')
        if (STATIC_EXTENSIONS.any { path.endsWith(it) }) return false
        if ("mime_type=audio" in lower) return false
        val host = lower.substringAfter("://").substringBefore('/')
        return "mime_type=video_mp4" in lower ||
            "/video/tos/" in lower ||
            path.endsWith(".mp4") ||
            ("douyinvod.com" in host && "/audio/" !in lower)
    }
}

/**
 * Đọc dữ liệu video từ JSON của Douyin. Chấp nhận cả hai kiểu đặt tên (snake_case như aweme_detail
 * trong API và camelCase như RENDER_DATA của trang web) và không phụ thuộc vị trí của nút video,
 * vì cấu trúc trang thay đổi thường xuyên.
 */
object DouyinPageParser {

    private val ID_KEYS = listOf("aweme_id", "awemeId")

    /**
     * Trả về null nếu JSON không có nút video nào. Ném NoMedia (bài ảnh) hoặc Unavailable (bị lọc/xóa)
     * khi nhận ra các trường hợp đó.
     */
    fun parse(json: String, expectedId: String?, requestHeaders: Map<String, String>): VideoInfo? {
        val root: Any = try {
            JSONTokener(json).nextValue()
        } catch (e: Exception) {
            return null
        } ?: return null

        val nodes = collectNodes(root)
        if (nodes.isEmpty()) {
            if (hasNonEmptyArray(root, "filter_list")) throw ExtractorException.Unavailable()
            return null
        }

        val node = nodes.firstOrNull { expectedId != null && idOf(it) == expectedId } ?: nodes.first()
        val video = node.optJSONObject("video")
        val formats = if (video != null) readFormats(video) else emptyList()
        val images = readImages(node)
        if (formats.isEmpty() && images.isEmpty()) {
            // Có khóa ảnh nhưng không lấy được URL nào: báo đúng là không có gì để tải.
            if (hasImageKeys(node)) throw ExtractorException.NoMedia()
            return null
        }

        val author = node.firstObj("author", "authorInfo", "author_info")
        val authorName = author.firstStr("nickname", "unique_id", "uniqueId", "short_id") ?: "douyin"
        val durationRaw = video.firstLong("duration") ?: node.firstLong("duration") ?: 0L
        // Douyin tính thời lượng bằng mili giây.
        val durationSec = if (durationRaw >= 1000) (durationRaw / 1000).toInt() else durationRaw.toInt()
        val cover = video?.let { v ->
            v.opt("cover") ?: v.opt("origin_cover") ?: v.opt("originCover")
                ?: v.opt("coverUrlList") ?: v.opt("cover_url_list")
        }
        val music = node.firstObj("music")
        val audio = urlsOf(music?.opt("play_url") ?: music?.opt("playUrl")).firstOrNull()

        return VideoInfo(
            id = idOf(node) ?: expectedId ?: "unknown",
            title = node.firstStr("desc", "caption") ?: if (images.isNotEmpty()) "Ảnh Douyin" else "Video Douyin",
            author = authorName,
            durationSec = durationSec,
            posterUrl = urlsOf(cover).firstOrNull() ?: images.firstOrNull()?.urls?.firstOrNull(),
            videoFormats = formats,
            audioUrl = audio,
            requestHeaders = requestHeaders,
            images = images
        )
    }

    /** Tên các khóa ở gốc JSON (tối đa 8), dùng cho thông báo chẩn đoán. */
    fun rootKeys(json: String): String {
        val root = try {
            JSONTokener(json).nextValue()
        } catch (e: Exception) {
            return "(JSON lỗi)"
        }
        if (root !is JSONObject) return "(không phải đối tượng)"
        val names = ArrayList<String>()
        val iter = root.keys()
        while (iter.hasNext()) names += iter.next()
        if (names.isEmpty()) return "(trống)"
        val shown = names.take(8).joinToString(", ") { it.take(30) }
        return if (names.size > 8) "$shown, … (+${names.size - 8})" else shown
    }

    private fun idOf(node: JSONObject): String? = node.firstStr(*ID_KEYS.toTypedArray())

    private fun hasImageKeys(o: JSONObject): Boolean =
        o.has("images") || o.has("image_post_info") || o.has("imagePostInfo")

    private fun isAwemeNode(o: JSONObject): Boolean =
        (o.optJSONObject("video") != null || hasImageKeys(o)) && (ID_KEYS.any { o.has(it) } || o.has("desc"))

    /**
     * Bài ảnh: image_post_info.images[].display_image.url_list, hoặc images[].url_list.
     * Không bao giờ dùng owner_watermark_image, user_watermark_image, download_url_list (có watermark).
     */
    private fun readImages(node: JSONObject): List<ImageItem> {
        val out = ArrayList<ImageItem>()
        fun add(entry: JSONObject?) {
            if (entry == null) return
            val source = entry.firstObj("display_image", "displayImage") ?: entry
            val urls = urlsOf(source)
            if (urls.isEmpty()) return
            out += ImageItem(urls, source.firstLong("width")?.toInt() ?: 0, source.firstLong("height")?.toInt() ?: 0)
        }
        node.firstObj("image_post_info", "imagePostInfo")?.optJSONArray("images")?.let { arr ->
            for (i in 0 until arr.length()) add(arr.optJSONObject(i))
        }
        node.optJSONArray("images")?.let { arr ->
            for (i in 0 until arr.length()) add(arr.optJSONObject(i))
        }
        return out.distinctBy { it.urls.first() }
    }

    private fun collectNodes(root: Any): List<JSONObject> {
        val out = ArrayList<JSONObject>()
        var visited = 0
        fun walk(node: Any?, depth: Int) {
            if (depth > 14 || visited > 60_000) return
            visited++
            when (node) {
                is JSONObject -> {
                    if (isAwemeNode(node)) out += node
                    val keys = node.keys()
                    while (keys.hasNext()) walk(node.opt(keys.next()), depth + 1)
                }
                is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i), depth + 1)
            }
        }
        walk(root, 0)
        return out
    }

    private fun hasNonEmptyArray(root: Any, key: String): Boolean {
        var visited = 0
        fun walk(node: Any?, depth: Int): Boolean {
            if (depth > 14 || visited > 60_000) return false
            visited++
            when (node) {
                is JSONObject -> {
                    val direct = node.optJSONArray(key)
                    if (direct != null && direct.length() > 0) return true
                    val keys = node.keys()
                    while (keys.hasNext()) if (walk(node.opt(keys.next()), depth + 1)) return true
                }
                is JSONArray -> for (i in 0 until node.length()) if (walk(node.opt(i), depth + 1)) return true
            }
            return false
        }
        return walk(root, 0)
    }

    /** Không bao giờ dùng download_addr/downloadAddr: luồng đó có logo. */
    private fun readFormats(video: JSONObject): List<MediaFormat> {
        val out = ArrayList<MediaFormat>()

        fun add(addr: Any?, width: Int, height: Int, bitrate: Long, h265: Boolean, size: Long?) {
            val urls = urlsOf(addr)
            if (urls.isEmpty()) return
            out += MediaFormat(urls, width, height, if (h265) "bytevc1" else "h264", bitrate, size)
        }

        val rates = video.optJSONArray("bit_rate") ?: video.optJSONArray("bitRateList") ?: video.optJSONArray("bitRate")
        if (rates != null) {
            for (i in 0 until rates.length()) {
                val entry = rates.optJSONObject(i) ?: continue
                val addr = entry.opt("play_addr") ?: entry.opt("playAddr")
                val addrObj = addr as? JSONObject
                add(
                    addr,
                    width = entry.firstLong("width")?.toInt() ?: addrObj.firstLong("width")?.toInt() ?: 0,
                    height = entry.firstLong("height")?.toInt() ?: addrObj.firstLong("height")?.toInt() ?: 0,
                    bitrate = entry.firstLong("bit_rate", "bitRate") ?: 0L,
                    h265 = (entry.firstLong("is_h265", "isH265") ?: 0L) != 0L,
                    size = addrObj.firstLong("data_size", "dataSize")?.takeIf { it > 0 }
                )
            }
        }

        for (key in listOf("play_addr", "playAddr", "play_addr_h264", "playAddrH264")) {
            val addr = video.opt(key) ?: continue
            val addrObj = addr as? JSONObject
            add(
                addr,
                width = addrObj.firstLong("width")?.toInt() ?: video.firstLong("width")?.toInt() ?: 0,
                height = addrObj.firstLong("height")?.toInt() ?: video.firstLong("height")?.toInt() ?: 0,
                bitrate = video.firstLong("bit_rate", "bitRate") ?: 0L,
                h265 = false,
                size = addrObj.firstLong("data_size", "dataSize")?.takeIf { it > 0 }
            )
        }
        return out.distinctBy { it.urls.first() }
    }

    /** Giá trị có thể là chuỗi URL, mảng, hoặc đối tượng có url_list/urlList/src/url. */
    private fun urlsOf(value: Any?): List<String> = when (value) {
        is String -> listOfNotNull(DouyinUrls.normalize(value))
        is JSONArray -> (0 until value.length()).flatMap { urlsOf(value.opt(it)) }
        is JSONObject -> listOf("url_list", "urlList", "src", "url", "play_url", "playUrl")
            .flatMap { key -> if (value.has(key)) urlsOf(value.opt(key)) else emptyList() }
        else -> emptyList()
    }.distinct()

    private fun JSONObject?.firstObj(vararg keys: String): JSONObject? {
        if (this == null) return null
        return keys.firstNotNullOfOrNull { optJSONObject(it) }
    }

    private fun JSONObject?.firstStr(vararg keys: String): String? {
        if (this == null) return null
        return keys.firstNotNullOfOrNull { k ->
            if (isNull(k)) null else optString(k, "").takeIf { it.isNotBlank() }
        }
    }

    private fun JSONObject?.firstLong(vararg keys: String): Long? {
        if (this == null) return null
        return keys.firstNotNullOfOrNull { k -> if (has(k) && !isNull(k)) optLong(k, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE } else null }
    }
}

/** Kết luận sau mỗi lần kiểm tra trang trong lúc chờ. */
enum class DouyinProgress {
    /** Đã có kết luận từ dữ liệu có cấu trúc (có video, hoặc bài ảnh/video bị xóa). */
    DONE,

    /** Chưa có dữ liệu có cấu trúc nhưng đã thấy nguồn video trực tiếp. */
    DIRECT,

    /** Trang xác minh. */
    VERIFY,

    /** Chưa có gì, chờ tiếp. */
    WAIT
}

object DouyinOutcome {

    fun progress(snapshot: DouyinSnapshot?, sniffed: List<String>): DouyinProgress {
        if (snapshot != null) {
            for (json in listOfNotNull(snapshot.routerJson, snapshot.renderJson)) {
                try {
                    if (DouyinPageParser.parse(json, null, emptyMap()) != null) return DouyinProgress.DONE
                } catch (e: ExtractorException) {
                    // NoMedia hoặc Unavailable là kết luận dứt khoát, không cần chờ thêm.
                    return DouyinProgress.DONE
                }
            }
        }
        val direct = (snapshot?.videos.orEmpty() + sniffed).any { DouyinUrls.normalize(it) != null }
        if (direct) return DouyinProgress.DIRECT
        if (snapshot?.verify == true) return DouyinProgress.VERIFY
        return DouyinProgress.WAIT
    }

    /** Chạy trong trang mỗi lần kiểm tra. Trả về chuỗi JSON. */
    const val SNAPSHOT_JS = """(function () {
  try {
    var el = document.getElementById('RENDER_DATA');
    var render = null;
    if (el && el.textContent) {
      try { render = decodeURIComponent(el.textContent); } catch (e) { render = el.textContent; }
    }
    var router = null;
    try { if (window._ROUTER_DATA) { router = JSON.stringify(window._ROUTER_DATA); } } catch (e) { router = null; }
    var videos = [];
    var nodes = document.querySelectorAll('video, video source');
    for (var i = 0; i < nodes.length; i++) {
      var s = nodes[i].currentSrc || nodes[i].src || (nodes[i].getAttribute && nodes[i].getAttribute('src'));
      if (s && videos.indexOf(s) < 0) { videos.push(s); }
    }
    var v0 = document.querySelector('video');
    var title = document.title || '';
    var captcha = !!document.querySelector('#captcha_container, .captcha_verify_container, [class*="captcha_verify"]');
    var ready = document.readyState === 'complete';
    var verify = ready && (captcha || /验证|verify|captcha/i.test(title) || /captcha|verify/i.test(location.href));
    var len = 0;
    if (!render && !router && document.documentElement) { len = (document.documentElement.outerHTML || '').length; }
    return JSON.stringify({
      render: render, router: router, videos: videos,
      vw: v0 ? (v0.videoWidth || 0) : 0, vh: v0 ? (v0.videoHeight || 0) : 0,
      title: title.substring(0, 120), url: location.href, len: len, verify: verify
    });
  } catch (e) {
    return JSON.stringify({ render: null, router: null, videos: [], vw: 0, vh: 0, title: 'js-error: ' + e, url: '', len: 0, verify: false });
  }
})()"""

    /**
     * Thứ tự ưu tiên: dữ liệu có cấu trúc (router rồi render), rồi nguồn <video>/mạng bắt được,
     * rồi mới là lỗi (xác minh, bị chặn, không đọc được kèm chẩn đoán).
     */
    fun resolve(
        snapshot: DouyinSnapshot?,
        sniffed: List<String>,
        httpStatus: Int,
        inputUrl: String,
        requestHeaders: Map<String, String>
    ): VideoInfo {
        val expectedId = snapshot?.url?.let { LinkParser.videoId(it) } ?: LinkParser.videoId(inputUrl)

        val sources = listOfNotNull(snapshot?.routerJson, snapshot?.renderJson)
        for (json in sources) {
            DouyinPageParser.parse(json, expectedId, requestHeaders)?.let { return it }
        }

        val direct = ((snapshot?.videos.orEmpty()) + sniffed).mapNotNull { DouyinUrls.normalize(it) }.distinct()
        if (direct.isNotEmpty()) {
            val title = snapshot?.title?.removeSuffix(" - 抖音")?.trim()?.takeIf { it.isNotBlank() } ?: "Video Douyin"
            return VideoInfo(
                id = expectedId ?: "unknown",
                title = title,
                author = "douyin",
                durationSec = 0,
                posterUrl = null,
                videoFormats = listOf(
                    MediaFormat(direct, snapshot?.videoWidth ?: 0, snapshot?.videoHeight ?: 0, null, 0L, null)
                ),
                audioUrl = null,
                requestHeaders = requestHeaders
            )
        }

        if (snapshot?.verify == true) throw ExtractorException.VerificationRequired("Douyin")
        if (httpStatus == 403 || httpStatus == 429) {
            throw ExtractorException.Blocked("WebView HTTP $httpStatus", "Douyin")
        }
        throw ExtractorException.Parse(diagnose(snapshot, sniffed), "Douyin")
    }

    private fun diagnose(snapshot: DouyinSnapshot?, sniffed: List<String>): String {
        fun source(name: String, json: String?): String =
            if (json == null) "$name: không" else "$name: có (khóa: ${DouyinPageParser.rootKeys(json)})"
        val title = snapshot?.title?.takeIf { it.isNotBlank() } ?: "(không có)"
        val url = snapshot?.url?.take(90).orEmpty().ifEmpty { "(không có)" }
        return "WebView Douyin không thấy video; ${source("RENDER_DATA", snapshot?.renderJson)}; " +
            "${source("_ROUTER_DATA", snapshot?.routerJson)}; <video>: ${snapshot?.videos?.size ?: 0}; " +
            "mạng: ${sniffed.size}; trang ${snapshot?.htmlLength ?: 0} ký tự, tiêu đề: $title; địa chỉ: $url"
    }
}
