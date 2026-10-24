package com.stone.tiktoksave.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stone.tiktoksave.data.ShareMode

@Composable
fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Quay lại",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Hộp thoại chọn chế độ khi nhận link từ menu Chia sẻ.
 * Hiện một lần ở lần chia sẻ đầu tiên; sau đó mở lại bằng cách nhấn giữ biểu tượng bánh răng.
 */
@Composable
fun ShareModeDialog(current: ShareMode?, onSelect: (ShareMode) -> Unit, onDismiss: () -> Unit) {
    var selected by remember(current) { mutableStateOf(current ?: ShareMode.OPEN_APP) }
    val colors = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurface,
        title = { Text("Khi chia sẻ link từ TikTok") },
        text = {
            Column(verticalArrangement = spacedBy(8.dp)) {
                ModeOption(
                    title = "Mở app và dán link",
                    description = "Link được dán vào ô nhập và tự động xử lý.",
                    selected = selected == ShareMode.OPEN_APP,
                    onClick = { selected = ShareMode.OPEN_APP }
                )
                ModeOption(
                    title = "Tải ngầm",
                    description = "Tải ngay video MP4 chất lượng cao nhất, không mở giao diện chính. Hiện thông báo tiến trình.",
                    selected = selected == ShareMode.BACKGROUND,
                    onClick = { selected = ShareMode.BACKGROUND }
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSelect(selected) }) { Text("Lưu") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}

@Composable
private fun ModeOption(title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(text = description, style = MaterialTheme.typography.bodySmall)
        }
    }
}
