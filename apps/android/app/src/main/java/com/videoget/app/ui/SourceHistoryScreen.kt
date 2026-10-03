package com.videoget.app.ui

import android.widget.Toast
import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.videoget.app.domain.UrlRouter
import com.videoget.app.history.SourceGroup
import com.videoget.app.history.SourceHistoryViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceHistoryScreen(onBack: () -> Unit, model: SourceHistoryViewModel = viewModel()) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val group = model.selectedGroup
    var exportGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    var pickingExport by rememberSaveable { mutableStateOf(false) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        pickingExport = false
        if (uri != null) model.export(uri, exportGroupId)
    }
    val goBack: () -> Unit = { if (group != null) model.closeGroup() else onBack() }
    BackHandler(onBack = goBack)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) model.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { model.refresh() }
    LaunchedEffect(model.message) {
        model.message?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); model.clearMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (group == null) "下载记录" else "下载来源") },
                navigationIcon = {
                    IconButton(onClick = goBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = model::refresh, enabled = !model.loading) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新记录")
                    }
                    TextButton(
                        enabled = !model.exporting && !pickingExport && (group != null || model.groups.isNotEmpty()),
                        onClick = {
                            exportGroupId = group?.id
                            pickingExport = true
                            val suffix = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(
                                Instant.now().atZone(ZoneId.systemDefault()),
                            )
                            exporter.launch("video-get-sources-$suffix.csv")
                        },
                    ) { Text(if (model.exporting) "导出中…" else if (group == null) "导出全部" else "导出") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (group == null && model.groups.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                if (model.loading) CircularProgressIndicator()
                else Text("暂无下载记录\n新下载成功的文件会自动保存来源", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (group == null) {
                    items(model.groups, key = { it.id }) { entry ->
                        Card(
                            onClick = { model.openGroup(entry) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(entry.firstFileName, style = MaterialTheme.typography.titleMedium,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("${platformLabel(entry)} · ${formatTime(entry.downloadedAt)} · ${entry.fileCount} 个文件",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (model.hasMore) item {
                        OutlinedButton(onClick = model::loadMore, enabled = !model.loading,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(if (model.loading) "加载中…" else "加载更多")
                        }
                    }
                } else {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("${platformLabel(group)} · ${formatTime(group.downloadedAt)}",
                                    style = MaterialTheme.typography.labelLarge)
                                Text(group.sourceUrl, style = MaterialTheme.typography.bodyMedium)
                                OutlinedButton(
                                    onClick = {
                                        if (!MediaActions.openSource(context, group.sourceUrl)) {
                                            Toast.makeText(context, "未找到可打开原帖的应用", Toast.LENGTH_SHORT).show()
                                        }
                                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                ) { Text("打开原帖") }
                                TextButton(onClick = {
                                    scope.launch {
                                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("原帖链接", group.sourceUrl)))
                                        Toast.makeText(context, "链接已复制", Toast.LENGTH_SHORT).show()
                                    }
                                }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("复制链接") }
                            }
                        }
                    }
                    items(model.files, key = { it.contentUri }) { file ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(file.fileName, style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = {
                                    if (!MediaActions.openRecordedFile(context, file)) {
                                        Toast.makeText(context, "文件已移动、删除，或没有可打开的应用", Toast.LENGTH_LONG).show()
                                    }
                                }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("打开文件") }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun platformLabel(group: SourceGroup): String = when (UrlRouter.match(group.sourceUrl)?.platform?.name) {
    "INSTAGRAM" -> "Instagram"
    "THREADS" -> "Threads"
    "YOUTUBE" -> "YouTube"
    else -> "X"
}

private fun formatTime(timestamp: Long): String = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    .format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
