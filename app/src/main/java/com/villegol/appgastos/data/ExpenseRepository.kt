package com.villegol.appgastos.data

import com.villegol.appgastos.ai.receipt.StructuredReceipt
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID

class ExpenseRepository(private val database: AppDatabase) {
    private val expenses = database.expenseDao()
    private val evidence = database.evidenceDao()
    private val sourceEvents = database.sourceEventDao()
    private val categories = database.categoryDao()
    private val paymentCards = database.paymentCardDao()
    private val products = database.expenseProductDao()

    fun observeActive(): Flow<List<Expense>> = expenses.observeActive().map { rows -> rows.map { it.toDomain() } }
    fun observeAll(): Flow<List<Expense>> = expenses.observeAll().map { rows -> rows.map { it.toDomain() } }
    fun observeExpense(id: String): Flow<Expense?> = expenses.observeById(id).map { it?.toDomain() }
    fun observePending(): Flow<List<Expense>> = expenses.observePending().map { rows -> rows.map { it.toDomain() } }
    fun observePendingCount(): Flow<Int> = expenses.observePendingCount()
    fun observeEvidence(expenseId: String): Flow<List<Evidence>> = evidence.observeForExpense(expenseId).map { rows ->
        rows.map {
            Evidence(
                id = it.id,
                type = EvidenceType.valueOf(it.type),
                capturedAt = Instant.ofEpochMilli(it.capturedAtEpochMs),
                rawPayload = it.rawPayload,
                derivedPayload = it.derivedPayload,
                confidence = it.confidence
            )
        }
    }
    fun observeCategories(): Flow<List<Category>> = categories.observeAll().map { rows ->
        rows.map { Category(it.id, it.name, it.sortOrder) }
    }
    fun observeProducts(expenseId: String): Flow<List<ExpenseProduct>> =
        products.observeForExpense(expenseId).map { rows -> rows.map { it.toDomain() } }
    fun observePaymentCards(): Flow<List<PaymentCard>> = paymentCards.observeAll().map { rows ->
        rows.map {
            PaymentCard(
                last4 = it.last4,
                name = it.name,
                firstSeenAt = Instant.ofEpochMilli(it.firstSeenAtEpochMs),
                colorHex = it.colorHex,
                type = runCatching { PaymentCardType.valueOf(it.type) }.getOrDefault(PaymentCardType.OTHER)
            )
        }
    }

    suspend fun updatePaymentCard(
        last4: String,
        name: String?,
        colorHex: String?,
        type: PaymentCardType = PaymentCardType.OTHER
    ) {
        normalizeCardLast4(last4)?.let { normalized ->
            database.withTransaction {
                paymentCards.updateName(normalized, name?.trim()?.takeIf { it.isNotEmpty() })
                paymentCards.updateColor(normalized, normalizeColorHex(colorHex))
                paymentCards.updateType(normalized, type.name)
            }
        }
    }

    suspend fun savePaymentCard(
        last4: String,
        name: String?,
        colorHex: String?,
        type: PaymentCardType = PaymentCardType.OTHER
    ) {
        val normalized = normalizeCardLast4(last4)
            ?: error("Ingresa exactamente los últimos 4 dígitos de la tarjeta.")
        val now = Instant.now()
        database.withTransaction {
            paymentCards.insertIfMissing(
                PaymentCardEntity(
                    last4 = normalized,
                    name = null,
                    firstSeenAtEpochMs = now.toEpochMilli(),
                    colorHex = null,
                    type = PaymentCardType.OTHER.name
                )
            )
            paymentCards.updateName(normalized, name?.trim()?.takeIf { it.isNotEmpty() })
            paymentCards.updateColor(normalized, normalizeColorHex(colorHex))
            paymentCards.updateType(normalized, type.name)
        }
    }

    suspend fun ensurePaymentCard(last4: String): String? {
        val normalized = normalizeCardLast4(last4) ?: return null
        paymentCards.insertIfMissing(
            PaymentCardEntity(
                last4 = normalized,
                name = null,
                firstSeenAtEpochMs = Instant.now().toEpochMilli(),
                colorHex = null,
                type = PaymentCardType.OTHER.name
            )
        )
        return normalized
    }

    suspend fun deletePaymentCard(last4: String) {
        normalizeCardLast4(last4)?.let { paymentCards.deleteByLast4(it) }
    }

    suspend fun addCategory(name: String) {
        val normalized = validateCategoryName(name)
        check(categories.findByName(normalized) == null) { "Ya existe una categoría con ese nombre." }
        categories.insert(
            CategoryEntity(
                id = UUID.randomUUID().toString(),
                name = normalized,
                sortOrder = categories.maxSortOrder() + 1
            )
        )
    }

    suspend fun renameCategory(category: Category, name: String) {
        val normalized = validateCategoryName(name)
        val existing = categories.findByName(normalized)
        check(existing == null || existing.id == category.id) { "Ya existe una categoría con ese nombre." }
        database.withTransaction {
            categories.update(CategoryEntity(category.id, normalized, category.sortOrder))
            expenses.renameCategory(category.name, normalized, Instant.now().toEpochMilli())
        }
    }

    suspend fun deleteCategory(category: Category) {
        categories.deleteById(category.id)
    }

    suspend fun replaceProducts(expenseId: String, values: List<ExpenseProduct>) {
        database.withTransaction {
            products.deleteForExpense(expenseId)
            products.insertAll(values.mapIndexedNotNull { index, product ->
                product.name.trim().takeIf { it.isNotEmpty() }?.let { name ->
                    ExpenseProductEntity(
                        id = product.id,
                        expenseId = expenseId,
                        name = name,
                        quantity = product.quantity,
                        unitPriceMinor = product.unitPriceMinor,
                        sortOrder = index
                    )
                }
            })
        }
    }

    suspend fun getProducts(expenseId: String): List<ExpenseProduct> =
        products.findForExpense(expenseId).map { it.toDomain() }

    suspend fun get(id: String): Expense? = expenses.findById(id)?.toDomain()

    suspend fun createManual(
        amountMinor: Long?,
        currency: String,
        merchantName: String?,
        categoryName: String?,
        description: String?,
        occurredAt: Instant = Instant.now(),
        cardLast4: String? = null,
        products: List<ExpenseProduct> = emptyList()
    ): Expense {
        val now = Instant.now()
        val normalizedCardLast4 = cardLast4?.let(::normalizeCardLast4)
        val expense = Expense(
            amountMinor = amountMinor,
            currency = currency,
            occurredAt = occurredAt,
            cardLast4 = normalizedCardLast4,
            merchantName = merchantName?.takeIf { it.isNotBlank() },
            categoryName = categoryName?.takeIf { it.isNotBlank() },
            description = description?.takeIf { it.isNotBlank() },
            status = if (requiresExpenseReview(merchantName, amountMinor)) {
                ExpenseStatus.PENDING_REVIEW
            } else {
                ExpenseStatus.IDENTIFIED
            },
            confidence = if (requiresExpenseReview(merchantName, amountMinor)) 0f else 1f,
            createdAt = now,
            updatedAt = now
        )
        database.withTransaction {
            normalizedCardLast4?.let { last4 ->
                paymentCards.insertIfMissing(
                    PaymentCardEntity(
                        last4 = last4,
                        name = null,
                        firstSeenAtEpochMs = now.toEpochMilli(),
                        colorHex = null,
                        type = PaymentCardType.OTHER.name
                    )
                )
            }
            expenses.insert(expense.toEntity())
            this@ExpenseRepository.products.insertAll(products.mapIndexedNotNull { index, product ->
                product.name.trim().takeIf { it.isNotEmpty() }?.let { name ->
                    ExpenseProductEntity(
                        id = product.id,
                        expenseId = expense.id,
                        name = name,
                        quantity = product.quantity,
                        unitPriceMinor = product.unitPriceMinor,
                        sortOrder = index
                    )
                }
            })
            database.auditEventDao().insert(
                AuditEventEntity(
                    id = UUID.randomUUID().toString(),
                    expenseId = expense.id,
                    eventType = "CREATED_MANUALLY",
                    beforeJson = null,
                    afterJson = expense.toString(),
                    createdAtEpochMs = now.toEpochMilli()
                )
            )
        }
        return expense
    }

    suspend fun update(expense: Expense) {
        val before = get(expense.id)
        val updated = expense.copy(updatedAt = Instant.now())
        database.withTransaction {
            expenses.update(updated.toEntity())
            database.auditEventDao().insert(
                AuditEventEntity(
                    id = UUID.randomUUID().toString(),
                    expenseId = updated.id,
                    eventType = "UPDATED",
                    beforeJson = before?.toString(),
                    afterJson = updated.toString(),
                    createdAtEpochMs = updated.updatedAt.toEpochMilli()
                )
            )
        }
    }

    suspend fun recordManualCorrection(expense: Expense, correction: String) {
        update(expense)
        attachEvidence(
            expense.id,
            Evidence(
                type = EvidenceType.MANUAL_CORRECTION,
                rawPayload = correction,
                derivedPayload = expense.merchantName,
                confidence = 1f
            ),
            reason = "Corrección explícita del usuario"
        )
    }

    suspend fun enrichExpense(expenseId: String) {
        val current = get(expenseId) ?: return
        val result = com.villegol.appgastos.domain.DefaultInferenceEngine().infer(current.merchantName, current.categoryName)
        update(
            current.copy(
                merchantName = result.merchantName,
                categoryName = result.categoryName,
                status = if (requiresExpenseReview(result.merchantName, current.amountMinor)) {
                    ExpenseStatus.PENDING_REVIEW
                } else {
                    ExpenseStatus.IDENTIFIED
                },
                confidence = result.confidence
            )
        )
    }

    suspend fun delete(id: String) = expenses.markDeleted(id, Instant.now().toEpochMilli())

    suspend fun restore(id: String) = expenses.setStatus(id, ExpenseStatus.IDENTIFIED.name, Instant.now().toEpochMilli())

    suspend fun confirmPending(id: String) {
        val before = get(id) ?: return
        if (before.status != ExpenseStatus.PENDING_REVIEW) return
        val now = Instant.now()
        val after = before.copy(status = ExpenseStatus.IDENTIFIED, updatedAt = now)
        database.withTransaction {
            expenses.setStatus(id, ExpenseStatus.IDENTIFIED.name, now.toEpochMilli())
            database.auditEventDao().insert(
                AuditEventEntity(
                    id = UUID.randomUUID().toString(),
                    expenseId = id,
                    eventType = "CONFIRMED_BY_USER",
                    beforeJson = before.toString(),
                    afterJson = after.toString(),
                    createdAtEpochMs = now.toEpochMilli()
                )
            )
        }
    }

    suspend fun attachEvidence(expenseId: String, item: Evidence, reason: String? = null) {
        database.withTransaction {
            evidence.insert(
                EvidenceEntity(
                    id = item.id,
                    type = item.type.name,
                    capturedAtEpochMs = item.capturedAt.toEpochMilli(),
                    rawPayload = item.rawPayload,
                    derivedPayload = item.derivedPayload,
                    confidence = item.confidence
                )
            )
            evidence.link(
                ExpenseEvidenceLinkEntity(
                    expenseId = expenseId,
                    evidenceId = item.id,
                    linkConfidence = item.confidence,
                    reason = reason
                )
            )
        }
    }

    suspend fun saveReceipt(expenseId: String, imagePath: String): String {
        val receipt = Receipt(id = UUID.randomUUID().toString(), expenseId = expenseId, imagePath = imagePath)
        database.receiptDao().insert(
            ReceiptEntity(
                id = receipt.id,
                expenseId = receipt.expenseId,
                imagePath = receipt.imagePath,
                capturedAtEpochMs = receipt.capturedAt.toEpochMilli(),
                rawOcr = null,
                structuredOcr = null,
                processingStatus = ReceiptProcessingStatus.PROCESSING.name,
                processingError = null
            )
        )
        attachEvidence(
            expenseId,
            Evidence(
                type = EvidenceType.RECEIPT,
                capturedAt = receipt.capturedAt,
                rawPayload = imagePath,
                confidence = 1f
            ),
            reason = "Boleta capturada para el gasto"
        )
        return receipt.id
    }

    suspend fun updateReceiptOcr(receiptId: String, rawOcr: String, structuredOcr: String? = null) {
        database.receiptDao().updateOcr(receiptId, rawOcr, structuredOcr)
    }

    suspend fun markReceiptProcessing(receiptId: String) {
        database.receiptDao().markProcessing(receiptId)
    }

    suspend fun markReceiptError(receiptId: String, error: String) {
        database.receiptDao().markError(receiptId, error.take(500))
    }

    suspend fun updateReceiptStructuredOcr(receiptId: String, structuredOcr: String) {
        database.receiptDao().updateStructuredOcr(receiptId, structuredOcr)
    }

    /**
     * Completes a placeholder/manual expense with receipt data without
     * overwriting values already supplied by Wallet or by the user.
     */
    suspend fun applyReceiptResult(expenseId: String, receipt: StructuredReceipt, confidence: Float?) {
        val current = get(expenseId) ?: return
        val merchant = current.merchantName?.takeIf { it.isNotBlank() } ?: receipt.merchantName
        val amount = current.amountMinor ?: receipt.totalMinor
        val receiptCategory = receipt.category?.category
            ?.takeIf { categories.findByName(it) != null }
        val category = current.categoryName?.takeIf { it.isNotBlank() } ?: receiptCategory
        val currency = if (current.amountMinor == null) receipt.currency else current.currency
        val requiresReview = requiresExpenseReview(merchant, amount)
        update(
            current.copy(
                amountMinor = amount,
                currency = currency,
                merchantName = merchant,
                categoryName = category,
                status = if (requiresReview) ExpenseStatus.PENDING_REVIEW else ExpenseStatus.IDENTIFIED,
                confidence = maxOf(current.confidence, confidence ?: 0f)
            )
        )
        if (getProducts(expenseId).isEmpty() && receipt.items.isNotEmpty()) {
            replaceProducts(
                expenseId,
                receipt.items.map { item ->
                    ExpenseProduct(
                        expenseId = expenseId,
                        name = item.description,
                        quantity = item.quantity?.toDouble(),
                        unitPriceMinor = item.unitPriceMinor
                            ?: item.lineTotalMinor?.let { total ->
                                item.quantity?.takeIf { it > 0 }?.let { total / it }
                            }
                    )
                }
            )
        }
    }

    fun observeReceipt(expenseId: String): Flow<Receipt?> = database.receiptDao()
        .observeLatestForExpense(expenseId)
        .map { it?.toDomain() }

    suspend fun getReceipt(receiptId: String): Receipt? = database.receiptDao().findById(receiptId)?.toDomain()

    suspend fun getLatestReceipt(expenseId: String): Receipt? =
        database.receiptDao().findLatestForExpense(expenseId)?.toDomain()

    private fun ReceiptEntity.toDomain() = Receipt(
        id = id,
        expenseId = expenseId,
        imagePath = imagePath,
        capturedAt = Instant.ofEpochMilli(capturedAtEpochMs),
        rawOcr = rawOcr,
        structuredOcr = structuredOcr,
        processingStatus = runCatching { ReceiptProcessingStatus.valueOf(processingStatus) }
            .getOrDefault(ReceiptProcessingStatus.PROCESSING),
        processingError = processingError
    )

    suspend fun attachLocationEvidence(expenseId: String, latitude: Double, longitude: Double, accuracy: Float, timestamp: Instant) {
        attachEvidence(
            expenseId,
            Evidence(
                type = EvidenceType.LOCATION,
                capturedAt = timestamp,
                rawPayload = "lat=$latitude,lon=$longitude,accuracy=$accuracy,timestamp=${timestamp.toEpochMilli()}",
                confidence = (1f / (1f + accuracy / 50f)).coerceIn(0.05f, 1f)
            ),
            reason = "Ubicación puntual del evento; no se usa para aprender sucursales"
        )
    }

    suspend fun ingestWalletSnapshot(snapshot: NotificationSnapshot): Expense? {
        val parser = com.villegol.appgastos.domain.WalletNotificationParser()
        val parsed = parser.parse(snapshot)
        val now = Instant.now()
        val candidate = ExpenseCandidate(
            amountMinor = parsed.amountMinor,
            currency = parsed.currency,
            occurredAt = snapshot.postedAt,
            merchantText = parsed.merchantText,
            cardLast4 = parsed.cardLast4,
            sourceEventKey = snapshot.eventKey
        )
        val expense = Expense(
            amountMinor = parsed.amountMinor,
            currency = parsed.currency,
            occurredAt = snapshot.postedAt,
            cardLast4 = parsed.cardLast4,
            merchantName = parsed.merchantText?.takeIf { it.isNotBlank() },
            status = if (parsed.amountMinor != null && !parsed.merchantText.isNullOrBlank()) {
                ExpenseStatus.ENRICHING
            } else {
                ExpenseStatus.PENDING_REVIEW
            },
            confidence = if (parsed.amountMinor != null && !parsed.merchantText.isNullOrBlank()) 0.75f else 0.2f,
            createdAt = now,
            updatedAt = now
        )
        val source = SourceEventEntity(
            id = UUID.randomUUID().toString(),
            eventKey = snapshot.eventKey,
            expenseId = expense.id,
            packageName = snapshot.packageName,
            notificationKey = snapshot.notificationKey,
            postedAtEpochMs = snapshot.postedAt.toEpochMilli(),
            title = snapshot.title,
            text = snapshot.text,
            subText = snapshot.subText,
            bigText = snapshot.bigText,
            rawExtras = snapshot.rawExtras
        )
        val walletEvidence = Evidence(
            type = EvidenceType.WALLET_NOTIFICATION,
            capturedAt = snapshot.postedAt,
            rawPayload = snapshot.rawText,
            derivedPayload = parsed.toString(),
            confidence = expense.confidence
        )

        return database.withTransaction {
            if (sourceEvents.findByEventKey(snapshot.eventKey) != null) return@withTransaction null

            parsed.cardLast4?.takeIf { it.isNotBlank() }?.let { last4 ->
                paymentCards.insertIfMissing(
                    PaymentCardEntity(
                        last4 = last4,
                        name = null,
                        firstSeenAtEpochMs = now.toEpochMilli(),
                        colorHex = null,
                        type = PaymentCardType.OTHER.name
                    )
                )
            }

            val active = expenses.findActive().map { it.toDomain() }
            val duplicate = com.villegol.appgastos.domain.DuplicateMatcher()
                .findCandidates(candidate, active)
                .firstOrNull()
            val autoMatch = duplicate?.takeIf { com.villegol.appgastos.domain.DuplicateMatcher().shouldAutoAttach(it) }
            val target = if (autoMatch != null) {
                autoMatch.expense
            } else {
                val created = if (duplicate != null) {
                    expense.copy(status = ExpenseStatus.PENDING_REVIEW, confidence = 0.4f)
                } else expense
                expenses.insert(created.toEntity())
                created
            }

            sourceEvents.insert(source.copy(expenseId = target.id))
            evidence.insert(
                EvidenceEntity(
                    id = walletEvidence.id,
                    type = walletEvidence.type.name,
                    capturedAtEpochMs = walletEvidence.capturedAt.toEpochMilli(),
                    rawPayload = walletEvidence.rawPayload,
                    derivedPayload = walletEvidence.derivedPayload,
                    confidence = walletEvidence.confidence
                )
            )
            evidence.link(
                ExpenseEvidenceLinkEntity(
                    expenseId = target.id,
                    evidenceId = walletEvidence.id,
                    linkConfidence = duplicate?.score?.div(100f) ?: expense.confidence,
                    reason = when {
                        autoMatch != null -> "Evidencia Wallet fusionada: ${autoMatch.reasons.joinToString()}"
                        duplicate != null -> "Posible duplicado pendiente de revisión: ${duplicate.reasons.joinToString()}"
                        else -> "Wallet notification source"
                    }
                )
            )
            target
        }
    }

    suspend fun seedCategories() {
        if (categories.count() > 0) return
        val names = listOf(
            "Alimentación", "Supermercado", "Restaurantes", "Transporte",
            "Estacionamiento", "Salud", "Ropa", "Tecnología", "Entretenimiento",
            "Universidad", "Suscripciones", "Servicios", "Hogar", "Viajes", "Regalos", "Otros"
        )
        categories.insertAll(names.mapIndexed { index, name ->
            CategoryEntity(id = name.lowercase().replace("[^a-záéíóúñ]+".toRegex(), "-"), name = name, sortOrder = index)
        })
    }

    private fun normalizeCardLast4(value: String): String? = value
        .filter(Char::isDigit)
        .takeLast(4)
        .takeIf { it.length == 4 }

    private fun normalizeColorHex(value: String?): String? {
        val normalized = value?.trim()?.uppercase()?.let { if (it.startsWith("#")) it else "#$it" }
        return normalized?.takeIf { it.matches(Regex("#[0-9A-F]{6}")) }
    }

    private fun validateCategoryName(value: String): String {
        val normalized = value.trim().replace(Regex("\\s+"), " ")
        require(normalized.isNotEmpty()) { "El nombre de la categoría no puede estar vacío." }
        require(normalized.length <= 40) { "Usa un nombre de hasta 40 caracteres." }
        return normalized
    }

    private fun ExpenseProductEntity.toDomain() = ExpenseProduct(
        id = id,
        expenseId = expenseId,
        name = name,
        quantity = quantity,
        unitPriceMinor = unitPriceMinor
    )
}
