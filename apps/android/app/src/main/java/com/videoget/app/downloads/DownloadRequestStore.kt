package com.videoget.app.downloads

import android.content.Context
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import java.util.UUID

data class StoredDownloadRequest(
    val url: String,
    val selector: String,
    val directUrl: String?,
    val title: String,
)

object DownloadRequestStore {
    private val mapper = ObjectMapper()
    private val idPattern = Regex("^[0-9a-f-]{36}$")

    fun save(context: Context, request: StoredDownloadRequest): String {
        val id = UUID.randomUUID().toString()
        val directory = File(context.cacheDir, "download-requests").apply { mkdirs() }
        val target = File(directory, "$id.json")
        val root = mapper.createObjectNode().apply {
            put("url", request.url)
            put("selector", request.selector)
            request.directUrl?.let { put("directUrl", it) }
            put("title", request.title)
        }
        mapper.writeValue(target, root)
        return id
    }

    fun load(context: Context, id: String): StoredDownloadRequest {
        require(idPattern.matches(id)) { "下载任务标识无效" }
        val file = File(File(context.cacheDir, "download-requests"), "$id.json")
        val root = mapper.readTree(file)
        return StoredDownloadRequest(
            url = root.path("url").asText().takeIf(String::isNotBlank) ?: error("下载任务缺少链接"),
            selector = root.path("selector").asText("best"),
            directUrl = root.path("directUrl").asText().takeIf(String::isNotBlank),
            title = root.path("title").asText(),
        )
    }

    fun delete(context: Context, id: String?) {
        if (id != null && idPattern.matches(id)) {
            File(File(context.cacheDir, "download-requests"), "$id.json").delete()
        }
    }
}
