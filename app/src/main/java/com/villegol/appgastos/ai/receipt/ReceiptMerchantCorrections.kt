package com.villegol.appgastos.ai.receipt

import android.content.Context
import org.json.JSONObject

/**
 * Small, local, explicit memory for merchant corrections made by the user.
 *
 * It is deliberately an exact normalized-alias lookup. A correction such as
 * "TIEMDA DEMO SPA" -> "COMERCIO DEMOSTRACION SPA" is reused when
 * the same OCR mistake appears again, but an unrelated merchant is never
 * changed because it happens to look vaguely similar.
 */
class ReceiptMerchantCorrectionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "receipt-merchant-corrections",
        Context.MODE_PRIVATE
    )

    fun resolve(observedMerchant: String?): String? {
        val key = keyFor(observedMerchant) ?: return null
        return preferences.getString(key, null)?.trim()?.takeIf { it.isNotBlank() }
    }

    fun rememberCorrection(observedMerchant: String?, correctedMerchant: String?): Boolean {
        val source = observedMerchant?.trim().orEmpty()
        val corrected = correctedMerchant?.trim().orEmpty()
        val key = keyFor(source) ?: return false
        if (corrected.isBlank() || keyFor(corrected) == key) return false

        preferences.edit().putString(key, corrected).apply()
        return true
    }

    fun rememberCorrectionFromStructuredReceipt(
        structuredOcr: String?,
        correctedMerchant: String?
    ): Boolean {
        val observedMerchant = structuredOcr
            ?.let { raw -> runCatching { JSONObject(raw).optString("merchantName") }.getOrNull() }
            ?.takeIf { it.isNotBlank() && it != "null" }
        return rememberCorrection(observedMerchant, correctedMerchant)
    }

    companion object {
        /** Stable key shared by persistence and unit tests, without storing raw OCR as a key. */
        fun keyFor(merchant: String?): String? {
            val normalized = ChileanReceiptUtils.normalizeMerchant(merchant)
            return normalized.takeIf { it.isNotBlank() }?.let { "merchant_$it" }
        }
    }
}
