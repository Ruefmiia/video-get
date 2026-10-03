package com.videoget.app.history

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SourceHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SourceHistoryStore.get(application)
    var groups by mutableStateOf<List<SourceGroup>>(emptyList())
        private set
    var selectedGroup by mutableStateOf<SourceGroup?>(null)
        private set
    var files by mutableStateOf<List<SourceFile>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var hasNext by mutableStateOf(false)
        private set
    var hasPrevious by mutableStateOf(false)
        private set
    var range by mutableStateOf(SourceDateRange())
        private set
    var cleaning by mutableStateOf(false)
        private set
    private var pageJob: Job? = null
    private var revision = 0
    var pickingExport by mutableStateOf(false)
        private set
    var exporting by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    fun refresh() = loadPage()
    fun nextPage() { if (!loading && hasNext) groups.lastOrNull()?.let { loadPage(SourceCursor.of(it)) } }
    fun previousPage() { if (!loading && hasPrevious) groups.firstOrNull()?.let { loadPage(SourceCursor.of(it), true) } }
    fun filter(value: SourceDateRange) { range = value; closeGroup(); groups = emptyList(); loadPage() }

    private fun loadPage(cursor: SourceCursor? = null, newer: Boolean = false) {
        pageJob?.cancel()
        val request = ++revision
        val requestedRange = range
        loading = true
        pageJob = viewModelScope.launch {
            try {
                val page = withContext(Dispatchers.IO) { store.groups(requestedRange, cursor, newer) }
                if (request == revision) {
                    if (page.groups.isEmpty() && cursor != null) { refresh(); return@launch }
                    groups = page.groups
                    hasNext = page.hasNext
                    hasPrevious = page.hasPrevious
                    selectedGroup?.let { openGroup(it) }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (request == revision) message = "读取下载记录失败，请重试" }
            finally { if (request == revision) loading = false }
        }
    }

    fun openGroup(group: SourceGroup) {
        selectedGroup = group
        files = emptyList()
        viewModelScope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) { store.files(group.id) }
                if (selectedGroup?.id == group.id) files = loaded
            } catch (_: Exception) { message = "读取文件记录失败，请重试" }
        }
    }

    fun closeGroup() { selectedGroup = null; files = emptyList() }

    fun prepareExport() { pickingExport = true }

    fun exportResult(uri: Uri?, groupId: String?, range: SourceDateRange) {
        pickingExport = false
        if (uri != null) export(uri, groupId, range)
    }

    private fun export(uri: Uri, groupId: String?, range: SourceDateRange) {
        if (exporting) return
        exporting = true
        viewModelScope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    val output = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                        ?: error("无法打开导出文件")
                    output.bufferedWriter(Charsets.UTF_8).use { store.exportCsv(it, groupId, range) }
                }
                message = "已导出 $count 条来源记录"
            } catch (_: Exception) { message = "导出失败，请检查保存位置后重试" }
            finally { exporting = false }
        }
    }

    fun cleanOldRecords(cutoff: Long) {
        if (cleaning || exporting || pickingExport) return
        cleaning = true
        viewModelScope.launch {
            try {
                val count = withContext(Dispatchers.IO) { store.deleteBefore(cutoff) }
                closeGroup()
                refresh()
                message = "已清理 $count 组下载记录，下载文件未删除"
            } catch (_: Exception) { message = "清理失败，请重试" }
            finally { cleaning = false }
        }
    }

    fun clearMessage() { message = null }
}
