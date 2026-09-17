package com.videoget.app.downloads

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import java.io.IOException

object YouTubeEngineManager {
    private const val PREFS = "youtube_engine"
    private const val KEY_LAST_CHECK = "last_successful_update_check"
    private const val CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L

    @Synchronized
    fun prepare(context: Context) {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastCheck = preferences.getLong(KEY_LAST_CHECK, 0L)
        if (now - lastCheck in 0 until CHECK_INTERVAL_MS) return

        try {
            YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel._STABLE)
            preferences.edit().putLong(KEY_LAST_CHECK, now).apply()
        } catch (error: Exception) {
            throw IOException("YouTube 解析组件更新失败，请确认手机可以访问 GitHub 后重试", error)
        }
    }
}
