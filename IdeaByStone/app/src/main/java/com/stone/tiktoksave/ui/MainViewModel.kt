package com.stone.tiktoksave.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.stone.tiktoksave.App
import com.stone.tiktoksave.download.DownloadKeys
import com.stone.tiktoksave.download.DownloadScheduler
import com.stone.tiktoksave.extractor.ExportOption
import com.stone.tiktoksave.extractor.ExportOptions
import com.stone.tiktoksave.extractor.ExtractorException
import com.stone.tiktoksave.extractor.LinkParser
import com.stone.tiktoksave.extractor.VideoInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class Phase { IDLE, LOADING, READY, ERROR }

sealed interface DownloadUi {
    data object None : DownloadUi
    data class Running(val workId: UUID, val stage: String, val percent: Int) : DownloadUi
    data class Done(val uri: String, val mime: String?, val label: String) : DownloadUi
    data class Failed(val message: String) : DownloadUi
    data object Canceled : DownloadUi
}

data class HomeUiState(
    val input: String = "",
    val phase: Phase = Phase.IDLE,
    val sourceUrl: String? = null,
    val info: VideoInfo? = null,
    val options: List<ExportOption> = emptyList(),
    val selectedOptionId: String? = null,
    val error: String? = null,
    /** Có giá trị khi TikTok yêu cầu xác minh: UI hiện nút mở màn Xác minh với link này. */
    val verifyUrl: String? = null,
    /** Nhãn nút mở trang TikTok trong WebView (xác minh hoặc chỉ để xem). */
    val verifyLabel: String? = null,
    val download: DownloadUi = DownloadUi.None
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app: Application = application
    private val container = (application as App).container

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var extractJob: Job? = null
    private var observeJob: Job? = null

    fun onInputChange(text: String) {
        _state.update { it.copy(input = text) }
    }

    /** Link nhận từ menu Chia sẻ (chế độ A): dán vào ô nhập và tự bấm Gỡ dấu chìm. */
    fun onSharedLink(text: String) {
        val url = LinkParser.findSupportedUrl(text)
        if (url == null) {
            showError("Liên kết không thuộc TikTok hoặc Douyin.")
            return
        }
        _state.update { it.copy(input = url) }
        extract()
    }

    fun clear() {
        extractJob?.cancel()
        observeJob?.cancel()
        _state.value = HomeUiState()
    }

    fun extract() {
        val url = LinkParser.findSupportedUrl(_state.value.input)
        if (url == null) {
            showError("Liên kết không hợp lệ hoặc không thuộc TikTok/Douyin.")
            return
        }
        val site = LinkParser.platformOf(url)?.displayName ?: "TikTok"
        extractJob?.cancel()
        observeJob?.cancel()
        _state.update {
            it.copy(
                phase = Phase.LOADING,
                error = null,
                verifyUrl = null,
                verifyLabel = null,
                info = null,
                options = emptyList(),
                selectedOptionId = null,
                download = DownloadUi.None
            )
        }
        extractJob = viewModelScope.launch {
            try {
                val info = container.extractor.extract(url)
                val options = ExportOptions.build(info)
                val preferred = options.firstOrNull { it.id == ExportOptions.VIDEO_MAX && it.enabled }
                    ?: options.firstOrNull { it.enabled }
                _state.update {
                    it.copy(
                        phase = Phase.READY,
                        sourceUrl = url,
                        info = info,
                        options = options,
                        selectedOptionId = preferred?.id
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ExtractorException.VerificationRequired) {
                showError(e.message ?: "$site yêu cầu xác minh.", verifyUrl = url, verifyLabel = "Xác minh với $site")
            } catch (e: ExtractorException.Parse) {
                // Trang có phản hồi nhưng không đọc được: cho mở trang để xem TikTok đang hiển thị gì.
                showError(e.message ?: "Không đọc được dữ liệu từ $site.", verifyUrl = url, verifyLabel = "Mở trang $site để xem")
            } catch (e: ExtractorException) {
                showError(e.message ?: "Không lấy được thông tin video.")
            } catch (e: Exception) {
                showError("Lỗi không xác định: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun selectOption(id: String) {
        _state.update { it.copy(selectedOptionId = id) }
    }

    fun startDownload() {
        val current = _state.value
        val url = current.sourceUrl ?: return
        val optionId = current.selectedOptionId ?: return
        observe(DownloadScheduler.enqueue(app, url, optionId))
    }

    /** Tải một tùy chọn cụ thể (bài ảnh: từng ảnh hoặc tất cả). */
    fun downloadOption(optionId: String) {
        val url = _state.value.sourceUrl ?: return
        observe(DownloadScheduler.enqueue(app, url, optionId))
    }

    fun cancelDownload() {
        (_state.value.download as? DownloadUi.Running)?.let { DownloadScheduler.cancel(app, it.workId) }
    }

    private fun observe(workId: UUID) {
        observeJob?.cancel()
        _state.update { it.copy(download = DownloadUi.Running(workId, "Đang chuẩn bị…", -1)) }
        observeJob = viewModelScope.launch {
            DownloadScheduler.observe(app, workId).collect { work ->
                if (work == null) return@collect
                val ui: DownloadUi = when (work.state) {
                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED ->
                        DownloadUi.Running(workId, "Đang chờ bắt đầu…", -1)
                    WorkInfo.State.RUNNING -> DownloadUi.Running(
                        workId,
                        stageText(work.progress.getString(DownloadKeys.STAGE)),
                        work.progress.getInt(DownloadKeys.PROGRESS, -1)
                    )
                    WorkInfo.State.SUCCEEDED -> DownloadUi.Done(
                        uri = work.outputData.getString(DownloadKeys.FILE_URI).orEmpty(),
                        mime = work.outputData.getString(DownloadKeys.MIME),
                        label = work.outputData.getString(DownloadKeys.LABEL).orEmpty()
                    )
                    WorkInfo.State.FAILED ->
                        DownloadUi.Failed(work.outputData.getString(DownloadKeys.ERROR) ?: "Tải xuống thất bại.")
                    WorkInfo.State.CANCELLED -> DownloadUi.Canceled
                }
                _state.update { it.copy(download = ui) }
            }
        }
    }

    private fun stageText(stage: String?): String = when (stage) {
        DownloadKeys.STAGE_EXTRACT -> "Đang lấy thông tin video…"
        DownloadKeys.STAGE_DOWNLOAD -> "Đang tải xuống…"
        else -> "Đang xử lý…"
    }

    private fun showError(message: String, verifyUrl: String? = null, verifyLabel: String? = null) {
        _state.update {
            it.copy(
                phase = Phase.ERROR,
                error = message,
                verifyUrl = verifyUrl,
                verifyLabel = verifyLabel,
                info = null,
                options = emptyList(),
                selectedOptionId = null,
                download = DownloadUi.None
            )
        }
    }
}
