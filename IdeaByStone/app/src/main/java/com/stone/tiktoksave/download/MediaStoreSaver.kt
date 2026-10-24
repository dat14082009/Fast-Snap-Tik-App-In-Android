package com.stone.tiktoksave.download

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.stone.tiktoksave.extractor.MediaKind
import com.stone.tiktoksave.extractor.ResolvedMedia
import com.stone.tiktoksave.extractor.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException

/** Lỗi tải có thông báo thân thiện, hiển thị thẳng cho người dùng. */
class DownloadException(message: String) : IOException(message)

/**
 * Lưu video và âm thanh vào Download/TikTokSave, ảnh vào Pictures/TikTokSave, bằng MediaStore
 * (scoped storage), không cần quyền lưu trữ trên Android 10 trở lên. Tệp ở trạng thái pending
 * cho tới khi tải xong; hủy hoặc lỗi thì xóa.
 */
class MediaStoreSaver(private val context: Context, private val client: OkHttpClient) {

    data class Saved(val uri: Uri, val mime: String, val extension: String, val bytes: Long)

    suspend fun save(
        media: ResolvedMedia,
        kind: MediaKind,
        headers: Map<String, String>,
        baseName: String,
        onProgress: suspend (done: Long, total: Long) -> Unit
    ): Saved = withContext(Dispatchers.IO) {
        val response = openFirstAvailable(media.urls, headers)
        response.use { resp ->
            val body = resp.body ?: throw DownloadException("Máy chủ không trả về dữ liệu.")

            val contentType = resp.header("Content-Type").orEmpty().lowercase()
            if (contentType.startsWith("text/") || "json" in contentType) {
                throw DownloadException("Liên kết tải đã hết hạn hoặc bị chặn (máy chủ không trả về tệp media).")
            }

            val (extension, mime) = resolveType(kind, resp, contentType)
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$baseName.$extension")
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/TikTokSave")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw DownloadException("Không tạo được tệp trong thư mục Download.")

            var completed = false
            try {
                val total = body.contentLength()
                var written = 0L
                val output = resolver.openOutputStream(uri)
                    ?: throw DownloadException("Không mở được tệp để ghi.")
                output.use { out ->
                    val input = body.byteStream()
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        written += read
                        onProgress(written, total)
                    }
                    out.flush()
                }
                if (written == 0L) throw DownloadException("Tệp tải về rỗng.")
                if (total > 0 && written < total) {
                    throw DownloadException("Tệp tải về bị thiếu dữ liệu. Hãy thử lại.")
                }

                val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
                completed = true
                Saved(uri, mime, extension, written)
            } finally {
                if (!completed) {
                    withContext(NonCancellable) { runCatching { resolver.delete(uri, null, null) } }
                }
            }
        }
    }

    /**
     * Tải một ảnh. Giữ nguyên định dạng gốc nếu Android đọc được (JPEG, PNG, WebP, GIF, BMP;
     * HEIC từ Android 9; AVIF từ Android 12), nếu không thì giải mã và lưu thành JPEG.
     */
    suspend fun saveImage(
        urls: List<String>,
        headers: Map<String, String>,
        baseName: String,
        onProgress: suspend (done: Long, total: Long) -> Unit
    ): Saved = withContext(Dispatchers.IO) {
        val response = openFirstAvailable(urls, headers)
        val raw: ByteArray = response.use { resp ->
            val body = resp.body ?: throw DownloadException("Máy chủ không trả về dữ liệu.")
            val contentType = resp.header("Content-Type").orEmpty().lowercase()
            if (contentType.startsWith("text/") || "json" in contentType) {
                throw DownloadException("Liên kết ảnh đã hết hạn hoặc bị chặn (máy chủ không trả về ảnh).")
            }
            val total = body.contentLength()
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(16 * 1024)
            val input = body.byteStream()
            var read = 0L
            while (true) {
                ensureActive()
                val n = input.read(chunk)
                if (n < 0) break
                buffer.write(chunk, 0, n)
                read += n
                if (read > MAX_IMAGE_BYTES) throw DownloadException("Ảnh quá lớn.")
                onProgress(read, total)
            }
            if (total > 0 && read < total) throw DownloadException("Ảnh tải về bị thiếu dữ liệu. Hãy thử lại.")
            buffer.toByteArray()
        }
        if (raw.isEmpty()) throw DownloadException("Ảnh tải về rỗng.")

        val bytes: ByteArray
        val extension: String
        val mime: String
        when (val plan = ImageFormats.plan(raw, Build.VERSION.SDK_INT)) {
            is ImageFormats.Plan.Keep -> {
                bytes = raw
                extension = plan.detected.extension
                mime = plan.detected.mime
            }
            ImageFormats.Plan.ConvertToJpeg -> {
                bytes = toJpeg(raw)
                extension = "jpg"
                mime = "image/jpeg"
            }
        }

        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$baseName.$extension")
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/TikTokSave")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw DownloadException("Không tạo được tệp trong thư mục Pictures.")

        var completed = false
        try {
            resolver.openOutputStream(uri)?.use { out ->
                out.write(bytes)
                out.flush()
            } ?: throw DownloadException("Không mở được tệp để ghi.")
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            completed = true
            Saved(uri, mime, extension, bytes.size.toLong())
        } finally {
            if (!completed) {
                withContext(NonCancellable) { runCatching { resolver.delete(uri, null, null) } }
            }
        }
    }

    private fun toJpeg(raw: ByteArray): ByteArray {
        val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size)
            ?: throw DownloadException("Định dạng ảnh này máy không đọc được nên không chuyển sang JPEG được.")
        try {
            val out = ByteArrayOutputStream()
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)) {
                throw DownloadException("Không chuyển được ảnh sang JPEG.")
            }
            return out.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }

    /** Thử lần lượt các URL dự phòng; trả về response đầu tiên thành công. */
    private suspend fun openFirstAvailable(urls: List<String>, headers: Map<String, String>): Response {
        var lastError: IOException? = null
        var lastCode = 0
        for (url in urls) {
            try {
                val builder = Request.Builder().url(url)
                headers.forEach { (name, value) -> builder.header(name, value) }
                val response = client.newCall(builder.build()).await()
                if (response.isSuccessful) return response
                lastCode = response.code
                response.close()
            } catch (e: IOException) {
                lastError = e
            }
        }
        when (lastCode) {
            401, 403, 410 -> throw DownloadException(
                "Liên kết tải đã hết hạn hoặc bị TikTok chặn (HTTP $lastCode). Hãy thử lại."
            )
            0 -> throw lastError ?: DownloadException("Không kết nối được máy chủ tải.")
            else -> throw DownloadException("Máy chủ tải trả về lỗi HTTP $lastCode.")
        }
    }

    /** Video luôn là MP4. Âm thanh giữ đúng định dạng gốc của nguồn (mp3 hoặc m4a). */
    private fun resolveType(kind: MediaKind, response: Response, contentType: String): Pair<String, String> {
        if (kind == MediaKind.VIDEO) return "mp4" to "video/mp4"
        val path = response.request.url.encodedPath.lowercase()
        return if ("mpeg" in contentType || "mp3" in contentType || path.endsWith(".mp3")) {
            "mp3" to "audio/mpeg"
        } else {
            "m4a" to "audio/mp4"
        }
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        const val MAX_IMAGE_BYTES = 30L * 1024 * 1024
    }
}
