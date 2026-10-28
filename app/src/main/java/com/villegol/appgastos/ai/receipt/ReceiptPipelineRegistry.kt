package com.villegol.appgastos.ai.receipt

import android.content.Context

class ReceiptPipelineRegistry(
    context: Context,
    groundTruth: ReceiptGroundTruth? = null
) {
    val preprocessor = ReceiptImagePreprocessor(context)
    val parser = ChileanReceiptParser()
    val validator = ReceiptValidator()
    val categoryResolver = ReceiptCategoryResolver(ReceiptCategoryStore(context))
    val merchantCorrections = ReceiptMerchantCorrectionStore(context)

    val mlKit = MlKitReceiptPipeline(
        context = context,
        parser = parser,
        validator = validator,
        categoryResolver = categoryResolver,
        preprocessor = preprocessor,
        merchantCorrections = merchantCorrections,
        groundTruth = groundTruth
    )

    /** The app intentionally ships one lightweight, fully local receipt path. */
    val all: List<ReceiptPipeline> = listOf(mlKit)

    fun byId(id: String): ReceiptPipeline? = all.firstOrNull { it.id == id }

    fun close() {
        mlKit.close()
    }
}
