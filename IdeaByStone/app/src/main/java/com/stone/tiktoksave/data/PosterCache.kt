package com.stone.tiktoksave.data

import android.content.Context
import com.stone.tiktoksave.extractor.await
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/** Lưu poster vào bộ nhớ trong để History vẫn hiển thị khi link ảnh trên CDN hết hạn. */
class PosterCache(private val context: Context, private val client: OkHttpClient) {

    suspend fun cache(url: String?, videoId: String, headers: Map<String, String>): String? {
        if (url.isNullOrBlank()) return null
        return try {
            withContext(Dispatchers.IO) {
                val builder = Request.Builder().url(url)
                headers.forEach { (name, value) -> builder.header(name, value) }
                client.newCall(builder.build()).await().use { response ->
                    if (!response.isSuccessful) return@use null
                    val bytes = response.body?.bytes() ?: return@use null
                    val dir = File(context.filesDir, "posters").apply { mkdirs() }
                    val file = File(dir, "${videoId}_${System.currentTimeMillis()}.jpg")
                    file.writeBytes(bytes)
                    file.absolutePath
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
}
