package com.villegol.appgastos.export

import com.villegol.appgastos.data.Expense
import com.villegol.appgastos.data.PaymentCard
import com.villegol.appgastos.data.PaymentCardType
import com.villegol.appgastos.data.ExpenseStatus
import com.villegol.appgastos.domain.SpendingAnalysis
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExpenseXlsxExporter(
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {
    fun write(
        output: OutputStream,
        expenses: List<Expense>,
        cards: List<PaymentCard>,
        analysis: SpendingAnalysis,
        monthlyBudgetMinor: Long?
    ) {
        val activeExpenses = expenses
            .filter { it.status != ExpenseStatus.DELETED }
            .sortedByDescending { it.occurredAt }
        val cardsByLast4 = cards.associateBy { it.last4 }

        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            zip.writeEntry("[Content_Types].xml", contentTypesXml)
            zip.writeEntry("_rels/.rels", rootRelationshipsXml)
            zip.writeEntry("xl/workbook.xml", workbookXml)
            zip.writeEntry("xl/_rels/workbook.xml.rels", workbookRelationshipsXml)
            zip.writeEntry("xl/styles.xml", stylesXml)
            zip.writeEntry(
                "xl/worksheets/sheet1.xml",
                summarySheetXml(analysis, monthlyBudgetMinor)
            )
            zip.writeEntry(
                "xl/worksheets/sheet2.xml",
                movementsSheetXml(activeExpenses, cardsByLast4)
            )
            zip.writeEntry("xl/worksheets/sheet3.xml", cardsSheetXml(cards))
        }
    }

    private fun summarySheetXml(
        analysis: SpendingAnalysis,
        monthlyBudgetMinor: Long?
    ): String {
        val monthLabel = analysis.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("es", "CL")))
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("es", "CL")) else it.toString() }
        val rows = mutableListOf<List<XlsxCell>>()
        rows += listOf(XlsxCell.Text("Resumen mensual", HEADER_STYLE))
        rows += listOf(XlsxCell.Text("Mes", LABEL_STYLE), XlsxCell.Text(monthLabel))
        rows += listOf(XlsxCell.Text("Total CLP", LABEL_STYLE), XlsxCell.Number(analysis.totalMinor.toDouble(), MONEY_STYLE))
        rows += listOf(XlsxCell.Text("Movimientos contabilizados", LABEL_STYLE), XlsxCell.Number(analysis.movementCount.toDouble()))
        rows += listOf(XlsxCell.Text("Promedio por movimiento", LABEL_STYLE), XlsxCell.Number(analysis.averageMovementMinor.toDouble(), MONEY_STYLE))
        rows += listOf(
            XlsxCell.Text("Presupuesto CLP", LABEL_STYLE),
            monthlyBudgetMinor?.let { XlsxCell.Number(it.toDouble(), MONEY_STYLE) } ?: XlsxCell.Text("Sin definir")
        )
        rows += listOf(
            XlsxCell.Text("Disponible CLP", LABEL_STYLE),
            monthlyBudgetMinor?.let { XlsxCell.Number((it - analysis.totalMinor).toDouble(), MONEY_STYLE) }
                ?: XlsxCell.Text("Sin definir")
        )
        rows.add(emptyList())
        rows += listOf(
            XlsxCell.Text("Categoría", HEADER_STYLE),
            XlsxCell.Text("Monto CLP", HEADER_STYLE),
            XlsxCell.Text("Movimientos", HEADER_STYLE)
        )
        analysis.categoryBreakdown.forEach { item ->
            rows += listOf(
                XlsxCell.Text(item.label),
                XlsxCell.Number(item.amountMinor.toDouble(), MONEY_STYLE),
                XlsxCell.Number(item.movementCount.toDouble())
            )
        }
        rows.add(emptyList())
        rows += listOf(
            XlsxCell.Text("Tarjeta", HEADER_STYLE),
            XlsxCell.Text("Monto CLP", HEADER_STYLE),
            XlsxCell.Text("Movimientos", HEADER_STYLE)
        )
        analysis.cardBreakdown.forEach { item ->
            rows += listOf(
                XlsxCell.Text(item.label),
                XlsxCell.Number(item.amountMinor.toDouble(), MONEY_STYLE),
                XlsxCell.Number(item.movementCount.toDouble())
            )
        }
        return worksheetXml(rows, listOf(30.0, 20.0, 16.0))
    }

    private fun movementsSheetXml(
        expenses: List<Expense>,
        cardsByLast4: Map<String, PaymentCard>
    ): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val rows = mutableListOf<List<XlsxCell>>()
        rows += listOf(
            "Fecha y hora",
            "Comercio",
            "Monto",
            "Moneda",
            "Categoría",
            "Tarjeta",
            "Tipo de tarjeta",
            "Descripción",
            "Estado"
        ).map { XlsxCell.Text(it, HEADER_STYLE) }
        expenses.forEach { expense ->
            val card = expense.cardLast4?.let(cardsByLast4::get)
            rows += listOf(
                XlsxCell.Text(formatter.format(expense.occurredAt.atZone(zoneId))),
                XlsxCell.Text(expense.merchantName.orEmpty()),
                expense.amountMinor?.let {
                    XlsxCell.Number(amountForSpreadsheet(it, expense.currency), MONEY_STYLE)
                } ?: XlsxCell.Text(""),
                XlsxCell.Text(expense.currency),
                XlsxCell.Text(expense.categoryName ?: "Sin categoría"),
                XlsxCell.Text(
                    expense.cardLast4?.let { last4 ->
                        card?.name?.takeIf(String::isNotBlank)?.let { "$it · •••• $last4" } ?: "•••• $last4"
                    }.orEmpty()
                ),
                XlsxCell.Text(card?.type?.displayName().orEmpty()),
                XlsxCell.Text(expense.description.orEmpty()),
                XlsxCell.Text(expense.status.name)
            )
        }
        return worksheetXml(rows, listOf(20.0, 30.0, 16.0, 12.0, 22.0, 26.0, 18.0, 36.0, 18.0))
    }

    private fun cardsSheetXml(cards: List<PaymentCard>): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val rows = mutableListOf<List<XlsxCell>>()
        rows += listOf("Nombre", "Últimos 4", "Tipo", "Color", "Primera detección")
            .map { XlsxCell.Text(it, HEADER_STYLE) }
        cards.sortedBy { it.name ?: it.last4 }.forEach { card ->
            rows += listOf(
                XlsxCell.Text(card.name.orEmpty()),
                XlsxCell.Text(card.last4),
                XlsxCell.Text(card.type.displayName()),
                XlsxCell.Text(card.colorHex.orEmpty()),
                XlsxCell.Text(formatter.format(card.firstSeenAt.atZone(zoneId)))
            )
        }
        return worksheetXml(rows, listOf(28.0, 14.0, 18.0, 14.0, 22.0))
    }

    private fun worksheetXml(rows: List<List<XlsxCell>>, widths: List<Double>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>")
        append("<cols>")
        widths.forEachIndexed { index, width ->
            val column = index + 1
            append("<col min=\"").append(column).append("\" max=\"").append(column)
                .append("\" width=\"").append(width).append("\" customWidth=\"1\"/>")
        }
        append("</cols><sheetData>")
        rows.forEachIndexed { rowIndex, cells ->
            val rowNumber = rowIndex + 1
            append("<row r=\"").append(rowNumber).append("\">")
            cells.forEachIndexed { columnIndex, cell ->
                val reference = "${columnName(columnIndex + 1)}$rowNumber"
                when (cell) {
                    is XlsxCell.Text -> {
                        append("<c r=\"").append(reference).append("\" t=\"inlineStr\"")
                        cell.style?.let { append(" s=\"").append(it).append("\"") }
                        append("><is><t xml:space=\"preserve\">")
                            .append(escapeXml(cell.value))
                            .append("</t></is></c>")
                    }
                    is XlsxCell.Number -> {
                        append("<c r=\"").append(reference).append("\"")
                        cell.style?.let { append(" s=\"").append(it).append("\"") }
                        append("><v>").append(formatNumber(cell.value)).append("</v></c>")
                    }
                }
            }
            append("</row>")
        }
        append("</sheetData><autoFilter ref=\"A1:")
            .append(columnName(widths.size)).append("1\"/>")
        append("</worksheet>")
    }

    private fun amountForSpreadsheet(amountMinor: Long, currency: String): Double =
        if (currency == "CLP") amountMinor.toDouble() else amountMinor / 100.0

    private fun PaymentCardType.displayName(): String = when (this) {
        PaymentCardType.CREDIT -> "Crédito"
        PaymentCardType.DEBIT -> "Débito"
        PaymentCardType.PREPAID -> "Prepago"
        PaymentCardType.OTHER -> "Otra"
    }

    private fun ZipOutputStream.writeEntry(path: String, content: String) {
        putNextEntry(ZipEntry(path))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun formatNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    private fun columnName(column: Int): String {
        var value = column
        return buildString {
            while (value > 0) {
                val remainder = (value - 1) % 26
                insert(0, ('A'.code + remainder).toChar())
                value = (value - 1) / 26
            }
        }
    }

    private fun escapeXml(value: String): String = value
        .filter { it == '\t' || it == '\n' || it == '\r' || it.code >= 0x20 }
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private sealed interface XlsxCell {
        val style: Int?

        data class Text(val value: String, override val style: Int? = null) : XlsxCell
        data class Number(val value: Double, override val style: Int? = null) : XlsxCell
    }

    private companion object {
        const val HEADER_STYLE = 1
        const val MONEY_STYLE = 2
        const val LABEL_STYLE = 3

        val contentTypesXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
              <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
              <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
              <Override PartName="/xl/worksheets/sheet3.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
              <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
            </Types>
        """.trimIndent()

        val rootRelationshipsXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
            </Relationships>
        """.trimIndent()

        val workbookXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              <sheets>
                <sheet name="Resumen" sheetId="1" r:id="rId1"/>
                <sheet name="Movimientos" sheetId="2" r:id="rId2"/>
                <sheet name="Tarjetas" sheetId="3" r:id="rId3"/>
              </sheets>
            </workbook>
        """.trimIndent()

        val workbookRelationshipsXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
              <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
              <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet3.xml"/>
              <Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
            </Relationships>
        """.trimIndent()

        val stylesXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <numFmts count="1"><numFmt numFmtId="164" formatCode="#,##0;[Red]-#,##0"/></numFmts>
              <fonts count="2">
                <font><sz val="11"/><name val="Calibri"/></font>
                <font><b/><color rgb="FFFFFFFF"/><sz val="11"/><name val="Calibri"/></font>
              </fonts>
              <fills count="3">
                <fill><patternFill patternType="none"/></fill>
                <fill><patternFill patternType="gray125"/></fill>
                <fill><patternFill patternType="solid"><fgColor rgb="FF245B49"/><bgColor indexed="64"/></patternFill></fill>
              </fills>
              <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
              <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
              <cellXfs count="4">
                <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
                <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFill="1" applyFont="1"/>
                <xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
                <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyFont="1"/>
              </cellXfs>
              <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
            </styleSheet>
        """.trimIndent()
    }
}
