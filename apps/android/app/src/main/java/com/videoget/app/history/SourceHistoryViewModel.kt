package com.videoget.app.history

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
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
    var hasMore by mutableStateOf(false)
        private set
    var exporting by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    fun refresh() = loadPage(reset = true)
    fun loadMore() = loadPage(reset = false)

    private fun loadPage(reset: Boolean) {
        if (loading) return
        loading = true
        viewModelScope.launch {
            try {
                val offset = if (reset) 0 else groups.size
                val page = withContext(Dispatchers.IO) { store.groups(offset = offset) }
                groups = if (reset) page else (groups + page).distinctBy { it.id }
                hasMore = page.size == SourceHistoryStore.PAGE_SIZE
                if (reset) selectedGroup?.let { openGroup(it) }
            } catch (_: Exception) { message = "读取下载记录失败，请重试" }
            finally { loading = false }
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

    fun export(uri: Uri, groupId: String?) {
        if (exporting) return
        exporting = true
        viewModelScope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    val output = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                        ?: error("无法打开导出文件")
                    output.bufferedWriter(Charsets.UTF_8).use { store.exportCsv(it, groupId) }
                }
                message = "已导出 $count 条来源记录"
            } catch (_: Exception) { message = "导出失败，请检查保存位置后重试" }
            finally { exporting = false }
        }
    }

    fun clearMessage() { message = null }
}
