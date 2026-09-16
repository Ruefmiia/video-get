package com.videoget.app.downloads

import android.content.Context
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AnalyzedMedia(
    val title: String,
    val formats: List<DownloadFormat>,
    val warning: String? = null,
)

data class DownloadFormat(
    val id: String,
    val label: String,
    val selector: String,
    val directUrl: String? = null,
)

object MediaAnalyzer {
    private val mapper = ObjectMapper()
    private val defaultFormats = listOf(
        DownloadFormat("best", "最佳画质（MP4）", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best"),
        DownloadFormat("1080", "最高 1080p", "bestvideo[height<=1080]+bestaudio/best[height<=1080]"),
        DownloadFormat("720", "最高 720p", "bestvideo[height<=720]+bestaudio/best[height<=720]"),
    )

    suspend fun analyze(context: Context, url: String): AnalyzedMedia {
        if (isThreadsUrl(url)) {
            return try {
                withContext(Dispatchers.IO) { ThreadsAnalyzer.analyze(url) }
            } catch (error: ThreadsAnalyzer.DynamicThreadsShareException) {
                ThreadsWebViewAnalyzer.analyze(context, url)
            }
        }
        return withContext(Dispatchers.IO) { analyzeWithYtDlp(url) }
    }

    private fun analyzeWithYtDlp(url: String): AnalyzedMedia {
        val request = YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("--no-warnings")
            addOption("--socket-timeout", "45")
            addOption("--retries", "3")
            addOption("--extractor-retries", "3")
        }
        return try {
            val info = YoutubeDL.getInstance().getInfo(request)
            AnalyzedMedia(
                title = info.title?.takeIf(String::isNotBlank) ?: "未命名视频",
                formats = defaultFormats,
            )
        } catch (primaryError: Exception) {
            if (!isXUrl(url)) throw primaryError
            runCatching { analyzeXFallback(url) }
                .getOrElse { fallbackError ->
                    primaryError.addSuppressed(fallbackError)
                    throw primaryError
                }
        }
    }

    internal fun parseFxTwitter(json: String): AnalyzedMedia {
        val root = mapper.readTree(json)
        if (root.path("code").asInt() != 200) error(root.path("message").asText("FxTwitter 返回错误"))
        val tweet = root.path("tweet")
        val videos = tweet.path("media").path("videos")
        if (!videos.isArray || videos.isEmpty) error("帖子中没有可下载的视频")
        val formats = videos.flatMap { video ->
            video.path("formats").filter { it.path("container").asText() == "mp4" }.mapNotNull(::toDirectFormat)
        }.distinctBy { it.directUrl }.sortedByDescending { bitrateFromId(it.id) }
        if (formats.isEmpty()) error("帖子中没有 MP4 视频格式")
        val author = tweet.path("author").path("name").asText().trim()
        val text = tweet.path("text").asText().replace(Regex("\\s+"), " ").trim().take(72)
        return AnalyzedMedia(
            title = listOf(author, text).filter(String::isNotBlank).joinToString(" — ").ifBlank { "X 视频" },
            formats = formats,
            warning = "X 原始解析失败，已使用 FxTwitter 获取公开媒体直链。",
        )
    }

    private fun analyzeXFallback(url: String): AnalyzedMedia {
        val uri = URI(url)
        val parts = uri.path.split('/').filter(String::isNotBlank)
        val endpoint = URL("https://api.fxtwitter.com/${parts.first()}/status/${parts[2]}")
        val connection = endpoint.openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 20_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "VideoGet/0.1 (+https://github.com/Ruefmiia/video-get)")
            if (connection.responseCode !in 200..299) error("FxTwitter HTTP ${connection.responseCode}")
            parseFxTwitter(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun toDirectFormat(node: JsonNode): DownloadFormat? {
        val url = node.path("url").asText().takeIf { it.startsWith("https://video.twimg.com/") } ?: return null
        val bitrate = node.path("bitrate").asLong(0)
        val dimensions = Regex("/(\\d+)x(\\d+)/").find(url)?.destructured?.let { (width, height) ->
            "${width}×${height}"
        } ?: "MP4"
        return DownloadFormat(
            id = "fx_$bitrate",
            label = "$dimensions · ${formatBitrate(bitrate)}",
            selector = "direct",
            directUrl = url,
        )
    }

    private fun isXUrl(url: String): Boolean = runCatching {
        URI(url).host.lowercase() in setOf("x.com", "www.x.com", "twitter.com", "www.twitter.com")
    }.getOrDefault(false)

    private fun isThreadsUrl(url: String): Boolean = runCatching {
        URI(url).host.lowercase() in setOf("threads.com", "www.threads.com", "threads.net", "www.threads.net")
    }.getOrDefault(false)

    private fun bitrateFromId(id: String) = id.substringAfter("fx_", "0").toLongOrNull() ?: 0

    private fun formatBitrate(value: Long): String = if (value > 0) "%.1f Mbps".format(value / 1_000_000.0) else "自适应"
}
