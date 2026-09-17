package com.videoget.app.downloads

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadRequestStoreTest {
    private val mapper = ObjectMapper()

    @Test
    fun jsonNullDoesNotBecomeLiteralNullDirectUrl() {
        assertNull(DownloadRequestStore.nullableText(mapper.readTree("null")))
    }

    @Test
    fun missingAndBlankDirectUrlsRemainNull() {
        assertNull(DownloadRequestStore.nullableText(mapper.readTree("{}").path("directUrl")))
        assertNull(DownloadRequestStore.nullableText(mapper.readTree("\"\"")))
    }

    @Test
    fun realDirectUrlIsPreserved() {
        assertEquals(
            "https://video.example.test/media.mp4",
            DownloadRequestStore.nullableText(mapper.readTree("\"https://video.example.test/media.mp4\"")),
        )
    }
}
