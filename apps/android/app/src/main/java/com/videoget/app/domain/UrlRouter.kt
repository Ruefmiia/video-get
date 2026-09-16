package com.videoget.app.domain

import java.net.URI
import java.nio.charset.StandardCharsets

object UrlRouter {
    private val urlPattern = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)
    private val trackingKeys = setOf("igsh", "s", "stkn", "t", "utm_campaign", "utm_content", "utm_medium", "utm_source")

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
            else -> return null
        }
        val valid = when (platform) {
            PlatformId.X -> parts.size >= 3 && parts[1] == "status" && parts[2].all(Char::isDigit)
            PlatformId.INSTAGRAM -> parts.size >= 2 && parts[0] in setOf("p", "reel", "reels")
            PlatformId.THREADS ->
                (parts.size >= 3 && parts[0].startsWith('@') && parts[1] == "post") ||
                    (parts.size >= 2 && parts[0] == "share")
        }
        if (!valid) return null
        val canonicalHost = when (platform) {
            PlatformId.X -> "x.com"
            PlatformId.INSTAGRAM -> "www.instagram.com"
            PlatformId.THREADS -> "www.threads.com"
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
        return UrlMatch(platform, "https://$canonicalHost$path$suffix", platform != PlatformId.THREADS)
    }
}
