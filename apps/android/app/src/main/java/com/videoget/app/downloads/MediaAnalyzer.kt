package com.videoget.app.downloads

import android.content.Context
import com.videoget.app.instagram.InstagramSession
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
    val items: List<DownloadItem> = emptyList(),
)

data class DownloadItem(
    val id: String,
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
        return withContext(Dispatchers.IO) {
            if (isXUrl(url)) {
                return@withContext runCatching { analyzeXFallback(url) }
                    .getOrElse { fallbackError ->
                        runCatching { analyzeWithYtDlp(context, url) }
                            .getOrElse { primaryError ->
                                primaryError.addSuppressed(fallbackError)
                                throw primaryError
                            }
                    }
            }
            if (isInstagramUrl(url) && InstagramSession.isLoggedIn()) {
                return@withContext runCatching { InstagramAnalyzer.analyze(context, url) }
                    .getOrElse { instagramError ->
                        runCatching { analyzeWithYtDlp(context, url) }
                            .getOrElse {
                                instagramError.addSuppressed(it)
                                throw instagramError
                            }
                    }
            }
            if (isYouTubeUrl(url)) YouTubeEngineManager.prepare(context)
            analyzeWithYtDlp(context, url)
        }
    }

    private fun analyzeWithYtDlp(context: Context, url: String): AnalyzedMedia {
        if (isInstagramUrl(url) && InstagramSession.isLoggedIn()) {
            return InstagramSession.withYtDlpCookies(context) { cookieFile, userAgent ->
                analyzeWithYtDlp(url, cookieFile.absolutePath, userAgent)
            }
        }
        return analyzeWithYtDlp(url, null, null)
    }

    private fun analyzeWithYtDlp(url: String, cookiePath: String?, userAgent: String?): AnalyzedMedia {
        val request = YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("--no-warnings")
            addOption("--socket-timeout", "45")
            addOption("--retries", "3")
            addOption("--extractor-retries", "3")
            if (cookiePath != null) addOption("--cookies", cookiePath)
            if (userAgent != null) addOption("--user-agent", userAgent)
            if (isYouTubeUrl(url)) addOption("--remote-components", "ejs:github")
        }
        val info = YoutubeDL.getInstance().getInfo(request)
        return AnalyzedMedia(
            title = info.title?.takeIf(String::isNotBlank) ?: "未命名视频",
            formats = if (isYouTubeUrl(url)) YouTubeFormats.formats else defaultFormats,
        )
    }

    internal fun parseFxTwitter(json: String): AnalyzedMedia {
        val root = mapper.readTree(json)
        if (root.path("code").asInt() != 200) error(root.path("message").asText("FxTwitter 返回错误"))
        val tweet = root.path("tweet")
        val videos = tweet.path("media").path("videos")
        if (!videos.isArray || videos.isEmpty) error("帖子中没有可下载的视频")
        val variantsByVideo = videos.mapIndexedNotNull { videoIndex, video ->
            video.path("formats")
                .filter { it.path("container").asText() == "mp4" }
                .mapNotNull { toDirectVariant(it, videoIndex) }
                .distinctBy(FxVariant::url)
                .sortedByDescending(FxVariant::bitrate)
                .takeIf(List<FxVariant>::isNotEmpty)
        }
        if (variantsByVideo.isEmpty()) error("帖子中没有 MP4 视频格式")
        val formats = fxDownloadPresets(variantsByVideo)
        val author = tweet.path("author").path("name").asText().trim()
        val text = tweet.path("text").asText().replace(Regex("\\s+"), " ").trim().take(72)
        return AnalyzedMedia(
            title = listOf(author, text).filter(String::isNotBlank).joinToString(" — ").ifBlank { "X 视频" },
            formats = formats,
            warning = if (variantsByVideo.size > 1) {
                "该帖子包含 ${variantsByVideo.size} 个视频，将按顺序下载全部视频。"
            } else {
                "已获取 X 公开视频媒体。"
            },
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

    private fun toDirectVariant(node: JsonNode, videoIndex: Int): FxVariant? {
        val url = node.path("url").asText().takeIf { it.startsWith("https://video.twimg.com/") } ?: return null
        val bitrate = node.path("bitrate").asLong(0)
        val dimensions = Regex("/(\\d+)x(\\d+)/").find(url)?.destructured?.let { (width, height) ->
            width.toInt() to height.toInt()
        }
        return FxVariant(
            id = "x_${videoIndex + 1}_$bitrate",
            url = url,
            bitrate = bitrate,
            resolution = dimensions?.let { minOf(it.first, it.second) } ?: 0,
        )
    }

    private fun fxDownloadPresets(videos: List<List<FxVariant>>): List<DownloadFormat> {
        fun preset(id: String, label: String, maximumResolution: Int?): DownloadFormat {
            val selected = videos.map { variants ->
                val candidates = maximumResolution?.let { cap -> variants.filter { it.resolution in 1..cap } }
                    .orEmpty()
                (candidates.ifEmpty { variants }).maxBy(FxVariant::bitrate)
            }
            val suffix = if (videos.size > 1) "（全部 ${videos.size} 个视频）" else ""
            return DownloadFormat(
                id = id,
                label = label + suffix,
                selector = "direct",
                items = selected.map { variant ->
                    DownloadItem(variant.id, "direct", variant.url)
                },
            )
        }
        return listOf(
            preset("fx_all_best", "最佳画质", null),
            preset("fx_all_1080", "最高 1080p", 1080),
            preset("fx_all_720", "最高 720p", 720),
        ).distinctBy { preset -> preset.items.joinToString("|") { it.directUrl.orEmpty() } }
    }

    private fun isXUrl(url: String): Boolean = runCatching {
        URI(url).host.lowercase() in setOf("x.com", "www.x.com", "twitter.com", "www.twitter.com")
    }.getOrDefault(false)

    private fun isThreadsUrl(url: String): Boolean = runCatching {
        URI(url).host.lowercase() in setOf("threads.com", "www.threads.com", "threads.net", "www.threads.net")
    }.getOrDefault(false)

    private fun isYouTubeUrl(url: String): Boolean = runCatching {
        URI(url).host.lowercase() in setOf(
            "youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be", "www.youtu.be",
        )
    }.getOrDefault(false)

    private fun isInstagramUrl(url: String): Boolean = runCatching {
        URI(url).host.lowercase() in setOf("instagram.com", "www.instagram.com")
    }.getOrDefault(false)

    private data class FxVariant(
        val id: String,
        val url: String,
        val bitrate: Long,
        val resolution: Int,
    )
}
