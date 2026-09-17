package com.videoget.app.downloads

import android.content.Context
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.videoget.app.instagram.InstagramSession
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

object InstagramAnalyzer {
    private val mapper = ObjectMapper()
    private const val SHORTCODE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    private const val APP_ID = "936619743392459"

    fun analyze(context: Context, rawUrl: String): AnalyzedMedia {
        check(InstagramSession.isLoggedIn()) { "Instagram 内容需要先连接 Instagram" }
        val shortcode = shortcode(rawUrl) ?: error("无法识别 Instagram 帖子编号")
        val mediaId = shortcodeToMediaId(shortcode)
        val connection = URL("https://www.instagram.com/api/v1/media/$mediaId/info/")
            .openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 20_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", InstagramSession.userAgent(context))
            connection.setRequestProperty("Cookie", InstagramSession.cookieHeader())
            connection.setRequestProperty("X-IG-App-ID", APP_ID)
            connection.setRequestProperty("X-Requested-With", "XMLHttpRequest")
            connection.setRequestProperty("Referer", rawUrl)
            InstagramSession.csrfToken()?.let { connection.setRequestProperty("X-CSRFToken", it) }
            when (connection.responseCode) {
                401, 403 -> error("Instagram 登录状态已失效，请重新连接 Instagram")
                !in 200..299 -> error("Instagram 媒体接口 HTTP ${connection.responseCode}")
            }
            parse(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    internal fun parse(json: String): AnalyzedMedia {
        val item = mapper.readTree(json).path("items").firstOrNull()
            ?: error("Instagram 未返回帖子媒体")
        val nodes = item.path("carousel_media").takeIf { it.isArray && !it.isEmpty }
            ?.toList() ?: listOf(item)
        val media = nodes.mapIndexedNotNull { index, node -> toDownloadItem(node, index) }
        if (media.isEmpty()) error("该 Instagram 帖子没有可下载的图片或视频")
        val author = item.path("user").path("full_name").asText().trim()
            .ifBlank { item.path("user").path("username").asText().trim() }
        val caption = item.path("caption").path("text").asText()
            .replace(Regex("\\s+"), " ").trim().take(72)
        val imageCount = media.count { it.id.startsWith("ig_image_") }
        val videoCount = media.size - imageCount
        val summary = buildList {
            if (imageCount > 0) add("$imageCount 张图片")
            if (videoCount > 0) add("$videoCount 个视频")
        }.joinToString("、")
        return AnalyzedMedia(
            title = listOf(author, caption).filter(String::isNotBlank).joinToString(" — ")
                .ifBlank { "Instagram 帖子" },
            formats = listOf(
                DownloadFormat(
                    id = "instagram_all",
                    label = if (media.size > 1) "下载全部（$summary）" else summary,
                    selector = "direct",
                    items = media,
                ),
            ),
            warning = if (media.size > 1) "已找到 $summary，将按帖子顺序全部下载。" else "已找到$summary。",
        )
    }

    private fun toDownloadItem(node: JsonNode, index: Int): DownloadItem? {
        val video = node.path("video_versions").takeIf { it.isArray }
            ?.maxByOrNull { it.path("width").asInt() * it.path("height").asInt() }
            ?.path("url")?.asText()?.takeIf(::isTrustedMediaUrl)
        if (video != null) return DownloadItem("ig_video_${index + 1}", "direct", video)
        val image = node.path("image_versions2").path("candidates").takeIf { it.isArray }
            ?.maxByOrNull { it.path("width").asInt() * it.path("height").asInt() }
            ?.path("url")?.asText()?.takeIf(::isTrustedMediaUrl)
        return image?.let { DownloadItem("ig_image_${index + 1}", "direct", it) }
    }

    internal fun shortcodeToMediaId(shortcode: String): Long {
        require(shortcode.isNotBlank()) { "Instagram 帖子编号为空" }
        return shortcode.fold(0L) { result, char ->
            val value = SHORTCODE_ALPHABET.indexOf(char)
            require(value >= 0) { "Instagram 帖子编号无效" }
            Math.addExact(Math.multiplyExact(result, 64L), value.toLong())
        }
    }

    private fun shortcode(rawUrl: String): String? = runCatching {
        URI(rawUrl).path.split('/').filter(String::isNotBlank).let { parts ->
            parts.getOrNull(1).takeIf { parts.firstOrNull() in setOf("p", "reel", "reels") }
        }
    }.getOrNull()

    private fun isTrustedMediaUrl(rawUrl: String): Boolean = runCatching {
        val uri = URI(rawUrl)
        val host = uri.host?.lowercase() ?: return@runCatching false
        uri.scheme == "https" && (host == "cdninstagram.com" || host.endsWith(".cdninstagram.com") ||
            host == "fbcdn.net" || host.endsWith(".fbcdn.net"))
    }.getOrDefault(false)
}
