package org.screenconsume.app.data.export

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.screenconsume.app.data.database.PortableUsageRow

class DataPortabilityTest {
    @Test fun `streamed JSON round trips multiple and empty batches`() {
        val rows = listOf(
            PortableUsageRow("2026-01-01", "example.one", "Comma, quote \" and newline\n", null, 60, 1, 60, 0, 0, 0),
            PortableUsageRow("2026-01-02", "example.two", "Café 日本", "Tools", 90, 2, 0, 90, 0, 0),
        )
        val writer = java.io.StringWriter()
        val stream = DataPortability.StreamWriter(writer, csv = false)
        stream.append(rows.take(1))
        stream.append(emptyList())
        stream.append(rows.drop(1))
        stream.finish()
        assertEquals(2, stream.count)
        assertEquals(rows, DataPortability.fromJson(writer.toString().toByteArray()))
    }

    @Test fun `streamed CSV has one header and preserves escaping across batches`() {
        val row = PortableUsageRow("2026-01-01", "example.app", "Example, \"App\"\nsecond line", null, 60, 1, 60, 0, 0, 0)
        val writer = java.io.StringWriter()
        val stream = DataPortability.StreamWriter(writer, csv = true)
        stream.append(listOf(row))
        stream.append(listOf(row))
        stream.finish()
        assertEquals(DataPortability.toCsv(listOf(row, row)).toString(Charsets.UTF_8), writer.toString())
        assertEquals(1, Regex("date,packageName").findAll(writer.toString()).count())
    }

    @Test fun `empty streamed JSON remains a valid restore file`() {
        val writer = java.io.StringWriter()
        DataPortability.StreamWriter(writer, csv = false).finish()
        assertTrue(DataPortability.fromJson(writer.toString().toByteArray()).isEmpty())
    }

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

    @Test fun `json reads nullable and segmented usage fields`() {
        val json = """{"format":"screen-consume-backup","version":1,"records":[{"date":"2026-01-01","packageName":"example.app","displayName":"Example","category":null,"usageSeconds":60,"launchCount":2,"morningUsageSeconds":10,"afternoonUsageSeconds":20,"eveningUsageSeconds":20,"nightUsageSeconds":10}]}""".toByteArray()

        val restored = DataPortability.fromJson(json)

        assertEquals(listOf(PortableUsageRow("2026-01-01", "example.app", "Example", null, 60, 2, 10, 20, 20, 10)), restored)
    }

    @Test fun `json rejects an unknown format and version`() {
        val wrongFormat = """{"format":"other","version":1,"records":[]}""".toByteArray()
        val wrongVersion = """{"format":"screen-consume-backup","version":2,"records":[]}""".toByteArray()

        assertTrue(runCatching { DataPortability.fromJson(wrongFormat) }.isFailure)
        assertTrue(runCatching { DataPortability.fromJson(wrongVersion) }.isFailure)
    }

    @Test fun `json rejects blank package names`() {
        val invalid = """{"format":"screen-consume-backup","version":1,"records":[{"date":"2026-01-01","packageName":"","displayName":"Example","category":null,"usageSeconds":0,"launchCount":0,"morningUsageSeconds":0,"afternoonUsageSeconds":0,"eveningUsageSeconds":0,"nightUsageSeconds":0}]}""".toByteArray()

        assertTrue(runCatching { DataPortability.fromJson(invalid) }.isFailure)
    }

    @Test fun `plain data is not mistaken for an encrypted backup`() {
        assertFalse(DataPortability.isEncrypted("SCB".toByteArray()))
        assertFalse(DataPortability.isEncrypted("plain text".toByteArray()))
    }

    @Test fun `encryption rejects an empty password and malformed backup`() {
        assertTrue(runCatching { DataPortability.encrypt(byteArrayOf(1), charArrayOf()) }.isFailure)
        assertTrue(runCatching { DataPortability.decrypt("SCB1".toByteArray(), "password".toCharArray()) }.isFailure)
    }
}
