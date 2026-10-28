package com.villegol.appgastos.data

import com.villegol.appgastos.ai.receipt.ReceiptPipelineRegistry
import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val amountMinor: Long?,
    val currency: String,
    val occurredAtEpochMs: Long,
    val cardLast4: String?,
    val merchantId: String?,
    val merchantName: String?,
    val categoryId: String?,
    val categoryName: String?,
    val description: String?,
    val necessity: String?,
    val status: String,
    val confidence: Float,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

@Entity(tableName = "evidence")
data class EvidenceEntity(
    @PrimaryKey val id: String,
    val type: String,
    val capturedAtEpochMs: Long,
    val rawPayload: String,
    val derivedPayload: String?,
    val confidence: Float?
)

@Entity(
    tableName = "expense_evidence_links",
    primaryKeys = ["expenseId", "evidenceId"]
)
data class ExpenseEvidenceLinkEntity(
    val expenseId: String,
    val evidenceId: String,
    val linkConfidence: Float?,
    val reason: String?,
    val active: Boolean = true
)

@Entity(tableName = "merchants")
data class MerchantEntity(
    @PrimaryKey val id: String,
    val name: String,
    val aliasesJson: String,
    val defaultCategoryId: String?
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortOrder: Int
)

@Entity(
    tableName = "expense_products",
    foreignKeys = [
        ForeignKey(
            entity = ExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["expenseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("expenseId")]
)
data class ExpenseProductEntity(
    @PrimaryKey val id: String,
    val expenseId: String,
    val name: String,
    val quantity: Double?,
    val unitPriceMinor: Long?,
    val sortOrder: Int
)

@Entity(tableName = "cards")
data class PaymentCardEntity(
    @PrimaryKey val last4: String,
    val name: String?,
    val firstSeenAtEpochMs: Long,
    val colorHex: String?,
    val type: String
)

@Entity(tableName = "merchant_rules")
data class MerchantRuleEntity(
    @PrimaryKey val id: String,
    val pattern: String,
    val merchantName: String,
    val source: String,
    val weight: Float,
    val enabled: Boolean
)

@Entity(tableName = "source_events", indices = [androidx.room.Index(value = ["eventKey"], unique = true)])
data class SourceEventEntity(
    @PrimaryKey val id: String,
    val eventKey: String,
    val expenseId: String?,
    val packageName: String,
    val notificationKey: String?,
    val postedAtEpochMs: Long,
    val title: String?,
    val text: String?,
    val subText: String?,
    val bigText: String?,
    val rawExtras: String?
)

@Entity(tableName = "receipts")
data class ReceiptEntity(
    @PrimaryKey val id: String,
    val expenseId: String,
    val imagePath: String,
    val capturedAtEpochMs: Long,
    val rawOcr: String?,
    val structuredOcr: String?,
    val processingStatus: String = ReceiptProcessingStatus.PROCESSING.name,
    val processingError: String? = null
)

@Entity(tableName = "audit_events")
data class AuditEventEntity(
    @PrimaryKey val id: String,
    val expenseId: String,
    val eventType: String,
    val beforeJson: String?,
    val afterJson: String?,
    val createdAtEpochMs: Long
)

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE status != 'DELETED' ORDER BY occurredAtEpochMs DESC")
    fun observeActive(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE status = 'PENDING_REVIEW' ORDER BY occurredAtEpochMs DESC")
    fun observePending(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses ORDER BY occurredAtEpochMs DESC")
    fun observeAll(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ExpenseEntity?

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<ExpenseEntity?>

    @Query("SELECT * FROM expenses WHERE status != 'DELETED'")
    suspend fun findActive(): List<ExpenseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: ExpenseEntity)

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Query("UPDATE expenses SET status = 'DELETED', updatedAtEpochMs = :updatedAt WHERE id = :id")
    suspend fun markDeleted(id: String, updatedAt: Long)

    @Query("UPDATE expenses SET status = :status, updatedAtEpochMs = :updatedAt WHERE id = :id")
    suspend fun setStatus(id: String, status: String, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM expenses WHERE status = 'PENDING_REVIEW'")
    fun observePendingCount(): Flow<Int>

    @Query("UPDATE expenses SET categoryName = :newName, updatedAtEpochMs = :updatedAt WHERE categoryName = :oldName")
    suspend fun renameCategory(oldName: String, newName: String, updatedAt: Long)
}

@Dao
interface EvidenceDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(evidence: EvidenceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun link(link: ExpenseEvidenceLinkEntity)

    @Query("SELECT evidence.id, evidence.type, evidence.capturedAtEpochMs, evidence.rawPayload, evidence.derivedPayload, evidence.confidence FROM evidence INNER JOIN expense_evidence_links ON evidence.id = expense_evidence_links.evidenceId WHERE expense_evidence_links.expenseId = :expenseId AND expense_evidence_links.active = 1 ORDER BY evidence.capturedAtEpochMs")
    fun observeForExpense(expenseId: String): Flow<List<EvidenceEntity>>
}

@Dao
interface SourceEventDao {
    @Query("SELECT * FROM source_events WHERE eventKey = :eventKey LIMIT 1")
    suspend fun findByEventKey(eventKey: String): SourceEventEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: SourceEventEntity)
}

@Dao
interface ReceiptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(receipt: ReceiptEntity)

    @Query("SELECT * FROM receipts WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ReceiptEntity?

    @Query("SELECT * FROM receipts WHERE expenseId = :expenseId ORDER BY capturedAtEpochMs DESC LIMIT 1")
    fun observeLatestForExpense(expenseId: String): Flow<ReceiptEntity?>

    @Query("SELECT * FROM receipts WHERE expenseId = :expenseId ORDER BY capturedAtEpochMs DESC LIMIT 1")
    suspend fun findLatestForExpense(expenseId: String): ReceiptEntity?

    @Query("UPDATE receipts SET rawOcr = :rawOcr, structuredOcr = :structuredOcr, processingStatus = 'COMPLETE', processingError = NULL WHERE id = :id")
    suspend fun updateOcr(id: String, rawOcr: String, structuredOcr: String?)

    @Query("UPDATE receipts SET structuredOcr = :structuredOcr WHERE id = :id")
    suspend fun updateStructuredOcr(id: String, structuredOcr: String)

    @Query("UPDATE receipts SET processingStatus = 'PROCESSING', processingError = NULL WHERE id = :id")
    suspend fun markProcessing(id: String)

    @Query("UPDATE receipts SET processingStatus = 'ERROR', processingError = :error WHERE id = :id")
    suspend fun markError(id: String, error: String)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: CategoryEntity)

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM categories WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun findByName(name: String): CategoryEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM categories")
    suspend fun maxSortOrder(): Int
}

@Dao
interface ExpenseProductDao {
    @Query("SELECT * FROM expense_products WHERE expenseId = :expenseId ORDER BY sortOrder")
    fun observeForExpense(expenseId: String): Flow<List<ExpenseProductEntity>>

    @Query("SELECT * FROM expense_products WHERE expenseId = :expenseId ORDER BY sortOrder")
    suspend fun findForExpense(expenseId: String): List<ExpenseProductEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(products: List<ExpenseProductEntity>)

    @Query("DELETE FROM expense_products WHERE expenseId = :expenseId")
    suspend fun deleteForExpense(expenseId: String)
}

@Dao
interface PaymentCardDao {
    @Query("SELECT * FROM cards ORDER BY last4")
    fun observeAll(): Flow<List<PaymentCardEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(card: PaymentCardEntity)

    @Query("UPDATE cards SET name = :name WHERE last4 = :last4")
    suspend fun updateName(last4: String, name: String?)

    @Query("UPDATE cards SET colorHex = :colorHex WHERE last4 = :last4")
    suspend fun updateColor(last4: String, colorHex: String?)

    @Query("UPDATE cards SET type = :type WHERE last4 = :last4")
    suspend fun updateType(last4: String, type: String)

    @Query("DELETE FROM cards WHERE last4 = :last4")
    suspend fun deleteByLast4(last4: String)
}

@Dao
interface MerchantRuleDao {
    @Query("SELECT * FROM merchant_rules WHERE enabled = 1 ORDER BY weight DESC")
    suspend fun findEnabled(): List<MerchantRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: MerchantRuleEntity)
}

@Dao
interface AuditEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: AuditEventEntity)
}

@Database(
    entities = [
        ExpenseEntity::class,
        EvidenceEntity::class,
        ExpenseEvidenceLinkEntity::class,
        MerchantEntity::class,
        CategoryEntity::class,
        ExpenseProductEntity::class,
        MerchantRuleEntity::class,
        PaymentCardEntity::class,
        SourceEventEntity::class,
        ReceiptEntity::class,
        AuditEventEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun evidenceDao(): EvidenceDao
    abstract fun sourceEventDao(): SourceEventDao
    abstract fun categoryDao(): CategoryDao
    abstract fun expenseProductDao(): ExpenseProductDao
    abstract fun paymentCardDao(): PaymentCardDao
    abstract fun merchantRuleDao(): MerchantRuleDao
    abstract fun auditEventDao(): AuditEventDao
    abstract fun receiptDao(): ReceiptDao

    companion object {
        fun create(context: Context): AppDatabase = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "gastos.db"
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build()

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS cards (last4 TEXT NOT NULL, name TEXT, firstSeenAtEpochMs INTEGER NOT NULL, PRIMARY KEY(last4))"
                )
                database.execSQL(
                    "INSERT OR IGNORE INTO cards(last4, name, firstSeenAtEpochMs) " +
                        "SELECT cardLast4, NULL, MIN(createdAtEpochMs) FROM expenses " +
                        "WHERE cardLast4 IS NOT NULL AND cardLast4 != '' GROUP BY cardLast4"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE receipts ADD COLUMN processingStatus TEXT NOT NULL DEFAULT 'PROCESSING'"
                )
                database.execSQL(
                    "ALTER TABLE receipts ADD COLUMN processingError TEXT"
                )
                database.execSQL(
                    "UPDATE receipts SET processingStatus = 'COMPLETE' WHERE rawOcr IS NOT NULL"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS expense_products (" +
                        "id TEXT NOT NULL, expenseId TEXT NOT NULL, name TEXT NOT NULL, " +
                        "quantity REAL, unitPriceMinor INTEGER, sortOrder INTEGER NOT NULL, " +
                        "PRIMARY KEY(id), FOREIGN KEY(expenseId) REFERENCES expenses(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_expense_products_expenseId ON expense_products(expenseId)"
                )
                database.execSQL(
                    "UPDATE expenses SET status = 'IDENTIFIED' " +
                        "WHERE status = 'PENDING_REVIEW' AND merchantName IS NOT NULL " +
                        "AND TRIM(merchantName) != '' AND amountMinor IS NOT NULL"
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE cards ADD COLUMN colorHex TEXT")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE cards ADD COLUMN type TEXT NOT NULL DEFAULT 'OTHER'")
            }
        }
    }
}

fun Expense.toEntity() = ExpenseEntity(
    id = id,
    amountMinor = amountMinor,
    currency = currency,
    occurredAtEpochMs = occurredAt.toEpochMilli(),
    cardLast4 = cardLast4,
    merchantId = merchantId,
    merchantName = merchantName,
    categoryId = categoryId,
    categoryName = categoryName,
    description = description,
    necessity = necessity?.name,
    status = status.name,
    confidence = confidence,
    createdAtEpochMs = createdAt.toEpochMilli(),
    updatedAtEpochMs = updatedAt.toEpochMilli()
)

fun ExpenseEntity.toDomain() = Expense(
    id = id,
    amountMinor = amountMinor,
    currency = currency,
    occurredAt = java.time.Instant.ofEpochMilli(occurredAtEpochMs),
    cardLast4 = cardLast4,
    merchantId = merchantId,
    merchantName = merchantName,
    categoryId = categoryId,
    categoryName = categoryName,
    description = description,
    necessity = necessity?.let { Necessity.valueOf(it) },
    status = ExpenseStatus.valueOf(status),
    confidence = confidence,
    createdAt = java.time.Instant.ofEpochMilli(createdAtEpochMs),
    updatedAt = java.time.Instant.ofEpochMilli(updatedAtEpochMs)
)

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val database: AppDatabase = AppDatabase.create(appContext)
    val expenseRepository = ExpenseRepository(database)
    val budgetRepository = BudgetRepository(appContext)
    val receiptPipelines = ReceiptPipelineRegistry(
        context = appContext
    )
}
