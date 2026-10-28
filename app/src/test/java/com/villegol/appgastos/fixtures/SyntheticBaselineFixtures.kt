package com.villegol.appgastos.fixtures

import com.villegol.appgastos.ai.receipt.OcrBounds
import com.villegol.appgastos.ai.receipt.OcrDocument
import com.villegol.appgastos.ai.receipt.OcrElement
import com.villegol.appgastos.ai.receipt.OcrLine
import com.villegol.appgastos.data.Category
import com.villegol.appgastos.data.Evidence
import com.villegol.appgastos.data.EvidenceType
import com.villegol.appgastos.data.Expense
import com.villegol.appgastos.data.ExpenseProduct
import com.villegol.appgastos.data.ExpenseStatus
import com.villegol.appgastos.data.NotificationSnapshot
import com.villegol.appgastos.data.PaymentCard
import com.villegol.appgastos.data.PaymentCardType
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject

/** JVM-only, invented data. Never seeds the app, Room or a connected device. */
object SyntheticBaselineFixtures {
    private val document: JSONObject by lazy {
        val stream = checkNotNull(javaClass.getResourceAsStream("/baseline/synthetic-expenses-v1.json"))
        stream.bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
    }

    val formatVersion: Int get() = document.getInt("formatVersion")
    val isSynthetic: Boolean get() = document.getBoolean("synthetic")
    val zoneId: ZoneId get() = ZoneId.of(document.getString("zoneId"))
    val month: YearMonth get() = YearMonth.parse(document.getString("month"))
    private val fixedTimestamp: Instant get() = Instant.parse(document.getString("fixedTimestamp"))

    fun expenses(): List<Expense> = document.getJSONArray("expenses").objects().map { row ->
        Expense(
            id = row.getString("id"),
            amountMinor = row.nullableLong("amountMinor"),
            currency = row.getString("currency"),
            occurredAt = Instant.parse(row.getString("occurredAt")),
            cardLast4 = row.nullableString("cardLast4"),
            merchantName = row.nullableString("merchantName"),
            categoryId = row.nullableString("categoryId"),
            categoryName = row.nullableString("categoryName"),
            status = ExpenseStatus.valueOf(row.getString("status")),
            createdAt = fixedTimestamp,
            updatedAt = fixedTimestamp
        )
    }

    fun cards(): List<PaymentCard> = document.getJSONArray("cards").objects().map { row ->
        PaymentCard(
            last4 = row.getString("last4"),
            name = row.getString("name"),
            firstSeenAt = fixedTimestamp,
            type = PaymentCardType.valueOf(row.getString("type"))
        )
    }

    fun categories(): List<Category> = document.getJSONArray("categories").objects().map {
        Category(it.getString("id"), it.getString("name"), it.getInt("sortOrder"))
    }

    fun products(): List<ExpenseProduct> = document.getJSONArray("products").objects().map {
        ExpenseProduct(
            id = it.getString("id"),
            expenseId = it.getString("expenseId"),
            name = it.getString("name"),
            quantity = it.getDouble("quantity"),
            unitPriceMinor = it.getLong("unitPriceMinor")
        )
    }

    fun evidence(): List<Pair<String, Evidence>> = document.getJSONArray("evidence").objects().map {
        it.getString("expenseId") to Evidence(
            id = it.getString("id"),
            type = EvidenceType.valueOf(it.getString("type")),
            capturedAt = Instant.parse(it.getString("capturedAt")),
            rawPayload = it.getString("rawPayload")
        )
    }

    fun walletSnapshots(): List<NotificationSnapshot> = document.getJSONArray("walletSnapshots").objects().map {
        NotificationSnapshot(
            packageName = it.getString("packageName"),
            notificationKey = it.getString("notificationKey"),
            postedAt = Instant.parse(it.getString("postedAt")),
            title = it.nullableString("title"),
            text = it.nullableString("text"),
            subText = it.nullableString("subText"),
            bigText = it.nullableString("bigText")
        )
    }

    /** Inputs reserved for F1.3, without asserting future matcher behavior. */
    fun futureMatchCandidates(): List<Pair<Expense, Instant>> {
        val expensesById = expenses().associateBy { it.id }
        return document.getJSONArray("futureMatchCandidates").objects().map {
            val expense = expensesById.getValue(it.getString("sourceExpenseId"))
            expense to expense.occurredAt.plusSeconds(it.getLong("offsetSeconds"))
        }
    }

    fun receiptOcr(): OcrDocument {
        val receipt = document.getJSONObject("receiptOcr")
        val rows = receipt.getJSONArray("rows")
        val lines = (0 until rows.length()).map { index ->
            val row = rows.getJSONArray(index)
            val top = 10 + index * 30
            val elements = (0 until row.length()).map { elementIndex ->
                val token = row.getJSONArray(elementIndex)
                val text = token.getString(0)
                val left = token.getInt(1)
                OcrElement(
                    text = text,
                    bounds = OcrBounds(left, top, left + text.length * 8, top + 16),
                    confidence = 0.99f,
                    blockIndex = 0,
                    lineIndex = index,
                    elementIndex = elementIndex,
                    order = index * 10 + elementIndex
                )
            }
            OcrLine(
                text = elements.joinToString(" ") { it.text },
                bounds = OcrBounds(elements.minOf { it.bounds!!.left }, top, elements.maxOf { it.bounds!!.right }, top + 16),
                confidence = 0.99f,
                blockIndex = 0,
                lineIndex = index,
                elements = elements,
                order = index
            )
        }
        return OcrDocument.fromLines(lines, engine = receipt.getString("engine"))
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)
    private fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else getString(key)
    private fun JSONObject.nullableLong(key: String): Long? = if (isNull(key)) null else getLong(key)
}
