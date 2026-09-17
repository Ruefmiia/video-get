package com.videoget.app.downloads

import android.content.Context
import java.util.UUID

data class ActiveDownloadSession(
    val workId: UUID,
    val sourceUrl: String,
    val title: String,
)

object DownloadSessionStore {
    private const val PREFERENCES = "active_download"
    private const val KEY_WORK_ID = "work_id"
    private const val KEY_SOURCE_URL = "source_url"
    private const val KEY_TITLE = "title"

    fun save(context: Context, session: ActiveDownloadSession) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_WORK_ID, session.workId.toString())
            .putString(KEY_SOURCE_URL, session.sourceUrl)
            .putString(KEY_TITLE, session.title)
            .apply()
    }

    fun load(context: Context): ActiveDownloadSession? {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val workId = preferences.getString(KEY_WORK_ID, null)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: return null
        return ActiveDownloadSession(
            workId = workId,
            sourceUrl = preferences.getString(KEY_SOURCE_URL, "").orEmpty(),
            title = preferences.getString(KEY_TITLE, "").orEmpty(),
        )
    }

    fun clear(context: Context, expectedWorkId: UUID? = null) {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (expectedWorkId != null && preferences.getString(KEY_WORK_ID, null) != expectedWorkId.toString()) {
            return
        }
        preferences.edit().clear().apply()
    }
}
