package com.villegol.appgastos.domain

import com.villegol.appgastos.data.Expense
import com.villegol.appgastos.data.ExpenseCandidate
import com.villegol.appgastos.data.NotificationSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class WalletNotificationParserTest {
    private val parser = WalletNotificationParser()

    @Test
    fun parsesWalletChileanAmountAndCard() {
        val parsed = parser.parse(
            NotificationSnapshot(
                packageName = "com.google.android.apps.walletnfcrel",
                notificationKey = "key-1",
                postedAt = Instant.parse("2026-08-10T20:00:00Z"),
                title = "COMERCIO DEMO LOCAL 01",
                text = "CLP11,000 con Banco Demo Crédito ••1111",
                subText = null,
                bigText = null
            )
        )

        assertEquals(11000L, parsed.amountMinor)
        assertEquals("CLP", parsed.currency)
        assertEquals("COMERCIO DEMO LOCAL 01", parsed.merchantText)
        assertEquals("1111", parsed.cardLast4)
    }

    @Test
    fun keepsUnknownAmountAsNull() {
        val parsed = parser.parse(
            NotificationSnapshot(
                packageName = "wallet",
                notificationKey = "key-2",
                postedAt = Instant.now(),
                title = "Comercio desconocido",
                text = "Compra aprobada",
                subText = null,
                bigText = null
            )
        )
        assertEquals(null, parsed.amountMinor)
        assertEquals("Comercio desconocido", parsed.merchantText)
    }

    @Test
    fun duplicateMatcherRequiresMoreThanAmount() {
        val now = Instant.parse("2026-08-10T20:00:00Z")
        val existing = Expense(amountMinor = 1000L, occurredAt = now, merchantName = "Jumbo", cardLast4 = "1234")
        val candidate = ExpenseCandidate(1000L, "CLP", now.plusSeconds(60), "Jumbo", "1234", "event")
        val match = DuplicateMatcher().findCandidates(candidate, listOf(existing)).firstOrNull()

        assertNotNull(match)
        assertTrue(DuplicateMatcher().shouldAutoAttach(match!!))
    }
}
