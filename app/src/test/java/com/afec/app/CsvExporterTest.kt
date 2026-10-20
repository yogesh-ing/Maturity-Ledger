package com.afec.app

import com.afec.app.data.InstrumentType
import com.afec.app.data.TrackedInstrument
import com.afec.app.export.CsvExporter
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** FR-12: CSV export shape (pure JVM, no Android calls in buildCsv). */
class CsvExporterTest {
    @Test
    fun `csv has header and escapes commas and quotes`() {
        val epoch = LocalDate.of(2026, 11, 12)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val csv = CsvExporter.buildCsv(
            listOf(
                TrackedInstrument(
                    title = "FD, \"Special\"", institutionName = "HDFC Bank",
                    instrumentType = InstrumentType.FD, principalAmount = 250000.0,
                    maturityDateEpoch = epoch
                )
            )
        )
        val lines = csv.trim().lines()
        assertTrue(lines[0].startsWith("Title,Institution,Type"))
        assertTrue(lines[1].contains("\"FD, \"\"Special\"\"\""))
        assertTrue(lines[1].contains("12 Nov 2026"))
    }
}
