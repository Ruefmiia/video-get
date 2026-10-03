package com.videoget.app.ui

import android.widget.Toast
import android.content.ClipData
import android.app.DatePickerDialog
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.videoget.app.R
import com.videoget.app.history.SourceGroup
import com.videoget.app.history.SourceHistoryViewModel
import com.videoget.app.history.SourceDateRange
import java.time.LocalDate
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
    var showFilter by rememberSaveable { mutableStateOf(false) }
    var showMenu by rememberSaveable { mutableStateOf(false) }
    var cleanupCutoff by rememberSaveable { mutableStateOf<Long?>(null) }
    // Preserve the chosen export scope even if the system picker recreates the Activity/process.
    var exportGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    var exportStart by rememberSaveable { mutableStateOf<String?>(null) }
    var exportEnd by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val detailState = rememberLazyListState()
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        model.exportResult(uri, exportGroupId, SourceDateRange(
            exportStart?.let(LocalDate::parse), exportEnd?.let(LocalDate::parse),
        ))
    }
    val goBack: () -> Unit = { if (group != null) model.closeGroup() else onBack() }
    BackHandler(onBack = goBack)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && model.groups.isEmpty() && !model.loading) model.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { if (model.groups.isEmpty() && !model.loading) model.refresh() }
    LaunchedEffect(model.groups.firstOrNull()?.id) { listState.scrollToItem(0) }
    LaunchedEffect(group?.id) { if (group != null) detailState.scrollToItem(0) }
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
                    IconButton(onClick = model::refresh, enabled = !model.loading && !model.cleaning) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新记录")
                    }
                    TextButton(
                        enabled = !model.exporting && !model.pickingExport && !model.cleaning && !model.loading && (group != null || model.groups.isNotEmpty()),
                        onClick = {
                            exportGroupId = group?.id
                            val exportRange = if (group == null) model.range else SourceDateRange()
                            exportStart = exportRange.start?.toString()
                            exportEnd = exportRange.end?.toString()
                            model.prepareExport()
                            val suffix = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(
                                Instant.now().atZone(ZoneId.systemDefault()),
                            )
                            exporter.launch("video-get-sources-$suffix.csv")
                        },
                    ) { Text(if (model.exporting) "导出中…" else if (group == null && model.range != SourceDateRange()) "导出筛选" else if (group == null) "导出全部" else "导出") }
                    if (group == null) Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "记录管理")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text("清理 30 天以前记录") },
                                enabled = !model.cleaning && !model.exporting && !model.pickingExport,
                                onClick = { showMenu = false; cleanupCutoff = SourceDateRange.cleanupCutoff() })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
            LazyColumn(
                state = if (group == null) listState else detailState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (group == null) {
                    item {
                        OutlinedButton(onClick = { showFilter = true }, enabled = !model.cleaning,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text("日期：${model.range.label}")
                        }
                    }
                    if (model.groups.isEmpty()) item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            if (model.loading || model.cleaning) CircularProgressIndicator()
                            else Text(if (model.range == SourceDateRange()) "暂无下载记录\n新下载成功的文件会自动保存来源"
                                else "该日期范围内没有下载记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
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
                    if (model.groups.isNotEmpty()) item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = model::previousPage, enabled = model.hasPrevious && !model.loading && !model.cleaning,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("上一页") }
                            OutlinedButton(onClick = model::nextPage, enabled = model.hasNext && !model.loading && !model.cleaning,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(if (model.loading) "加载中…" else "下一页") }
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
    if (showFilter) AlertDialog(
        onDismissRequest = { showFilter = false }, title = { Text("按日期筛选") },
        text = {
            Column {
                listOf("全部" to SourceDateRange(), "今天" to SourceDateRange.recent(1),
                    "近 7 天" to SourceDateRange.recent(7), "近 30 天" to SourceDateRange.recent(30)).forEach { (label, range) ->
                    TextButton(onClick = { showFilter = false; model.filter(range) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(label) }
                }
                TextButton(onClick = {
                    showFilter = false
                    pickDateRange(context, model.range, model::filter)
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("自定义日期") }
            }
        }, confirmButton = { TextButton(onClick = { showFilter = false }) { Text("取消") } },
    )
    cleanupCutoff?.let { cutoff ->
        AlertDialog(onDismissRequest = { cleanupCutoff = null }, title = { Text("清理旧记录？") },
            text = { Text("将清理 ${formatTime(cutoff)} 之前的所有下载记录，不受日期筛选影响。\n\n不会删除视频或图片。记录无法恢复，建议先导出备份。") },
            confirmButton = { TextButton(onClick = { cleanupCutoff = null; model.cleanOldRecords(cutoff) }) { Text("确认清理") } },
            dismissButton = { TextButton(onClick = { cleanupCutoff = null }) { Text("取消") } })
    }
}

private fun pickDateRange(context: Context, range: SourceDateRange, onSelected: (SourceDateRange) -> Unit) {
    val initial = range.start ?: LocalDate.now()
    DatePickerDialog(context, R.style.Theme_VideoGet_DatePicker, { _, year, month, day ->
        val start = LocalDate.of(year, month + 1, day)
        val end = (range.end ?: start).let { if (it.isBefore(start)) start else it }
        DatePickerDialog(context, R.style.Theme_VideoGet_DatePicker, { _, endYear, endMonth, endDay ->
            val selectedEnd = LocalDate.of(endYear, endMonth + 1, endDay)
            if (!selectedEnd.isBefore(start)) onSelected(SourceDateRange(start, selectedEnd))
        }, end.year, end.monthValue - 1, end.dayOfMonth).apply {
            setTitle("结束日期（含当天）")
            datePicker.minDate = start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.show()
    }, initial.year, initial.monthValue - 1, initial.dayOfMonth).apply { setTitle("开始日期") }.show()
}

private fun platformLabel(group: SourceGroup): String = when (UrlRouter.match(group.sourceUrl)?.platform?.name) {
    "INSTAGRAM" -> "Instagram"
    "THREADS" -> "Threads"
    "YOUTUBE" -> "YouTube"
    else -> "X"
}

private fun formatTime(timestamp: Long): String = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    .format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
