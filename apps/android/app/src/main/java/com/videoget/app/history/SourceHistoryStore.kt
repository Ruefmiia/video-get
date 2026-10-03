package com.videoget.app.history

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.videoget.app.domain.UrlRouter
import java.io.Writer

data class SourceGroup(
    val id: String,
    val sourceUrl: String,
    val downloadedAt: Long,
    val firstFileName: String,
    val fileCount: Int,
)

data class SourceFile(val contentUri: String, val fileName: String)

/** Text-only provenance. Call on IO; no thumbnails, credentials or CDN URLs. */
class SourceHistoryStore(context: Context, databaseName: String = "source-history.db") :
    SQLiteOpenHelper(context.applicationContext, databaseName, null, 1) {
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE source_groups (
                id TEXT PRIMARY KEY NOT NULL,
                source_url TEXT NOT NULL,
                downloaded_at INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE source_files (
                content_uri TEXT PRIMARY KEY NOT NULL,
                group_id TEXT NOT NULL REFERENCES source_groups(id) ON DELETE CASCADE,
                file_name TEXT NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX source_files_group ON source_files(group_id)")
        db.execSQL("CREATE INDEX source_groups_time ON source_groups(downloaded_at DESC, id DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("Unsupported source history database upgrade: $oldVersion -> $newVersion")
    }

    fun record(groupId: String, sourceUrl: String, downloadedAt: Long, file: SourceFile) {
        val canonicalUrl = UrlRouter.match(sourceUrl)?.canonicalUrl
            ?: error("无法记录下载来源：原帖链接无效")
        require(file.contentUri.isNotBlank() && file.fileName.isNotBlank())
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.rawQuery("SELECT 1 FROM source_files WHERE content_uri = ?", arrayOf(file.contentUri)).use {
                if (it.moveToFirst()) return
            }
            db.insertWithOnConflict("source_groups", null, ContentValues().apply {
                put("id", groupId)
                put("source_url", canonicalUrl)
                put("downloaded_at", downloadedAt)
            }, SQLiteDatabase.CONFLICT_IGNORE)
            db.insertOrThrow("source_files", null, ContentValues().apply {
                put("content_uri", file.contentUri)
                put("group_id", groupId)
                put("file_name", file.fileName)
            })
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun groups(limit: Int = PAGE_SIZE, offset: Int = 0): List<SourceGroup> {
        require(limit in 1..100 && offset >= 0)
        return readableDatabase.rawQuery("""
            SELECT g.id, g.source_url, g.downloaded_at,
                (SELECT file_name FROM source_files WHERE group_id = g.id ORDER BY rowid LIMIT 1),
                (SELECT COUNT(*) FROM source_files WHERE group_id = g.id)
            FROM source_groups g
            WHERE EXISTS (SELECT 1 FROM source_files WHERE group_id = g.id)
            ORDER BY g.downloaded_at DESC, g.id DESC LIMIT ? OFFSET ?
        """.trimIndent(), arrayOf(limit.toString(), offset.toString())).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(SourceGroup(
                    cursor.getString(0), cursor.getString(1), cursor.getLong(2),
                    cursor.getString(3), cursor.getInt(4),
                ))
            }
        }
    }

    fun files(groupId: String): List<SourceFile> = readableDatabase.rawQuery(
        "SELECT content_uri, file_name FROM source_files WHERE group_id = ? ORDER BY rowid",
        arrayOf(groupId),
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(SourceFile(cursor.getString(0), cursor.getString(1)))
        }
    }

    /** Streams CSV without loading the entire history or creating a local export copy. */
    fun exportCsv(writer: Writer, groupId: String? = null): Int {
        SourceCsv.writeHeader(writer)
        val filter = if (groupId == null) "" else "WHERE g.id = ?"
        return readableDatabase.rawQuery("""
            SELECT g.source_url, g.downloaded_at, f.file_name
            FROM source_groups g JOIN source_files f ON f.group_id = g.id
            $filter ORDER BY g.downloaded_at DESC, g.id DESC, f.rowid
        """.trimIndent(), groupId?.let { arrayOf(it) }).use { cursor ->
            var count = 0
            while (cursor.moveToNext()) {
                SourceCsv.writeRow(writer, cursor.getString(0), cursor.getLong(1), cursor.getString(2))
                count++
            }
            count
        }
    }

    companion object {
        const val PAGE_SIZE = 30
        @Volatile private var instance: SourceHistoryStore? = null
        fun get(context: Context): SourceHistoryStore = instance ?: synchronized(this) {
            instance ?: SourceHistoryStore(context).also { instance = it }
        }
    }
}
