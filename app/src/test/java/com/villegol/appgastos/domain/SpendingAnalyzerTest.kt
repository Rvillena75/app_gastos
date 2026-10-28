package com.villegol.appgastos.domain

import com.villegol.appgastos.data.Expense
import com.villegol.appgastos.data.PaymentCard
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class SpendingAnalyzerTest {
    private val utc = ZoneId.of("UTC")
    private val month = YearMonth.of(2026, 8)

    @Test
    fun `builds category card and weekly breakdowns for selected month`() {
        val expenses = listOf(
            expense("2026-08-02T10:00:00Z", 10_000L, "Salud", "1111"),
            expense("2026-08-09T10:00:00Z", 5_000L, "Salud", "1111"),
            expense("2026-08-21T10:00:00Z", 20_000L, null, null),
            expense("2026-07-31T10:00:00Z", 99_000L, "Otro", "1111")
        )

        val result = SpendingAnalyzer().analyze(
            expenses = expenses,
            cards = listOf(PaymentCard(last4 = "1111", name = "Visa personal")),
            month = month,
            zoneId = utc
        )

        assertEquals(35_000L, result.totalMinor)
        assertEquals(3, result.movementCount)
        assertEquals(20_000L, result.categoryBreakdown.first().amountMinor)
        assertEquals("Sin categoría", result.categoryBreakdown.first().label)
        assertEquals("Sin tarjeta", result.cardBreakdown.first().label)
        assertEquals(listOf(10_000L, 5_000L, 20_000L, 0L, 0L), result.weeklySpend.map { it.amountMinor })
    }

    @Test
    fun `does not count missing amounts or other currencies as CLP totals`() {
        val expenses = listOf(
            expense("2026-08-02T10:00:00Z", null, "Salud", null),
            expense("2026-08-03T10:00:00Z", 1_000L, "Viajes", null, currency = "USD")
        )

        val result = SpendingAnalyzer().analyze(expenses, emptyList(), month, utc)

        assertEquals(0L, result.totalMinor)
        assertEquals(0, result.movementCount)
        assertEquals(2, result.uncountedMovementCount)
    }

    private fun expense(
        occurredAt: String,
        amountMinor: Long?,
        category: String?,
        cardLast4: String?,
        currency: String = "CLP"
    ) = Expense(
        amountMinor = amountMinor,
        currency = currency,
        occurredAt = Instant.parse(occurredAt),
        merchantName = "Comercio",
        categoryName = category,
        cardLast4 = cardLast4
    )
}
