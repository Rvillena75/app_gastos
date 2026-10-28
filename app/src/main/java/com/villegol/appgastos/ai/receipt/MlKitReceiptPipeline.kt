package com.villegol.appgastos.ai.receipt

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object MlKitOcrNormalizer {
    fun normalize(result: Text, imageWidth: Int? = null, imageHeight: Int? = null): OcrDocument {
        var order = 0
        val blocks = result.textBlocks.mapIndexed { blockIndex, block ->
            val lines = block.lines.mapIndexed { lineIndex, line ->
                val elements = line.elements.mapIndexed { elementIndex, element ->
                    OcrElement(
                        text = element.text,
                        bounds = element.boundingBox?.toOcrBounds(),
                        confidence = element.confidence,
                        blockIndex = blockIndex,
                        lineIndex = lineIndex,
                        elementIndex = elementIndex,
                        order = order++
                    )
                }
                OcrLine(
                    text = line.text,
                    bounds = line.boundingBox?.toOcrBounds(),
                    confidence = line.confidence,
                    blockIndex = blockIndex,
                    lineIndex = lineIndex,
                    elements = elements,
                    order = order++
                )
            }
            OcrBlock(
                text = block.text,
                bounds = block.boundingBox?.toOcrBounds(),
                // TextBlock does not expose confidence in the bundled ML Kit API.
                confidence = null,
                blockIndex = blockIndex,
                lines = lines,
                order = blockIndex
            )
        }
        return OcrDocument(
            text = result.text,
            blocks = blocks,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            engine = "ml-kit-text-recognition-latin"
        )
    }
}

class MlKitOcrEngine(context: Context) {
    private val appContext = context.applicationContext
    private val recognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun recognize(imagePath: String): TimedValue<OcrDocument> {
        val started = monotonicNowNanos()
        val image = InputImage.fromFilePath(appContext, Uri.fromFile(java.io.File(imagePath)))
        val result = recognizer.process(image).awaitResult()
        val dimensions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imagePath, dimensions)
        return TimedValue(
            value = MlKitOcrNormalizer.normalize(
                result,
                imageWidth = dimensions.outWidth.takeIf { it > 0 },
                imageHeight = dimensions.outHeight.takeIf { it > 0 }
            ),
            elapsedMs = elapsedMillis(started)
        )
    }

    fun close() = recognizer.close()
}

class MlKitReceiptPipeline(
    context: Context,
    private val parser: ChileanReceiptParser,
    private val validator: ReceiptValidator,
    private val categoryResolver: ReceiptCategoryResolver,
    private val preprocessor: ReceiptImagePreprocessor,
    private val merchantCorrections: ReceiptMerchantCorrectionStore,
    private val groundTruth: ReceiptGroundTruth? = null
) : ReceiptPipeline {
    private val ocrEngine = MlKitOcrEngine(context)

    override val id: String = "mlkit_ocr_deterministic_parser"
    override val displayName: String = "ML Kit OCR + parser determinista"
    override val usesOcr: Boolean = true
    override val supportsDirectImage: Boolean = false

    override suspend fun capability(): PipelineCapability = PipelineCapability(
        availability = PipelineAvailability.AVAILABLE,
        detail = "Text Recognition latino bundled; conserva blocks/lines/elements y bounding boxes",
        modelSize = "Bundled ML Kit dependency",
        supportsWarmInference = true
    )

    override suspend fun process(input: ReceiptPipelineInput): ReceiptPipelineRun {
        val totalStarted = monotonicNowNanos()
        return try {
            val prepared = if (input.preprocessedImagePath == null) {
                val result = measureStage { preprocessor.prepare(input.imagePath) }
                result
            } else {
                TimedValue(
                    PreparedReceiptImage(input.preprocessedImagePath, input.sharedPreprocessingMs, transformed = true),
                    input.sharedPreprocessingMs
                )
            }
            val ocrTimed = input.sharedOcr?.let { TimedValue(it, 0L) } ?: ocrEngine.recognize(prepared.value.path)
            val parsed = measureStage {
                val extracted = parser.parse(ocrTimed.value)
                val correctedMerchant = merchantCorrections.resolve(extracted.merchantName)
                if (correctedMerchant == null) {
                    extracted
                } else {
                    extracted.copy(
                        merchantName = correctedMerchant,
                        legalName = extracted.legalName?.let { legalName ->
                            if (ChileanReceiptUtils.merchantMatches(legalName, extracted.merchantName) == true) {
                                correctedMerchant
                            } else {
                                legalName
                            }
                        }
                    )
                }
            }
            val timingBeforeFinalize = PipelineTiming(
                preprocessingMs = if (input.preprocessedImagePath == null) prepared.elapsedMs else 0L,
                ocrMs = ocrTimed.elapsedMs,
                parsingMs = parsed.elapsedMs,
                mode = input.mode,
                coldStart = input.mode == BenchmarkMode.COLD_END_TO_END,
                sharedPreprocessing = input.preprocessedImagePath != null,
                sharedOcr = input.sharedOcr != null,
                totalMs = 0L,
                endToEndMs = 0L
            )
            val run = finishReceiptRun(
                pipelineId = id,
                displayName = displayName,
                capability = capability(),
                inputImagePath = input.imagePath,
                preprocessedImagePath = prepared.value.path,
                receipt = parsed.value,
                ocr = ocrTimed.value,
                rawModelOutput = ocrTimed.value.rawText(),
                timing = timingBeforeFinalize,
                validator = validator,
                categoryResolver = categoryResolver,
                confidenceScorer = ReceiptConfidenceScorer(),
                groundTruth = input.groundTruth ?: groundTruth,
                notes = listOf(
                    "OCR espacial ML Kit + parser determinista; no depende del orden de un String aplanado",
                    "Las correcciones de comercio se aplican por alias local exacto"
                )
            )
            val totalMs = elapsedMillis(totalStarted)
            val sharedPrefix = if (input.sharedOcr != null) input.sharedPreprocessingMs + input.sharedOcrMs else 0L
            run.copy(timing = run.timing.copy(totalMs = totalMs, endToEndMs = totalMs + sharedPrefix))
        } catch (error: Throwable) {
            failedReceiptRun(id, displayName, capability(), error, timing = PipelineTiming(totalMs = elapsedMillis(totalStarted)))
        }
    }

    fun close() = ocrEngine.close()
}

private fun android.graphics.Rect.toOcrBounds(): OcrBounds = OcrBounds(left, top, right, bottom)

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { value -> if (continuation.isActive) continuation.resume(value) }
    addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
}
