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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("VIDEO GET", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                        Text("LOCAL MEDIA TRANSFER", style = MaterialTheme.typography.labelSmall)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { safePadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(safePadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "intro") {
                Text("保存你有权使用的视频", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("在设备本地分析和下载，不需要云服务器，也不会后台读取剪贴板。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item(key = "input") {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = state.input,
                            onValueChange = onInputChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("帖子或 Reel 链接") },
                            minLines = 3,
                            isError = state.state == AnalyzeState.INVALID,
                            supportingText = if (state.state == AnalyzeState.INVALID) {
                                { Text(state.message) }
                            } else {
                                null
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        clipboard.getText()?.text?.let(onInputChange)
                                    },
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(Icons.Outlined.ContentPaste, contentDescription = "粘贴剪贴板链接")
                                }
                            },
                        )
                        Button(
                            onClick = onAnalyze,
                            enabled = state.input.isNotBlank() && state.state != AnalyzeState.ANALYZING && state.state != AnalyzeState.DOWNLOADING,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            if (state.state == AnalyzeState.ANALYZING) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.size(8.dp))
                            }
                            Icon(Icons.Outlined.Link, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text(if (state.state == AnalyzeState.ANALYZING) "正在分析" else "分析链接")
                        }
                    }
                }
            }
            if (state.formats.isNotEmpty()) {
                item(key = "formats") {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(state.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("选择下载清晰度", style = MaterialTheme.typography.labelLarge)
                            state.formats.forEach { format ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = state.selectedFormatId == format.id,
                                        onClick = { onSelectFormat(format.id) },
                                        enabled = state.state == AnalyzeState.READY || state.state == AnalyzeState.COMPLETED,
                                    )
                                    Text(format.label)
                                }
                            }
                            if (state.state == AnalyzeState.DOWNLOADING) {
                                androidx.compose.material3.LinearProgressIndicator(
                                    progress = { state.progress / 100f },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                                    Text("取消下载")
                                }
                            } else {
                                Button(
                                    onClick = onDownload,
                                    enabled = state.state == AnalyzeState.READY || state.state == AnalyzeState.COMPLETED,
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                ) {
                                    Text(if (state.state == AnalyzeState.COMPLETED) "再次下载" else "开始下载")
                                }
                            }
                        }
                    }
                }
            }
            item(key = "status") {
                StatusCard(state)
            }
            item(key = "privacy") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(Icons.Outlined.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Column {
                        Text("本地优先", fontWeight = FontWeight.Bold)
                        Text("第一版不读取浏览器 Cookie，不绕过登录、DRM 或其他访问控制。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(state: HomeUiState) {
    val container = when (state.state) {
        AnalyzeState.INVALID -> MaterialTheme.colorScheme.errorContainer
        AnalyzeState.PLANNED -> MaterialTheme.colorScheme.tertiaryContainer
        AnalyzeState.READY, AnalyzeState.COMPLETED -> MaterialTheme.colorScheme.primaryContainer
        AnalyzeState.ANALYZING, AnalyzeState.DOWNLOADING -> MaterialTheme.colorScheme.secondaryContainer
        AnalyzeState.IDLE -> MaterialTheme.colorScheme.surfaceVariant
    }
    Card(colors = CardDefaults.cardColors(containerColor = container)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp), contentAlignment = Alignment.Center) {
                Text("•", color = MaterialTheme.colorScheme.onSurface)
            }
            Column {
                Text(
                    state.match?.platform?.name ?: "等待链接",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(state.message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
