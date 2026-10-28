package com.villegol.appgastos.ai.receipt

import org.json.JSONArray
import org.json.JSONObject

/** A platform-neutral rectangle retained from an OCR engine. */
data class OcrBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

data class OcrElement(
    val text: String,
    val bounds: OcrBounds?,
    val confidence: Float?,
    val blockIndex: Int,
    val lineIndex: Int,
    val elementIndex: Int,
    val order: Int
)

data class OcrLine(
    val text: String,
    val bounds: OcrBounds?,
    val confidence: Float?,
    val blockIndex: Int,
    val lineIndex: Int,
    val elements: List<OcrElement>,
    val order: Int
)

data class OcrBlock(
    val text: String,
    val bounds: OcrBounds?,
    val confidence: Float?,
    val blockIndex: Int,
    val lines: List<OcrLine>,
    val order: Int
)

/**
 * Common OCR contract. Consumers must use blocks/lines/elements when available;
 * [text] is only the lossless reading-order convenience view.
 */
data class OcrDocument(
    val text: String,
    val blocks: List<OcrBlock>,
    val imageWidth: Int? = null,
    val imageHeight: Int? = null,
    val engine: String,
    val sourceConfidence: Float? = blocks
        .flatMap { block -> block.lines.flatMap { line -> listOfNotNull(line.confidence) + line.elements.mapNotNull { it.confidence } } }
        .averageOrNull()
) {
    val lines: List<OcrLine> get() = blocks.flatMap { it.lines }.sortedBy { it.order }
    val elements: List<OcrElement> get() = lines.flatMap { it.elements }.sortedBy { it.order }

    fun rawText(): String = lines.joinToString("\n") { line ->
        line.text.ifBlank { line.elements.sortedBy { it.bounds?.left ?: Int.MAX_VALUE }.joinToString(" ") { it.text } }
    }.ifBlank { text }

    fun toJson(): String = JSONObject().apply {
        put("engine", engine)
        put("text", rawText())
        put("imageWidth", imageWidth ?: JSONObject.NULL)
        put("imageHeight", imageHeight ?: JSONObject.NULL)
        put("sourceConfidence", sourceConfidence ?: JSONObject.NULL)
        put("blocks", JSONArray(blocks.map { it.toJson() }))
    }.toString(2)

    companion object {
        fun fromText(text: String, engine: String = "text-fixture"): OcrDocument {
            val lines = text.lines().filter { it.isNotBlank() }.mapIndexed { index, lineText ->
                OcrLine(
                    text = lineText,
                    bounds = null,
                    confidence = null,
                    blockIndex = 0,
                    lineIndex = index,
                    elements = emptyList(),
                    order = index
                )
            }
            return fromLines(lines, engine = engine, text = text)
        }

        fun fromLines(
            lines: List<OcrLine>,
            engine: String = "fixture",
            text: String = lines.sortedBy { it.order }.joinToString("\n") { it.text }
        ): OcrDocument {
            val block = OcrBlock(
                text = text,
                bounds = lines.mapNotNull { it.bounds }.let { bounds ->
                    if (bounds.isEmpty()) null else OcrBounds(
                        left = bounds.minOf { it.left },
                        top = bounds.minOf { it.top },
                        right = bounds.maxOf { it.right },
                        bottom = bounds.maxOf { it.bottom }
                    )
                },
                confidence = lines.mapNotNull { it.confidence }.averageOrNull(),
                blockIndex = 0,
                lines = lines.sortedBy { it.order },
                order = 0
            )
            return OcrDocument(text = text, blocks = listOf(block), engine = engine)
        }
    }
}

data class ReceiptLineItem(
    val description: String,
    val quantity: Int?,
    val unitPriceMinor: Long?,
    val lineTotalMinor: Long?,
    val sourceLineIndices: List<Int> = emptyList()
)

data class ReceiptDiscount(
    val description: String,
    /** Absolute CLP magnitude. A discount is always subtracted by validation. */
    val amountMinor: Long,
    val sourceLineIndex: Int? = null
)

data class ReceiptCategory(
    val category: String,
    val subcategory: String? = null,
    val confidence: Float,
    val source: String
)

/** The one structured contract shared by every receipt pipeline. */
data class StructuredReceipt(
    val merchantName: String? = null,
    val legalName: String? = null,
    val rut: String? = null,
    val receiptNumber: String? = null,
    val date: String? = null,
    val time: String? = null,
    val branch: String? = null,
    val address: String? = null,
    val register: String? = null,
    val items: List<ReceiptLineItem> = emptyList(),
    val discounts: List<ReceiptDiscount> = emptyList(),
    val subtotalMinor: Long? = null,
    val netMinor: Long? = null,
    val exemptMinor: Long? = null,
    val taxRatePercent: Int? = null,
    val taxMinor: Long? = null,
    val totalMinor: Long? = null,
    val currency: String = "CLP",
    val paymentMethod: String? = null,
    val category: ReceiptCategory? = null
) {
    fun withCategory(value: ReceiptCategory?): StructuredReceipt = copy(category = value)

    fun toJson(): String = JSONObject().apply {
        put("merchantName", merchantName ?: JSONObject.NULL)
        put("legalName", legalName ?: JSONObject.NULL)
        put("rut", rut ?: JSONObject.NULL)
        put("receiptNumber", receiptNumber ?: JSONObject.NULL)
        put("date", date ?: JSONObject.NULL)
        put("time", time ?: JSONObject.NULL)
        put("branch", branch ?: JSONObject.NULL)
        put("address", address ?: JSONObject.NULL)
        put("register", register ?: JSONObject.NULL)
        put("items", JSONArray(items.map { item ->
            JSONObject().apply {
                put("description", item.description)
                put("quantity", item.quantity ?: JSONObject.NULL)
                put("unitPriceMinor", item.unitPriceMinor ?: JSONObject.NULL)
                put("lineTotalMinor", item.lineTotalMinor ?: JSONObject.NULL)
                put("sourceLineIndices", JSONArray(item.sourceLineIndices))
            }
        }))
        put("discounts", JSONArray(discounts.map { discount ->
            JSONObject().apply {
                put("description", discount.description)
                put("amountMinor", discount.amountMinor)
                put("sourceLineIndex", discount.sourceLineIndex ?: JSONObject.NULL)
            }
        }))
        put("subtotalMinor", subtotalMinor ?: JSONObject.NULL)
        put("netMinor", netMinor ?: JSONObject.NULL)
        put("exemptMinor", exemptMinor ?: JSONObject.NULL)
        put("taxRatePercent", taxRatePercent ?: JSONObject.NULL)
        put("taxMinor", taxMinor ?: JSONObject.NULL)
        put("totalMinor", totalMinor ?: JSONObject.NULL)
        put("currency", currency)
        put("paymentMethod", paymentMethod ?: JSONObject.NULL)
        put("category", category?.let {
            JSONObject().apply {
                put("category", it.category)
                put("subcategory", it.subcategory ?: JSONObject.NULL)
                put("confidence", it.confidence)
                put("source", it.source)
            }
        } ?: JSONObject.NULL)
    }.toString(2)
}

enum class PipelineAvailability {
    AVAILABLE,
    DOWNLOADABLE,
    NOT_DOWNLOADED,
    DOWNLOADING,
    UNSUPPORTED,
    ERROR
}

data class PipelineCapability(
    val availability: PipelineAvailability,
    val detail: String? = null,
    val modelSize: String? = null,
    val supportsColdStart: Boolean = true,
    val supportsWarmInference: Boolean = false
)

enum class BenchmarkMode { COLD_END_TO_END, WARM_END_TO_END, SHARED_OCR_WARM }

data class PipelineTiming(
    val preprocessingMs: Long = 0,
    val modelLoadMs: Long = 0,
    val ocrMs: Long = 0,
    val inferenceMs: Long = 0,
    val parsingMs: Long = 0,
    val validationMs: Long = 0,
    val categorizationMs: Long = 0,
    val totalMs: Long = 0,
    val endToEndMs: Long = totalMs,
    val mode: BenchmarkMode = BenchmarkMode.COLD_END_TO_END,
    val coldStart: Boolean = true,
    val sharedPreprocessing: Boolean = false,
    val sharedOcr: Boolean = false
)

data class ReceiptPipelineInput(
    val imagePath: String,
    val preprocessedImagePath: String? = null,
    val sharedOcr: OcrDocument? = null,
    val sharedPreprocessingMs: Long = 0,
    val sharedOcrMs: Long = 0,
    val mode: BenchmarkMode = BenchmarkMode.COLD_END_TO_END,
    /** Non-null only when this input is a registered benchmark fixture. */
    val groundTruth: ReceiptGroundTruth? = null
)

data class ReceiptValidation(
    val checks: List<ValidationCheck>,
    val score: Float
) {
    val passed: Int get() = checks.count { it.status == ValidationStatus.PASS }
    val failed: Int get() = checks.count { it.status == ValidationStatus.FAIL }
    val unknown: Int get() = checks.count { it.status == ValidationStatus.UNKNOWN }

    fun toJson(): String = JSONObject().apply {
        put("score", score)
        put("passed", passed)
        put("failed", failed)
        put("unknown", unknown)
        put("checks", JSONArray(checks.map { check ->
            JSONObject().apply {
                put("name", check.name)
                put("status", check.status.name)
                put("observed", check.observed ?: JSONObject.NULL)
                put("expected", check.expected ?: JSONObject.NULL)
                put("message", check.message)
            }
        }))
    }.toString(2)
}

enum class ValidationStatus { PASS, FAIL, UNKNOWN }

data class ValidationCheck(
    val name: String,
    val status: ValidationStatus,
    val observed: String? = null,
    val expected: String? = null,
    val message: String
)

data class AccuracyMatch(
    val name: String,
    val matched: Boolean?,
    val expected: String?,
    val actual: String?,
    val message: String
)

data class ReceiptAccuracy(
    val matches: List<AccuracyMatch>,
    val score: Float
) {
    fun toJson(): String = JSONObject().apply {
        put("score", score)
        put("matches", JSONArray(matches.map { match ->
            JSONObject().apply {
                put("name", match.name)
                put("matched", match.matched ?: JSONObject.NULL)
                put("expected", match.expected ?: JSONObject.NULL)
                put("actual", match.actual ?: JSONObject.NULL)
                put("message", match.message)
            }
        }))
    }.toString(2)
}

data class ReceiptGroundTruth(
    val merchantName: String,
    val rut: String,
    val date: String,
    val totalMinor: Long,
    val netMinor: Long,
    val taxMinor: Long,
    val productCount: Int,
    val productAmounts: Map<String, Long>,
    val discountMinor: Long,
    val category: String
) {
    companion object {
        /** Entirely synthetic receipt example; not derived from a personal photo or used by parsing rules. */
        val SYNTHETIC_EXAMPLE = ReceiptGroundTruth(
            merchantName = "COMERCIO DEMOSTRACION SPA",
            rut = "11.111.111-1",
            date = "2026-08-10",
            totalMinor = 11_000L,
            netMinor = 9_244L,
            taxMinor = 1_756L,
            productCount = 2,
            productAmounts = mapOf(
                "PRODUCTO DEMO A" to 6_000L,
                "PRODUCTO DEMO B" to 7_000L
            ),
            discountMinor = 2_000L,
            category = "Salud"
        )
    }
}

data class ReceiptPipelineRun(
    val pipelineId: String,
    val displayName: String,
    val capability: PipelineCapability,
    val inputImagePath: String? = null,
    val preprocessedImagePath: String? = null,
    val receipt: StructuredReceipt? = null,
    val ocr: OcrDocument? = null,
    val rawModelOutput: String? = null,
    val validation: ReceiptValidation? = null,
    val accuracy: ReceiptAccuracy? = null,
    val confidence: Float? = null,
    val timing: PipelineTiming = PipelineTiming(),
    val error: String? = null,
    val notes: List<String> = emptyList()
) {
    val isSuccess: Boolean get() = receipt != null && error == null

    fun toJson(): String = JSONObject().apply {
        put("pipelineId", pipelineId)
        put("displayName", displayName)
        put("status", if (isSuccess) "SUCCESS" else "ERROR")
        put("capability", capability.availability.name)
        put("capabilityDetail", capability.detail ?: JSONObject.NULL)
        put("inputImagePath", inputImagePath ?: JSONObject.NULL)
        put("preprocessedImagePath", preprocessedImagePath ?: JSONObject.NULL)
        put("timing", JSONObject().apply {
            put("preprocessingMs", timing.preprocessingMs)
            put("modelLoadMs", timing.modelLoadMs)
            put("ocrMs", timing.ocrMs)
            put("inferenceMs", timing.inferenceMs)
            put("parsingMs", timing.parsingMs)
            put("validationMs", timing.validationMs)
            put("categorizationMs", timing.categorizationMs)
            put("totalMs", timing.totalMs)
            put("endToEndMs", timing.endToEndMs)
            put("mode", timing.mode.name)
            put("coldStart", timing.coldStart)
            put("sharedPreprocessing", timing.sharedPreprocessing)
            put("sharedOcr", timing.sharedOcr)
        })
        put("confidence", confidence ?: JSONObject.NULL)
        put("receipt", receipt?.let { JSONObject(it.toJson()) } ?: JSONObject.NULL)
        put("ocr", ocr?.let { JSONObject(it.toJson()) } ?: JSONObject.NULL)
        put("rawModelOutput", rawModelOutput ?: JSONObject.NULL)
        put("validation", validation?.let { JSONObject(it.toJson()) } ?: JSONObject.NULL)
        put("accuracy", accuracy?.let { JSONObject(it.toJson()) } ?: JSONObject.NULL)
        put("notes", JSONArray(notes))
        put("error", error ?: JSONObject.NULL)
    }.toString(2)
}

interface ReceiptPipeline {
    val id: String
    val displayName: String
    val usesOcr: Boolean
    val supportsDirectImage: Boolean

    suspend fun capability(): PipelineCapability
    suspend fun process(input: ReceiptPipelineInput): ReceiptPipelineRun
}

private fun Iterable<Float>.averageOrNull(): Float? {
    val values = toList()
    return values.takeIf { it.isNotEmpty() }?.average()?.toFloat()
}

private fun OcrBounds.toJson(): JSONObject = JSONObject().apply {
    put("left", left)
    put("top", top)
    put("right", right)
    put("bottom", bottom)
    put("width", width)
    put("height", height)
    put("centerX", centerX)
    put("centerY", centerY)
}

private fun OcrElement.toJson(): JSONObject = JSONObject().apply {
    put("text", text)
    put("bounds", bounds?.toJson() ?: JSONObject.NULL)
    put("confidence", confidence ?: JSONObject.NULL)
    put("blockIndex", blockIndex)
    put("lineIndex", lineIndex)
    put("elementIndex", elementIndex)
    put("order", order)
}

private fun OcrLine.toJson(): JSONObject = JSONObject().apply {
    put("text", text)
    put("bounds", bounds?.toJson() ?: JSONObject.NULL)
    put("confidence", confidence ?: JSONObject.NULL)
    put("blockIndex", blockIndex)
    put("lineIndex", lineIndex)
    put("order", order)
    put("elements", JSONArray(elements.map { it.toJson() }))
}

private fun OcrBlock.toJson(): JSONObject = JSONObject().apply {
    put("text", text)
    put("bounds", bounds?.toJson() ?: JSONObject.NULL)
    put("confidence", confidence ?: JSONObject.NULL)
    put("blockIndex", blockIndex)
    put("order", order)
    put("lines", JSONArray(lines.map { it.toJson() }))
}
