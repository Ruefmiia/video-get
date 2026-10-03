package com.videoget.app.history

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceDateRangeTest {
    @Test fun recentIncludesTodayAndThirtyCalendarDates() {
        val today = LocalDate.of(2026, 10, 3)
        assertEquals(SourceDateRange(today, today), SourceDateRange.recent(1, today))
        assertEquals(SourceDateRange(today.minusDays(29), today), SourceDateRange.recent(30, today))
    }
    @Test fun boundsUseLocalMidnightsIncludingDst() {
        val day = LocalDate.of(2026, 3, 8)
        val (start, end) = SourceDateRange(day, day).bounds(ZoneId.of("America/New_York"))
        assertEquals(23 * 60 * 60 * 1000L, end!! - start!!)
        assertEquals(null to null, SourceDateRange().bounds())
    }
    @Test fun cleanupRetainsBoundaryDate() {
        val today = LocalDate.of(2026, 10, 3)
        val zone = ZoneId.of("Asia/Shanghai")
        assertEquals(today.minusDays(30).atStartOfDay(zone).toInstant().toEpochMilli(), SourceDateRange.cleanupCutoff(today, zone))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsReversedDates() {
        SourceDateRange(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 2))
    }
}
