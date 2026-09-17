package com.videoget.app.instagram

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import java.io.File

object InstagramSession {
    private const val INSTAGRAM_URL = "https://www.instagram.com/"
    private const val PREFS = "instagram_session"
    private const val USER_AGENT = "user_agent"

    fun isLoggedIn(): Boolean = cookies().any { it.first == "sessionid" && it.second.isNotBlank() }

    fun saveUserAgent(context: Context, userAgent: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(USER_AGENT, userAgent).apply()
    }

    fun userAgent(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(USER_AGENT, null)
            ?: WebSettings.getDefaultUserAgent(context)

    internal fun cookieHeader(): String = cookies().joinToString("; ") { (name, value) -> "$name=$value" }

    internal fun csrfToken(): String? = cookies().firstOrNull { it.first == "csrftoken" }?.second

    fun clear() {
        val manager = CookieManager.getInstance()
        cookies().map(Pair<String, String>::first).distinct().forEach { name ->
            manager.setCookie(INSTAGRAM_URL, "$name=; Max-Age=0; Path=/; Domain=.instagram.com; Secure")
        }
        manager.flush()
    }

    fun <T> withYtDlpCookies(context: Context, block: (File, String) -> T): T {
        val cookies = cookies()
        check(cookies.any { it.first == "sessionid" && it.second.isNotBlank() }) {
            "Instagram 登录状态已失效，请重新连接 Instagram"
        }
        val directory = File(context.cacheDir, "instagram-session").apply { mkdirs() }
        val file = File.createTempFile("cookies-", ".txt", directory)
        return try {
            file.bufferedWriter().use { writer ->
                writer.appendLine("# Netscape HTTP Cookie File")
                netscapeLines(cookies).forEach(writer::appendLine)
            }
            block(file, userAgent(context))
        } finally {
            file.delete()
        }
    }

    private fun cookies(): List<Pair<String, String>> =
        parseCookieHeader(CookieManager.getInstance().getCookie(INSTAGRAM_URL).orEmpty())

    internal fun parseCookieHeader(header: String): List<Pair<String, String>> =
        header.split(';').mapNotNull { part ->
            val pair = part.trim().split('=', limit = 2)
            if (pair.size == 2 && pair[0].isNotBlank()) pair[0] to pair[1] else null
        }

    internal fun netscapeLines(cookies: List<Pair<String, String>>): List<String> = cookies.map { (name, value) ->
        ".instagram.com\tTRUE\t/\tTRUE\t0\t${safe(name)}\t${safe(value)}"
    }

    private fun safe(value: String): String = value.replace(Regex("[\\t\\r\\n]"), "")
}
