package com.stone.tiktoksave.extractor

import kotlin.math.abs

enum class MediaKind { VIDEO, AUDIO, IMAGE }

/** Luồng đã chọn cho một tùy chọn xuất. */
data class ResolvedMedia(
    val urls: List<String>,
    val quality: String?,
    val resolution: String?,
    val sizeBytes: Long?
)

/** Một tùy chọn xuất hiển thị trên UI. media == null nghĩa là không khả dụng. */
data class ExportOption(
    val id: String,
    val title: String,
    val kind: MediaKind,
    val media: ResolvedMedia?,
    val disabledReason: String?
) {
    val enabled: Boolean get() = media != null
}

/**
 * Danh sách tùy chọn xuất được định nghĩa bằng dữ liệu (specs). Thêm định dạng mới chỉ cần
 * thêm một Spec; UI không hardcode tùy chọn nào.
 */
object ExportOptions {
    const val VIDEO_720 = "video_720"
    const val VIDEO_1080 = "video_1080"
    const val VIDEO_MAX = "video_max"
    const val AUDIO = "audio"
    const val IMAGE_ALL = "image_all"
    private const val IMAGE_PREFIX = "image_"

    /** Id của tùy chọn tải ảnh thứ [index] (tính từ 0). */
    fun imageId(index: Int): String = "$IMAGE_PREFIX${index + 1}"

    /** Chỉ số (từ 0) của ảnh trong id dạng image_N; null với image_all hoặc id sai. */
    fun imageIndex(id: String): Int? {
        if (!id.startsWith(IMAGE_PREFIX) || id == IMAGE_ALL) return null
        val number = id.removePrefix(IMAGE_PREFIX).toIntOrNull() ?: return null
        return (number - 1).takeIf { it >= 0 }
    }

    fun isImageOption(id: String): Boolean = id.startsWith(IMAGE_PREFIX)

    /**
     * Bài ảnh không có tùy chọn video: yêu cầu kiểu video (ví dụ chế độ tải ngầm luôn xin video
     * chất lượng cao nhất) được đổi thành tải tất cả ảnh.
     */
    fun effectiveOptionId(info: VideoInfo, requested: String): String =
        if (info.isPhotoPost && !isImageOption(requested)) IMAGE_ALL else requested

    private const val TOLERANCE = 40

    private class Spec(
        val id: String,
        val title: String,
        val kind: MediaKind,
        val targetShortSide: Int?,
        val missingReason: String
    )

    private val specs = listOf(
        Spec(VIDEO_720, "MP4 720p", MediaKind.VIDEO, 720, "Video này không có bản 720p."),
        Spec(VIDEO_1080, "MP4 1080p", MediaKind.VIDEO, 1080, "Video này không có bản 1080p."),
        Spec(VIDEO_MAX, "MP4 độ phân giải tối đa", MediaKind.VIDEO, null, "Không tìm thấy luồng video khả dụng."),
        Spec(AUDIO, "Âm thanh", MediaKind.AUDIO, null, "Video này không có âm thanh tách riêng.")
    )

    fun build(info: VideoInfo): List<ExportOption> =
        if (info.isPhotoPost) imageOptions(info) else specs.map { resolve(it, info) }

    /** Bài ảnh: "Tải tất cả" rồi từng ảnh. */
    private fun imageOptions(info: VideoInfo): List<ExportOption> {
        val each = info.images.mapIndexed { index, image ->
            ExportOption(
                id = imageId(index),
                title = "Ảnh ${index + 1}",
                kind = MediaKind.IMAGE,
                media = ResolvedMedia(
                    urls = image.urls,
                    quality = null,
                    resolution = if (image.width > 0 && image.height > 0) "${image.width}x${image.height}" else null,
                    sizeBytes = null
                ),
                disabledReason = null
            )
        }
        val all = ExportOption(
            id = IMAGE_ALL,
            title = "Tải tất cả (${info.images.size} ảnh)",
            kind = MediaKind.IMAGE,
            media = ResolvedMedia(info.images.map { it.urls.first() }, null, null, null),
            disabledReason = null
        )
        return listOf(all) + each
    }

    private fun resolve(spec: Spec, info: VideoInfo): ExportOption {
        val media: ResolvedMedia? = when (spec.kind) {
            MediaKind.IMAGE -> null
            MediaKind.AUDIO -> info.audioUrl?.let { ResolvedMedia(listOf(it), null, null, null) }
            MediaKind.VIDEO -> pickVideo(spec, info.videoFormats)?.let { f ->
                ResolvedMedia(
                    urls = f.urls,
                    quality = f.shortSide.takeIf { it > 0 }?.let { "${it}p" },
                    resolution = if (f.width > 0 && f.height > 0) "${f.width}x${f.height}" else null,
                    sizeBytes = f.sizeBytes
                )
            }
        }
        val title = if (spec.id == VIDEO_MAX && media?.quality != null) "MP4 tối đa (${media.quality})" else spec.title
        return ExportOption(spec.id, title, spec.kind, media, if (media == null) spec.missingReason else null)
    }

    private fun pickVideo(spec: Spec, formats: List<MediaFormat>): MediaFormat? {
        val target = spec.targetShortSide
        return if (target != null) {
            formats
                .filter { abs(it.shortSide - target) <= TOLERANCE }
                .sortedWith(compareBy<MediaFormat> { it.isHevc }.thenByDescending { it.bitrate })
                .firstOrNull()
        } else {
            formats
                .sortedWith(
                    compareByDescending<MediaFormat> { it.shortSide }
                        .thenBy { it.isHevc }
                        .thenByDescending { it.bitrate }
                )
                .firstOrNull()
        }
    }
}
