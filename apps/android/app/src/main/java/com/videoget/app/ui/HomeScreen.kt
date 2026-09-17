package com.videoget.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.videoget.app.HomeUiState
import com.videoget.app.MainViewModel
import com.videoget.app.domain.AnalyzeState

@Composable
fun VideoGetApp(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.download()
    }
    val startDownload = {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.download()
        }
    }
    VideoGetTheme {
        HomeScreen(
            state = viewModel.uiState,
            onInputChange = viewModel::updateInput,
            onAnalyze = viewModel::analyze,
            onSelectFormat = viewModel::selectFormat,
            onDownload = startDownload,
            onCancel = viewModel::cancelDownload,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    state: HomeUiState,
    onInputChange: (String) -> Unit,
    onAnalyze: () -> Unit,
    onSelectFormat: (String) -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val busy = state.state == AnalyzeState.ANALYZING || state.state == AnalyzeState.DOWNLOADING

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "VIDEO GET",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.6.sp,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { safePadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(safePadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "input") {
                    InputCard(
                        state = state,
                        busy = busy,
                        onInputChange = onInputChange,
                        onPaste = { clipboard.getText()?.text?.let(onInputChange) },
                        onClear = { onInputChange("") },
                        onAnalyze = onAnalyze,
                    )
                }

                if (state.formats.isNotEmpty()) {
                    item(key = "formats") {
                        FormatCard(
                            state = state,
                            onSelectFormat = onSelectFormat,
                            onDownload = onDownload,
                            onCancel = onCancel,
                        )
                    }
                }

                if (state.state != AnalyzeState.IDLE) {
                    item(key = "status") {
                        StatusCard(state)
                    }
                }
            }
        }
    }
}

@Composable
private fun InputCard(
    state: HomeUiState,
    busy: Boolean,
    onInputChange: (String) -> Unit,
    onPaste: () -> Unit,
    onClear: () -> Unit,
    onAnalyze: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("视频链接", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = state.input,
                onValueChange = onInputChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("粘贴 X、Instagram 或 Threads 链接") },
                minLines = 3,
                maxLines = 5,
                enabled = !busy,
                isError = state.state == AnalyzeState.INVALID,
                supportingText = if (state.state == AnalyzeState.INVALID) {
                    { Text(state.message) }
                } else {
                    null
                },
                trailingIcon = {
                    IconButton(
                        onClick = onPaste,
                        enabled = !busy,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.Outlined.ContentPaste, contentDescription = "粘贴链接")
                    }
                },
                shape = RoundedCornerShape(12.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onClear,
                    enabled = state.input.isNotEmpty() && !busy,
                    modifier = Modifier.height(52.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                ) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("清空")
                }
                Button(
                    onClick = onAnalyze,
                    enabled = state.input.isNotBlank() && !busy,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    if (state.state == AnalyzeState.ANALYZING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(19.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Outlined.Link, contentDescription = null)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.state == AnalyzeState.ANALYZING) "正在分析" else "分析链接")
                }
            }
        }
    }
}

@Composable
private fun FormatCard(
    state: HomeUiState,
    onSelectFormat: (String) -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "选择清晰度",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            state.formats.forEach { format ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = state.selectedFormatId == format.id,
                        onClick = { onSelectFormat(format.id) },
                        enabled = state.state == AnalyzeState.READY || state.state == AnalyzeState.COMPLETED,
                    )
                    Text(format.label, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.state == AnalyzeState.DOWNLOADING) {
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Text("取消下载")
                    }
                } else {
                    Button(
                        onClick = onDownload,
                        enabled = state.state == AnalyzeState.READY || state.state == AnalyzeState.COMPLETED,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Icon(Icons.Outlined.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.state == AnalyzeState.COMPLETED) "再次下载" else "开始下载")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(state: HomeUiState) {
    val (icon, label) = when (state.state) {
        AnalyzeState.INVALID -> Icons.Outlined.ErrorOutline to "处理失败"
        AnalyzeState.PLANNED -> Icons.Outlined.Info to "暂不支持"
        AnalyzeState.READY -> Icons.Outlined.CheckCircle to "分析完成"
        AnalyzeState.COMPLETED -> Icons.Outlined.CheckCircle to "下载完成"
        AnalyzeState.ANALYZING -> Icons.Outlined.Sync to "正在分析"
        AnalyzeState.DOWNLOADING -> Icons.Outlined.Download to "正在下载"
        AnalyzeState.IDLE -> Icons.Outlined.Info to "等待链接"
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            StatusIcon(icon, label)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusIcon(icon: ImageVector, description: String) {
    Icon(
        imageVector = icon,
        contentDescription = description,
        modifier = Modifier.size(24.dp),
        tint = MaterialTheme.colorScheme.onSurface,
    )
}
