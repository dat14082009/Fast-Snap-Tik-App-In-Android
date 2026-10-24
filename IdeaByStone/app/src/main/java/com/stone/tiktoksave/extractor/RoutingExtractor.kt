package com.stone.tiktoksave.extractor

/**
 * Chọn Extractor theo tên miền của link. Mỗi nền tảng có đường xử lý riêng và độc lập,
 * nên sửa Douyin không ảnh hưởng TikTok và ngược lại.
 */
class RoutingExtractor(
    private val tiktok: Extractor,
    private val douyin: Extractor
) : Extractor {

    override suspend fun extract(input: String): VideoInfo {
        val url = LinkParser.findSupportedUrl(input) ?: throw ExtractorException.InvalidLink()
        return when (LinkParser.platformOf(url)) {
            Platform.TIKTOK -> tiktok.extract(url)
            Platform.DOUYIN -> douyin.extract(url)
            null -> throw ExtractorException.InvalidLink()
        }
    }
}
