package org.screenconsume.app.ui

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExportDateRangeTest {
    private val today = LocalDate.of(2026, 9, 10)
    private fun millis(date: LocalDate) = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test fun `single day is an inclusive export range`() {
        val range = requireNotNull(exportDateRange(millis(today), millis(today), today))
        assertEquals(today, range.start)
        assertEquals(today, range.endInclusive)
        assertEquals(1L, range.dayCount)
    }
    @Test fun `missing reversed and future ranges cannot be exported`() {
        assertNull(exportDateRange(null, millis(today), today))
        assertNull(exportDateRange(millis(today), null, today))
        assertNull(exportDateRange(millis(today), millis(today.minusDays(1)), today))
        assertNull(exportDateRange(millis(today), millis(today.plusDays(1)), today))
    }
    @Test fun `date picker UTC values survive daylight saving boundary`() {
        val start = LocalDate.of(2026, 3, 28)
        val end = LocalDate.of(2026, 3, 30)
        val range = requireNotNull(exportDateRange(millis(start), millis(end), today))
        assertEquals(start, range.start)
        assertEquals(end, range.endInclusive)
        assertEquals(3L, range.dayCount)
    }
}
