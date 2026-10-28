package com.villegol.appgastos.ai.receipt

import android.os.SystemClock

internal fun monotonicNowNanos(): Long = SystemClock.elapsedRealtimeNanos()

internal fun elapsedMillis(startedNanos: Long): Long =
    ((SystemClock.elapsedRealtimeNanos() - startedNanos) / 1_000_000L).coerceAtLeast(0L)

data class TimedValue<T>(val value: T, val elapsedMs: Long)

internal inline fun <T> measureStage(block: () -> T): TimedValue<T> {
    val started = monotonicNowNanos()
    return TimedValue(block(), elapsedMillis(started))
}

internal suspend inline fun <T> measureSuspendStage(crossinline block: suspend () -> T): TimedValue<T> {
    val started = monotonicNowNanos()
    return TimedValue(block(), elapsedMillis(started))
}

internal fun finishReceiptRun(
    pipelineId: String,
    displayName: String,
    capability: PipelineCapability,
    inputImagePath: String? = null,
    preprocessedImagePath: String? = null,
    receipt: StructuredReceipt,
    ocr: OcrDocument?,
    rawModelOutput: String?,
    timing: PipelineTiming,
    validator: ReceiptValidator,
    categoryResolver: ReceiptCategoryResolver,
    confidenceScorer: ReceiptConfidenceScorer,
    modelCategoryHint: String? = null,
    groundTruth: ReceiptGroundTruth? = null,
    notes: List<String> = emptyList()
): ReceiptPipelineRun {
    val categoryTimed = measureStage { categoryResolver.resolve(receipt, modelCategoryHint) }
    val categorized = receipt.withCategory(categoryTimed.value)
    val validationTimed = measureStage { validator.validate(categorized) }
    val confidence = confidenceScorer.score(categorized, validationTimed.value, ocr?.sourceConfidence)
    val completedTiming = timing.copy(
        validationMs = validationTimed.elapsedMs,
        categorizationMs = categoryTimed.elapsedMs,
        totalMs = timing.totalMs,
        endToEndMs = timing.endToEndMs
    )
    return ReceiptPipelineRun(
        pipelineId = pipelineId,
        displayName = displayName,
        capability = capability,
        inputImagePath = inputImagePath,
        preprocessedImagePath = preprocessedImagePath,
        receipt = categorized,
        ocr = ocr,
        rawModelOutput = rawModelOutput,
        validation = validationTimed.value,
        accuracy = groundTruth?.let { ReceiptAccuracyEvaluator().evaluate(categorized, it) },
        confidence = confidence,
        timing = completedTiming,
        notes = notes
    )
}

internal fun failedReceiptRun(
    pipelineId: String,
    displayName: String,
    capability: PipelineCapability,
    error: Throwable,
    timing: PipelineTiming = PipelineTiming(),
    notes: List<String> = emptyList()
): ReceiptPipelineRun = ReceiptPipelineRun(
    pipelineId = pipelineId,
    displayName = displayName,
    capability = capability,
    timing = timing,
    error = error.message ?: error::class.java.simpleName,
    notes = notes
)
