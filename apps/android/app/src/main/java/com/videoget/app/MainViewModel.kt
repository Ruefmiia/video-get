package com.videoget.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.videoget.app.domain.AnalyzeState
import com.videoget.app.domain.UrlMatch
import com.videoget.app.domain.UrlRouter
import com.videoget.app.downloads.DownloadFormat
import com.videoget.app.downloads.DownloadWorker
import com.videoget.app.downloads.DownloadRequestStore
import com.videoget.app.downloads.MediaAnalyzer
import com.videoget.app.downloads.StoredDownloadRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.util.UUID

data class HomeUiState(
    val input: String = "",
    val state: AnalyzeState = AnalyzeState.IDLE,
    val match: UrlMatch? = null,
    val title: String = "",
    val formats: List<DownloadFormat> = emptyList(),
    val selectedFormatId: String = "best",
    val progress: Int = 0,
    val message: String = "粘贴链接，或从 X、Instagram 分享到 Video Get。",
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val workManager = WorkManager.getInstance(application)
    private var activeWorkId: UUID? = null
    private var workObserver: Job? = null

    var uiState by mutableStateOf(HomeUiState())
        private set

    fun updateInput(value: String) {
        uiState = HomeUiState(input = value)
    }

    fun acceptSharedText(text: String?) {
        val url = text?.let(UrlRouter::extractSingleUrl)
        if (url == null) {
            uiState = HomeUiState(
                input = text.orEmpty(),
                state = AnalyzeState.INVALID,
                message = "分享内容中需要恰好包含一个支持的链接。",
            )
        } else {
            uiState = HomeUiState(input = url)
            analyze(url)
        }
    }

    fun analyze(value: String = uiState.input) {
        val match = UrlRouter.match(value)
        when {
            match == null -> uiState = HomeUiState(
                input = value,
                state = AnalyzeState.INVALID,
                message = "请输入有效的 X、Instagram 或 Threads 帖子链接。",
            )
            !match.available -> uiState = HomeUiState(
                input = value,
                state = AnalyzeState.PLANNED,
                match = match,
                message = "已识别 Threads 链接，该平台已规划但暂不可下载。",
            )
            else -> {
                uiState = HomeUiState(
                    input = match.canonicalUrl,
                    state = AnalyzeState.ANALYZING,
                    match = match,
                    message = "正在设备本地分析媒体信息…",
                )
                viewModelScope.launch {
                    runCatching { MediaAnalyzer.analyze(getApplication(), match.canonicalUrl) }
                        .onSuccess { media ->
                            uiState = uiState.copy(
                                state = AnalyzeState.READY,
                                title = media.title,
                                formats = media.formats,
                                selectedFormatId = media.formats.first().id,
                                message = media.warning ?: "分析完成，请选择清晰度后下载。",
                            )
                        }
                        .onFailure { error ->
                            uiState = uiState.copy(
                                state = AnalyzeState.INVALID,
                                message = "分析失败：${friendlyError(error)}",
                            )
                        }
                }
            }
        }
    }

    fun selectFormat(id: String) {
        if (uiState.state == AnalyzeState.READY) uiState = uiState.copy(selectedFormatId = id)
    }

    fun download() {
        val state = uiState
        val format = state.formats.firstOrNull { it.id == state.selectedFormatId } ?: return
        val result = runCatching {
            val requestId = DownloadRequestStore.save(
                getApplication(),
                StoredDownloadRequest(
                    url = state.match?.canonicalUrl ?: error("缺少下载链接"),
                    selector = format.selector,
                    directUrl = format.directUrl,
                    title = state.title,
                ),
            )
            try {
                OneTimeWorkRequestBuilder<DownloadWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setInputData(Data.Builder().putString(DownloadWorker.KEY_REQUEST_ID, requestId).build())
                    .build()
                    .also(workManager::enqueue)
            } catch (error: Exception) {
                DownloadRequestStore.delete(getApplication(), requestId)
                throw error
            }
        }
        result.onSuccess { request ->
            activeWorkId = request.id
            uiState = state.copy(state = AnalyzeState.DOWNLOADING, progress = 0, message = "下载任务已开始。")
            observe(request.id)
        }.onFailure { error ->
            uiState = state.copy(state = AnalyzeState.READY, message = "无法启动下载：${friendlyError(error)}")
        }
    }

    fun cancelDownload() {
        activeWorkId?.let(workManager::cancelWorkById)
    }

    private fun observe(id: UUID) {
        workObserver?.cancel()
        workObserver = viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(id).filterNotNull().collect { info ->
                val progress = info.progress.getInt(DownloadWorker.KEY_PROGRESS, uiState.progress)
                uiState = when (info.state) {
                    WorkInfo.State.RUNNING -> uiState.copy(
                        state = AnalyzeState.DOWNLOADING,
                        progress = progress,
                        message = "正在下载：$progress%",
                    )
                    WorkInfo.State.SUCCEEDED -> uiState.copy(
                        state = AnalyzeState.COMPLETED,
                        progress = 100,
                        message = "下载完成，视频已保存到 Movies/Video Get。",
                    )
                    WorkInfo.State.FAILED -> uiState.copy(
                        state = AnalyzeState.READY,
                        message = "下载失败：${info.outputData.getString(DownloadWorker.KEY_ERROR) ?: "请重试"}",
                    )
                    WorkInfo.State.CANCELLED -> uiState.copy(
                        state = AnalyzeState.READY,
                        message = "下载已取消。",
                    )
                    else -> uiState
                }
            }
        }
    }

    private fun friendlyError(error: Throwable): String {
        val text = error.message.orEmpty()
        return when {
            text.contains("Unsupported URL", true) -> "该链接暂不受支持"
            text.contains("I/O operation on closed file", true) -> "媒体服务连接被中断，请检查手机网络或代理后重试"
            text.contains("timed out", true) -> "媒体服务连接超时，请检查手机网络或代理后重试"
            text.contains("login", true) || text.contains("cookies", true) -> "内容需要登录，第一版不读取账号 Cookie"
            text.isBlank() -> "无法获取媒体信息，请检查网络后重试"
            else -> text.take(240)
        }
    }
}
