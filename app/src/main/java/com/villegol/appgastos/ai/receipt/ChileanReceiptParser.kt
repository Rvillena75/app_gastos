package com.villegol.appgastos.ai.receipt

import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

object ChileanReceiptUtils {
    private val rutWithDash = Regex("(?<!\\d)([0-9][0-9. ]{6,10})\\s*-\\s*([0-9Kk])(?!\\d)")
    private val rutWithGroups = Regex("(?<!\\d)([0-9]{1,3}(?:[ .][0-9]{3}){2})\\s+([0-9Kk])(?!\\d)")
    private val compactRut = Regex("(?<!\\d)(\\d{7,9})([0-9Kk])(?!\\d)")
    private val datePattern = Regex(
        "(?<!\\d)(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{4})(?!\\d)|" +
            "(?<!\\d)(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})(?!\\d)"
    )
    private val timePattern = Regex(
        "(?<!\\d)((?:[01]?\\d|2[0-3]))\\s*[:.]\\s*([0-5]\\d)(?:\\s*[:.]\\s*([0-5]\\d))?(?!\\d)"
    )

    fun findRut(text: String): String? {
        val candidates = buildList {
            rutWithDash.findAll(text).forEach { add(it.value) }
            rutWithGroups.findAll(text).forEach { add(it.value) }
            compactRut.findAll(text).forEach { add(it.value) }
        }
        return candidates.firstNotNullOfOrNull(::normalizeRut)
    }

    fun normalizeRut(raw: String): String? {
        val clean = raw.trim()
            .replace(".", "")
            .replace(" ", "")
            .replace("-", "")
            .uppercase(Locale.ROOT)
        if (clean.length !in 8..10) return null
        val body = clean.dropLast(1)
        val verifier = clean.last()
        if (!body.all(Char::isDigit) || !isValidRut(body, verifier)) return null
        val grouped = body.reversed().chunked(3).map { it.reversed() }.reversed().joinToString(".")
        return "$grouped-$verifier"
    }

    fun isValidRut(raw: String): Boolean {
        val normalized = normalizeRutWithoutValidation(raw) ?: return false
        return isValidRut(normalized.substringBefore('-').replace(".", ""), normalized.last())
    }

    fun isValidRut(body: String, verifier: Char): Boolean {
        if (body.isBlank() || !body.all(Char::isDigit)) return false
        var multiplier = 2
        var sum = 0
        for (digit in body.reversed()) {
            sum += digit.digitToInt() * multiplier
            multiplier = if (multiplier == 7) 2 else multiplier + 1
        }
        val remainder = 11 - (sum % 11)
        val expected = when (remainder) {
            11 -> '0'
            10 -> 'K'
            else -> remainder.digitToChar()
        }
        return verifier.uppercaseChar() == expected
    }

    fun parseClpAmount(raw: String): Long? {
        // OCR sometimes keeps accounting decimals, e.g. "17.679.00".
        // CLP receipts do not use that suffix as a decimal amount.
        val value = raw.trim()
            .replace(Regex("([.,]00)(?=\\D*$)"), "")
        val negative = value.startsWith("-") || value.endsWith("-") || value.startsWith("(")
        val digits = value.filter(Char::isDigit)
        if (digits.isBlank()) return null
        val amount = digits.toLongOrNull() ?: return null
        return if (negative) -amount else amount
    }

    fun moneyTokens(text: String): List<String> = Regex(
        "(?<![A-Za-z0-9])[-+]?\\s*\\$?\\s*[0-9][0-9.,]*\\s*-?(?![A-Za-z0-9%])"
    ).findAll(text).map { it.value.trim() }.toList()

    fun normalizeDate(raw: String): String? {
        val match = datePattern.find(raw) ?: return null
        val year: Int
        val month: Int
        val day: Int
        if (match.groupValues[4].isNotBlank()) {
            year = match.groupValues[4].toInt()
            month = match.groupValues[5].toInt()
            day = match.groupValues[6].toInt()
        } else {
            day = match.groupValues[1].toInt()
            month = match.groupValues[2].toInt()
            year = match.groupValues[3].toInt()
        }
        return runCatching { LocalDate.of(year, month, day).format(DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
    }

    fun normalizeTime(raw: String): String? {
        val match = timePattern.find(raw) ?: return null
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        val seconds = match.groupValues[3].takeIf { it.isNotBlank() }?.toInt()
        return if (seconds == null) {
            "%02d:%02d".format(Locale.ROOT, hour, minute)
        } else {
            "%02d:%02d:%02d".format(Locale.ROOT, hour, minute, seconds)
        }
    }

    fun fold(raw: String): String = Normalizer.normalize(raw, Normalizer.Form.NFD)
        .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        .uppercase(Locale.ROOT)

    fun normalizeMerchant(raw: String?): String = raw.orEmpty()
        .let(::fold)
        .replace("[^A-Z0-9]+".toRegex(), " ")
        .replace("\\b(SPA|S A|LTDA|LIMITADA|SAS)\\b".toRegex(), " ")
        .trim()
        .replace("\\s+".toRegex(), " ")

    fun merchantMatches(actual: String?, expected: String?): Boolean? {
        if (actual.isNullOrBlank() || expected.isNullOrBlank()) return null
        val left = normalizeMerchant(actual).replace(" ", "")
        val right = normalizeMerchant(expected).replace(" ", "")
        return left == right || left.contains(right) || right.contains(left)
    }

    fun isReasonableQuantity(value: Int?): Boolean = value != null && value in 1..1_000

    private fun normalizeRutWithoutValidation(raw: String): String? {
        val clean = raw.trim().replace(".", "").replace(" ", "").replace("-", "").uppercase(Locale.ROOT)
        if (clean.length !in 8..10) return null
        val body = clean.dropLast(1)
        val verifier = clean.last()
        if (!body.all(Char::isDigit) || verifier !in "0123456789K") return null
        return "${body}-$verifier"
    }
}

data class SpatialRow(
    val elements: List<OcrElement>,
    val text: String,
    val centerY: Float,
    val sourceLineIndices: List<Int>
)

object OcrSpatialGrouping {
    fun group(document: OcrDocument): List<SpatialRow> {
        val elements = document.elements.filter { it.text.isNotBlank() }
        if (elements.isEmpty()) {
            return document.lines.sortedBy { it.order }.map { line ->
                SpatialRow(
                    elements = line.elements,
                    text = line.text,
                    centerY = line.bounds?.centerY ?: line.order.toFloat(),
                    sourceLineIndices = listOf(line.order)
                )
            }
        }

        val heights = elements.mapNotNull { it.bounds?.height?.takeIf { height -> height > 0 } }.sorted()
        val medianHeight = heights.getOrNull(heights.size / 2)?.toFloat() ?: 16f
        // A generous tolerance merges adjacent summary lines on narrow
        // tickets (for example SUBTOTAL with TOTAL AFECTO). Keep enough
        // tolerance for perspective/skew, but separate distinct printed rows.
        val tolerance = max(6f, medianHeight * 0.5f)
        val rows = mutableListOf<MutableSpatialRow>()
        elements.sortedWith(compareBy({ it.bounds?.centerY ?: it.order.toFloat() }, { it.bounds?.left ?: it.order }))
            .forEach { element ->
                val y = element.bounds?.centerY ?: element.order.toFloat()
                val row = rows.minByOrNull { abs(it.centerY - y) }
                    ?.takeIf { abs(it.centerY - y) <= tolerance }
                if (row == null) {
                    rows += MutableSpatialRow(y, mutableListOf(element))
                } else {
                    row.elements += element
                    row.centerY = row.elements.mapNotNull { it.bounds?.centerY }.averageOrNull() ?: y
                }
            }
        return rows.sortedBy { it.centerY }.map { row ->
            // OCR engines can repeat an element while splitting/merging lines.
            // Remove only exact spatial duplicates; equal values in different
            // columns remain distinct because their bounds differ.
            val ordered = row.elements
                .sortedWith(compareBy({ it.bounds?.left ?: Int.MAX_VALUE }, { it.order }))
                .distinctBy { it.text.trim() to it.bounds }
            SpatialRow(
                elements = ordered,
                text = ordered.joinToString(" ") { it.text },
                centerY = row.centerY,
                sourceLineIndices = ordered.map { it.lineIndex }.distinct()
            )
        }
    }

    private data class MutableSpatialRow(
        var centerY: Float,
        val elements: MutableList<OcrElement>
    )
}

private fun Iterable<Float>.averageOrNull(): Float? {
    val values = toList()
    return values.takeIf { it.isNotEmpty() }?.average()?.toFloat()
}

class ChileanReceiptParser {
    fun parse(document: OcrDocument): StructuredReceipt {
        val lines = document.lines.sortedBy { it.order }
        val lineTexts = lines.map { it.text.ifBlank { it.elements.joinToString(" ") { element -> element.text } } }
        val fullText = document.rawText().ifBlank { lineTexts.joinToString("\n") }
        val normalizedLines = lineTexts.map(ChileanReceiptUtils::fold)

        val rut = ChileanReceiptUtils.findRut(fullText)
        val merchant = findMerchant(lineTexts, normalizedLines, rut)
        val receiptNumber = findReceiptNumber(lineTexts, normalizedLines)
        val date = ChileanReceiptUtils.normalizeDate(fullText)
        val time = lineTexts.asSequence()
            .filter { line ->
                val folded = ChileanReceiptUtils.fold(line)
                folded.contains("HORA") || folded.contains("TIME")
            }
            .mapNotNull(ChileanReceiptUtils::normalizeTime)
            .firstOrNull()
        val branch = findLabelValue(lineTexts, normalizedLines, listOf("SUCURSAL"))
        val address = findAddress(lineTexts, normalizedLines)
        val register = findRegister(lineTexts, normalizedLines)
        val taxLine = findLastLine(lineTexts, normalizedLines) {
            compactReceiptLabel(it).contains("IVA") && !it.contains("NETO")
        }
        val totalLine = findLastLine(lineTexts, normalizedLines) {
            it.contains("TOTAL") && !it.contains("NETO") && !compactReceiptLabel(it).contains("IVA") &&
                !it.contains("EXENTO") && !it.contains("SUBTOTAL") && !it.contains("AHORRO") &&
                !it.contains("ACUMULADO") && !it.contains("NUMERO") && !it.contains("ARTIC") &&
                !it.contains("AFECTO")
        }
        val subtotalLine = findLastLine(lineTexts, normalizedLines) { compactReceiptLabel(it).contains("SUBTOTAL") }
        val netLine = findLastLine(lineTexts, normalizedLines) {
            (it.contains("NETO") || it.contains("AFECTO")) && !it.contains("EXENTO")
        }
        val exemptLine = findLastLine(lineTexts, normalizedLines) { it.contains("EXENTO") }

        val spatialRows = OcrSpatialGrouping.group(document)
        // ML Kit can split a table header into separate lines such as
        // ARTICULO / PRODUCTOS / CANT PRECIO / VALOR. Keep the first anchor
        // and infer columns from the following OCR elements.
        val headerIndex = normalizedLines.indexOfFirst {
            it.contains("ARTICULO") ||
                (it.contains("CANT") && (it.contains("PRECIO") || it.contains("VALOR"))) ||
                (it.contains("ITEM") && (it.contains("VALOR") || it.contains("SUBTOTAL")))
        }
        val columnHints = headerIndex.takeIf { it >= 0 }?.let { inferColumns(lines, it) } ?: ColumnHints()
        val productResult = parseProducts(document, lines, headerIndex, columnHints).let { result ->
            result.copy(items = reconcileProductArithmetic(result.items))
        }
        val explicitTotal = amountForLabel(
            lineTexts,
            normalizedLines,
            spatialRows,
            totalLine,
            predicate = { it.contains("TOTAL") && !it.contains("NETO") && !compactReceiptLabel(it).contains("IVA") &&
                !it.contains("EXENTO") && !it.contains("SUBTOTAL") && !it.contains("AHORRO") &&
                !it.contains("ACUMULADO") && !it.contains("NUMERO") && !it.contains("ARTIC") &&
                !it.contains("AFECTO") }
        )
        var parsedSubtotal = amountForLabel(
            lineTexts,
            normalizedLines,
            spatialRows,
            subtotalLine,
            predicate = { compactReceiptLabel(it).contains("SUBTOTAL") }
        )
        val explicitNet = amountForLabel(
            lineTexts,
            normalizedLines,
            spatialRows,
            netLine,
            predicate = { (it.contains("NETO") || it.contains("AFECTO")) && !it.contains("EXENTO") }
        )
        var parsedExempt = amountForLabel(
            lineTexts,
            normalizedLines,
            spatialRows,
            exemptLine,
            predicate = { it.contains("EXENTO") }
        )
        val explicitTax = amountForLabel(
            lineTexts,
            normalizedLines,
            spatialRows,
            taxLine,
            predicate = { compactReceiptLabel(it).contains("IVA") && !it.contains("NETO") }
        )
        val taxRate = extractTaxRate(fullText)
        var parsedNet = explicitNet
        var tax = explicitTax
        var parsedTotal = explicitTotal
        // These are arithmetic deductions from observed receipt values. They
        // cover OCR layouts where a label and its right-aligned amount arrive
        // as separate lines, without inventing a merchant-specific value.
        if (parsedNet != null && taxRate != null) {
            val derivedTax = (parsedNet * taxRate / 100.0).roundToLong()
            // Prefer an explicit OCR amount when it is coherent; otherwise
            // use the observed NETO + IVA rate to reject a partial/malformed
            // token such as the tail of a grouped CLP amount.
            if (tax == null || abs(tax - derivedTax) > 1) tax = derivedTax
        }
        if (parsedTotal == null && parsedNet != null && tax != null) {
            parsedTotal = parsedNet + tax
        }
        if (parsedTotal == null && productResult.items.any { it.lineTotalMinor != null }) {
            parsedTotal = productResult.items.mapNotNull { it.lineTotalMinor }.sum() -
                productResult.discounts.sumOf { abs(it.amountMinor) }
        }
        if (parsedNet == null && parsedTotal != null && tax != null) {
            parsedNet = parsedTotal - tax
        }
        val productSum = productResult.items
            .mapNotNull { it.lineTotalMinor }
            .takeIf { it.isNotEmpty() }
            ?.sum()
        val discountSum = productResult.discounts.sumOf { abs(it.amountMinor) }
        val productAfterDiscount = productSum?.minus(discountSum)
        val netPlusTax = parsedNet?.let { netValue -> tax?.let { taxValue -> netValue + taxValue } }
        if (productSum != null) {
            // Prefer a subtotal supported by the observed product column when
            // OCR attached a nearby tax/payment value to the SUBTOTAL label.
            // Keep a coherent explicit post-discount subtotal when present.
            val subtotalIsCoherent = parsedSubtotal?.let { subtotal ->
                subtotal == productSum || subtotal == productAfterDiscount
            } == true
            if (parsedSubtotal == null || !subtotalIsCoherent) parsedSubtotal = productSum

            if (netPlusTax != null && productAfterDiscount != null && abs(netPlusTax - productAfterDiscount) <= 1) {
                // When net + IVA already accounts for the product total, an
                // OCR value attached to EXENTO is a false spatial association.
                parsedExempt = 0L
                parsedTotal = productAfterDiscount
            } else if (parsedTotal == null && productAfterDiscount != null) {
                parsedTotal = productAfterDiscount
            }
        }
        // If OCR attached a loyalty/payment amount to a TOTAL label, use the
        // independent tax reconciliation when all of its operands were
        // observed. This is layout-independent and does not add values.
        val observedTaxTotal = parsedNet?.let { netValue ->
            tax?.let { taxValue -> netValue + taxValue + (parsedExempt ?: 0L) }
        }
        if (observedTaxTotal != null && (parsedTotal == null || abs(parsedTotal - observedTaxTotal) > 1)) {
            parsedTotal = observedTaxTotal
        }
        val paymentMethod = findPaymentMethod(normalizedLines)

        return StructuredReceipt(
            merchantName = merchant,
            legalName = merchant?.takeIf { ChileanReceiptUtils.fold(it).contains("SPA") || ChileanReceiptUtils.fold(it).contains("LTDA") },
            rut = rut,
            receiptNumber = receiptNumber,
            date = date,
            time = time,
            branch = branch,
            address = address,
            register = register,
            items = productResult.items,
            discounts = productResult.discounts,
            subtotalMinor = parsedSubtotal,
            netMinor = parsedNet,
            exemptMinor = parsedExempt,
            taxRatePercent = taxRate,
            taxMinor = tax,
            totalMinor = parsedTotal,
            currency = "CLP",
            paymentMethod = paymentMethod
        )
    }

    private fun findMerchant(lines: List<String>, normalized: List<String>, rut: String?): String? {
        val rutLine = rut?.let { rutValue -> normalized.indexOfFirst { it.contains(ChileanReceiptUtils.fold(rutValue).replace(".", "")) } }
            ?: normalized.indexOfFirst { it.contains("RUT") }
        return lines.take(if (rutLine >= 0) rutLine else minOf(lines.size, 5))
            .map(String::trim)
            .firstOrNull { candidate ->
                val folded = ChileanReceiptUtils.fold(candidate)
                candidate.length >= 3 && folded.any(Char::isLetter) &&
                    !folded.contains("FECHA") && !folded.contains("BOLETA") &&
                    !folded.contains("CASA MATRIZ") && !folded.contains("GIRO") &&
                    !folded.contains("SANTIAGO") &&
                    !folded.contains("ELECTRON") &&
                    !folded.matches(Regex("^(SUC|SUCURSAL|AV\\.?|DIRECCION|RUT|CAJA|CAMINO|COMUNA|CIUDAD)\\b.*"))
            }
            ?.trim('*', ':', ' ')
            ?.takeIf { it.isNotBlank() }
    }

    private fun findReceiptNumber(lines: List<String>, normalized: List<String>): String? {
        val labels = listOf("BOLETA", "FOLIO", "DOCUMENTO")
        val index = normalized.indexOfFirst { line -> labels.any { label -> line.contains(label) } }
        if (index < 0) return null
        val line = lines[index]
        val afterLabel = Regex("(?i)(?:N[°ºO]?|NO|NUMERO|NÚMERO)\\s*[:#]?\\s*([A-Z0-9-]{4,})").find(line)
            ?.groupValues?.getOrNull(1)
        if (!afterLabel.isNullOrBlank()) return afterLabel
        val candidates = Regex("\\b[A-Z0-9-]{6,}\\b").findAll(line)
            .map { it.value }
            .filter { !it.all(Char::isLetter) }
            .toList()
        return candidates.lastOrNull()
    }

    private fun findAddress(lines: List<String>, normalized: List<String>): String? {
        val index = normalized.indexOfFirst {
            it.contains("CASA MATRIZ") || it.contains("DIRECCION") || it.contains("DIRECCIÓN") ||
                it.startsWith("CAMINO") || it.startsWith("AVENIDA") || it.startsWith("AV ")
        }
        if (index < 0) return null
        val first = afterColon(lines[index]).takeIf { it.isNotBlank() } ?: lines[index].trim()
        val continuation = lines.getOrNull(index + 1)?.trim().takeIf { next ->
            !next.isNullOrBlank() && !ChileanReceiptUtils.fold(next).matches(Regex(".*(GIRO|BOLETA|FECHA|SUCURSAL|ARTICULO|TOTAL).*"))
        }
        val continuationWithoutDuplicate = continuation?.takeUnless {
            ChileanReceiptUtils.fold(first).contains(ChileanReceiptUtils.fold(it))
        }
        return listOfNotNull(first, continuationWithoutDuplicate).joinToString(" ").trim().trimEnd(',', ';')
    }

    private fun findRegister(lines: List<String>, normalized: List<String>): String? {
        val line = findLastLine(lines, normalized) { it.contains("CAJA") || it.contains("REGISTRO") } ?: return null
        return Regex("(?i)(?:CAJA|REGISTRO)\\s*[:#]?\\s*([A-Z0-9-]+)").find(line)?.groupValues?.getOrNull(1)
            ?: afterColon(line).takeIf { it.isNotBlank() }
    }

    private fun findLabelValue(lines: List<String>, normalized: List<String>, labels: List<String>): String? {
        val index = normalized.indexOfFirst { line -> labels.any { line.contains(it) } }
        if (index < 0) return null
        return afterColon(lines[index]).takeIf { it.isNotBlank() }
    }

    private fun findLastLine(lines: List<String>, normalized: List<String>, predicate: (String) -> Boolean): String? =
        lines.indices.lastOrNull { predicate(normalized[it]) }?.let(lines::get)

    private fun findPaymentMethod(normalized: List<String>): String? {
        val values = listOf(
            "TRANSBANK", "TARJETA LIDER BCI", "TARJETA LIDERBCI", "TARJETA DE CREDITO",
            "TARJETA", "DEBITO", "CREDITO", "EFECTIVO", "TRANSFERENCIA"
        )
        return normalized.firstOrNull { line -> values.any { line.contains(it) } }
            ?.let { line -> values.firstOrNull { line.contains(it) }?.replace("DE CREDITO", " DE CRÉDITO") }
    }

    private data class ColumnHints(
        val quantityX: Float? = null,
        val priceX: Float? = null,
        val valueX: Float? = null
    )

    private fun inferColumns(lines: List<OcrLine>, headerIndex: Int): ColumnHints {
        val anchorY = lines[headerIndex].bounds?.centerY
        // ML Kit may return the header labels in separate blocks and in a
        // different reading order from the product rows. Restrict candidates
        // to the physical header band so summary labels such as TOTAL NETO do
        // not become the value column.
        val headerElements = lines.flatMap { it.elements }.filter { element ->
            val y = element.bounds?.centerY
            anchorY == null || y == null || kotlin.math.abs(y - anchorY) <= 70f
        }
        fun xFor(vararg labels: String): Float? = headerElements.firstOrNull { element ->
            val text = ChileanReceiptUtils.fold(element.text)
            labels.any { text.contains(it) }
        }?.bounds?.centerX
        return ColumnHints(
            quantityX = xFor("CANT", "UNID"),
            priceX = xFor("PRECIO", "UNIT"),
            valueX = xFor("VALOR") ?: xFor("TOTAL", "IMPORTE")
        )
    }

    private data class ProductParseResult(
        val items: List<ReceiptLineItem>,
        val discounts: List<ReceiptDiscount>
    )

    private fun parseProducts(
        document: OcrDocument,
        lines: List<OcrLine>,
        headerIndex: Int,
        columns: ColumnHints
    ): ProductParseResult {
        val spatialRows = OcrSpatialGrouping.group(document)
        val headerY = if (headerIndex >= 0) {
            lines[headerIndex].bounds?.bottom?.toFloat() ?: lines[headerIndex].order.toFloat()
        } else {
            // Cropped supermarket receipts sometimes omit the table header.
            // Start after their date/time block and apply the same row parser.
            spatialRows.firstOrNull { row ->
                val normalized = ChileanReceiptUtils.fold(row.text)
                normalized.contains("HORA") || normalized.contains("FECHA")
            }?.centerY ?: -1f
        }
        val rows = spatialRows.filter { row -> row.centerY > headerY + 1f }
        val items = mutableListOf<ReceiptLineItem>()
        val discounts = mutableListOf<ReceiptDiscount>()
        if (headerIndex < 0) {
            return parseProductsWithoutHeader(rows)
        }
        var pendingDescription: String? = null
        var pendingQuantity: Int? = null
        var pendingUnitPrice: Long? = null
        for (row in rows) {
            val normalized = ChileanReceiptUtils.fold(row.text)
            if (normalized.isBlank() || normalized.all { it == '-' || it == '_' || it == ' ' }) continue
            if (isProductHeader(normalized)) continue
            if (isProductSectionEnd(normalized)) break

            val parsed = parseRowNumbers(row, columns)
            val description = cleanProductDescription(rowDescription(row, parsed.assignedElementOrders))
            val isDiscount = normalized.contains("DCTO") || normalized.contains("DESCUENTO") ||
                normalized.contains("DESC ") || parsed.amounts.any { it.raw.contains("-") }

            if (isDiscount && parsed.amounts.isNotEmpty()) {
                val amount = parsed.amounts.maxByOrNull { it.value }?.value ?: 0L
                if (amount > 0) {
                    discounts += ReceiptDiscount(
                        description = listOfNotNull(pendingDescription, description).joinToString(" ").trim().ifBlank { row.text },
                        amountMinor = amount,
                        sourceLineIndex = row.sourceLineIndices.firstOrNull()
                    )
                }
                pendingDescription = null
                pendingQuantity = null
                pendingUnitPrice = null
                continue
            }

            // Supermarket tickets often print quantity x unit price on a
            // separate line, e.g. 4X6.190, followed by the description and
            // value on another line. Hold those operands until the value row.
            if (
                parsed.quantity != null && parsed.unitPrice != null && parsed.lineTotal == null &&
                description.isBlank()
            ) {
                pendingQuantity = parsed.quantity
                pendingUnitPrice = parsed.unitPrice
                continue
            }

            val hasProductAmount = parsed.unitPrice != null || parsed.lineTotal != null
            if (!hasProductAmount) {
                if (description.isNotBlank()) pendingDescription = listOfNotNull(pendingDescription, description).joinToString(" ")
                continue
            }
            // A common ticket layout places the unit price on a continuation
            // line below the product description. Attach it to the preceding
            // row instead of creating a synthetic second product.
            if (
                parsed.unitPrice != null && parsed.lineTotal == null && parsed.quantity == null &&
                items.isNotEmpty() && (description.length <= 24 || ChileanReceiptUtils.fold(description).contains("C/U"))
            ) {
                val last = items.last()
                items[items.lastIndex] = last.copy(unitPriceMinor = parsed.unitPrice)
                continue
            }
            val fullDescription = listOfNotNull(pendingDescription, description).joinToString(" ").trim()
            if (fullDescription.isBlank()) continue
            items += ReceiptLineItem(
                description = fullDescription,
                quantity = parsed.quantity ?: pendingQuantity,
                unitPriceMinor = parsed.unitPrice ?: pendingUnitPrice,
                lineTotalMinor = parsed.lineTotal,
                sourceLineIndices = row.sourceLineIndices
            )
            pendingDescription = null
            pendingQuantity = null
            pendingUnitPrice = null
        }
        if (discounts.isEmpty()) discounts += parseStandaloneDiscounts(lines)
        return ProductParseResult(items, discounts.distinctBy { it.description to it.amountMinor })
    }

    private data class NoHeaderRow(
        val description: String,
        val quantity: Int?,
        val unitPrice: Long?,
        val amounts: List<Long>,
        val hasCode: Boolean
    )

    /**
     * Supermarket tickets commonly print the product value on the code row,
     * while the quantity/unit-price and description arrive on the next OCR
     * row. This small state machine keeps those observations together instead
     * of pairing arbitrary numbers by the reading-order string.
     */
    private fun parseProductsWithoutHeader(rows: List<SpatialRow>): ProductParseResult {
        val items = mutableListOf<ReceiptLineItem>()
        val discounts = mutableListOf<ReceiptDiscount>()
        val sectionEndIndex = rows.indexOfFirst { row ->
            val normalized = ChileanReceiptUtils.fold(row.text)
            !isProductHeader(normalized) && isProductSectionEnd(normalized)
        }
        val productRows = if (sectionEndIndex >= 0) rows.take(sectionEndIndex) else rows
        val trailingRows = if (sectionEndIndex >= 0) rows.drop(sectionEndIndex + 1) else emptyList()
        var pendingDescription = ""
        var pendingQuantity: Int? = null
        var pendingUnitPrice: Long? = null
        var pendingLineTotal: Long? = null
        var carriedLineTotal: Long? = null

        fun clearPending() {
            pendingDescription = ""
            pendingQuantity = null
            pendingUnitPrice = null
            pendingLineTotal = null
        }

        fun emitPending() {
            if (pendingDescription.isBlank() && pendingLineTotal == null && pendingUnitPrice == null) return
            val lineTotal = pendingLineTotal
            val quantity = pendingQuantity
            val unitPrice = pendingUnitPrice?.let { unit ->
                if (lineTotal != null && quantity != null && quantity > 0 && unit * quantity != lineTotal && lineTotal % quantity == 0L) {
                    // Recover a clipped grouping such as OCR "1.00" for the
                    // observed 1.000 unit price. The value is derived only
                    // from the row's quantity and observed line total.
                    lineTotal / quantity
                } else unit
            }
            if (pendingDescription.isNotBlank() || lineTotal != null || unitPrice != null) {
                items += ReceiptLineItem(
                    description = pendingDescription.ifBlank { "Producto detectado" },
                    quantity = quantity,
                    unitPriceMinor = unitPrice,
                    lineTotalMinor = lineTotal,
                    sourceLineIndices = emptyList()
                )
            }
            clearPending()
        }

        for (row in productRows) {
            val normalized = ChileanReceiptUtils.fold(row.text)
            if (normalized.isBlank() || normalized.all { it == '-' || it == '_' || it == ' ' }) continue
            if (isProductHeader(normalized)) continue
            if (isProductSectionEnd(normalized)) break

            val parsed = parseNoHeaderRow(row)
            if (parsed.description.isBlank() && parsed.quantity == null && parsed.unitPrice == null && parsed.amounts.isEmpty()) {
                continue
            }

            val isDiscount = normalized.contains("DCTO") || normalized.contains("DESCUENTO") ||
                normalized.contains("DESC ") || row.elements.any { it.text.contains("-") }
            if (isDiscount && parsed.amounts.isNotEmpty()) {
                val amount = parsed.amounts.maxOrNull() ?: 0L
                if (amount > 0) {
                    discounts += ReceiptDiscount(
                        description = parsed.description.ifBlank { row.text },
                        amountMinor = amount,
                        sourceLineIndex = row.sourceLineIndices.firstOrNull()
                    )
                }
                clearPending()
                carriedLineTotal = null
                continue
            }

            val previousLineTotal = pendingLineTotal ?: carriedLineTotal
            val currentDescription = parsed.description
            if (
                currentDescription.isNotBlank() && pendingDescription.isNotBlank() &&
                pendingUnitPrice != null && pendingLineTotal == null &&
                parsed.quantity == null && parsed.unitPrice == null && parsed.amounts.isEmpty()
            ) {
                // A new description after a c/u line closes the preceding
                // one when the ticket does not print quantity in that row.
                pendingLineTotal = pendingUnitPrice
                emitPending()
            }
            val combinedDescription = listOf(pendingDescription, currentDescription)
                .filter { it.isNotBlank() }
                .joinToString(" ")

            if (parsed.quantity != null || parsed.unitPrice != null) {
                // A value observed on the preceding code/value row belongs to
                // this quantity/description row. Any additional amount on the
                // current row is retained for the next product (perspective
                // can move the right-aligned value one OCR row upward).
                pendingDescription = combinedDescription
                pendingQuantity = parsed.quantity ?: pendingQuantity
                pendingUnitPrice = parsed.unitPrice ?: pendingUnitPrice
                pendingLineTotal = previousLineTotal
                carriedLineTotal = parsed.amounts.lastOrNull()?.takeIf { it != previousLineTotal }
                if (pendingLineTotal != null) emitPending()
                continue
            }

            if (parsed.amounts.isNotEmpty()) {
                if (currentDescription.isBlank() && parsed.hasCode) {
                    // The next row normally carries x/unit and the product
                    // name. Do not create a fake code item.
                    carriedLineTotal = parsed.amounts.last()
                    continue
                }
                if (currentDescription.isBlank()) continue
                pendingDescription = combinedDescription
                pendingLineTotal = parsed.amounts.last()
                if (pendingUnitPrice != null || pendingQuantity != null) emitPending()
                continue
            }

            if (currentDescription.isNotBlank()) {
                pendingDescription = combinedDescription
                if (carriedLineTotal != null) {
                    pendingLineTotal = carriedLineTotal
                    carriedLineTotal = null
                    emitPending()
                }
            }
        }
        if (carriedLineTotal != null && pendingLineTotal == null) pendingLineTotal = carriedLineTotal
        emitPending()

        // Some pharmacy layouts expose the value column after the summary in
        // ML Kit reading order. Match only exact observed amounts to pending
        // unit prices; never use the receipt total or tax as a substitute.
        val trailingAmounts = trailingRows.flatMap { parseNoHeaderRow(it).amounts }.toMutableList()
        items.indices.forEach { index ->
            val item = items[index]
            if (item.lineTotalMinor == null && item.unitPriceMinor != null) {
                val match = trailingAmounts.indexOf(item.unitPriceMinor)
                if (match >= 0) {
                    items[index] = item.copy(lineTotalMinor = trailingAmounts.removeAt(match))
                }
            }
        }
        return ProductParseResult(items, discounts.distinctBy { it.description to it.amountMinor })
    }

    /**
     * OCR column inference can assign the quantity token to both CANT and
     * PRECIO when a printed header is split across lines. If the observed
     * quantity and line value reconcile exactly, recover the unit price from
     * those two observations instead of retaining a demonstrably incorrect
     * small token such as `1`. This is arithmetic reconstruction, not a
     * merchant-specific fallback.
     */
    private fun reconcileProductArithmetic(items: List<ReceiptLineItem>): List<ReceiptLineItem> =
        items.map { item ->
            val quantity = item.quantity
            val lineTotal = item.lineTotalMinor
            val correctedUnitPrice = if (
                quantity != null && quantity > 0 &&
                lineTotal != null && lineTotal % quantity == 0L &&
                item.unitPriceMinor != null &&
                item.unitPriceMinor * quantity != lineTotal &&
                item.unitPriceMinor <= 1_000L
            ) {
                lineTotal / quantity
            } else {
                item.unitPriceMinor
            }
            val description = item.description.removeTrailingObservedAmount(lineTotal)
            item.copy(
                description = description,
                unitPriceMinor = correctedUnitPrice
            )
        }

    private fun String.removeTrailingObservedAmount(expected: Long?): String {
        if (expected == null) return this
        val match = Regex("\\s+\\$?\\s*[0-9][0-9.,]*\\s*$").find(this) ?: return this
        val raw = match.value.trim()
        val observed = ChileanReceiptUtils.parseClpAmount(raw) ?: return this
        if (abs(observed) != abs(expected)) return this
        if (!raw.contains('$') && !raw.contains('.') && !raw.contains(',')) return this
        return removeRange(match.range).trim()
    }

    private fun parseNoHeaderRow(row: SpatialRow): NoHeaderRow {
        val elements = row.elements
        val compound = elements.firstNotNullOfOrNull { element ->
            Regex("(?i)(?<!\\d)(\\d{1,3})\\s*[x×]\\s*\\$?([0-9][0-9.,]*)").find(element.text)
        }
        val compoundQuantity = compound?.groupValues?.getOrNull(1)?.toIntOrNull()
        val compoundUnit = compound?.groupValues?.getOrNull(2)?.let(ChileanReceiptUtils::parseClpAmount)?.let(::abs)
        val usedOrders = compound?.let {
            elements.firstOrNull { element -> it.value in element.text }?.order
        }?.let(::setOf).orEmpty()

        val numeric = elements.flatMap { element ->
            if (element.order in usedOrders) return@flatMap emptyList()
            ChileanReceiptUtils.moneyTokens(element.text).mapNotNull { raw ->
                val value = ChileanReceiptUtils.parseClpAmount(raw)?.let(::abs) ?: return@mapNotNull null
                if (value <= 0 || raw.filter(Char::isDigit).length > 10) return@mapNotNull null
                val index = elements.indexOf(element)
                val adjacentUnit = listOfNotNull(
                    element.text,
                    elements.getOrNull(index - 1)?.text,
                    elements.getOrNull(index + 1)?.text
                ).any { text -> Regex("(?i)\\b(GR|KG|ML|MG|LT|L)\\b").containsMatchIn(text) }
                if (value in 1..999 && adjacentUnit && !raw.contains('$')) return@mapNotNull null
                NumericToken(element, raw, value, element.order)
            }
        }
        val amountValues = numeric.map { it.value }
        val amountOrders = numeric.map { it.order }.toSet()
        val hasUnitMarker = ChileanReceiptUtils.fold(row.text).contains("C/U") ||
            ChileanReceiptUtils.fold(row.text).contains("C U")
        val rowUnitPrice = compoundUnit ?: amountValues.lastOrNull().takeIf { hasUnitMarker }
        val rowAmounts = if (hasUnitMarker && amountValues.isNotEmpty()) amountValues.dropLast(1) else amountValues
        val description = elements
            .filter { it.order !in usedOrders && it.order !in amountOrders }
            .joinToString(" ") { it.text }
            .let(::cleanProductDescription)
        val hasCode = ChileanReceiptUtils.fold(row.text).contains("CODIGO") ||
            elements.any { it.text.filter(Char::isDigit).length >= 10 }
        return NoHeaderRow(
            description = description,
            quantity = compoundQuantity,
            unitPrice = rowUnitPrice,
            amounts = rowAmounts,
            hasCode = hasCode
        )
    }

    private data class NumericToken(
        val element: OcrElement?,
        val raw: String,
        val value: Long,
        val order: Int
    )

    private data class ParsedRowNumbers(
        val quantity: Int?,
        val unitPrice: Long?,
        val lineTotal: Long?,
        val amounts: List<NumericToken>,
        val assignedElementOrders: Set<Int>
    )

    private fun parseRowNumbers(row: SpatialRow, columns: ColumnHints): ParsedRowNumbers {
        val sourceTokens = if (row.elements.isEmpty()) {
            row.text.split(Regex("\\s+")).mapIndexed { index, raw -> index to raw }
        } else {
            row.elements.flatMap { element ->
                val compound = Regex("(?i)(?<!\\d)(\\d{1,3})\\s*[x×]\\s*\\$?([0-9][0-9.,]*)").find(element.text)
                when {
                    compound != null -> listOf(
                        element.order to compound.groupValues[1],
                        (element.order + 1_000_000) to compound.groupValues[2]
                    )
                    else -> {
                        val moneyTokens = ChileanReceiptUtils.moneyTokens(element.text)
                        if (moneyTokens.isEmpty()) listOf(element.order to element.text)
                        else moneyTokens.map { token -> element.order to token }
                    }
                }
            }
        }
        val tokens = sourceTokens.mapNotNull { (order, sourceText) ->
            val raw = sourceText.trim()
            val digits = raw.filter(Char::isDigit)
            if (digits.isBlank() || raw.any(Char::isLetter) || raw.contains("%")) return@mapNotNull null
            if (digits.length > 10) return@mapNotNull null
            ChileanReceiptUtils.parseClpAmount(raw)?.let { value ->
                val element = row.elements.firstOrNull { it.order == order }
                NumericToken(element, raw, abs(value), order)
            }
        }
        if (tokens.isEmpty()) return ParsedRowNumbers(null, null, null, emptyList(), emptySet())

        fun closest(x: Float?): NumericToken? = x?.let { expected ->
            tokens.minByOrNull { token -> abs((token.element?.bounds?.centerX ?: expected) - expected) }
                ?.takeIf { token -> abs((token.element?.bounds?.centerX ?: expected) - expected) <= 90f }
        }
        val quantityToken = closest(columns.quantityX)?.takeIf { it.value in 1..1_000 }
        val priceToken = closest(columns.priceX)?.takeIf { it.value > 0 }
        val valueToken = closest(columns.valueX)?.takeIf { it.value > 0 }
        if (columns.quantityX != null || columns.priceX != null || columns.valueX != null) {
            val assigned = listOfNotNull(quantityToken, priceToken, valueToken).map { it.order }.toSet()
            return ParsedRowNumbers(
                quantity = quantityToken?.value?.toInt(),
                unitPrice = priceToken?.value,
                lineTotal = valueToken?.value,
                amounts = tokens,
                assignedElementOrders = assigned
            )
        }

        val quantity = tokens.firstOrNull { it.value in 1..1_000 && it.raw.filter(Char::isDigit).length <= 3 }
        val remaining = tokens.filterNot { it.order == quantity?.order }
        val unit = remaining.getOrNull(0)
        val value = remaining.getOrNull(1)
        return ParsedRowNumbers(
            quantity = quantity?.value?.toInt(),
            unitPrice = unit?.value,
            lineTotal = value?.value,
            amounts = tokens,
            assignedElementOrders = (listOfNotNull(quantity, unit, value)).map { it.order }.toSet()
        )
    }

    private fun rowDescription(row: SpatialRow, assignedOrders: Set<Int>): String = if (row.elements.isEmpty()) {
        row.text.split(Regex("\\s+")).mapIndexedNotNull { index, token ->
            token.takeIf { index !in assignedOrders }
        }.joinToString(" ")
    } else row.elements
        .filter { it.order !in assignedOrders }
        .joinToString(" ") { it.text }
        .replace("***", "")
        .trim()

    private fun cleanProductDescription(value: String): String = value
        .replace(Regex("(?i)CODIGO\\s*:?\\s*\\d{6,}"), "")
        .replace(Regex("\\b\\d{10,}\\b"), "")
        .replace(Regex("(?i)\\(?\\s*C\\s*/?\\s*U\\s*\\)?"), "")
        .replace("$", "")
        .replace(Regex("\\s+"), " ")
        .trim(' ', ':', '-', '.')

    private fun isProductSectionEnd(normalized: String): Boolean =
        normalized.contains("SUBTOTAL") || normalized.contains("TOTAL NETO") ||
            normalized.contains("TOTAL IVA") || normalized.contains("TOTAL EXENTO") ||
            normalized.matches(Regex(".*\\bTOTAL\\b.*"))

    private fun isProductHeader(normalized: String): Boolean =
        normalized == "ARTICULO" || normalized.startsWith("ARTICUL0") || normalized == "PRODUCTOS" ||
            normalized.contains("CANT") || normalized.contains("PRECIO") ||
            normalized == "VALOR" || normalized == "ITEM"

    private fun parseStandaloneDiscounts(lines: List<OcrLine>): List<ReceiptDiscount> = lines.mapNotNull { line ->
        val normalized = ChileanReceiptUtils.fold(line.text)
        if (!normalized.contains("DCTO") && !normalized.contains("DESCUENTO") && !normalized.contains("DESC ")) return@mapNotNull null
        val amount = ChileanReceiptUtils.moneyTokens(line.text).lastOrNull()?.let(ChileanReceiptUtils::parseClpAmount) ?: return@mapNotNull null
        ReceiptDiscount(line.text.replace("***", "").trim(), abs(amount), line.order)
    }.distinctBy { it.description to it.amountMinor }

    private fun amountForLabel(
        lines: List<String>,
        normalized: List<String>,
        rows: List<SpatialRow>,
        matchingLine: String?,
        predicate: (String) -> Boolean
    ): Long? {
        matchingLine?.let(::lastAmount)?.let { return it }
        return rows.asReversed()
            .firstOrNull { predicate(ChileanReceiptUtils.fold(it.text)) }
            ?.let { lastAmount(it.text) }
    }

    private fun extractTaxRate(text: String): Int? = Regex(
        "I\\.?V\\.?A\\.?\\s*\\(?\\s*(\\d{1,3})\\s*%?",
        RegexOption.IGNORE_CASE
    ).findAll(text).mapNotNull { match ->
        val raw = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
        // A frequent OCR rendering of IVA (19%) is IVA(193), where the
        // percent sign became a final 3. Treat only plausible Chilean rates.
        when {
            raw in 10..30 -> raw
            raw in 100..300 && raw % 10 == 3 && raw / 10 in 10..30 -> raw / 10
            else -> null
        }
    }.lastOrNull()

    private fun lastAmount(line: String): Long? {
        val withoutTaxRate = line.replace(
            Regex("I\\.?V\\.?A\\.?\\s*\\(?\\s*\\d{1,3}\\s*%?\\s*\\)?", RegexOption.IGNORE_CASE),
            "IVA"
        )
        return ChileanReceiptUtils.moneyTokens(withoutTaxRate)
            .lastOrNull()
            ?.let(ChileanReceiptUtils::parseClpAmount)
            ?.let(::abs)
    }

    private fun compactReceiptLabel(value: String): String =
        value.replace(Regex("[.\\-\\s]"), "")

    private fun afterColon(value: String): String = value.substringAfter(':', "").trim()
}
