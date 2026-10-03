package com.videoget.app.history

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.StringWriter
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SourceHistoryStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "source-history-test-${UUID.randomUUID()}.db"
    private lateinit var store: SourceHistoryStore

    @Before fun setUp() { store = SourceHistoryStore(context, databaseName) }
    @After fun tearDown() { store.close(); context.deleteDatabase(databaseName) }

    @Test fun groupsMultipleSavedFilesAndPersistsAcrossReopening() {
        val first = SourceFile("content://media/external/video/media/1", "one.mp4")
        val second = SourceFile("content://media/external/images/media/2", "two.jpg")
        store.record("work-1", "https://twitter.com/u/status/123?s=20", 100L, first)
        store.record("work-1", "https://x.com/u/status/123", 200L, second)
        store.record("work-1", "https://x.com/u/status/123", 200L, first)
        store.close()
        store = SourceHistoryStore(context, databaseName)
        val group = store.groups().single()
        assertEquals("https://x.com/u/status/123", group.sourceUrl)
        assertEquals(100L, group.downloadedAt)
        assertEquals(2, group.fileCount)
        assertEquals(listOf(first, second), store.files(group.id))
    }

    @Test fun exportsOnlyRequestedGroupAndPagesInNewestFirstOrder() {
        repeat(35) { index ->
            store.record("work-$index", "https://x.com/u/status/${100 + index}", index.toLong(),
                SourceFile("content://media/external/video/media/$index", "$index.mp4"))
        }
        assertEquals(30, store.groups().size)
        assertEquals("work-34", store.groups().first().id)
        assertEquals(5, store.groups(offset = 30).size)
        val writer = StringWriter()
        assertEquals(1, store.exportCsv(writer, "work-3"))
        assertTrue(writer.toString().contains("https://x.com/u/status/103"))
        assertEquals(35, store.exportCsv(StringWriter()))
    }

    @Test fun rejectsMediaCdnUrlsAndNeverCreatesEmptyGroups() {
        try {
            store.record("bad", "https://video.twimg.com/file.mp4?token=secret", 1L,
                SourceFile("content://media/external/video/media/1", "video.mp4"))
        } catch (_: IllegalStateException) { }
        assertTrue(store.groups().isEmpty())
        assertEquals(0, store.exportCsv(StringWriter()))
    }
}
