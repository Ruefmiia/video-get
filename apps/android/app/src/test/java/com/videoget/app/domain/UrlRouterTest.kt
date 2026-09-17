package com.videoget.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlRouterTest {
    @Test
    fun normalizesSupportedUrlsAndTrackingParameters() {
        assertEquals(
            "https://x.com/user/status/123",
            UrlRouter.match("https://twitter.com/user/status/123?s=20")?.canonicalUrl,
        )
        assertEquals(
            "https://www.instagram.com/reel/ABC",
            UrlRouter.match("https://instagram.com/reels/ABC/?igsh=x&stkn=y")?.canonicalUrl,
        )
    }

    @Test
    fun recognizesThreadsAsAvailable() {
        val match = UrlRouter.match("https://threads.net/@user/post/ABC")
        assertEquals(PlatformId.THREADS, match?.platform)
        assertTrue(match?.available ?: false)
        assertEquals(
            "https://www.threads.com/@user/post/ABC",
            match?.canonicalUrl,
        )
    }

    @Test
    fun normalizesYouTubeVideoLinks() {
        val id = "Pv61yEcOqpw"
        listOf(
            "https://www.youtube.com/watch?v=$id&list=PL123&feature=share",
            "https://m.youtube.com/watch?v=$id&si=tracking",
            "https://youtu.be/$id?si=tracking",
            "https://www.youtube.com/shorts/$id?feature=share",
        ).forEach { rawUrl ->
            val match = UrlRouter.match(rawUrl)
            assertEquals(PlatformId.YOUTUBE, match?.platform)
            assertTrue(match?.available ?: false)
            assertEquals("https://www.youtube.com/watch?v=$id", match?.canonicalUrl)
        }
    }

    @Test
    fun rejectsUnsupportedYouTubePagesAndInvalidIds() {
        assertNull(UrlRouter.match("https://www.youtube.com/playlist?list=PL123"))
        assertNull(UrlRouter.match("https://www.youtube.com/@creator"))
        assertNull(UrlRouter.match("https://www.youtube.com/live/Pv61yEcOqpw"))
        assertNull(UrlRouter.match("https://youtu.be/too-short"))
        assertNull(UrlRouter.match("https://youtube.com.example/watch?v=Pv61yEcOqpw"))
    }

    @Test
    fun rejectsLookalikesAndUnsupportedPaths() {
        assertNull(UrlRouter.match("https://x.com.example/user/status/123"))
        assertNull(UrlRouter.match("https://instagram.com/accounts/login"))
    }

    @Test
    fun extractsExactlyOneUrlFromSharedText() {
        assertEquals("https://x.com/a/status/1", UrlRouter.extractSingleUrl("See https://x.com/a/status/1."))
        assertNull(UrlRouter.extractSingleUrl("https://x.com/a/status/1 https://x.com/b/status/2"))
        assertTrue(UrlRouter.extractSingleUrl("nothing here") == null)
    }
}
