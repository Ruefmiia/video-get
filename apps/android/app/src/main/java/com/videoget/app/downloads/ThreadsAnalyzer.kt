package com.videoget.app.downloads

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object ThreadsAnalyzer {
    private const val GOOGLEBOT = "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"
    private const val BROWSER_UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/131.0 Mobile Safari/537.36"
    private const val MAX_HTML_BYTES = 6 * 1024 * 1024
    private const val MAX_REDIRECTS = 6
    private val mapper = ObjectMapper()
    private val scriptPattern = Regex(
        """<script\s+type=["']application/json["'][^>]*>(.*?)</script>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val tagPattern = Regex("""<(?:meta|link)\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val attributePattern = Regex("""([\w:-]+)\s*=\s*(["'])(.*?)\2""", RegexOption.IGNORE_CASE)
    private val mediaIdPattern = Regex("""\b\d{12,24}\b""")

    fun analyze(url: String): AnalyzedMedia {
        val primary = downloadPage(url, GOOGLEBOT)
        return try {
            parsePage(primary.html, url, primary.finalUrl)
        } catch (error: TargetNotFoundException) {
            val browserPage = downloadPage(url, BROWSER_UA)
            parsePage(browserPage.html, url, browserPage.finalUrl)
        }
    }

    internal fun parsePage(html: String, requestUrl: String): AnalyzedMedia =
        parsePage(html, requestUrl, requestUrl)

    internal fun parsePage(html: String, requestUrl: String, finalUrl: String): AnalyzedMedia {
        val targetCodes = linkedSetOf<String>().apply {
            canonicalCode(requestUrl)?.let(::add)
            canonicalCode(finalUrl)?.let(::add)
            pageCanonicalUrls(html).mapNotNull(::canonicalCode).forEach(::add)
        }
        val targetIds = extractTargetMediaIds(html, finalUrl)
        val candidates = mutableListOf<JsonNode>()
        scriptPattern.findAll(html).forEach { match ->
            runCatching { mapper.readTree(match.groupValues[1]) }.getOrNull()?.let {
                collectPostCandidates(it, candidates)
            }
        }
        val post = candidates.firstOrNull { candidate ->
            postCode(candidate)?.let(targetCodes::contains) == true ||
                candidateIds(candidate).any(targetIds::contains)
        } ?: when {
            isShareUrl(requestUrl) && targetCodes.isEmpty() && targetIds.isEmpty() ->
                throw DynamicThreadsShareException(
                    "Threads 分享页通过 xmt 动态加载目标帖子，静态页面未提供可验证的媒体标识",
                )
            else -> throw TargetNotFoundException("目标帖子未出现在 Threads 返回的静态页面数据中")
        }

        val caption = post.path("caption").path("text").asText().trim()
        val username = post.path("user").path("username").asText().trim()
        val formats = formatsFromPost(post)
        if (formats.isEmpty()) {
            error("已找到目标 Threads 帖子，但帖子没有可下载的视频或使用了尚未支持的媒体结构")
        }
        val code = postCode(post).orEmpty()
        val title = caption.lineSequence().firstOrNull()?.take(72).orEmpty().ifBlank {
            if (username.isNotBlank()) "Threads video by $username" else "Threads video $code"
        }
        return AnalyzedMedia(title = title, formats = formats)
    }

    private fun formatsFromPost(post: JsonNode): List<DownloadFormat> {
        val versions = mutableListOf<MediaVersion>()
        collectMediaVersions(post, versions)
        val seenPaths = mutableSetOf<String>()
        return versions.mapNotNull { version ->
            val path = runCatching { URI(version.url).path }.getOrNull() ?: return@mapNotNull null
            if (!seenPaths.add(path)) return@mapNotNull null
            val width = version.width
            val height = version.height
            val resolution = if (width > 0 && height > 0) "${width}×${height}" else "MP4"
            val number = seenPaths.size
            val partLabel = if (number > 1) "视频 $number · " else ""
            DownloadFormat(
                id = "threads_${number}_${version.type}",
                label = "$partLabel$resolution",
                selector = "direct",
                directUrl = version.url,
            )
        }
    }

    private fun collectMediaVersions(node: JsonNode, output: MutableList<MediaVersion>) {
        when {
            node.isObject -> {
                val fallbackWidth = node.path("original_width").asInt(0)
                val fallbackHeight = node.path("original_height").asInt(0)
                listOf("video_versions", "video_resources").forEach { key ->
                    node.path(key).takeIf(JsonNode::isArray)?.forEach { version ->
                        addVersion(version, fallbackWidth, fallbackHeight, output)
                    }
                }
                listOf("video_url", "playback_url", "video_src").forEach { key ->
                    node.path(key).asText().takeIf(::isAllowedMediaUrl)?.let { url ->
                        output.add(MediaVersion(url, fallbackWidth, fallbackHeight, "direct"))
                    }
                }
                node.path("video_dash_manifest").asText().takeIf(String::isNotBlank)?.let { manifest ->
                    extractDashVideoUrls(manifest).forEach { url ->
                        output.add(MediaVersion(url, fallbackWidth, fallbackHeight, "dash"))
                    }
                }
                node.elements().forEachRemaining { collectMediaVersions(it, output) }
            }
            node.isArray -> node.elements().forEachRemaining { collectMediaVersions(it, output) }
        }
    }

    private fun addVersion(
        version: JsonNode,
        fallbackWidth: Int,
        fallbackHeight: Int,
        output: MutableList<MediaVersion>,
    ) {
        val url = version.path("url").asText()
        if (!isAllowedMediaUrl(url)) return
        output.add(
            MediaVersion(
                url = url,
                width = version.path("width").asInt(0).takeIf { it > 0 } ?: fallbackWidth,
                height = version.path("height").asInt(0).takeIf { it > 0 } ?: fallbackHeight,
                type = version.path("type").asText("http"),
            ),
        )
    }

    private fun extractDashVideoUrls(manifest: String): List<String> {
        val decoded = decodeHtmlAttribute(manifest)
        val videoSections = mutableListOf<String>()
        Regex(
            """<AdaptationSet\b[^>]*(?:contentType|mimeType)=["']video(?:/[^"']*)?["'][^>]*>.*?</AdaptationSet>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).findAll(decoded).mapTo(videoSections, MatchResult::value)
        Regex(
            """<Representation\b[^>]*(?:contentType|mimeType)=["']video(?:/[^"']*)?["'][^>]*>.*?</Representation>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).findAll(decoded).mapTo(videoSections, MatchResult::value)
        return videoSections.flatMap { section ->
            Regex("""<BaseURL[^>]*>(.*?)</BaseURL>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .findAll(section)
                .map { decodeHtmlAttribute(it.groupValues[1].trim()) }
                .filter(::isAllowedMediaUrl)
                .toList()
        }
    }

    private fun collectPostCandidates(node: JsonNode, output: MutableList<JsonNode>) {
        when {
            node.isObject -> {
                val hasIdentity = postCode(node) != null || candidateIds(node).isNotEmpty()
                val hasPostShape = node.has("caption") || node.has("user") || node.has("carousel_media") ||
                    node.has("video_versions") || node.has("video_url") || node.has("clips_metadata")
                if (hasIdentity && hasPostShape) output.add(node)
                node.elements().forEachRemaining { collectPostCandidates(it, output) }
            }
            node.isArray -> node.elements().forEachRemaining { collectPostCandidates(it, output) }
        }
    }

    private fun downloadPage(rawUrl: String, userAgent: String): DownloadedPage {
        require(isThreadsHost(URI(rawUrl).host)) { "不支持的 Threads 域名" }
        var lastError: Exception? = null
        repeat(3) { attempt ->
            try {
                return downloadPageOnce(rawUrl, userAgent)
            } catch (error: Exception) {
                lastError = error
                if (!isRetryable(error) || attempt == 2) throw friendlyNetworkError(error)
                Thread.sleep(listOf(500L, 1_500L, 3_000L)[attempt])
            }
        }
        throw friendlyNetworkError(lastError ?: IOException("未知网络错误"))
    }

    private fun downloadPageOnce(rawUrl: String, userAgent: String): DownloadedPage {
        var current = URI(rawUrl)
        val redirects = mutableListOf<String>()
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            require(isThreadsHost(current.host)) { "Threads 重定向到了不受信任的域名" }
            val connection = current.toURL().openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 20_000
                connection.readTimeout = 30_000
                connection.setRequestProperty("User-Agent", userAgent)
                connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
                connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.7")
                connection.setRequestProperty("Connection", "close")
                connection.setRequestProperty("Referer", "https://www.threads.com/")
                val status = connection.responseCode
                if (status in 300..399) {
                    if (redirectCount >= MAX_REDIRECTS) error("Threads 重定向次数过多")
                    val location = connection.getHeaderField("Location")
                        ?: error("Threads 重定向缺少目标地址")
                    current = current.resolve(location)
                    redirects.add(current.toString())
                    return@repeat
                }
                if (status == 429 || status in 500..599) throw RetryableHttpException(status)
                if (status !in 200..299) error("Threads HTTP $status")
                val bytes = connection.inputStream.use { it.readNBytes(MAX_HTML_BYTES + 1) }
                if (bytes.size > MAX_HTML_BYTES) error("Threads 页面过大，已停止解析")
                return DownloadedPage(bytes.toString(Charsets.UTF_8), current.toString(), redirects)
            } finally {
                connection.errorStream?.close()
                connection.disconnect()
            }
        }
        error("Threads 重定向次数过多")
    }

    private fun isRetryable(error: Exception): Boolean = error is RetryableHttpException ||
        error is SocketTimeoutException || error is IOException

    private fun friendlyNetworkError(error: Exception): Exception = when {
        error is RetryableHttpException && error.status == 429 ->
            IOException("Threads 请求频率受限，请稍后重试", error)
        error is RetryableHttpException ->
            IOException("Threads 服务暂时不可用（HTTP ${error.status}）", error)
        error.message.orEmpty().contains("closed", true) ||
            error.message.orEmpty().contains("reset", true) ->
            IOException("Threads 临时关闭了连接，已自动重试 3 次", error)
        error is SocketTimeoutException -> IOException("Threads 连接超时，已自动重试 3 次", error)
        else -> error
    }

    private fun pageCanonicalUrls(html: String): List<String> = tagPattern.findAll(html).mapNotNull { match ->
        val attrs = attributes(match.value)
        val isOgUrl = attrs["property"].equals("og:url", true) || attrs["name"].equals("og:url", true)
        val isCanonical = attrs["rel"].equals("canonical", true)
        when {
            isOgUrl -> attrs["content"]
            isCanonical -> attrs["href"]
            else -> null
        }
    }.toList()

    private fun extractTargetMediaIds(html: String, finalUrl: String): Set<String> {
        val output = linkedSetOf<String>()
        val decodedFinalUrl = decodePercent(finalUrl)
        if (decodedFinalUrl.contains("injected_media_ids", ignoreCase = true)) {
            mediaIdPattern.findAll(decodedFinalUrl).mapTo(output, MatchResult::value)
        }
        val xmt = queryParameter(finalUrl, "xmt") ?: return output
        scriptPattern.findAll(html).forEach { match ->
            runCatching { mapper.readTree(match.groupValues[1]) }.getOrNull()?.let { node ->
                collectXmtBoundMediaIds(node, xmt, output)
            }
        }
        return output
    }

    private fun collectXmtBoundMediaIds(node: JsonNode, xmt: String, output: MutableSet<String>) {
        when {
            node.isObject -> {
                val directlyBound = node.fields().asSequence().any { (key, value) ->
                    value.isTextual && value.asText().contains(xmt) &&
                        (key.contains("xmt", true) || key.contains("share", true) || key.contains("url", true))
                }
                if (directlyBound) collectExplicitMediaIds(node, output)
                node.elements().forEachRemaining { collectXmtBoundMediaIds(it, xmt, output) }
            }
            node.isArray -> node.elements().forEachRemaining { collectXmtBoundMediaIds(it, xmt, output) }
        }
    }

    private fun collectExplicitMediaIds(node: JsonNode, output: MutableSet<String>) {
        when {
            node.isObject -> node.fields().forEachRemaining { (key, value) ->
                if (key in setOf("injected_media_ids", "media_id", "post_id", "pk")) {
                    mediaIdPattern.findAll(value.toString()).mapTo(output, MatchResult::value)
                } else if (value.isContainerNode) {
                    collectExplicitMediaIds(value, output)
                }
            }
            node.isArray -> node.elements().forEachRemaining { collectExplicitMediaIds(it, output) }
        }
    }

    private fun queryParameter(url: String, name: String): String? = runCatching {
        URI(url).rawQuery?.split('&')?.firstNotNullOfOrNull { part ->
            val pieces = part.split('=', limit = 2)
            if (decodePercent(pieces[0]) == name) decodePercent(pieces.getOrElse(1) { "" }) else null
        }
    }.getOrNull()

    private fun attributes(tag: String): Map<String, String> = attributePattern.findAll(tag).associate {
        it.groupValues[1].lowercase() to decodeHtmlAttribute(it.groupValues[3])
    }

    private fun decodeHtmlAttribute(value: String): String {
        var decoded = value
        repeat(2) {
            decoded = decoded
                .replace("&amp;", "&", ignoreCase = true)
                .replace("&quot;", "\"", ignoreCase = true)
                .replace("&#39;", "'", ignoreCase = true)
                .replace("&lt;", "<", ignoreCase = true)
                .replace("&gt;", ">", ignoreCase = true)
        }
        return decoded
    }

    private fun decodePercent(value: String): String = runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8)
    }.getOrDefault(value)

    private fun postCode(node: JsonNode): String? = listOf("code", "shortcode")
        .firstNotNullOfOrNull { key -> node.path(key).asText().takeIf(String::isNotBlank) }

    private fun candidateIds(node: JsonNode): Set<String> = listOf("id", "pk", "media_id", "post_id")
        .mapNotNull { key -> node.path(key).asText().takeIf(mediaIdPattern::matches) }
        .toSet()

    private fun canonicalCode(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (!isThreadsHost(uri.host)) return null
        val parts = uri.path.split('/').filter(String::isNotBlank)
        return when {
            parts.size >= 3 && parts[0].startsWith('@') && parts[1] == "post" -> parts[2]
            parts.size >= 2 && parts[0] == "t" -> parts[1]
            else -> null
        }
    }

    private fun isShareUrl(url: String): Boolean = runCatching {
        URI(url).path.split('/').filter(String::isNotBlank).firstOrNull() == "share"
    }.getOrDefault(false)

    private fun isThreadsHost(host: String?): Boolean = host?.lowercase() in setOf(
        "threads.com", "www.threads.com", "threads.net", "www.threads.net",
    )

    private fun isAllowedMediaUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        val host = uri.host.lowercase()
        uri.scheme == "https" && (
            host == "cdninstagram.com" || host.endsWith(".cdninstagram.com") ||
                host == "fbcdn.net" || host.endsWith(".fbcdn.net")
            )
    }.getOrDefault(false)

    private data class DownloadedPage(
        val html: String,
        val finalUrl: String,
        val redirectChain: List<String>,
    )

    private data class MediaVersion(
        val url: String,
        val width: Int,
        val height: Int,
        val type: String,
    )

    private class RetryableHttpException(val status: Int) : IOException("Threads HTTP $status")
    open class TargetNotFoundException(message: String) : IllegalStateException(message)
    class DynamicThreadsShareException(message: String) : TargetNotFoundException(message)
}
