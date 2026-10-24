package com.stone.tiktoksave.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.stone.tiktoksave.extractor.ExportOption
import com.stone.tiktoksave.extractor.ExportOptions
import com.stone.tiktoksave.extractor.TIKTOK_REFERER
import com.stone.tiktoksave.extractor.VideoInfo
import com.stone.tiktoksave.ui.DownloadUi
import com.stone.tiktoksave.ui.HomeUiState
import com.stone.tiktoksave.ui.MainViewModel
import com.stone.tiktoksave.ui.MediaActions
import com.stone.tiktoksave.ui.Phase
import com.stone.tiktoksave.ui.VerifyActivity
import com.stone.tiktoksave.ui.formatDuration
import com.stone.tiktoksave.ui.formatSize

@Composable
fun HomeScreen(vm: MainViewModel, onOpenSettings: () -> Unit, onGearLongPress: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Xác minh xong (RESULT_OK) thì tự thử lại.
    val verifyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) vm.extract()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                GearButton(onClick = onOpenSettings, onLongClick = onGearLongPress)
            }

            LinkInput(value = state.input, onChange = vm::onInputChange, onClear = vm::clear)

            Button(
                onClick = vm::extract,
                enabled = state.input.isNotBlank() && state.phase != Phase.LOADING,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Gỡ dấu chìm")
            }

            when (state.phase) {
                Phase.IDLE -> Unit
                Phase.LOADING -> LoadingRow()
                Phase.ERROR -> {
                    MessageCard(text = state.error.orEmpty(), isError = true)
                    val verifyUrl = state.verifyUrl
                    if (verifyUrl != null) {
                        Button(
                            onClick = { verifyLauncher.launch(VerifyActivity.intent(context, verifyUrl)) },
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text(state.verifyLabel ?: "Xác minh với TikTok")
                        }
                    }
                }
                Phase.READY -> ResultCard(state = state, vm = vm)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Nhấn: mở Cài đặt. Nhấn giữ: đổi chế độ chia sẻ A/B. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GearButton(onClick: () -> Unit, onLongClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = "Mở cài đặt",
                onLongClickLabel = "Đổi chế độ chia sẻ",
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = "Cài đặt",
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun LinkInput(value: String, onChange: (String) -> Unit, onClear: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val colors = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("Dán liên kết TikTok") },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (value.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Xóa")
                    }
                }
                TextButton(onClick = { clipboard.getText()?.text?.let(onChange) }) {
                    Text("Dán")
                }
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.surface,
            unfocusedContainerColor = colors.surface,
            focusedBorderColor = colors.primary,
            unfocusedBorderColor = colors.outline
        )
    )
}

@Composable
private fun LoadingRow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        Text("Đang xử lý…")
    }
}

@Composable
private fun MessageCard(text: String, isError: Boolean) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = colors.surface,
            contentColor = if (isError) colors.error else colors.onSurface
        )
    ) {
        Text(text = text, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun ResultCard(state: HomeUiState, vm: MainViewModel) {
    val info = state.info ?: return
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.surface, contentColor = colors.onSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(info.posterUrl)
                        .addHeader("Referer", info.requestHeaders["Referer"] ?: TIKTOK_REFERER)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Poster video",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(96.dp)
                        .height(128.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.onSurface.copy(alpha = 0.08f))
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = info.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(text = "@${info.author}", style = MaterialTheme.typography.bodyMedium)
                    if (info.durationSec > 0) {
                        Text(text = formatDuration(info.durationSec), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (info.isPhotoPost) {
                // Bài ảnh: thay các nút cấu hình video bằng nút tải ảnh.
                PhotoSection(
                    info = info,
                    download = state.download,
                    onDownload = vm::downloadOption,
                    onCancel = vm::cancelDownload
                )
            } else {
                Text(text = "Tùy chọn xuất", style = MaterialTheme.typography.titleSmall)
                state.options.forEach { option ->
                    OptionRow(
                        option = option,
                        selected = option.id == state.selectedOptionId,
                        onSelect = { vm.selectOption(option.id) }
                    )
                }

                DownloadSection(
                    download = state.download,
                    canStart = state.options.any { it.id == state.selectedOptionId && it.enabled },
                    onStart = vm::startDownload,
                    onCancel = vm::cancelDownload
                )
            }
        }
    }
}

@Composable
private fun OptionRow(option: ExportOption, selected: Boolean, onSelect: () -> Unit) {
    val content = LocalContentColor.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = option.enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null, enabled = option.enabled)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = option.title,
                color = if (option.enabled) Color.Unspecified else content.copy(alpha = 0.5f)
            )
            val subtitle = if (option.enabled) {
                listOfNotNull(option.media?.resolution, option.media?.sizeBytes?.let(::formatSize))
                    .joinToString(" · ")
            } else {
                option.disabledReason.orEmpty()
            }
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun DownloadSection(
    download: DownloadUi,
    canStart: Boolean,
    onStart: () -> Unit,
    onCancel: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (download) {
            DownloadUi.None -> Button(
                onClick = onStart,
                enabled = canStart,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("Tải xuống") }

            is DownloadUi.Running -> {
                Text(text = download.stage, style = MaterialTheme.typography.bodyMedium)
                if (download.percent >= 0) {
                    LinearProgressIndicator(
                        progress = { download.percent / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(text = "${download.percent}%", style = MaterialTheme.typography.bodySmall)
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Hủy") }
            }

            is DownloadUi.Done -> {
                Text(
                    text = "Đã lưu vào Download/TikTokSave" +
                        if (download.label.isNotEmpty()) " (${download.label})." else ".",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { MediaActions.open(context, download.uri, download.mime) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Mở") }
                    OutlinedButton(onClick = onStart, modifier = Modifier.weight(1f)) { Text("Tải lại") }
                }
            }

            is DownloadUi.Failed -> {
                Text(text = download.message, color = colors.error, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Thử lại") }
            }

            DownloadUi.Canceled -> {
                Text(text = "Đã hủy tải xuống.", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Tải lại") }
            }
        }
    }
}

/** Bài ảnh: nút Tải tất cả và danh sách từng ảnh, mỗi ảnh có nút Tải riêng. */
@Composable
private fun PhotoSection(
    info: VideoInfo,
    download: DownloadUi,
    onDownload: (String) -> Unit,
    onCancel: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val running = download is DownloadUi.Running
    val referer = info.requestHeaders["Referer"] ?: TIKTOK_REFERER

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Ảnh (${info.images.size})", style = MaterialTheme.typography.titleSmall)

        when (download) {
            is DownloadUi.Running -> {
                Text(text = download.stage, style = MaterialTheme.typography.bodyMedium)
                if (download.percent >= 0) {
                    LinearProgressIndicator(
                        progress = { download.percent / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(text = "${download.percent}%", style = MaterialTheme.typography.bodySmall)
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Hủy") }
            }

            is DownloadUi.Done -> {
                Text(
                    text = "Đã lưu vào Pictures/TikTokSave" +
                        if (download.label.isNotEmpty()) " (${download.label})." else ".",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(
                    onClick = { MediaActions.open(context, download.uri, download.mime) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Mở") }
            }

            is DownloadUi.Failed ->
                Text(text = download.message, color = colors.error, style = MaterialTheme.typography.bodyMedium)

            DownloadUi.Canceled -> Text(text = "Đã hủy tải xuống.", style = MaterialTheme.typography.bodyMedium)

            DownloadUi.None -> Unit
        }

        Button(
            onClick = { onDownload(ExportOptions.IMAGE_ALL) },
            enabled = !running,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Tải tất cả (${info.images.size} ảnh)")
        }

        info.images.forEachIndexed { index, image ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(image.urls.first())
                        .addHeader("Referer", referer)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Ảnh ${index + 1}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(56.dp)
                        .height(72.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.onSurface.copy(alpha = 0.08f))
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Ảnh ${index + 1}", style = MaterialTheme.typography.bodyMedium)
                    if (image.width > 0 && image.height > 0) {
                        Text(text = "${image.width}x${image.height}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                OutlinedButton(onClick = { onDownload(ExportOptions.imageId(index)) }, enabled = !running) {
                    Text("Tải")
                }
            }
        }
    }
}
