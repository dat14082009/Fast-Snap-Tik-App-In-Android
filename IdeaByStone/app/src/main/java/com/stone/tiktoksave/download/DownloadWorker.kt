package com.stone.tiktoksave.download

import android.content.Context
import android.os.SystemClock
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.stone.tiktoksave.App
import com.stone.tiktoksave.data.DownloadStatus
import com.stone.tiktoksave.data.HistoryEntity
import com.stone.tiktoksave.extractor.ExportOptions
import com.stone.tiktoksave.extractor.ExtractorException
import com.stone.tiktoksave.extractor.MediaKind
import com.stone.tiktoksave.extractor.VideoInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Trích xuất rồi tải một tùy chọn xuất. Dùng chung cho chế độ A (bấm Tải xuống)
 * và chế độ B (tải ngầm). Chạy như foreground service để không bị hệ thống dừng.
 */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val container get() = (applicationContext as App).container
    private val notificationId = 1000 + (id.hashCode() and 0xFFFF)

    private var lastReportAt = 0L
    private var lastPercent = -2

    override suspend fun getForegroundInfo(): ForegroundInfo =
        Notifications.foregroundInfo(applicationContext, id, notificationId, "Đang chuẩn bị…", null)

    override suspend fun doWork(): Result {
        val sourceUrl = inputData.getString(DownloadKeys.URL)
            ?: return Result.failure(errorData("Thiếu liên kết nguồn."))
        val requestedOptionId = inputData.getString(DownloadKeys.OPTION) ?: ExportOptions.VIDEO_MAX

        var info: VideoInfo? = null
        var label = "-"
        try {
            publish(DownloadKeys.STAGE_EXTRACT, -1, "Đang lấy thông tin video…")
            val extracted = container.extractor.extract(sourceUrl)
            info = extracted

            // Bài ảnh không có tùy chọn video: yêu cầu kiểu video (chế độ tải ngầm) thành tải tất cả ảnh.
            val optionId = ExportOptions.effectiveOptionId(extracted, requestedOptionId)
            val option = ExportOptions.build(extracted).firstOrNull { it.id == optionId }
                ?: throw DownloadException("Tùy chọn xuất không tồn tại.")
            val media = option.media
                ?: throw DownloadException(option.disabledReason ?: "Tùy chọn xuất không khả dụng.")

            if (option.kind == MediaKind.IMAGE) {
                val indices = if (option.id == ExportOptions.IMAGE_ALL) {
                    extracted.images.indices.toList()
                } else {
                    listOf(ExportOptions.imageIndex(option.id) ?: throw DownloadException("Tùy chọn ảnh không hợp lệ."))
                }
                label = if (indices.size == 1) "Ảnh" else "${indices.size} ảnh"
                val savedImages = downloadImages(extracted, indices)
                label = imageLabel(savedImages, indices.size)
                val first = savedImages.first()
                val poster = container.posters.cache(extracted.posterUrl, extracted.id, extracted.requestHeaders)
                record(extracted, label, DownloadStatus.COMPLETED, first.uri.toString(), first.mime, poster, null)
                Notifications.notifyDone(
                    applicationContext, notificationId + RESULT_OFFSET, extracted.title, first.uri, first.mime
                )
                return Result.success(
                    workDataOf(
                        DownloadKeys.FILE_URI to first.uri.toString(),
                        DownloadKeys.MIME to first.mime,
                        DownloadKeys.LABEL to label
                    )
                )
            }

            label = if (option.kind == MediaKind.VIDEO) "MP4 ${media.quality.orEmpty()}".trim() else "Âm thanh"
            publish(DownloadKeys.STAGE_DOWNLOAD, 0, extracted.title)

            val saved = container.saver.save(
                media = media,
                kind = option.kind,
                headers = extracted.requestHeaders,
                baseName = safeName("${extracted.author}_${extracted.id}")
            ) { done, total -> report(extracted.title, done, total) }

            if (option.kind == MediaKind.AUDIO) label = "Âm thanh ${saved.extension.uppercase()}"

            val poster = container.posters.cache(extracted.posterUrl, extracted.id, extracted.requestHeaders)
            record(extracted, label, DownloadStatus.COMPLETED, saved.uri.toString(), saved.mime, poster, null)
            Notifications.notifyDone(
                applicationContext, notificationId + RESULT_OFFSET, extracted.title, saved.uri, saved.mime
            )
            return Result.success(
                workDataOf(
                    DownloadKeys.FILE_URI to saved.uri.toString(),
                    DownloadKeys.MIME to saved.mime,
                    DownloadKeys.LABEL to label
                )
            )
        } catch (e: CancellationException) {
            info?.let { withContext(NonCancellable) { record(it, label, DownloadStatus.CANCELED, null, null, null, null) } }
            throw e
        } catch (e: Exception) {
            val message = friendlyMessage(e)
            info?.let { record(it, label, DownloadStatus.FAILED, null, null, null, message) }
            Notifications.notifyFailed(applicationContext, notificationId + RESULT_OFFSET, message)
            return Result.failure(errorData(message))
        }
    }

    private suspend fun report(title: String, done: Long, total: Long) {
        val percent = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else -1
        if (percent == lastPercent) return
        val now = SystemClock.elapsedRealtime()
        if (percent != 100 && now - lastReportAt < 400) return
        lastReportAt = now
        lastPercent = percent
        publish(DownloadKeys.STAGE_DOWNLOAD, percent, title)
    }

    /**
     * Tải lần lượt từng ảnh. Một ảnh lỗi không làm hỏng cả loạt: lỗi chỉ được ném khi không tải được
     * ảnh nào. Hủy tác vụ thì dừng ngay; các ảnh đã lưu xong vẫn được giữ.
     */
    private suspend fun downloadImages(info: VideoInfo, indices: List<Int>): List<MediaStoreSaver.Saved> {
        val saved = ArrayList<MediaStoreSaver.Saved>()
        var firstFailure: IOException? = null
        for ((position, index) in indices.withIndex()) {
            val image = info.images.getOrNull(index) ?: throw DownloadException("Không tìm thấy ảnh ${index + 1}.")
            publish(DownloadKeys.STAGE_DOWNLOAD, position * 100 / indices.size, "${info.title} (${position + 1}/${indices.size})")
            try {
                saved += container.saver.saveImage(
                    urls = image.urls,
                    headers = info.requestHeaders,
                    baseName = safeName("${info.author}_${info.id}_${index + 1}")
                ) { done, total -> reportImage(info.title, position, indices.size, done, total) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                if (firstFailure == null) firstFailure = e
            }
        }
        if (saved.isEmpty()) throw firstFailure ?: DownloadException("Không tải được ảnh nào.")
        return saved
    }

    private fun imageLabel(saved: List<MediaStoreSaver.Saved>, total: Int): String = when {
        total == 1 -> "Ảnh ${saved.first().extension.uppercase()}"
        saved.size == total -> "$total ảnh"
        else -> "${saved.size}/$total ảnh"
    }

    private suspend fun reportImage(title: String, position: Int, count: Int, done: Long, total: Long) {
        val within = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else 0
        val percent = (position * 100 + within) / count
        if (percent == lastPercent) return
        val now = SystemClock.elapsedRealtime()
        if (percent != 100 && now - lastReportAt < 400) return
        lastReportAt = now
        lastPercent = percent
        publish(DownloadKeys.STAGE_DOWNLOAD, percent, "$title (${position + 1}/$count)")
    }

    private suspend fun publish(stage: String, percent: Int, text: String) {
        setProgress(workDataOf(DownloadKeys.STAGE to stage, DownloadKeys.PROGRESS to percent))
        try {
            setForeground(
                Notifications.foregroundInfo(
                    applicationContext, id, notificationId, text, if (percent >= 0) percent else null
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Hệ thống không cho phép foreground ở thời điểm này: vẫn tải bình thường.
        }
    }

    private suspend fun record(
        info: VideoInfo,
        label: String,
        status: DownloadStatus,
        uri: String?,
        mime: String?,
        poster: String?,
        error: String?
    ) {
        container.history.add(
            HistoryEntity(
                videoId = info.id,
                title = info.title,
                author = info.author,
                posterPath = poster,
                fileUri = uri,
                mime = mime,
                label = label,
                status = status.name,
                errorMessage = error,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    private fun friendlyMessage(e: Exception): String = when (e) {
        is ExtractorException -> e.message ?: "Không lấy được thông tin video."
        is DownloadException -> e.message ?: "Tải xuống thất bại."
        is IOException -> "Mất kết nối mạng hoặc kết nối bị gián đoạn. Hãy thử lại."
        else -> "Lỗi không xác định: ${e.message ?: e.javaClass.simpleName}"
    }

    private fun errorData(message: String): Data = workDataOf(DownloadKeys.ERROR to message)

    /** Tên tệp: {tác giả}_{videoId}, bỏ ký tự đặc biệt, giữ chữ có dấu. */
    private fun safeName(raw: String): String =
        raw.replace(Regex("[^\\p{L}\\p{N}_.-]+"), "_")
            .trim('_', '.', '-')
            .take(80)
            .ifEmpty { "tiktok" }

    private companion object {
        const val RESULT_OFFSET = 100_000
    }
}
