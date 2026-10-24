package com.stone.tiktoksave.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.stone.tiktoksave.data.DownloadStatus
import com.stone.tiktoksave.data.HistoryEntity
import com.stone.tiktoksave.data.HistoryRepository
import com.stone.tiktoksave.ui.MediaActions
import com.stone.tiktoksave.ui.components.ScreenHeader
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(history: HistoryRepository, onBack: () -> Unit) {
    val entries by history.items.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            ScreenHeader(title = "History", onBack = onBack)
            if (entries.isEmpty()) {
                Text(text = "Chưa có video nào được tải.", modifier = Modifier.padding(top = 16.dp))
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(entries, key = { it.id }) { entry ->
                        HistoryItem(
                            entry = entry,
                            onOpen = { entry.fileUri?.let { MediaActions.open(context, it, entry.mime) } },
                            onShare = { entry.fileUri?.let { MediaActions.share(context, it, entry.mime) } },
                            onDelete = { scope.launch { history.remove(entry) } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(entry: HistoryEntity, onOpen: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val status = DownloadStatus.fromName(entry.status)
    val canUse = status == DownloadStatus.COMPLETED && entry.fileUri != null
    val date = remember(entry.createdAt) {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("vi", "VN")).format(Date(entry.createdAt))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.surface, contentColor = colors.onSurface)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AsyncImage(
                    model = entry.posterPath?.let { File(it) },
                    contentDescription = "Poster video",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(64.dp)
                        .height(86.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.onSurface.copy(alpha = 0.08f))
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(text = "@${entry.author}", style = MaterialTheme.typography.bodySmall)
                    Text(text = "$date · ${entry.label}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = status.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (status == DownloadStatus.FAILED) colors.error else colors.onSurface
                    )
                    if (status == DownloadStatus.FAILED && !entry.errorMessage.isNullOrEmpty()) {
                        Text(
                            text = entry.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.error,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onOpen, enabled = canUse) { Text("Mở") }
                TextButton(onClick = onShare, enabled = canUse) { Text("Chia sẻ") }
                TextButton(onClick = onDelete) { Text("Xóa") }
            }
        }
    }
}
