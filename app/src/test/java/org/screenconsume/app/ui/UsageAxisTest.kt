package org.screenconsume.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageAxisTest {
    @Test fun `hour scales cover the peak with exact whole hour ticks`() {
        for (intervals in listOf(3, 4)) {
            for (peak in listOf(3_600L, 15_720L, 86_400L, 3_000_000L)) {
                val step = usageAxisStepSeconds(peak, intervals)
                assertEquals(0L, step % 3_600L)
                assertTrue(step * intervals >= peak)
                assertTrue((step - 3_600L) * intervals < peak)
            }
        }
    }

    @Test fun `short and empty ranges have readable nonzero minute steps`() {
        assertEquals(60L, usageAxisStepSeconds(0, 4))
        assertEquals(300L, usageAxisStepSeconds(900, 4))
        assertEquals(900L, usageAxisStepSeconds(3_599, 4))
    }

    @Test fun `axis labels omit zero minutes for whole hours`() {
        assertEquals("4h", usageAxisLabel(14_400))
        assertEquals("15m", usageAxisLabel(900))
        assertEquals("0m", usageAxisLabel(0))
    }
}
