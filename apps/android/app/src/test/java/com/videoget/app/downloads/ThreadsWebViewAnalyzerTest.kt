package com.videoget.app.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThreadsWebViewAnalyzerTest {
    @Test
    fun parsesVerifiedRenderedPost() {
        val escaped = """{
          "verified":true,
          "permalink":"https://www.threads.com/@creator/post/CODE",
          "title":"Rendered post",
          "videos":[{"url":"https://scontent.cdninstagram.com/o1/v/video.mp4?oe=1"}]
        }"""

        val result = ThreadsWebViewAnalyzer.parseExtraction(escaped)

        assertEquals("Rendered post", result!!.title)
        assertEquals(1, result.formats.size)
    }

    @Test
    fun refusesUnverifiedRecommendationVideo() {
        val json = """{
          "verified":false,
          "permalink":"https://www.threads.com/@other/post/WRONG",
          "videos":[{"url":"https://scontent.cdninstagram.com/o1/v/wrong.mp4"}]
        }"""

        assertNull(ThreadsWebViewAnalyzer.parseExtraction(json))
    }

    @Test
    fun refusesNonMetaMediaHost() {
        val json = """{
          "verified":true,
          "permalink":"https://www.threads.com/@creator/post/CODE",
          "videos":[{"url":"https://evil.example/video.mp4"}]
        }"""

        assertNull(ThreadsWebViewAnalyzer.parseExtraction(json))
    }
}
