package com.villegol.appgastos.source.wallet

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.villegol.appgastos.GastosApplication
import com.villegol.appgastos.ai.receipt.ReceiptPipelineInput
import com.google.android.gms.tasks.Task
import java.io.File
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class EnrichExpenseWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val expenseId = inputData.getString(EXPENSE_ID) ?: return Result.failure()
        val app = applicationContext as GastosApplication
        app.container.expenseRepository.enrichExpense(expenseId)
        return Result.success()
    }

    companion object { const val EXPENSE_ID = "expense_id" }
}

class ReceiptOcrWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val receiptId = inputData.getString(RECEIPT_ID) ?: return Result.failure()
        val imagePath = inputData.getString(IMAGE_PATH) ?: return Result.failure()
        val app = applicationContext as GastosApplication
        app.container.expenseRepository.markReceiptProcessing(receiptId)
        val imageFile = File(imagePath)
        if (!imageFile.exists()) {
            app.container.expenseRepository.markReceiptError(receiptId, "No se encontró la imagen guardada.")
            return Result.failure()
        }

        return try {
            val run = app.container.receiptPipelines.mlKit.process(
                ReceiptPipelineInput(imagePath = imageFile.absolutePath)
            )
            val ocr = run.ocr ?: run {
                app.container.expenseRepository.markReceiptError(receiptId, "No se detectó texto legible en la boleta.")
                return Result.success()
            }
            app.container.expenseRepository.updateReceiptOcr(
                receiptId = receiptId,
                rawOcr = ocr.rawText(),
                structuredOcr = run.receipt?.toJson()
            )
            app.container.expenseRepository.getReceipt(receiptId)?.let { receipt ->
                run.receipt?.let { structuredReceipt ->
                    app.container.expenseRepository.applyReceiptResult(
                        expenseId = receipt.expenseId,
                        receipt = structuredReceipt,
                        confidence = run.confidence
                    )
                }
                app.container.expenseRepository.attachEvidence(
                    receipt.expenseId,
                    com.villegol.appgastos.data.Evidence(
                        type = com.villegol.appgastos.data.EvidenceType.RECEIPT_OCR,
                        rawPayload = ocr.toJson(),
                        derivedPayload = run.receipt?.toJson(),
                        confidence = run.confidence
                    ),
                    reason = "ML Kit OCR estructurado + parser determinista + validaciones"
                )
            }
            Result.success()
        } catch (exception: Exception) {
            app.container.expenseRepository.markReceiptError(
                receiptId,
                exception.message ?: "No se pudo procesar la boleta."
            )
            Result.failure()
        }
    }

    companion object {
        const val RECEIPT_ID = "receipt_id"
        const val IMAGE_PATH = "image_path"
    }
}

/** Shared Play Services Task adapter used by the location worker. */
suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { value ->
        if (continuation.isActive) continuation.resume(value)
    }
    addOnFailureListener { error ->
        if (continuation.isActive) continuation.resumeWithException(error)
    }
    addOnCanceledListener {
        if (continuation.isActive) continuation.cancel()
    }
}
