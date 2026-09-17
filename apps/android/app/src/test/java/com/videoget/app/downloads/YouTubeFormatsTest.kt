package com.videoget.app.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeFormatsTest {
    @Test
    fun exposesThreeSingleVideoCompatibilityPresets() {
        assertEquals(3, YouTubeFormats.formats.size)
        assertEquals(
            listOf("youtube_best", "youtube_1080", "youtube_720"),
            YouTubeFormats.formats.map { it.id },
        )
        YouTubeFormats.formats.forEach { format ->
            assertTrue(format.selector.contains("vcodec^=avc1"))
            assertTrue(format.selector.contains("ba[ext=m4a]"))
            assertTrue(format.directUrl == null)
        }
    }

    @Test
    fun resolutionPresetsApplyExpectedCaps() {
        assertTrue(YouTubeFormats.formats[1].selector.contains("height<=1080"))
        assertTrue(YouTubeFormats.formats[2].selector.contains("height<=720"))
    }
}
