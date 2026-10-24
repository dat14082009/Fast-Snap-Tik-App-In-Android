package com.stone.tiktoksave.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.stone.tiktoksave.App
import com.stone.tiktoksave.extractor.LinkParser
import com.stone.tiktoksave.extractor.WebViewSupport
import com.stone.tiktoksave.ui.screens.VerifyScreen
import com.stone.tiktoksave.ui.theme.ThemedContent

/**
 * Mở trang TikTok hoặc Douyin trong WebView hiển thị để người dùng giải bước xác minh (nếu có).
 * Cookie lưu trong kho chung của WebView nên lần trích xuất ẩn sau đó dùng được ngay.
 */
class VerifyActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val url = intent.getStringExtra(EXTRA_URL) ?: "https://www.tiktok.com/"
        val container = (application as App).container
        val siteName = LinkParser.platformOf(url)?.displayName ?: "TikTok"
        setContent {
            ThemedContent(container.settings) {
                VerifyScreen(url = url, siteName = siteName, onDone = ::finishOk)
            }
        }
    }

    private fun finishOk() {
        WebViewSupport.flushCookies()
        setResult(RESULT_OK)
        finish()
    }

    companion object {
        private const val EXTRA_URL = "com.stone.tiktoksave.VERIFY_URL"

        fun intent(context: Context, url: String): Intent =
            Intent(context, VerifyActivity::class.java).putExtra(EXTRA_URL, url)
    }
}
