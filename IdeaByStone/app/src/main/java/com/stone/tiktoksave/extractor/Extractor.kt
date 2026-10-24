package com.stone.tiktoksave.extractor

/** Một luồng video khả dụng (không logo). Có thể có nhiều URL dự phòng. */
data class MediaFormat(
    val urls: List<String>,
    val width: Int,
    val height: Int,
    val codec: String?,
    val bitrate: Long,
    val sizeBytes: Long?
) {
    /** Cạnh ngắn: 720p/1080p của video dọc là 720x1280 / 1080x1920. */
    val shortSide: Int get() = minOf(width, height)

    val isHevc: Boolean
        get() = codec?.lowercase()?.let { "bytevc1" in it || "265" in it || "hevc" in it } == true
}

/** Một ảnh của bài đăng dạng ảnh. Có thể có nhiều URL dự phòng. */
data class ImageItem(
    val urls: List<String>,
    val width: Int,
    val height: Int
)

data class VideoInfo(
    val id: String,
    val title: String,
    val author: String,
    val durationSec: Int,
    val posterUrl: String?,
    val videoFormats: List<MediaFormat>,
    val audioUrl: String?,
    /** Header cần gửi kèm khi tải file từ CDN. */
    val requestHeaders: Map<String, String>,
    /** Có phần tử khi link là bài ảnh; khi đó UI đổi tùy chọn video thành tải ảnh. */
    val images: List<ImageItem> = emptyList()
) {
    val isPhotoPost: Boolean get() = images.isNotEmpty()
}

/**
 * Toàn bộ logic trích xuất nằm sau interface này. Có thể thay bằng implementation khác
 * (ví dụ backend yt-dlp) mà không đụng tới UI hay lớp tải xuống.
 */
interface Extractor {
    suspend fun extract(input: String): VideoInfo
}

sealed class ExtractorException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InvalidLink : ExtractorException("Liên kết không hợp lệ hoặc không thuộc TikTok/Douyin.")
    class Unavailable : ExtractorException("Video riêng tư, đã bị xóa hoặc không khả dụng ở khu vực của bạn.")
    class Blocked(val detail: String? = null, val site: String = "TikTok") :
        ExtractorException("$site đang chặn yêu cầu. Hãy thử lại sau ít phút hoặc đổi mạng." + (detail?.let { " [$it]" } ?: ""))
    class VerificationRequired(val site: String = "TikTok") :
        ExtractorException("$site yêu cầu xác minh. Mở app, dán link, bấm Gỡ dấu chìm rồi chọn Xác minh với $site.")
    class Network(cause: Throwable?) : ExtractorException("Mất kết nối mạng hoặc kết nối bị gián đoạn.", cause)
    class NoMedia : ExtractorException("Bài đăng này không có video hoặc ảnh để tải.")
    class Parse(val detail: String, val site: String = "TikTok") :
        ExtractorException("Không đọc được dữ liệu từ $site ($detail). Cấu trúc trang có thể đã đổi, cần cập nhật Extractor.")
}

/** Chi tiết ngắn gọn để ghép vào thông báo tổng hợp khi nhiều lần thử đều thất bại. */
fun ExtractorException.shortDetail(): String = when (this) {
    is ExtractorException.Blocked -> detail ?: "bị chặn"
    is ExtractorException.Parse -> detail
    else -> message.orEmpty()
}
