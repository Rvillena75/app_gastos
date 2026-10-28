package com.villegol.appgastos.ai.receipt

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToLong

class ReceiptValidator {
    fun validate(receipt: StructuredReceipt): ReceiptValidation {
        val checks = buildList {
            add(checkRut(receipt.rut))
            add(checkDate(receipt.date))
            add(checkTotal(receipt.totalMinor))
            add(checkQuantities(receipt.items))
            add(checkLineArithmetic(receipt.items))
            add(checkItemsDiscountsTotal(receipt))
            add(checkNetTaxTotal(receipt))
            add(checkSubtotal(receipt))
            add(checkTaxRate(receipt))
            add(checkCategory(receipt.category))
        }
        val pass = checks.count { it.status == ValidationStatus.PASS }
        // UNKNOWN is evidence that the pipeline did not extract enough data to
        // verify a rule.  It must not be removed from the denominator: doing
        // so would give an incomplete generative result a perfect validation
        // score just because its few present fields happened to be valid.
        return ReceiptValidation(checks = checks, score = pass.toFloat() / checks.size)
    }

    private fun checkRut(rut: String?): ValidationCheck = when {
        rut.isNullOrBlank() -> ValidationCheck("rut_valid", ValidationStatus.UNKNOWN, message = "No se detectó RUT")
        ChileanReceiptUtils.isValidRut(rut) -> ValidationCheck("rut_valid", ValidationStatus.PASS, rut, "dígito verificador válido", "RUT válido")
        else -> ValidationCheck("rut_valid", ValidationStatus.FAIL, rut, "dígito verificador válido", "RUT inválido")
    }

    private fun checkDate(date: String?): ValidationCheck = when {
        date.isNullOrBlank() -> ValidationCheck("date_valid", ValidationStatus.UNKNOWN, message = "No se detectó fecha")
        runCatching { LocalDate.parse(date) }.isSuccess -> ValidationCheck("date_valid", ValidationStatus.PASS, date, "fecha calendario válida", "Fecha válida")
        else -> ValidationCheck("date_valid", ValidationStatus.FAIL, date, "fecha calendario válida", "Fecha inválida")
    }

    private fun checkTotal(total: Long?): ValidationCheck = when {
        total == null -> ValidationCheck("total_valid", ValidationStatus.UNKNOWN, message = "No se detectó total")
        total > 0 -> ValidationCheck("total_valid", ValidationStatus.PASS, total.toString(), "> 0 CLP", "Total razonable")
        else -> ValidationCheck("total_valid", ValidationStatus.FAIL, total.toString(), "> 0 CLP", "Total no positivo")
    }

    private fun checkQuantities(items: List<ReceiptLineItem>): ValidationCheck {
        if (items.isEmpty()) return ValidationCheck("quantities_reasonable", ValidationStatus.UNKNOWN, message = "No hay productos")
        val known = items.filter { it.quantity != null }
        if (known.isEmpty()) return ValidationCheck("quantities_reasonable", ValidationStatus.UNKNOWN, message = "No se detectaron cantidades")
        val invalid = known.filter { !ChileanReceiptUtils.isReasonableQuantity(it.quantity) }
        return if (invalid.isEmpty()) {
            ValidationCheck("quantities_reasonable", ValidationStatus.PASS, known.size.toString(), "cantidades entre 1 y 1000", "Cantidades razonables")
        } else {
            ValidationCheck("quantities_reasonable", ValidationStatus.FAIL, invalid.joinToString { it.quantity.toString() }, "cantidades entre 1 y 1000", "Hay cantidades fuera de rango")
        }
    }

    private fun checkLineArithmetic(items: List<ReceiptLineItem>): ValidationCheck {
        val comparable = items.filter { it.quantity != null && it.unitPriceMinor != null && it.lineTotalMinor != null }
        if (comparable.isEmpty()) return ValidationCheck("quantity_times_unit_price", ValidationStatus.UNKNOWN, message = "Faltan cantidad, precio unitario o valor")
        val invalid = comparable.filter {
            val expected = it.quantity!!.toLong() * it.unitPriceMinor!!
            abs(expected - it.lineTotalMinor!!) > 1
        }
        return if (invalid.isEmpty()) {
            ValidationCheck("quantity_times_unit_price", ValidationStatus.PASS, "${comparable.size}/${items.size}", "diferencia <= 1 CLP", "Líneas consistentes")
        } else {
            ValidationCheck("quantity_times_unit_price", ValidationStatus.FAIL, invalid.joinToString { it.description }, "diferencia <= 1 CLP", "Una o más líneas no cuadran")
        }
    }

    private fun checkItemsDiscountsTotal(receipt: StructuredReceipt): ValidationCheck {
        val lineTotals = receipt.items.mapNotNull { it.lineTotalMinor }
        if (lineTotals.isEmpty() || receipt.totalMinor == null) {
            return ValidationCheck("items_minus_discounts_total", ValidationStatus.UNKNOWN, message = "Faltan productos o total")
        }
        val gross = lineTotals.sum()
        val discounts = receipt.discounts.sumOf { abs(it.amountMinor) }
        val expected = gross - discounts
        return if (abs(expected - receipt.totalMinor) <= 1) {
            ValidationCheck("items_minus_discounts_total", ValidationStatus.PASS, expected.toString(), receipt.totalMinor.toString(), "Productos menos descuentos cuadran")
        } else {
            ValidationCheck("items_minus_discounts_total", ValidationStatus.FAIL, expected.toString(), receipt.totalMinor.toString(), "Productos menos descuentos no cuadran")
        }
    }

    private fun checkNetTaxTotal(receipt: StructuredReceipt): ValidationCheck {
        val net = receipt.netMinor ?: return ValidationCheck("net_plus_tax_total", ValidationStatus.UNKNOWN, message = "No se detectó neto")
        val tax = receipt.taxMinor ?: return ValidationCheck("net_plus_tax_total", ValidationStatus.UNKNOWN, message = "No se detectó IVA")
        val total = receipt.totalMinor ?: return ValidationCheck("net_plus_tax_total", ValidationStatus.UNKNOWN, message = "No se detectó total")
        val expected = net + tax
        return if (abs(expected - total) <= 1) {
            ValidationCheck("net_plus_tax_total", ValidationStatus.PASS, expected.toString(), total.toString(), "Neto más IVA cuadra")
        } else {
            ValidationCheck("net_plus_tax_total", ValidationStatus.FAIL, expected.toString(), total.toString(), "Neto más IVA no cuadra")
        }
    }

    private fun checkSubtotal(receipt: StructuredReceipt): ValidationCheck {
        val subtotal = receipt.subtotalMinor ?: return ValidationCheck("subtotal_reconciliation", ValidationStatus.UNKNOWN, message = "No se detectó subtotal")
        val total = receipt.totalMinor ?: return ValidationCheck("subtotal_reconciliation", ValidationStatus.UNKNOWN, message = "No se detectó total")
        val gross = receipt.items.mapNotNull { it.lineTotalMinor }.sum().takeIf { it > 0 }
        val discounts = receipt.discounts.sumOf { abs(it.amountMinor) }
        val pass = abs(subtotal - total) <= 1 || (gross != null && abs(gross - subtotal - discounts) <= 1) || abs(subtotal - discounts - total) <= 1
        return if (pass) {
            ValidationCheck("subtotal_reconciliation", ValidationStatus.PASS, subtotal.toString(), total.toString(), "Subtotal consistente con total/descuentos")
        } else {
            ValidationCheck("subtotal_reconciliation", ValidationStatus.FAIL, subtotal.toString(), total.toString(), "Subtotal no reconciliado")
        }
    }

    private fun checkTaxRate(receipt: StructuredReceipt): ValidationCheck {
        val rate = receipt.taxRatePercent ?: return ValidationCheck("tax_rate_coherent", ValidationStatus.UNKNOWN, message = "No se detectó tasa de IVA")
        val net = receipt.netMinor ?: return ValidationCheck("tax_rate_coherent", ValidationStatus.UNKNOWN, message = "No se detectó neto")
        val tax = receipt.taxMinor ?: return ValidationCheck("tax_rate_coherent", ValidationStatus.UNKNOWN, message = "No se detectó IVA")
        val expected = (net * rate / 100.0).roundToLong()
        return if (abs(expected - tax) <= 1) {
            ValidationCheck("tax_rate_coherent", ValidationStatus.PASS, tax.toString(), "$rate% de $net", "Tasa de IVA coherente con tolerancia de 1 CLP")
        } else {
            ValidationCheck("tax_rate_coherent", ValidationStatus.FAIL, tax.toString(), expected.toString(), "Tasa de IVA no coherente")
        }
    }

    private fun checkCategory(category: ReceiptCategory?): ValidationCheck = if (category == null) {
        ValidationCheck("category_present", ValidationStatus.UNKNOWN, message = "No se categorizó")
    } else {
        ValidationCheck("category_present", ValidationStatus.PASS, category.category, "categoría no vacía", "Categoría disponible")
    }
}

class ReceiptConfidenceScorer {
    fun score(receipt: StructuredReceipt, validation: ReceiptValidation, ocrConfidence: Float? = null): Float {
        val fieldSignals = listOf(
            receipt.merchantName != null,
            receipt.rut != null,
            receipt.date != null,
            receipt.totalMinor != null,
            receipt.items.isNotEmpty(),
            receipt.netMinor != null,
            receipt.taxMinor != null,
            receipt.category != null
        )
        val fieldScore = fieldSignals.count { it }.toFloat() / fieldSignals.size
        val validationScore = validation.score
        val observedOcr = ocrConfidence?.coerceIn(0f, 1f) ?: fieldScore
        return (fieldScore * 0.35f + validationScore * 0.45f + observedOcr * 0.20f).coerceIn(0f, 1f)
    }
}

class ReceiptAccuracyEvaluator {
    fun evaluate(actual: StructuredReceipt, truth: ReceiptGroundTruth): ReceiptAccuracy {
        val matches = listOf(
            match("merchant", ChileanReceiptUtils.merchantMatches(actual.merchantName, truth.merchantName), truth.merchantName, actual.merchantName),
            match("rut", actual.rut?.let { ChileanReceiptUtils.normalizeRut(it) == ChileanReceiptUtils.normalizeRut(truth.rut) }, truth.rut, actual.rut),
            match("date", actual.date == truth.date, truth.date, actual.date),
            match("total", actual.totalMinor == truth.totalMinor, truth.totalMinor.toString(), actual.totalMinor?.toString()),
            match("net", actual.netMinor == truth.netMinor, truth.netMinor.toString(), actual.netMinor?.toString()),
            match("tax", actual.taxMinor == truth.taxMinor, truth.taxMinor.toString(), actual.taxMinor?.toString()),
            match("product_count", actual.items.size == truth.productCount, truth.productCount.toString(), actual.items.size.toString()),
            productAmountMatch(actual, truth),
            match("discount", actual.discounts.sumOf { kotlin.math.abs(it.amountMinor) } == truth.discountMinor, truth.discountMinor.toString(), actual.discounts.sumOf { kotlin.math.abs(it.amountMinor) }.toString()),
            match("category", actual.category?.category?.let { ChileanReceiptUtils.fold(it) == ChileanReceiptUtils.fold(truth.category) }, truth.category, actual.category?.category)
        )
        val known = matches.count { it.matched != null }
        val passed = matches.count { it.matched == true }
        return ReceiptAccuracy(matches, if (known == 0) 0f else passed.toFloat() / known)
    }

    private fun productAmountMatch(actual: StructuredReceipt, truth: ReceiptGroundTruth): AccuracyMatch {
        val expected = truth.productAmounts.entries.sortedBy { ChileanReceiptUtils.normalizeMerchant(it.key) }
        val actualItems = actual.items
        if (actualItems.isEmpty()) return AccuracyMatch("product_amounts", null, expected.joinToString(), null, "No hay productos")
        val matched = expected.all { (description, amount) ->
            actualItems.any { item ->
                ChileanReceiptUtils.merchantMatches(item.description, description) == true && item.lineTotalMinor == amount
            }
        }
        return AccuracyMatch(
            name = "product_amounts",
            matched = matched,
            expected = expected.joinToString { "${it.key}=${it.value}" },
            actual = actualItems.joinToString { "${it.description}=${it.lineTotalMinor}" },
            message = if (matched) "Montos de productos coinciden" else "Hay diferencias en productos"
        )
    }

    private fun match(name: String, matched: Boolean?, expected: String?, actual: String?): AccuracyMatch = AccuracyMatch(
        name = name,
        matched = matched,
        expected = expected,
        actual = actual,
        message = when (matched) {
            true -> "Coincide"
            false -> "No coincide"
            null -> "No disponible"
        }
    )
}
