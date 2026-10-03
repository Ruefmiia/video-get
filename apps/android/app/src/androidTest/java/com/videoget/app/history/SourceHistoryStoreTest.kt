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
        val group = store.groups().groups.single()
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
        val first = store.groups()
        assertEquals(30, first.groups.size)
        assertEquals("work-34", first.groups.first().id)
        val last = store.groups(cursor = SourceCursor.of(first.groups.last()))
        assertEquals(5, last.groups.size)
        assertEquals(first.groups, store.groups(cursor = SourceCursor.of(last.groups.first()), newer = true).groups)
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
        assertTrue(store.groups().groups.isEmpty())
        assertEquals(0, store.exportCsv(StringWriter()))
    }

    @Test fun filtersExportsAndCleansOnlyBeforeBoundary() {
        val day = java.time.LocalDate.of(2026, 10, 3)
        val range = SourceDateRange(day, day)
        val (start, end) = range.bounds()
        val times = listOf(start!! - 1, start, end!! - 1, end)
        times.forEachIndexed { i, time ->
            store.record("range-$i", "https://x.com/u/status/${100 + i}", time,
                SourceFile("content://media/external/video/media/$i", "$i.mp4"))
        }
        assertEquals(listOf("range-2", "range-1"), store.groups(range).groups.map { it.id })
        assertEquals(2, store.exportCsv(StringWriter(), range = range))
        assertEquals(1, store.deleteBefore(start))
        assertTrue(store.files("range-0").isEmpty())
        assertEquals(3, store.groups().groups.size)
        assertEquals(1, store.files("range-1").size)
    }

    @Test fun tenThousandGroupsPageWithoutDuplicatesAndIgnoreNewerInsert() {
        // Bulk fixture transaction avoids measuring 10,000 individual durable commits.
        val db = store.writableDatabase
        db.beginTransaction()
        try {
            repeat(10_000) { i ->
                store.record("bulk-${i.toString().padStart(5, '0')}", "https://x.com/u/status/${100 + i}", (i / 5).toLong(),
                    SourceFile("content://media/external/video/media/$i", "$i.mp4"))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        var page = store.groups()
        val first = page.groups
        val seen = mutableSetOf<String>()
        store.record("new", "https://x.com/u/status/99999", 100_000,
            SourceFile("content://media/external/video/media/new", "new.mp4"))
        while (true) {
            assertTrue(page.groups.size <= 30)
            page.groups.forEach { assertTrue(seen.add(it.id)) }
            if (!page.hasNext) break
            val next = store.groups(cursor = SourceCursor.of(page.groups.last()))
            assertEquals(page.groups, store.groups(cursor = SourceCursor.of(next.groups.first()), newer = true).groups)
            page = next
        }
        assertEquals(10_000, seen.size)
        assertEquals(10_001, store.exportCsv(StringWriter()))
        assertEquals(first.first().id, store.groups(cursor = SourceCursor(100_000, "new")).groups.first().id)
    }
}
