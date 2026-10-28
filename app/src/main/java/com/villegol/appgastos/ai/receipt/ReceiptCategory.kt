package com.villegol.appgastos.ai.receipt

import android.content.Context
import org.json.JSONObject

class ReceiptCategoryStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("receipt-category-corrections", Context.MODE_PRIVATE)

    fun get(merchant: String?): ReceiptCategory? {
        val key = ChileanReceiptUtils.normalizeMerchant(merchant).replace(" ", "_")
        if (key.isBlank()) return null
        val raw = preferences.getString(key, null) ?: return null
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        return ReceiptCategory(
            category = json.optString("category").takeIf { it.isNotBlank() } ?: return null,
            subcategory = json.optString("subcategory").takeIf { it.isNotBlank() },
            confidence = json.optDouble("confidence", 1.0).toFloat().coerceIn(0f, 1f),
            source = "user_correction"
        )
    }

    fun put(merchant: String, category: String, subcategory: String? = null) {
        val key = ChileanReceiptUtils.normalizeMerchant(merchant).replace(" ", "_")
        if (key.isBlank() || category.isBlank()) return
        preferences.edit().putString(
            key,
            JSONObject().apply {
                put("category", category.trim())
                put("subcategory", subcategory?.trim().takeIf { !it.isNullOrBlank() } ?: JSONObject.NULL)
                put("confidence", 1.0)
            }.toString()
        ).apply()
    }
}

class ReceiptCategoryResolver(
    private val store: ReceiptCategoryStore? = null
) {
    fun resolve(receipt: StructuredReceipt, modelHint: String? = null): ReceiptCategory? =
        resolve(receipt.merchantName, receipt.items.joinToString(" ") { it.description }, modelHint)

    fun resolve(merchant: String?, text: String, modelHint: String? = null): ReceiptCategory? {
        store?.get(merchant)?.let { return it }
        val folded = ChileanReceiptUtils.fold(listOfNotNull(merchant, text).joinToString(" "))
        return when {
            Regex("\\b(FARMACIA|FARMACIAS|PHARMACY|CRUZ VERDE|AHUMADA|SALCOBRAND)\\b").containsMatchIn(folded) ->
                ReceiptCategory("Salud", "Medicamentos", 0.99f, "merchant_rule")
            Regex("\\b(SUPERMERCADO|JUMBO|LIDER|UNIMARC|SANTA ISABEL)\\b").containsMatchIn(folded) ->
                ReceiptCategory("Alimentación", "Supermercado", 0.98f, "merchant_rule")
            Regex("\\b(UBER EATS|RESTAURANTE|RESTAURANT|STARBUCKS|MCDONALD)\\b").containsMatchIn(folded) ->
                ReceiptCategory("Alimentación", "Comida", 0.96f, "keyword_rule")
            Regex("\\b(COPEC|SHELL|ENEX|COMBUSTIBLE|BENCINA)\\b").containsMatchIn(folded) ->
                ReceiptCategory("Transporte", "Combustible", 0.96f, "merchant_rule")
            Regex("\\b(MEDICAMENTO|MEDICAMENTOS|TABLETA|CAPSULA|JARABE)\\b").containsMatchIn(folded) ->
                ReceiptCategory("Salud", "Medicamentos", 0.90f, "keyword_rule")
            !modelHint.isNullOrBlank() -> ReceiptCategory(modelHint.trim(), null, 0.5f, "model_fallback")
            else -> null
        }
    }

    fun rememberCorrection(merchant: String, category: String, subcategory: String? = null) {
        store?.put(merchant, category, subcategory)
    }
}
