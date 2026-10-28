package com.villegol.appgastos.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseReviewPolicyTest {
    @Test
    fun `merchant and amount are enough to finish a movement`() {
        assertFalse(requiresExpenseReview("Jumbo", 15_990L))
    }

    @Test
    fun `category is not part of the review requirement`() {
        assertFalse(requiresExpenseReview("Café", 3_500L))
    }

    @Test
    fun `missing merchant or amount still requires review`() {
        assertTrue(requiresExpenseReview(null, 9_990L))
        assertTrue(requiresExpenseReview("", 9_990L))
        assertTrue(requiresExpenseReview("Farmacia", null))
    }
}
