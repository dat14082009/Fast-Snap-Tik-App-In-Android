package com.stone.tiktoksave.ui.screens

import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.stone.tiktoksave.extractor.WebViewSupport

@Composable
fun VerifyScreen(url: String, siteName: String, onDone: () -> Unit) {
    val startUrl = url
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Trang $siteName",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Button(onClick = onDone) { Text("Xong") }
            }
            Text(
                text = "Nếu $siteName yêu cầu xác minh hoặc đăng nhập, hãy hoàn tất rồi chờ video hiện ra. Bấm Xong để app thử lại.",
                style = MaterialTheme.typography.bodySmall
            )
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        WebViewSupport.configure(this)
                        setWebViewClient(WebViewSupport.SafeClient())
                        loadUrl(startUrl)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp)),
                onRelease = { it.destroy() }
            )
        }
    }
}
