package com.videoget.app.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaAnalyzerTest {
    @Test
    fun buildsOneQualityChoiceForAllFxTwitterVideos() {
        val result = MediaAnalyzer.parseFxTwitter(
            """
            {
              "code": 200,
              "tweet": {
                "text": "Public post",
                "author": {"name": "Author"},
                "media": {
                  "videos": [
                    {"formats": [
                      {"url": "https://video.twimg.com/one/320x568/low.mp4", "bitrate": 632000, "container": "mp4"},
                      {"url": "https://video.twimg.com/one/720x1280/high.mp4", "bitrate": 2176000, "container": "mp4"},
                      {"url": "https://video.twimg.com/one/list.m3u8", "container": "m3u8"}
                    ]},
                    {"formats": [
                      {"url": "https://video.twimg.com/two/320x568/low.mp4", "bitrate": 632000, "container": "mp4"},
                      {"url": "https://video.twimg.com/two/720x1280/high.mp4", "bitrate": 2176000, "container": "mp4"}
                    ]}
                  ]
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals("Author — Public post", result.title)
        assertEquals("最佳画质（全部 2 个视频）", result.formats.first().label)
        assertEquals(2, result.formats.first().items.size)
        assertEquals(listOf("x_1_2176000", "x_2_2176000"), result.formats.first().items.map { it.id })
        assertTrue(result.formats.first().items.all { it.directUrl!!.startsWith("https://video.twimg.com/") })
        assertTrue(result.warning!!.contains("2 个视频"))
    }
}
