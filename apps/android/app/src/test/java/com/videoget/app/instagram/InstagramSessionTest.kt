package com.videoget.app.instagram

import org.junit.Assert.assertEquals
import org.junit.Test

class InstagramSessionTest {
    @Test
    fun parsesCookieValuesContainingEqualsSigns() {
        assertEquals(
            listOf("sessionid" to "abc==", "csrftoken" to "token"),
            InstagramSession.parseCookieHeader("sessionid=abc==; csrftoken=token"),
        )
    }

    @Test
    fun netscapeExportRemovesControlCharacters() {
        assertEquals(
            listOf(".instagram.com\tTRUE\t/\tTRUE\t0\tsessionid\tabc"),
            InstagramSession.netscapeLines(listOf("session\nid" to "a\tbc")),
        )
    }
}
