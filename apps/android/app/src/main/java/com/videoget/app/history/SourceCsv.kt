package com.videoget.app.history

import java.io.Writer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object SourceCsv {
    fun writeHeader(writer: Writer) { writer.write("\uFEFF原帖 URL,下载时间,文件名\r\n") }

    fun writeRow(writer: Writer, sourceUrl: String, downloadedAt: Long, fileName: String) {
        val time = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
            Instant.ofEpochMilli(downloadedAt).atZone(ZoneId.systemDefault()),
        )
        writer.write(listOf(sourceUrl, time, fileName).joinToString(",") { escape(it) })
        writer.write("\r\n")
    }

    internal fun escape(value: String): String {
        // Excel can treat untrusted filenames as formulas even in quoted cells.
        val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@') ||
            value.firstOrNull() in listOf('\t', '\r', '\n')) "'$value" else value
        return "\"${safe.replace("\"", "\"\"")}\""
    }
}
