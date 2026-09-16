package com.videoget.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun keepsThreadsRecognizedButUnavailable() {
        val match = UrlRouter.match("https://threads.net/@user/post/ABC")
        assertEquals(PlatformId.THREADS, match?.platform)
        assertFalse(match?.available ?: true)
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
