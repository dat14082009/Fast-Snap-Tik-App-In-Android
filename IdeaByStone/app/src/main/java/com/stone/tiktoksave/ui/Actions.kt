package com.stone.tiktoksave.ui

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.util.Locale

object MediaActions {

    fun open(context: Context, uriString: String, mime: String?) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse(uriString), mime ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            toast(context, "Không mở được tệp. Tệp có thể đã bị xóa hoặc chưa có ứng dụng phù hợp.")
        }
    }

    fun share(context: Context, uriString: String, mime: String?) {
        val uri = Uri.parse(uriString)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime ?: "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "Chia sẻ").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(chooser)
        } catch (e: Exception) {
            toast(context, "Không chia sẻ được tệp này.")
        }
    }

    private fun toast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}

fun formatDuration(totalSeconds: Int): String =
    String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)

fun formatSize(bytes: Long): String {
    val mb = bytes / 1_048_576.0
    return if (mb >= 1.0) String.format(Locale.US, "%.1f MB", mb) else "${bytes / 1024} KB"
}
