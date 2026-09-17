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
    val items: List<StoredDownloadItem> = emptyList(),
)

data class StoredDownloadItem(
    val id: String,
    val selector: String,
    val directUrl: String?,
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
            set<com.fasterxml.jackson.databind.JsonNode>("items", mapper.valueToTree(request.items))
        }
        mapper.writeValue(target, root)
        return id
    }

    fun load(context: Context, id: String): StoredDownloadRequest {
        require(idPattern.matches(id)) { "下载任务标识无效" }
        val file = File(File(context.cacheDir, "download-requests"), "$id.json")
        val root = mapper.readTree(file)
        val items = root.path("items").takeIf { it.isArray }?.map { item ->
            StoredDownloadItem(
                id = item.path("id").asText(),
                selector = item.path("selector").asText("best"),
                directUrl = nullableText(item.path("directUrl")),
            )
        }.orEmpty()
        return StoredDownloadRequest(
            url = root.path("url").asText().takeIf(String::isNotBlank) ?: error("下载任务缺少链接"),
            selector = root.path("selector").asText("best"),
            directUrl = nullableText(root.path("directUrl")),
            title = root.path("title").asText(),
            items = items,
        )
    }

    fun delete(context: Context, id: String?) {
        if (id != null && idPattern.matches(id)) {
            File(File(context.cacheDir, "download-requests"), "$id.json").delete()
        }
    }

    internal fun nullableText(node: com.fasterxml.jackson.databind.JsonNode): String? =
        node.takeUnless { it.isMissingNode || it.isNull }?.asText()?.takeIf(String::isNotBlank)
}
