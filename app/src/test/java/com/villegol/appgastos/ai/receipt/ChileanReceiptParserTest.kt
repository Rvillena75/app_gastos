package com.villegol.appgastos.ai.receipt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class ChileanReceiptParserTest {
    @Test
    fun syntheticGroundTruthResourceMatchesCanonicalExample() {
        val stream = checkNotNull(javaClass.getResourceAsStream("/receipt/synthetic_ground_truth.json"))
        val fixture = stream.bufferedReader().use { JSONObject(it.readText()) }
        val truth = ReceiptGroundTruth.SYNTHETIC_EXAMPLE
        assertTrue(fixture.getBoolean("synthetic"))
        assertEquals(truth.merchantName, fixture.getString("merchantName"))
        assertEquals(truth.rut, fixture.getString("rut"))
        assertEquals(truth.date, fixture.getString("date"))
        assertEquals(truth.totalMinor, fixture.getLong("totalMinor"))
        assertEquals(truth.netMinor, fixture.getLong("netMinor"))
        assertEquals(truth.taxMinor, fixture.getLong("taxMinor"))
        assertEquals(truth.discountMinor, fixture.getLong("discountMinor"))
        val items = fixture.getJSONArray("items")
        assertEquals(truth.productCount, items.length())
        for (index in 0 until items.length()) {
            val item = items.getJSONObject(index)
            assertEquals(truth.productAmounts.getValue(item.getString("description")), item.getLong("lineTotalMinor"))
        }
    }

    @Test
    fun parsesChileanRutAndValidatesItsCheckDigit() {
        assertEquals("11.111.111-1", ChileanReceiptUtils.normalizeRut("11 111 111 1"))
        assertTrue(ChileanReceiptUtils.isValidRut("11.111.111-1"))
        assertFalse(ChileanReceiptUtils.isValidRut("11.111.111-2"))
    }

    @Test
    fun parsesClpGroupingAndNegativeDiscountsWithoutDecimalInterpretation() {
        assertEquals(11_000L, ChileanReceiptUtils.parseClpAmount("11.000"))
        assertEquals(11_000L, ChileanReceiptUtils.parseClpAmount("$11.000"))
        assertEquals(11_000L, ChileanReceiptUtils.parseClpAmount("11,000"))
        assertEquals(11_000L, ChileanReceiptUtils.parseClpAmount("11000"))
        assertEquals(-2_000L, ChileanReceiptUtils.parseClpAmount("-2.000"))
        assertEquals(-2_000L, ChileanReceiptUtils.parseClpAmount("-$2.000"))
        assertEquals(-2_000L, ChileanReceiptUtils.parseClpAmount("2.000-"))
        assertEquals(17_679L, ChileanReceiptUtils.parseClpAmount("$ 17.679.00 c/u"))
        assertEquals(2_823L, ChileanReceiptUtils.parseClpAmount("2.823"))
        assertEquals(listOf("2.823"), ChileanReceiptUtils.moneyTokens("I.V.A. 19% 2.823"))
    }

    @Test
    fun normalizesMerchantAndCalendarValuesWithoutInventingSeconds() {
        assertEquals("COMERCIO DEMOSTRACION", ChileanReceiptUtils.normalizeMerchant("Comércio Demostración SPA"))
        assertEquals("2026-08-10", ChileanReceiptUtils.normalizeDate("10/08/2026"))
        assertEquals("17:44", ChileanReceiptUtils.normalizeTime("17:44"))
    }

    @Test
    fun merchantCorrectionKeyIsStableAndExactAfterNormalization() {
        assertEquals(
            "merchant_TIEMDA DEMO",
            ReceiptMerchantCorrectionStore.keyFor("Tiemda Démo SPA")
        )
        assertEquals(
            ReceiptMerchantCorrectionStore.keyFor("TIEMDA DEMO SPA"),
            ReceiptMerchantCorrectionStore.keyFor("tiemda-demo")
        )
    }

    @Test
    fun parsesSyntheticExampleShapeUsingSpatialColumnsAndReconcilesIt() {
        val receipt = ChileanReceiptParser().parse(syntheticExampleOcr())
        val categorized = receipt.copy(category = ReceiptCategory("Salud", "Medicamentos", 0.99f, "merchant_rule"))
        val validation = ReceiptValidator().validate(categorized)
        val accuracy = ReceiptAccuracyEvaluator().evaluate(categorized, ReceiptGroundTruth.SYNTHETIC_EXAMPLE)

        assertEquals("COMERCIO DEMOSTRACION SPA", receipt.merchantName)
        assertEquals("11.111.111-1", receipt.rut)
        assertEquals("2026-08-10", receipt.date)
        assertEquals("12:30:00", receipt.time)
        assertEquals("11.000", receipt.totalMinor?.let { "%1$,d".format(java.util.Locale.ROOT, it).replace(',', '.') })
        assertEquals(2, receipt.items.size)
        assertEquals(6_000L, receipt.items[0].lineTotalMinor)
        assertEquals(7_000L, receipt.items[1].lineTotalMinor)
        assertEquals(2_000L, receipt.discounts.single().amountMinor)
        assertEquals(1f, validation.score)
        assertEquals(1f, accuracy.score)
        assertTrue(validation.checks.all { it.status == ValidationStatus.PASS })
    }

    @Test
    fun spatialGroupingReordersElementsIntoRowsAndColumns() {
        val document = OcrDocument.fromLines(
            listOf(
                fixtureLine(0, 100, "VALOR" to 500, "ARTICULO" to 10, "CANT" to 270),
                fixtureLine(1, 130, "6.000" to 500, "PRODUCTO DEMO A" to 10, "1" to 270),
                fixtureLine(2, 130, "6.000" to 500, "PRODUCTO DEMO A" to 10, "1" to 270)
            )
        )
        val rows = OcrSpatialGrouping.group(document)
        assertEquals(2, rows.size)
        assertEquals("ARTICULO CANT VALOR", rows[0].text)
        assertEquals("PRODUCTO DEMO A 1 6.000", rows[1].text)
    }

    @Test
    fun associatesPharmacyUnitPriceRowsWithTrailingObservedValues() {
        val receipt = ChileanReceiptParser().parse(
            OcrDocument.fromLines(
                listOf(
                    fixtureLine(0, 100, "ARTICUL0" to 10),
                    fixtureLine(1, 120, "PRECIO UNITARIO" to 10),
                    fixtureLine(2, 150, "PRODUCTO DEMO C 355ML" to 10),
                    fixtureLine(3, 170, "($ 8.000,00 c/u)" to 10),
                    fixtureLine(4, 200, "PRODUCTO DEMO D 355ML" to 10),
                    fixtureLine(5, 220, "($ 9.000,00 c/u)" to 10),
                    fixtureLine(6, 250, "TOTAL NETO" to 10),
                    fixtureLine(7, 270, "I.V.A. 19%" to 10),
                    fixtureLine(8, 290, "TOTAL BOLETA" to 10),
                    fixtureLine(9, 330, "8.000" to 300),
                    fixtureLine(10, 350, "9.000" to 300),
                    fixtureLine(11, 370, "14.286" to 300),
                    fixtureLine(12, 390, "2.714" to 300),
                    fixtureLine(13, 410, "17.000" to 300)
                ),
                engine = "pharmacy-no-header-fixture"
            )
        )

        assertEquals(2, receipt.items.size)
        assertEquals(8_000L, receipt.items[0].unitPriceMinor)
        assertEquals(8_000L, receipt.items[0].lineTotalMinor)
        assertEquals(9_000L, receipt.items[1].unitPriceMinor)
        assertEquals(9_000L, receipt.items[1].lineTotalMinor)
    }

    @Test
    fun repairsQuantityAssignedToPriceWhenLineArithmeticIsExact() {
        val receipt = ChileanReceiptParser().parse(
            OcrDocument.fromLines(
                listOf(
                    fixtureLine(0, 100, "ARTICULO" to 10),
                    fixtureLine(1, 120, "CANT PRECIO" to 270),
                    fixtureLine(2, 140, "VALOR" to 500),
                    fixtureLine(3, 170, "PRODUCTO DEMO A" to 10, "1" to 270, "6.000" to 500),
                    fixtureLine(4, 200, "SUBTOTAL BOLETA" to 10),
                    fixtureLine(5, 220, "TOTAL NETO 9.244" to 10),
                    fixtureLine(6, 240, "TOTAL IVA(19%) 1.756" to 10),
                    fixtureLine(7, 260, "TOTAL 11.000" to 10)
                ),
                engine = "split-header-fixture"
            )
        )

        assertEquals(1, receipt.items.size)
        assertEquals(1, receipt.items[0].quantity)
        assertEquals(6_000L, receipt.items[0].unitPriceMinor)
        assertEquals(6_000L, receipt.items[0].lineTotalMinor)
        assertEquals("PRODUCTO DEMO A", receipt.items[0].description)
    }

    @Test
    fun receiptSerializationUsesOneComparableContract() {
        val receipt = StructuredReceipt(
            merchantName = "Jumbo",
            totalMinor = 23_990,
            items = listOf(ReceiptLineItem("Pan", 1, 1_000, 1_000)),
            category = ReceiptCategory("Alimentación", "Supermercado", 0.98f, "merchant_rule")
        )
        val json = receipt.toJson()
        assertTrue(json.contains("\"items\""))
        assertTrue(json.contains("\"category\""))
        assertTrue(json.contains("\"totalMinor\": 23990"))
    }

    @Test
    fun validatorReportsUnknownWhenArithmeticInputsAreMissing() {
        val validation = ReceiptValidator().validate(StructuredReceipt(merchantName = "Comercio"))
        assertTrue(validation.checks.any { it.name == "items_minus_discounts_total" && it.status == ValidationStatus.UNKNOWN })
        assertTrue(validation.checks.any { it.name == "net_plus_tax_total" && it.status == ValidationStatus.UNKNOWN })
        assertTrue(validation.score == 0f)
    }

    @Test
    fun incompleteReceiptDoesNotReceivePerfectValidationScore() {
        val validation = ReceiptValidator().validate(
            StructuredReceipt(
                merchantName = "Comercio",
                totalMinor = 11_000,
                category = ReceiptCategory("Salud", confidence = 0.9f, source = "rule")
            )
        )

        assertTrue(validation.checks.any { it.status == ValidationStatus.UNKNOWN })
        assertTrue(validation.score < 1f)
    }

    private fun syntheticExampleOcr(): OcrDocument {
        val lines = listOf(
            fixtureLine(0, 10, "COMERCIO DEMOSTRACION SPA" to 10),
            fixtureLine(1, 30, "R.U.T.:" to 10, "11.111.111-1" to 130),
            fixtureLine(2, 50, "Casa Matriz:" to 10, "AVENIDA DEMO # 100, CIUDAD FICTICIA," to 160),
            fixtureLine(3, 70, "CIUDAD FICTICIA" to 10),
            fixtureLine(4, 90, "Boleta Electronica Nº:" to 10, "1000000001" to 190, "Caja:6" to 410),
            fixtureLine(5, 110, "Fecha:" to 10, "10-08-2026" to 160, "Hora:12:30:00" to 410),
            fixtureLine(6, 130, "Sucursal:" to 10, "001 SUCURSAL DEMO" to 120),
            fixtureLine(7, 170, "ARTICULO" to 10, "CANT" to 270, "PRECIO" to 350, "VALOR" to 500),
            fixtureLine(8, 200, "PRODUCTO DEMO A" to 10, "1" to 270, "6.000" to 350, "6.000" to 500),
            fixtureLine(9, 230, "***DCTO DEMOSTRACION" to 10, "-2.000" to 500),
            fixtureLine(10, 260, "PRODUCTO DEMO B" to 10, "1" to 270, "7.000" to 350, "7.000" to 500),
            fixtureLine(11, 300, "SUBTOTAL BOLETA $ 11.000" to 300),
            fixtureLine(12, 320, "TOTAL NETO $ 9.244" to 300),
            fixtureLine(13, 340, "TOTAL EXENTO $ 0" to 300),
            fixtureLine(14, 360, "TOTAL IVA(19%) $ 1.756" to 300),
            fixtureLine(15, 380, "TOTAL $ 11.000" to 300)
        )
        return OcrDocument.fromLines(lines, engine = "synthetic-example-fixture")
    }

    private fun fixtureLine(index: Int, top: Int, vararg tokens: Pair<String, Int>): OcrLine {
        val elements = tokens.mapIndexed { elementIndex, (text, left) ->
            OcrElement(
                text = text,
                bounds = OcrBounds(left, top, left + (text.length * 8).coerceAtLeast(8), top + 16),
                confidence = 0.99f,
                blockIndex = 0,
                lineIndex = index,
                elementIndex = elementIndex,
                order = index * 10 + elementIndex
            )
        }
        return OcrLine(
            text = tokens.joinToString(" ") { it.first },
            bounds = OcrBounds(
                tokens.minOfOrNull { it.second } ?: 0,
                top,
                (tokens.maxOfOrNull { (text, left) -> left + (text.length * 8).coerceAtLeast(8) } ?: 0),
                top + 16
            ),
            confidence = 0.99f,
            blockIndex = 0,
            lineIndex = index,
            elements = elements,
            order = index
        )
    }
}
