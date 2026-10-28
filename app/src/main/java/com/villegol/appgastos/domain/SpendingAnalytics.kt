package com.villegol.appgastos.domain

import com.villegol.appgastos.data.Expense
import com.villegol.appgastos.data.ExpenseStatus
import com.villegol.appgastos.data.PaymentCard
import java.time.YearMonth
import java.time.ZoneId

data class SpendingBreakdown(
    val key: String,
    val label: String,
    val amountMinor: Long,
    val movementCount: Int
)

data class WeeklySpend(
    val weekIndex: Int,
    val label: String,
    val amountMinor: Long
)

data class SpendingAnalysis(
    val month: YearMonth,
    val currency: String,
    val totalMinor: Long,
    val movementCount: Int,
    val averageMovementMinor: Long,
    val categoryBreakdown: List<SpendingBreakdown>,
    val cardBreakdown: List<SpendingBreakdown>,
    val weeklySpend: List<WeeklySpend>,
    val uncountedMovementCount: Int
)

class SpendingAnalyzer {
    fun analyze(
        expenses: List<Expense>,
        cards: List<PaymentCard>,
        month: YearMonth,
        zoneId: ZoneId = ZoneId.systemDefault(),
        currency: String = "CLP"
    ): SpendingAnalysis {
        val monthExpenses = expenses.filter { expense ->
            expense.status != ExpenseStatus.DELETED &&
                YearMonth.from(expense.occurredAt.atZone(zoneId)) == month
        }
        val counted = monthExpenses.filter { it.amountMinor != null && it.currency == currency }
        val total = counted.sumOf { it.amountMinor ?: 0L }
        val cardsByLast4 = cards.associateBy { it.last4 }

        val categories = counted
            .groupBy { it.categoryName?.trim()?.takeIf(String::isNotEmpty) ?: UNCATEGORIZED_KEY }
            .map { (key, values) ->
                SpendingBreakdown(
                    key = key,
                    label = if (key == UNCATEGORIZED_KEY) "Sin categoría" else key,
                    amountMinor = values.sumOf { it.amountMinor ?: 0L },
                    movementCount = values.size
                )
            }
            .sortedByDescending { it.amountMinor }

        val cardBreakdown = counted
            .groupBy { it.cardLast4?.takeIf(String::isNotBlank) ?: WITHOUT_CARD_KEY }
            .map { (key, values) ->
                val card = cardsByLast4[key]
                val label = when {
                    key == WITHOUT_CARD_KEY -> "Sin tarjeta"
                    !card?.name.isNullOrBlank() -> "${card?.name} · •••• $key"
                    else -> "•••• $key"
                }
                SpendingBreakdown(
                    key = key,
                    label = label,
                    amountMinor = values.sumOf { it.amountMinor ?: 0L },
                    movementCount = values.size
                )
            }
            .sortedByDescending { it.amountMinor }

        val weekly = (0 until ((month.lengthOfMonth() - 1) / 7 + 1)).map { weekIndex ->
            val firstDay = weekIndex * 7 + 1
            val lastDay = minOf(firstDay + 6, month.lengthOfMonth())
            val weekTotal = counted
                .filter { expense ->
                    expense.occurredAt.atZone(zoneId).dayOfMonth in firstDay..lastDay
                }
                .sumOf { it.amountMinor ?: 0L }
            WeeklySpend(
                weekIndex = weekIndex,
                label = "$firstDay–$lastDay",
                amountMinor = weekTotal
            )
        }

        return SpendingAnalysis(
            month = month,
            currency = currency,
            totalMinor = total,
            movementCount = counted.size,
            averageMovementMinor = if (counted.isEmpty()) 0L else total / counted.size,
            categoryBreakdown = categories,
            cardBreakdown = cardBreakdown,
            weeklySpend = weekly,
            uncountedMovementCount = monthExpenses.size - counted.size
        )
    }

    companion object {
        const val WITHOUT_CARD_KEY = "__without_card__"
        const val UNCATEGORIZED_KEY = "__uncategorized__"
    }
}
