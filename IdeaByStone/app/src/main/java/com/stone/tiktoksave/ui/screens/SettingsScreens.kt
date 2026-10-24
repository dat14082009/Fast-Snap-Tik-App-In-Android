package com.stone.tiktoksave.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.stone.tiktoksave.BuildConfig
import com.stone.tiktoksave.R
import com.stone.tiktoksave.data.ThemeChoice
import com.stone.tiktoksave.ui.components.ScreenHeader
import com.stone.tiktoksave.ui.theme.LocalThemeChoice
import androidx.compose.ui.res.stringResource

/** Cài đặt có đúng 3 mục, theo thứ tự: About, Theme, History. */
@Composable
fun SettingsScreen(onBack: () -> Unit, onAbout: () -> Unit, onTheme: () -> Unit, onHistory: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ScreenHeader(title = "Cài đặt", onBack = onBack)
            SettingsRow(title = "About", onClick = onAbout)
            SettingsRow(title = "Theme", onClick = onTheme)
            SettingsRow(title = "History", onClick = onHistory)
        }
    }
}

@Composable
private fun SettingsRow(title: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.onSurface
        )
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ScreenHeader(title = "About", onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface
                )
                Text(
                    text = "Phiên bản ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface
                )
                Text(
                    text = "Tải video TikTok không dấu chìm bằng cách lấy trực tiếp tệp video gốc, " +
                        "không xử lý hình ảnh. Không thu thập dữ liệu, không quảng cáo, không đăng nhập.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface
                )
                Text(
                    text = "Hỗ trợ tải video Douyin",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface
                )
                Text(
                    text = "Chỉ dùng cho mục đích cá nhân. Hãy tôn trọng quyền tác giả của người tạo video.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface
                )
                Text(
                    text = "Dành cho mục đích cá nhân, chủ sở hữu: Stone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface
                )
            }
        }
    }
}

@Composable
fun ThemeScreen(onBack: () -> Unit, onSelect: (ThemeChoice) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val current = LocalThemeChoice.current
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ScreenHeader(title = "Theme", onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface)
                    .padding(vertical = 8.dp)
            ) {
                ThemeChoice.entries.forEach { choice ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = choice == current,
                                role = Role.RadioButton,
                                onClick = { onSelect(choice) }
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = choice == current, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(text = choice.label, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                    }
                }
            }
        }
    }
}
