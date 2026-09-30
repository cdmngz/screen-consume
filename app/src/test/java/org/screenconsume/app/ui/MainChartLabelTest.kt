package org.screenconsume.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class MainChartLabelTest {
    @Test
    fun `six hour labels use hyphen separator`() {
        assertEquals(
            listOf("0-6", "6-12", "12-18", "18-24"),
            (0 until 24 step 6).map(::sixHourBucketLabel),
        )
    }

    @Test
    fun `month labels use localized initials`() {
        val dates = (1..12).map { LocalDate.of(2026, it, 1) }
        assertEquals(listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D"), dates.map { monthInitial(it, Locale.ENGLISH) })
        assertEquals("E", monthInitial(dates.first(), Locale.forLanguageTag("es")))
    }

    @Test
    fun `short month and year labels have one separator in each supported language`() {
        val september = LocalDate.of(2026, 9, 1)
        assertEquals(
            listOf("Sep'26", "sept'26", "sept'26", "Sept'26", "set'26", "set'26"),
            listOf("en", "es", "fr", "de", "it", "pt").map { shortMonthYear(september, Locale.forLanguageTag(it)) },
        )
    }
}
