package com.villegol.appgastos.fixtures

import com.villegol.appgastos.ai.receipt.ChileanReceiptParser
import com.villegol.appgastos.ai.receipt.ReceiptCategory
import com.villegol.appgastos.ai.receipt.ReceiptValidator
import com.villegol.appgastos.data.ExpenseStatus
import com.villegol.appgastos.data.requiresExpenseReview
import com.villegol.appgastos.domain.SpendingAnalyzer
import com.villegol.appgastos.domain.WalletNotificationParser
import java.time.Duration
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntheticBaselineFixtureTest {
    private val fixtures = SyntheticBaselineFixtures

    @Test
    fun `synthetic corpus has unique identities and valid evidence product card and category references`() {
        assertTrue(fixtures.isSynthetic)
        assertEquals(1, fixtures.formatVersion)
        val expenses = fixtures.expenses()
        val ids = expenses.map { it.id }.toSet()
        assertEquals(expenses.size, ids.size)
        val evidence = fixtures.evidence()
        assertEquals(evidence.size, evidence.map { it.second.id }.toSet().size)
        assertTrue(evidence.all { it.first in ids })
        assertTrue(fixtures.products().all { it.expenseId in ids })
        val cards = fixtures.cards().map { it.last4 }.toSet()
        assertTrue(expenses.mapNotNull { it.cardLast4 }.all { it in cards })
        val categories = fixtures.categories().associateBy { it.id }
        expenses.filter { it.categoryId != null }.forEach {
            assertEquals(it.categoryName, categories.getValue(it.categoryId!!).name)
        }
        assertEquals(expenses, fixtures.expenses())
        assertEquals(evidence, fixtures.evidence())
    }

    @Test
    fun `monthly CLP analysis excludes deleted previous month missing amounts and foreign currencies`() {
        val result = SpendingAnalyzer().analyze(fixtures.expenses(), fixtures.cards(), fixtures.month, fixtures.zoneId)
        assertEquals(20_000L, result.totalMinor)
        assertEquals(4, result.movementCount)
        assertEquals(3, result.uncountedMovementCount)
        assertEquals(20_000L, result.weeklySpend.sumOf { it.amountMinor })
        assertEquals(20_000L, result.cardBreakdown.sumOf { it.amountMinor })
        assertEquals(20_000L, result.categoryBreakdown.sumOf { it.amountMinor })
    }

    @Test
    fun `USD and EUR keep their own minor units without conversion to CLP`() {
        val analyzer = SpendingAnalyzer()
        val usd = analyzer.analyze(fixtures.expenses(), fixtures.cards(), fixtures.month, fixtures.zoneId, "USD")
        val eur = analyzer.analyze(fixtures.expenses(), fixtures.cards(), fixtures.month, fixtures.zoneId, "EUR")
        assertEquals(1_250L, usd.totalMinor)
        assertEquals(9_900L, eur.totalMinor)
        assertEquals(1, usd.movementCount)
        assertEquals(1, eur.movementCount)
    }

    @Test
    fun `purchase near UTC month boundary belongs to August in Santiago`() {
        val boundary = fixtures.expenses().single { it.id == "f0:local-month-boundary" }
        val analyzer = SpendingAnalyzer()
        assertEquals(3_000L, analyzer.analyze(listOf(boundary), emptyList(), fixtures.month, fixtures.zoneId).totalMinor)
        assertEquals(0L, analyzer.analyze(listOf(boundary), emptyList(), fixtures.month, ZoneId.of("UTC")).totalMinor)
    }

    @Test
    fun `synthetic pending cases are caused by missing merchant or amount rather than optional fields`() {
        fixtures.expenses().filter { it.status != ExpenseStatus.DELETED }.forEach {
            assertEquals(it.status == ExpenseStatus.PENDING_REVIEW, requiresExpenseReview(it.merchantName, it.amountMinor))
        }
    }

    @Test
    fun `synthetic Wallet evidence parses CLP USD and missing amount and exact snapshot identity is stable`() {
        val snapshots = fixtures.walletSnapshots()
        val parsed = snapshots.map(WalletNotificationParser()::parse)
        assertEquals(10_000L, parsed[0].amountMinor)
        assertEquals("1111", parsed[0].cardLast4)
        assertEquals(1_250L, parsed[1].amountMinor)
        assertEquals("USD", parsed[1].currency)
        assertNull(parsed[2].amountMinor)
        assertEquals(snapshots[0].eventKey, snapshots[0].copy().eventKey)
        assertNotEquals(snapshots[0].eventKey, snapshots[0].copy(postedAt = snapshots[0].postedAt.plusSeconds(1)).eventKey)
        assertEquals(listOf(86_400L, 86_401L), fixtures.futureMatchCandidates().map { Duration.between(it.first.occurredAt, it.second).seconds })
    }

    @Test
    fun `synthetic spatial receipt reconciles discounts tax products and date without an invented time`() {
        val receipt = ChileanReceiptParser().parse(fixtures.receiptOcr())
        assertEquals("COMERCIO SINTETICO A SPA", receipt.merchantName)
        assertEquals("2026-08-10", receipt.date)
        assertNull(receipt.time)
        assertEquals(10_000L, receipt.totalMinor)
        assertEquals(8_403L, receipt.netMinor)
        assertEquals(1_597L, receipt.taxMinor)
        assertEquals(2, receipt.items.size)
        assertEquals(listOf(8_000L, 4_500L), receipt.items.map { it.lineTotalMinor })
        assertEquals(2_500L, receipt.discounts.sumOf { it.amountMinor })
        val categorized = receipt.copy(category = ReceiptCategory(fixtures.categories().single().name, confidence = 1f, source = "synthetic_fixture"))
        val validation = ReceiptValidator().validate(categorized)
        assertEquals(validation.checks.toString(), 0, validation.failed)
        assertEquals(1f, validation.score)
    }
}
