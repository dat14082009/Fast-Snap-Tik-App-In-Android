package com.stone.tiktoksave.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.stone.tiktoksave.App
import com.stone.tiktoksave.AppContainer
import com.stone.tiktoksave.data.ShareMode
import com.stone.tiktoksave.download.DownloadScheduler
import com.stone.tiktoksave.extractor.ExportOptions
import com.stone.tiktoksave.extractor.LinkParser
import com.stone.tiktoksave.ui.components.ShareModeDialog
import com.stone.tiktoksave.ui.theme.ThemedContent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Đích của menu Chia sẻ. Activity trong suốt: nhận link, chọn chế độ rồi đóng ngay.
 * A: mở app và dán link. B: xếp lịch tải ngầm (MP4, chất lượng cao nhất).
 */
class ShareReceiverActivity : ComponentActivity() {

    private lateinit var container: AppContainer
    private var sharedUrl: String? = null
    private var showDialog by mutableStateOf(false)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { startBackgroundDownload() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container = (application as App).container

        val url = LinkParser.findSupportedUrl(intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
        if (url == null) {
            Toast.makeText(this, "Liên kết không thuộc TikTok hoặc Douyin.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        sharedUrl = url

        setContent {
            ThemedContent(container.settings) {
                if (showDialog) {
                    ShareModeDialog(
                        current = null,
                        onSelect = ::onModeChosen,
                        onDismiss = { finish() }
                    )
                }
            }
        }

        lifecycleScope.launch {
            val mode = container.settings.shareMode.first()
            if (mode == null) showDialog = true else dispatch(mode)
        }
    }

    private fun onModeChosen(mode: ShareMode) {
        showDialog = false
        lifecycleScope.launch {
            container.settings.setShareMode(mode)
            dispatch(mode)
        }
    }

    private fun dispatch(mode: ShareMode) {
        when (mode) {
            ShareMode.OPEN_APP -> {
                val open = Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_SHARED_URL, sharedUrl)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(open)
                finish()
            }
            ShareMode.BACKGROUND -> {
                val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                if (needsPermission) {
                    // Dù người dùng cho phép hay từ chối, việc tải vẫn tiếp tục.
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    startBackgroundDownload()
                }
            }
        }
    }

    private fun startBackgroundDownload() {
        val url = sharedUrl
        if (url != null) {
            DownloadScheduler.enqueue(applicationContext, url, ExportOptions.VIDEO_MAX)
            Toast.makeText(this, "Đang tải video ở chế độ nền…", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
