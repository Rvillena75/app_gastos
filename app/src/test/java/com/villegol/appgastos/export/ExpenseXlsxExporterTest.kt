package com.villegol.appgastos.export

import com.villegol.appgastos.data.Expense
import com.villegol.appgastos.data.PaymentCard
import com.villegol.appgastos.data.PaymentCardType
import com.villegol.appgastos.domain.SpendingAnalyzer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseXlsxExporterTest {
    @Test
    fun `writes a valid xlsx package with summary movements and cards`() {
        val expense = Expense(
            amountMinor = 11_000L,
            occurredAt = Instant.parse("2026-08-10T21:13:00Z"),
            merchantName = "COMERCIO DEMO & LOCAL 01",
            categoryName = "Salud",
            cardLast4 = "1111"
        )
        val cards = listOf(
            PaymentCard(
                last4 = "1111",
                name = "Visa <personal>",
                type = PaymentCardType.CREDIT
            )
        )
        val analysis = SpendingAnalyzer().analyze(
            listOf(expense),
            cards,
            YearMonth.of(2026, 8),
            ZoneId.of("UTC")
        )
        val output = ByteArrayOutputStream()

        ExpenseXlsxExporter(ZoneId.of("UTC")).write(output, listOf(expense), cards, analysis, 100_000L)

        val entries = unzip(output.toByteArray())
        assertEquals(
            setOf(
                "[Content_Types].xml",
                "_rels/.rels",
                "xl/workbook.xml",
                "xl/_rels/workbook.xml.rels",
                "xl/styles.xml",
                "xl/worksheets/sheet1.xml",
                "xl/worksheets/sheet2.xml",
                "xl/worksheets/sheet3.xml"
            ),
            entries.keys
        )
        assertTrue(entries.getValue("xl/workbook.xml").contains("name=\"Movimientos\""))
        assertTrue(entries.getValue("xl/worksheets/sheet2.xml").contains("COMERCIO DEMO &amp; LOCAL 01"))
        assertTrue(entries.getValue("xl/worksheets/sheet3.xml").contains("Visa &lt;personal&gt;"))
        assertTrue(entries.getValue("xl/worksheets/sheet1.xml").contains("<v>100000</v>"))
    }

    private fun unzip(bytes: ByteArray): Map<String, String> = buildMap {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                put(entry.name, zip.readBytes().toString(Charsets.UTF_8))
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
}
