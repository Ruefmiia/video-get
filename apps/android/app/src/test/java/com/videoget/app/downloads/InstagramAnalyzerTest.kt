package com.videoget.app.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramAnalyzerTest {
    @Test
    fun parsesCarouselImagesAndVideosInPostOrder() {
        val media = InstagramAnalyzer.parse(
            """
            {
              "items": [{
                "user": {"username": "owner"},
                "caption": {"text": "mixed post"},
                "carousel_media": [
                  {
                    "media_type": 1,
                    "image_versions2": {"candidates": [
                      {"width": 320, "height": 320, "url": "https://scontent.cdninstagram.com/small.jpg"},
                      {"width": 1080, "height": 1080, "url": "https://scontent.cdninstagram.com/full.jpg"}
                    ]}
                  },
                  {
                    "media_type": 2,
                    "video_versions": [
                      {"width": 720, "height": 1280, "url": "https://video.cdninstagram.com/video.mp4"}
                    ]
                  }
                ]
              }]
            }
            """.trimIndent(),
        )

        val items = media.formats.single().items
        assertEquals(listOf("ig_image_1", "ig_video_2"), items.map(DownloadItem::id))
        assertEquals("https://scontent.cdninstagram.com/full.jpg", items[0].directUrl)
        assertTrue(media.warning.orEmpty().contains("1 张图片、1 个视频"))
    }

    @Test
    fun decodesInstagramShortcodeAlphabet() {
        assertEquals(0L, InstagramAnalyzer.shortcodeToMediaId("A"))
        assertEquals(1L, InstagramAnalyzer.shortcodeToMediaId("B"))
        assertEquals(63L, InstagramAnalyzer.shortcodeToMediaId("_"))
        assertEquals(64L, InstagramAnalyzer.shortcodeToMediaId("BA"))
    }
}
