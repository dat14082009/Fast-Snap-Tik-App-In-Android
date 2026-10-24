package com.stone.tiktoksave.download

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.Flow
import java.util.UUID

object DownloadKeys {
    const val URL = "url"
    const val OPTION = "option"
    const val PROGRESS = "progress"
    const val STAGE = "stage"
    const val FILE_URI = "file_uri"
    const val MIME = "mime"
    const val LABEL = "label"
    const val ERROR = "error"

    const val STAGE_EXTRACT = "extract"
    const val STAGE_DOWNLOAD = "download"
}

/**
 * Một lượt tải = (link nguồn, id tùy chọn xuất). Worker trích xuất lại ngay lúc tải
 * nên link CDN luôn mới, không bị lỗi hết hạn do chờ lâu.
 */
object DownloadScheduler {

    fun enqueue(context: Context, sourceUrl: String, optionId: String): UUID {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadKeys.URL to sourceUrl, DownloadKeys.OPTION to optionId))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueue(request)
        return request.id
    }

    fun cancel(context: Context, id: UUID) {
        WorkManager.getInstance(context).cancelWorkById(id)
    }

    fun observe(context: Context, id: UUID): Flow<WorkInfo?> =
        WorkManager.getInstance(context).getWorkInfoByIdFlow(id)
}
