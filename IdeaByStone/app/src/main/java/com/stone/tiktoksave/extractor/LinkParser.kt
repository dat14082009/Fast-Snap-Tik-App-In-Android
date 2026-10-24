package com.stone.tiktoksave.extractor

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

enum class Platform(val displayName: String) {
    TIKTOK("TikTok"),
    DOUYIN("Douyin")
}

object LinkParser {
    /**
     * Chỉ gồm ký tự ASCII hợp lệ của URL. Nhờ vậy URL dừng đúng chỗ khi gặp chữ Hán dính liền
     * (văn bản chia sẻ của Douyin), khoảng trắng, dấu [ ] ( ) hoặc dấu nháy.
     */
    private val URL_REGEX = Regex("""https?://[A-Za-z0-9\-._~:/?#@!$&*+,;=%]+""", RegexOption.IGNORE_CASE)
    private val VIDEO_ID_REGEX = Regex("""/(?:video|photo|note|v)/(\d+)""")
    private val MODAL_ID_REGEX = Regex("""[?&]modal_id=(\d+)""")

    /** Link TikTok hoặc Douyin đầu tiên trong một đoạn văn bản chia sẻ. */
    fun findSupportedUrl(text: String): String? = findUrl(text) { platformOf(it) != null }

    fun findTikTokUrl(text: String): String? = findUrl(text) { isTikTokUrl(it) }

    fun findDouyinUrl(text: String): String? = findUrl(text) { isDouyinUrl(it) }

    private fun findUrl(text: String, accept: (String) -> Boolean): String? =
        URL_REGEX.findAll(text)
            .map { it.value.trimEnd('.', ',', ';', '!', ')', ']', '"', '\'') }
            .firstOrNull(accept)

    /** Chỉ chấp nhận tên miền chính thức và subdomain (vt., vm., v., www., m.), không nhận tên miền giả. */
    fun platformOf(url: String): Platform? {
        val host = url.toHttpUrlOrNull()?.host ?: return null
        return when {
            host == "tiktok.com" || host.endsWith(".tiktok.com") -> Platform.TIKTOK
            host == "douyin.com" || host.endsWith(".douyin.com") ||
                host == "iesdouyin.com" || host.endsWith(".iesdouyin.com") -> Platform.DOUYIN
            else -> null
        }
    }

    fun isTikTokUrl(url: String): Boolean = platformOf(url) == Platform.TIKTOK

    fun isDouyinUrl(url: String): Boolean = platformOf(url) == Platform.DOUYIN

    /** Lấy ID từ /video/ID, /share/video/ID, /photo/ID, /note/ID hoặc ?modal_id=ID. */
    fun videoId(url: String): String? =
        VIDEO_ID_REGEX.find(url)?.groupValues?.get(1) ?: MODAL_ID_REGEX.find(url)?.groupValues?.get(1)
}
