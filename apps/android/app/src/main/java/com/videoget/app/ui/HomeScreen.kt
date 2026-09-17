package com.videoget.app.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.videoget.app.FailureStage
import com.videoget.app.HomeUiState
import com.videoget.app.MainViewModel
import com.videoget.app.domain.AnalyzeState
import com.videoget.app.downloads.DownloadFormat

@Composable
fun VideoGetApp(viewModel: MainViewModel = viewModel()) {
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    VideoGetTheme {
        HomeScreen(
            state = viewModel.uiState,
            onInputChange = viewModel::updateInput,
            onPasteAndAnalyze = viewModel::pasteAndAnalyze,
            onAnalyze = { viewModel.analyze() },
            onSelectFormat = viewModel::selectFormat,
            onDownload = viewModel::download,
            onRetry = viewModel::retry,
            onCancel = viewModel::cancelDownload,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    state: HomeUiState,
    onInputChange: (String) -> Unit,
    onPasteAndAnalyze: (String?) -> Unit,
    onAnalyze: () -> Unit,
    onSelectFormat: (String) -> Unit,
    onDownload: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val busy = state.state == AnalyzeState.ANALYZING || state.state == AnalyzeState.DOWNLOADING
    val showTask = state.formats.isNotEmpty() ||
        state.state == AnalyzeState.DOWNLOADING ||
        state.completedDownload != null ||
        state.failureStage == FailureStage.DOWNLOAD ||
        state.failureStage == FailureStage.SAVE

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Video Get") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = state.input,
                            onValueChange = onInputChange,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy,
                            minLines = 1,
                            maxLines = 2,
                            label = { Text("视频链接") },
                            placeholder = { Text("粘贴 X、Instagram 或 Threads 链接") },
                            leadingIcon = {
                                Icon(Icons.Outlined.Link, contentDescription = null)
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        onPasteAndAnalyze(clipboard.getText()?.text)
                                    },
                                    enabled = !busy,
                                ) {
                                    Icon(Icons.Outlined.ContentPaste, contentDescription = "粘贴并分析")
                                }
                            },
                            isError = state.failureStage == FailureStage.ANALYZE,
                            supportingText = state.message
                                .takeIf { state.failureStage == FailureStage.ANALYZE }
                                ?.let { message -> { Text(message) } },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Go,
                            ),
                            keyboardActions = KeyboardActions(
                                onGo = { if (!busy && state.input.isNotBlank()) onAnalyze() },
                            ),
                            singleLine = false,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = { onInputChange("") },
                                modifier = Modifier.height(52.dp),
                                enabled = !busy && state.input.isNotEmpty(),
                                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                            ) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("清空")
                            }
                            Button(
                                onClick = onAnalyze,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                enabled = !busy && state.input.isNotBlank(),
                            ) {
                                if (state.state == AnalyzeState.ANALYZING) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("正在分析")
                                } else {
                                    Text(if (state.failureStage == FailureStage.ANALYZE) "重新分析" else "分析链接")
                                }
                            }
                        }

                        if (state.state == AnalyzeState.ANALYZING) {
                            Text(
                                state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (showTask) {
                    item {
                        DownloadTaskCard(
                            state = state,
                            onSelectFormat = onSelectFormat,
                            onDownload = onDownload,
                            onRetry = onRetry,
                            onCancel = onCancel,
                            onOpenVideo = {
                                val media = state.completedDownload ?: return@DownloadTaskCard
                                if (!MediaActions.openVideo(context, media)) {
                                    Toast.makeText(context, "未找到可播放该视频的应用", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onOpenLocation = {
                                if (!MediaActions.openLocation(context)) {
                                    Toast.makeText(context, "未找到可打开下载目录的文件应用", Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                    }
                }

                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
private fun DownloadTaskCard(
    state: HomeUiState,
    onSelectFormat: (String) -> Unit,
    onDownload: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onOpenVideo: () -> Unit,
    onOpenLocation: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.title.isNotBlank()) {
                Text(
                    state.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                )
            }

            when {
                state.state == AnalyzeState.DOWNLOADING -> DownloadProgress(state, onCancel)
                state.state == AnalyzeState.COMPLETED -> CompletedActions(
                    state = state,
                    onOpenVideo = onOpenVideo,
                    onOpenLocation = onOpenLocation,
                    onDownloadAgain = if (state.formats.isNotEmpty()) onDownload else onRetry,
                )
                state.failureStage == FailureStage.DOWNLOAD || state.failureStage == FailureStage.SAVE -> {
                    FailureActions(state.message, onRetry)
                }
                state.formats.isNotEmpty() -> ReadyActions(
                    formats = state.formats,
                    selectedId = state.selectedFormatId,
                    onSelect = onSelectFormat,
                    onDownload = onDownload,
                )
            }
        }
    }
}

@Composable
private fun ReadyActions(
    formats: List<DownloadFormat>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onDownload: () -> Unit,
) {
    Text("选择清晰度", style = MaterialTheme.typography.labelLarge)
    Column {
        formats.forEachIndexed { index, format ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .selectable(
                        selected = format.id == selectedId,
                        onClick = { onSelect(format.id) },
                        role = Role.RadioButton,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = format.id == selectedId, onClick = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    format.label,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Medium,
                )
            }
            if (index < formats.lastIndex) HorizontalDivider()
        }
    }
    Button(
        onClick = onDownload,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Icon(Icons.Outlined.Download, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("下载视频")
    }
}

@Composable
private fun DownloadProgress(state: HomeUiState, onCancel: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            state.progressLabel.ifBlank { "正在下载" },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
        )
        Text("${state.progress}%", style = MaterialTheme.typography.labelLarge)
    }
    LinearProgressIndicator(
        progress = { state.progress.coerceIn(0, 100) / 100f },
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp),
    )
    Text(
        state.message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(
        onClick = onCancel,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        Text("取消下载")
    }
}

@Composable
private fun CompletedActions(
    state: HomeUiState,
    onOpenVideo: () -> Unit,
    onOpenLocation: () -> Unit,
    onDownloadAgain: () -> Unit,
) {
    val media = state.completedDownload
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.CheckCircle, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("下载完成", style = MaterialTheme.typography.titleMedium)
    }
    if (media != null) {
        Text(media.displayName, style = MaterialTheme.typography.bodyMedium)
        Text(
            media.relativePath,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onOpenVideo,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Icon(Icons.Outlined.PlayCircle, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("打开视频")
        }
    }
    OutlinedButton(
        onClick = onOpenLocation,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Icon(Icons.Outlined.FolderOpen, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("查看位置")
    }
    TextButton(
        onClick = onDownloadAgain,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        Text("再次下载")
    }
}

@Composable
private fun FailureActions(message: String, onRetry: () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Outlined.ErrorOutline, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
    Button(
        onClick = onRetry,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Text("重试下载")
    }
}
