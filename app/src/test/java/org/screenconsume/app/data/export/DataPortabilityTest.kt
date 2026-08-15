package org.screenconsume.app.data.export

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.screenconsume.app.data.database.PortableUsageRow

class DataPortabilityTest {
    @Test fun `csv escapes commas and quotes`() {
        val row = PortableUsageRow("2026-01-01", "example.app", "Example, \"App\"", null, 60, 1, 60, 0, 0, 0)
        val csv = DataPortability.toCsv(listOf(row)).toString(Charsets.UTF_8)
        assertTrue(csv.contains("\"Example, \"\"App\"\"\""))
    }

    @Test fun `encrypted backup round trips and has identifiable header`() {
        val input = "private daily aggregates".toByteArray()
        val encrypted = DataPortability.encrypt(input, "correct horse battery".toCharArray())
        assertTrue(DataPortability.isEncrypted(encrypted))
        assertArrayEquals(input, DataPortability.decrypt(encrypted, "correct horse battery".toCharArray()))
    }
}
