package com.stone.tiktoksave.extractor

import org.json.JSONObject
import org.json.JSONTokener

/** Ảnh chụp trạng thái trang trong WebView tại một thời điểm. */
class WebSnapshot(
    /** Nội dung khối __UNIVERSAL_DATA_FOR_REHYDRATION__ nếu trang đã có. */
    val json: String?,
    val title: String,
    val url: String,
    val htmlLength: Int,
    /** Trang có dấu hiệu captcha/xác minh. */
    val verify: Boolean
)

object WebSnapshotParser {
    /**
     * evaluateJavascript trả về chuỗi JSON được bọc trong dấu nháy và escape,
     * nên phải giải mã hai lớp: lớp chuỗi rồi lớp đối tượng.
     */
    fun parse(raw: String?): WebSnapshot? {
        if (raw.isNullOrBlank() || raw == "null") return null
        return try {
            val inner = JSONTokener(raw).nextValue() as? String ?: return null
            val o = JSONObject(inner)
            WebSnapshot(
                json = if (o.isNull("json")) null else o.optString("json", "").takeIf { it.isNotBlank() },
                title = o.optString("title", ""),
                url = o.optString("url", ""),
                htmlLength = o.optInt("len", 0),
                verify = o.optBoolean("verify", false)
            )
        } catch (e: Exception) {
            null
        }
    }
}

object WebViewOutcome {

    /** Chạy trong trang mỗi lần kiểm tra. Trả về chuỗi JSON. */
    const val SNAPSHOT_JS = """(function () {
  try {
    var el = document.getElementById('__UNIVERSAL_DATA_FOR_REHYDRATION__');
    var title = document.title || '';
    var captcha = !!document.querySelector('#captcha_container, .captcha_verify_container, [class*="captcha_verify"], [id*="captcha"]');
    var ready = document.readyState === 'complete';
    var verify = ready && (captcha || /verify|captcha/i.test(title) || /\/verify/i.test(location.pathname));
    var len = 0;
    if (!el && document.documentElement) { len = (document.documentElement.outerHTML || '').length; }
    return JSON.stringify({
      json: el ? el.textContent : null,
      title: title.substring(0, 120),
      url: location.href,
      len: len,
      verify: verify
    });
  } catch (e) {
    return JSON.stringify({ json: null, title: 'js-error: ' + e, url: '', len: 0, verify: false });
  }
})()"""

    /** Biến trạng thái cuối cùng của trang thành VideoInfo hoặc ném đúng loại lỗi. */
    fun resolve(
        snapshot: WebSnapshot?,
        httpStatus: Int,
        inputUrl: String,
        requestHeaders: Map<String, String>
    ): VideoInfo {
        val json = snapshot?.json
        if (snapshot != null && json != null) {
            val id = LinkParser.videoId(snapshot.url) ?: LinkParser.videoId(inputUrl) ?: "unknown"
            return TikTokPageParser.parseJson(json, id, requestHeaders, snapshot.url)
        }
        if (snapshot?.verify == true) throw ExtractorException.VerificationRequired()
        if (httpStatus == 403 || httpStatus == 429) throw ExtractorException.Blocked("WebView HTTP $httpStatus")
        val title = snapshot?.title?.takeIf { it.isNotBlank() } ?: "(không có)"
        throw ExtractorException.Parse("WebView không thấy dữ liệu; trang ${snapshot?.htmlLength ?: 0} ký tự, tiêu đề: $title")
    }
}
