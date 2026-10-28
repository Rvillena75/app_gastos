package com.villegol.appgastos.domain

import com.villegol.appgastos.data.Expense
import com.villegol.appgastos.data.ExpenseCandidate
import com.villegol.appgastos.data.InferenceResult
import com.villegol.appgastos.data.MatchCandidate
import com.villegol.appgastos.data.MerchantRule
import com.villegol.appgastos.data.NotificationSnapshot
import com.villegol.appgastos.data.WalletParsedData
import java.time.Duration
import java.util.Locale

class WalletNotificationParser {
    fun parse(snapshot: NotificationSnapshot): WalletParsedData {
        val body = listOfNotNull(snapshot.text, snapshot.subText, snapshot.bigText).joinToString(" ")
        val amountMatch = amountRegex.find(body)
        val currency = amountMatch?.groupValues?.getOrNull(1)?.uppercase(Locale.ROOT)
            ?.takeIf { it.isNotBlank() } ?: if (body.contains("$")) "CLP" else "CLP"
        val amount = amountMatch?.groupValues?.getOrNull(2)?.let { parseAmount(it, currency) }
        val merchant = snapshot.title
            ?.trim()
            ?.takeIf { it.isNotBlank() && !amountRegex.containsMatchIn(it) }
        val cardLast4 = cardRegex.find(body)?.groupValues?.getOrNull(1)

        return WalletParsedData(
            amountMinor = amount,
            currency = currency,
            merchantText = merchant,
            cardLast4 = cardLast4,
            rawText = snapshot.rawText
        )
    }

    private fun parseAmount(raw: String, currency: String): Long? {
        val value = raw.trim()
        if (currency == "CLP") return value.filter(Char::isDigit).toLongOrNull()

        val lastSeparator = maxOf(value.lastIndexOf(','), value.lastIndexOf('.'))
        return if (lastSeparator >= 0 && value.length - lastSeparator - 1 in 1..2) {
            val major = value.substring(0, lastSeparator).filter(Char::isDigit).toLongOrNull() ?: return null
            val decimals = value.substring(lastSeparator + 1).filter(Char::isDigit).padEnd(2, '0').take(2)
            major * 100 + decimals.toLongOrNull().orZero()
        } else {
            value.filter(Char::isDigit).toLongOrNull()?.times(100)
        }
    }

    private fun Long?.orZero(): Long = this ?: 0L

    private companion object {
        val amountRegex = Regex("(?i)(?:\\b(CL[P]|USD|EUR)\\s*|\\$\\s*)([0-9][0-9.,]*)")
        val cardRegex = Regex("(?:•{2,}|[*xX]{2,}|ending\\s*)\\s*([0-9]{4})")
    }
}

class DefaultInferenceEngine(
    private val rules: List<MerchantRule> = emptyList()
) {
    fun infer(merchantText: String?, categoryHint: String? = null): InferenceResult {
        val normalized = merchantText.orEmpty().normalizeMerchant()
        if (normalized.isBlank()) {
            return InferenceResult(null, categoryHint, 0.1f, listOf("No se encontró comercio en la evidencia"), true)
        }

        val rule = rules.firstOrNull { it.enabled && normalized.contains(it.pattern.normalizeMerchant()) }
        if (rule != null) {
            return InferenceResult(
                merchantName = rule.merchantName,
                categoryName = categoryHint ?: categoryFor(rule.merchantName),
                confidence = 0.92f,
                reasons = listOf("Regla aprendida desde ${rule.source}"),
                requiresReview = false
            )
        }

        val known = knownAliases.entries.firstOrNull { (alias, _) -> normalized.contains(alias) }
        if (known != null) {
            return InferenceResult(
                merchantName = known.value,
                categoryName = categoryHint ?: categoryFor(known.value),
                confidence = 0.9f,
                reasons = listOf("Texto del comercio reconocido en Wallet"),
                requiresReview = false
            )
        }

        return InferenceResult(
            merchantName = merchantText?.trim(),
            categoryName = categoryHint,
            confidence = 0.55f,
            reasons = listOf("Comercio conservado desde el texto original"),
            requiresReview = false
        )
    }

    private fun categoryFor(merchant: String): String? = when {
        merchant.contains("farmacia", true) || merchant.contains("cruz verde", true) -> "Salud"
        merchant.contains("jumbo", true) -> "Supermercado"
        merchant.contains("uber eats", true) || merchant.contains("starbucks", true) -> "Alimentación"
        else -> null
    }

    private companion object {
        val knownAliases = mapOf(
            "sb 648" to "Starbucks",
            "starbucks" to "Starbucks",
            "ahum" to "Farmacias Ahumada",
            "cruz verde" to "Cruz Verde",
            "cv 886" to "Cruz Verde",
            "jumbo" to "Jumbo",
            "uber eats" to "Uber Eats"
        )
    }
}

class DuplicateMatcher {
    fun findCandidates(candidate: ExpenseCandidate, existing: List<Expense>): List<MatchCandidate> = existing.mapNotNull { expense ->
        var score = 0
        val reasons = mutableListOf<String>()

        if (candidate.amountMinor != null && expense.amountMinor == candidate.amountMinor && candidate.currency == expense.currency) {
            score += 50
            reasons += "monto y moneda coinciden"
        }
        val minutes = kotlin.math.abs(Duration.between(candidate.occurredAt, expense.occurredAt).toMinutes())
        if (minutes <= 10) {
            score += 25
            reasons += "evento dentro de 10 minutos"
        }
        if (!candidate.merchantText.isNullOrBlank() &&
            normalize(candidate.merchantText) == normalize(expense.merchantName.orEmpty())
        ) {
            score += 25
            reasons += "comercio coincide"
        }
        if (!candidate.cardLast4.isNullOrBlank() && candidate.cardLast4 == expense.cardLast4) {
            score += 10
            reasons += "tarjeta coincide"
        }

        if (score >= 50) MatchCandidate(expense, score, reasons) else null
    }.sortedByDescending { it.score }

    fun shouldAutoAttach(candidate: MatchCandidate): Boolean =
        candidate.score >= 75 && candidate.reasons.any { it == "comercio coincide" || it == "tarjeta coincide" }

    private fun normalize(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace("[^a-z0-9áéíóúñ]+".toRegex(), "")
}

private fun String.normalizeMerchant(): String = lowercase(Locale.ROOT)
    .replace("[^a-z0-9áéíóúñ]+".toRegex(), " ")
    .trim()
