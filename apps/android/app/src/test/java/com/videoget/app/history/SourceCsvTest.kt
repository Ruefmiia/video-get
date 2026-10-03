package com.videoget.app.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter
import java.time.Instant
import java.time.OffsetDateTime

class SourceCsvTest {
    @Test fun writesExcelCompatibleChineseCsvWithBomAndCrLf() {
        val writer = StringWriter()
        SourceCsv.writeHeader(writer)
        SourceCsv.writeRow(writer, "https://x.com/a/status/123", 0L, "中文,\"视频\"\n.mp4")
        val csv = writer.toString()
        assertTrue(csv.startsWith("\uFEFF原帖 URL,下载时间,文件名\r\n"))
        assertTrue(csv.contains("\"https://x.com/a/status/123\""))
        assertTrue(csv.contains("\"中文,\"\"视频\"\"\n.mp4\"\r\n"))
        val time = csv.substringAfter("\"https://x.com/a/status/123\",\"").substringBefore('"')
        assertEquals(Instant.EPOCH, OffsetDateTime.parse(time).toInstant())
    }

    @Test fun protectsFormulaLikeFileNamesWithoutChangingNormalNames() {
        listOf("=1+1", "+cmd.mp4", "-1.mp4", "@x.jpg", "  =SUM(1)", "\tfoo", "\nfoo").forEach {
            assertEquals("\"'$it\"", SourceCsv.escape(it))
        }
        assertEquals("\"video-get.mp4\"", SourceCsv.escape("video-get.mp4"))
        assertEquals("\"\"", SourceCsv.escape(""))
    }
}
