package com.videoget.app.history

import java.time.LocalDate
import java.time.ZoneId

/** Inclusive local dates converted to an indexed half-open timestamp range. */
data class SourceDateRange(val start: LocalDate? = null, val end: LocalDate? = null) {
    init { require(start == null || end == null || !end.isBefore(start)) }
    fun bounds(zone: ZoneId = ZoneId.systemDefault()): Pair<Long?, Long?> =
        start?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() to
            end?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
    val label: String get() = when {
        start == null && end == null -> "全部日期"
        start == end -> "$start"
        else -> "${start ?: "最早"} 至 ${end ?: "现在"}"
    }
    companion object {
        fun recent(days: Long, today: LocalDate = LocalDate.now()): SourceDateRange {
            require(days > 0)
            return SourceDateRange(today.minusDays(days - 1), today)
        }
        fun cleanupCutoff(today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Long =
            today.minusDays(30).atStartOfDay(zone).toInstant().toEpochMilli()
    }
}

data class SourceCursor(val time: Long, val id: String) {
    companion object { fun of(group: SourceGroup) = SourceCursor(group.downloadedAt, group.id) }
}
data class SourcePage(val groups: List<SourceGroup>, val hasPrevious: Boolean, val hasNext: Boolean)
