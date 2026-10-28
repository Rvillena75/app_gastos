package com.villegol.appgastos.data

import java.time.Instant
import java.util.UUID

enum class ExpenseStatus { DETECTED, ENRICHING, PENDING_REVIEW, IDENTIFIED, DELETED }
enum class ReceiptProcessingStatus { PROCESSING, COMPLETE, ERROR }
enum class EvidenceType {
    WALLET_NOTIFICATION,
    LOCATION,
    RECEIPT,
    RECEIPT_OCR,
    MANUAL_CORRECTION,
    MERCHANT_RULE,
    SYSTEM_INFERENCE
}
enum class Necessity { NECESSARY, USEFUL, WHIM }
enum class PaymentCardType { CREDIT, DEBIT, PREPAID, OTHER }

data class Expense(
    val id: String = UUID.randomUUID().toString(),
    val amountMinor: Long?,
    val currency: String = "CLP",
    val occurredAt: Instant = Instant.now(),
    val cardLast4: String? = null,
    val merchantId: String? = null,
    val merchantName: String? = null,
    val categoryId: String? = null,
    val categoryName: String? = null,
    val description: String? = null,
    val necessity: Necessity? = null,
    val status: ExpenseStatus = ExpenseStatus.DETECTED,
    val confidence: Float = 0f,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

data class Evidence(
    val id: String = UUID.randomUUID().toString(),
    val type: EvidenceType,
    val capturedAt: Instant = Instant.now(),
    val rawPayload: String,
    val derivedPayload: String? = null,
    val confidence: Float? = null
)

data class Merchant(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val aliases: List<String> = emptyList(),
    val defaultCategoryId: String? = null
)

data class Category(val id: String, val name: String, val sortOrder: Int)

data class ExpenseProduct(
    val id: String = UUID.randomUUID().toString(),
    val expenseId: String,
    val name: String,
    val quantity: Double? = 1.0,
    val unitPriceMinor: Long? = null
)

fun requiresExpenseReview(merchantName: String?, amountMinor: Long?): Boolean =
    merchantName.isNullOrBlank() || amountMinor == null

data class PaymentCard(
    val last4: String,
    val name: String? = null,
    val firstSeenAt: Instant = Instant.now(),
    val colorHex: String? = null,
    val type: PaymentCardType = PaymentCardType.OTHER
)

data class SourceEvent(
    val id: String = UUID.randomUUID().toString(),
    val eventKey: String,
    val packageName: String,
    val notificationKey: String?,
    val postedAt: Instant,
    val title: String?,
    val text: String?,
    val subText: String?,
    val bigText: String?,
    val rawExtras: String?
)

data class Receipt(
    val id: String = UUID.randomUUID().toString(),
    val expenseId: String,
    val imagePath: String,
    val capturedAt: Instant = Instant.now(),
    val rawOcr: String? = null,
    val structuredOcr: String? = null,
    val processingStatus: ReceiptProcessingStatus = ReceiptProcessingStatus.PROCESSING,
    val processingError: String? = null
)

data class WalletParsedData(
    val amountMinor: Long?,
    val currency: String,
    val merchantText: String?,
    val cardLast4: String?,
    val rawText: String
)

data class ExpenseCandidate(
    val amountMinor: Long?,
    val currency: String,
    val occurredAt: Instant,
    val merchantText: String?,
    val cardLast4: String?,
    val sourceEventKey: String
)

data class MatchCandidate(
    val expense: Expense,
    val score: Int,
    val reasons: List<String>
)

data class InferenceResult(
    val merchantName: String?,
    val categoryName: String?,
    val confidence: Float,
    val reasons: List<String>,
    val requiresReview: Boolean
)

data class AuditEvent(
    val id: String = UUID.randomUUID().toString(),
    val expenseId: String,
    val eventType: String,
    val beforeJson: String?,
    val afterJson: String?,
    val createdAt: Instant = Instant.now()
)

data class MerchantRule(
    val id: String = UUID.randomUUID().toString(),
    val pattern: String,
    val merchantName: String,
    val source: String,
    val weight: Float = 1f,
    val enabled: Boolean = true
)

data class NotificationSnapshot(
    val packageName: String,
    val notificationKey: String?,
    val postedAt: Instant,
    val title: String?,
    val text: String?,
    val subText: String?,
    val bigText: String?,
    val rawExtras: String? = null
) {
    val rawText: String
        get() = listOfNotNull(title, text, subText, bigText).joinToString(" ")

    val eventKey: String
        get() = buildString {
            append(packageName).append('|')
            append(notificationKey ?: "no-key").append('|')
            append(postedAt.toEpochMilli()).append('|')
            append(rawText.trim().lowercase())
        }.sha256()
}

fun String.sha256(): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    return digest.digest(toByteArray()).joinToString("") { "%02x".format(it) }
}

data class ExpenseUiModel(
    val expense: Expense,
    val displayMerchant: String,
    val displayAmount: String,
    val displayDate: String,
    val category: String,
    val isPending: Boolean
)
