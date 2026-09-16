package com.videoget.app.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaAnalyzerTest {
    @Test
    fun parsesAndSortsFxTwitterMp4Formats() {
        val result = MediaAnalyzer.parseFxTwitter(
            """
            {
              "code": 200,
              "tweet": {
                "text": "Public post",
                "author": {"name": "Author"},
                "media": {
                  "videos": [{
                    "formats": [
                      {"url": "https://video.twimg.com/path/320x568/low.mp4", "bitrate": 632000, "container": "mp4"},
                      {"url": "https://video.twimg.com/path/720x1280/high.mp4", "bitrate": 2176000, "container": "mp4"},
                      {"url": "https://video.twimg.com/path/list.m3u8", "container": "m3u8"}
                    ]
                  }]
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals("Author — Public post", result.title)
        assertEquals(2, result.formats.size)
        assertEquals("720×1280 · 2.2 Mbps", result.formats.first().label)
        assertTrue(result.formats.first().directUrl!!.startsWith("https://video.twimg.com/"))
    }
}
