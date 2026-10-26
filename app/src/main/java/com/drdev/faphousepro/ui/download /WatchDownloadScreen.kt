package com.drdev.faphousepro.ui.download

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.drdev.faphousepro.data.download.DownloadEntity
import com.drdev.faphousepro.data.download.DownloadState
import com.drdev.faphousepro.network.QualityDto
import com.drdev.faphousepro.network.VideoInfoDto
import com.drdev.faphousepro.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchDownloadScreen(
    vm: WatchDownloadViewModel,
    initialMode: String,
    onClose: () -> Unit,
    onOpenPlayer: (path: String, title: String, subsJson: String) -> Unit,
    onShare: (path: String, mime: String, title: String) -> Unit
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    val persisted by vm.persistedList.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(if (initialMode == WatchDownloadActivity.MODE_DOWNLOADS) 1 else 0) }

    Scaffold(
        containerColor = FhBg,
        topBar = {
            TopAppBar(
                title = { Text("Watch & Download", color = FhText, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = FhText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = FhBg)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).background(FhBg)
        ) {
            TabRow(selectedTabIndex = tab, containerColor = FhBg, contentColor = FhGold) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text("New Download", color = if (tab == 0) FhGold else FhMuted) }
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text("My Downloads", color = if (tab == 1) FhGold else FhMuted) }
                )
            }

            if (tab == 0) {
                NewDownloadTab(
                    ui = ui,
                    active = active,
                    onUrlChange = vm::onUrlChange,
                    onFetch = vm::fetch,
                    onPaste = {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val text = cm?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                        vm.onUrlChange(text)
                    },
                    onStartDownload = vm::startDownload,
                    onClearError = vm::clearError
                )
            } else {
                DownloadsTab(
                    active = active,
                    persisted = persisted,
                    onCancel = vm::cancelDownload,
                    onDelete = vm::deleteDownload,
                    onPlay = { entity ->
                        val path = entity.playbackPath.ifBlank { entity.filePath }
                        if (path.isNotBlank()) onOpenPlayer(path, entity.title, entity.subtitlesJson)
                    },
                    onShare = { entity ->
                        val path = entity.sharePath.ifBlank { entity.playbackPath }
                        if (path.isNotBlank()) {
                            val mime = if (path.endsWith(".mp4", ignoreCase = true)) "video/mp4" else "video/mp2t"
                            onShare(path, mime, entity.title)
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewDownloadTab(
    ui: WatchUiState,
    active: List<ActiveDownload>,
    onUrlChange: (String) -> Unit,
    onFetch: () -> Unit,
    onPaste: () -> Unit,
    onStartDownload: (QualityDto) -> Unit,
    onClearError: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedTextField(
                value = ui.url,
                onValueChange = onUrlChange,
                singleLine = true,
                placeholder = { Text("Paste a faphouse video URL", color = FhMuted) },
                leadingIcon = { Icon(Icons.Default.Link, null, tint = FhMuted) },
                trailingIcon = {
                    IconButton(onClick = onPaste) {
                        Icon(Icons.Default.ContentPaste, "Paste", tint = FhGold)
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = FhCard,
                    unfocusedContainerColor = FhCard,
                    focusedBorderColor = FhGold,
                    unfocusedBorderColor = FhBorder,
                    focusedTextColor = FhText,
                    unfocusedTextColor = FhText,
                    cursorColor = FhGold
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Button(
                onClick = onFetch,
                enabled = !ui.loading && ui.url.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FhGold, contentColor = FhBg,
                    disabledContainerColor = FhBorder, disabledContentColor = FhMuted
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (ui.loading) {
                    CircularProgressIndicator(
                        color = FhBg, strokeWidth = 2.dp, modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Fetching…", fontWeight = FontWeight.Bold)
                } else {
                    Text("Fetch Video Info", fontWeight = FontWeight.Bold)
                }
            }
        }

        ui.error?.let { err ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = FhCard),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = FhDanger)
                        Spacer(Modifier.width(8.dp))
                        Text(err, color = FhText, modifier = Modifier.weight(1f))
                        IconButton(onClick = onClearError) {
                            Icon(Icons.Default.Close, "Dismiss", tint = FhMuted)
                        }
                    }
                }
            }
        }

        ui.videoInfo?.let { info ->
            item { VideoSummaryCard(info) }
            item {
                Text("Choose quality", color = FhText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            items(info.qualities) { q -> QualityRow(q, onStartDownload = { onStartDownload(q) }) }
            if (info.qualities.isEmpty()) {
                item {
                    Text(
                        "No downloadable qualities were found for this video.",
                        color = FhMuted, fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoSummaryCard(info: VideoInfoDto) {
    Card(
        colors = CardDefaults.cardColors(containerColor = FhCard),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(110.dp, 70.dp).clip(RoundedCornerShape(8.dp)).background(FhBorder)
            ) {
                if (info.thumbnail.isNotBlank()) {
                    AsyncImage(
                        model = info.thumbnail,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    info.title,
                    color = FhText,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(4.dp))
                Text("${info.studio} · ${info.durationFormatted}", color = FhMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun QualityRow(q: QualityDto, onStartDownload: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = FhCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable { onStartDownload() }
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(FhGold),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    q.quality.replace("p", "").take(4),
                    color = FhBg, fontWeight = FontWeight.Black, fontSize = 13.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${q.quality} · ${q.type.uppercase()}",
                    color = FhText, fontWeight = FontWeight.Bold, fontSize = 14.sp
                )
                Text("~%.1f MB".format(q.sizeMb), color = FhMuted, fontSize = 12.sp)
            }
            Icon(Icons.Default.Download, "Download", tint = FhGold)
        }
    }
}

@Composable
private fun DownloadsTab(
    active: List<ActiveDownload>,
    persisted: List<DownloadEntity>,
    onCancel: (String) -> Unit,
    onDelete: (String) -> Unit,
    onPlay: (DownloadEntity) -> Unit,
    onShare: (DownloadEntity) -> Unit
) {
    val merged = remember(active, persisted) {
        val byId = LinkedHashMap<String, Any>()
        active.forEach { byId[it.entity.id] = it }
        persisted.forEach { if (!byId.containsKey(it.id)) byId[it.id] = it }
        byId.values.toList()
    }

    if (merged.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.FileDownload, null, tint = FhMuted, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(12.dp))
                Text("No downloads yet.", color = FhMuted)
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(merged) { item ->
            when (item) {
                is ActiveDownload -> ActiveRow(item, onCancel)
                is DownloadEntity -> PersistedRow(item, onPlay, onShare, onDelete)
            }
        }
    }
}

@Composable
private fun ActiveRow(a: ActiveDownload, onCancel: (String) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = FhCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    a.entity.title,
                    color = FhText,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    fontSize = 14.sp
                )
                IconButton(onClick = { onCancel(a.entity.id) }) {
                    Icon(Icons.Default.Close, "Cancel", tint = FhDanger)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${a.entity.qualityLabel} · ${if (a.bytesTotal > 0) "%.1f / %.1f MB".format(a.bytesDownloaded/1024.0/1024.0, a.bytesTotal/1024.0/1024.0) else "%.1f MB".format(a.bytesDownloaded/1024.0/1024.0)}",
                color = FhMuted, fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            if (a.state == DownloadState.FAILED) {
                Text("Failed: ${a.error ?: "unknown"}", color = FhDanger, fontSize = 12.sp)
            } else {
                LinearProgressIndicator(
                    progress = { a.progressPct / 100f },
                    color = FhGold,
                    trackColor = FhBorder,
                    modifier = Modifier.fillMaxWidth().height(6.dp)
                )
                Spacer(Modifier.height(6.dp))
                Text("${a.progressPct}%", color = FhGold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun PersistedRow(
    e: DownloadEntity,
    onPlay: (DownloadEntity) -> Unit,
    onShare: (DownloadEntity) -> Unit,
    onDelete: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = FhCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                e.title,
                color = FhText,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(4.dp))
            Row {
                Text("${e.qualityLabel} · ", color = FhMuted, fontSize = 12.sp)
                Text(
                    when (e.state) {
                        DownloadState.COMPLETED -> "Completed · ${e.displaySizeMb}"
                        DownloadState.FAILED -> "Failed: ${e.errorMessage ?: "unknown"}"
                        DownloadState.CANCELLED -> "Cancelled"
                        else -> e.state.name.lowercase()
                    },
                    color = when (e.state) {
                        DownloadState.COMPLETED -> FhSuccess
                        DownloadState.FAILED -> FhDanger
                        else -> FhMuted
                    },
                    fontSize = 12.sp
                )
            }
            if (e.state == DownloadState.COMPLETED) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onPlay(e) },
                        colors = ButtonDefaults.buttonColors(containerColor = FhGold, contentColor = FhBg),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Play", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { onShare(e) },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = FhText),
                        border = androidx.compose.foundation.BorderStroke(1.dp, FhBorder)
                    ) {
                        Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Share")
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { onDelete(e.id) }) {
                        Icon(Icons.Default.Delete, "Delete", tint = FhDanger)
                    }
                }
            } else {
                Row {
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { onDelete(e.id) }) {
                        Icon(Icons.Default.Delete, "Delete", tint = FhDanger)
                    }
                }
            }
        }
    }
}