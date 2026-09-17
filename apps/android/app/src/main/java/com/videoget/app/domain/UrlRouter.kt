package com.videoget.app.domain

import java.net.URI
import java.nio.charset.StandardCharsets

object UrlRouter {
    private val urlPattern = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)
    private val trackingKeys = setOf(
        "feature", "igsh", "pp", "s", "si", "stkn", "t",
        "utm_campaign", "utm_content", "utm_medium", "utm_source",
    )
    private val youtubeIdPattern = Regex("^[A-Za-z0-9_-]{11}$")

    fun extractSingleUrl(text: String): String? {
        val matches = urlPattern.findAll(text).map { it.value.trimEnd('.', ',', ')', ']', '}') }.distinct().toList()
        return matches.singleOrNull()
    }

    fun match(rawUrl: String): UrlMatch? {
        val uri = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
        val host = uri.host?.lowercase() ?: return null
        val parts = uri.path.orEmpty().split('/').filter(String::isNotBlank)
        val platform = when (host) {
            "x.com", "www.x.com", "twitter.com", "www.twitter.com" -> PlatformId.X
            "instagram.com", "www.instagram.com" -> PlatformId.INSTAGRAM
            "threads.com", "www.threads.com", "threads.net", "www.threads.net" -> PlatformId.THREADS
            "youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be", "www.youtu.be" -> PlatformId.YOUTUBE
            else -> return null
        }
        if (platform == PlatformId.YOUTUBE) return matchYoutube(uri, host, parts)
        val valid = when (platform) {
            PlatformId.X -> parts.size >= 3 && parts[1] == "status" && parts[2].all(Char::isDigit)
            PlatformId.INSTAGRAM -> parts.size >= 2 && parts[0] in setOf("p", "reel", "reels")
            PlatformId.THREADS ->
                (parts.size >= 3 && parts[0].startsWith('@') && parts[1] == "post") ||
                    (parts.size >= 2 && parts[0] in setOf("share", "t"))
            PlatformId.YOUTUBE -> false
        }
        if (!valid) return null
        val canonicalHost = when (platform) {
            PlatformId.X -> "x.com"
            PlatformId.INSTAGRAM -> "www.instagram.com"
            PlatformId.THREADS -> "www.threads.com"
            PlatformId.YOUTUBE -> error("YouTube URL 已提前处理")
        }
        val canonicalParts = if (platform == PlatformId.INSTAGRAM && parts[0] == "reels") {
            listOf("reel") + parts.drop(1)
        } else {
            parts
        }
        val query = uri.rawQuery.orEmpty().split('&').filter(String::isNotBlank).mapNotNull { item ->
            val pair = item.split('=', limit = 2)
            val key = java.net.URLDecoder.decode(pair[0], StandardCharsets.UTF_8.name())
            if (key in trackingKeys) null else item
        }.joinToString("&")
        val path = canonicalParts.joinToString(separator = "/", prefix = "/")
        val suffix = if (query.isBlank()) "" else "?$query"
        return UrlMatch(platform, "https://$canonicalHost$path$suffix", true)
    }

    private fun matchYoutube(uri: URI, host: String, parts: List<String>): UrlMatch? {
        val videoId = when {
            host in setOf("youtu.be", "www.youtu.be") -> parts.singleOrNull()
            parts.size == 2 && parts[0] == "shorts" -> parts[1]
            parts.singleOrNull() == "watch" -> queryValue(uri.rawQuery, "v")
            else -> null
        }?.takeIf { youtubeIdPattern.matches(it) } ?: return null
        return UrlMatch(
            platform = PlatformId.YOUTUBE,
            canonicalUrl = "https://www.youtube.com/watch?v=$videoId",
            available = true,
        )
    }

    private fun queryValue(rawQuery: String?, target: String): String? = rawQuery
        .orEmpty()
        .split('&')
        .filter(String::isNotBlank)
        .firstNotNullOfOrNull { item ->
            val pair = item.split('=', limit = 2)
            val key = java.net.URLDecoder.decode(pair[0], StandardCharsets.UTF_8.name())
            if (key == target && pair.size == 2) {
                java.net.URLDecoder.decode(pair[1], StandardCharsets.UTF_8.name())
            } else {
                null
            }
        }
}
