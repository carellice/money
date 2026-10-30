package com.money.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TxType { EXPENSE, INCOME }

enum class PaymentMethod(val label: String, val icon: String) {
    CARD("Carta", "card"),
    DEBIT("Bancomat", "atm"),
    CASH("Contanti", "cash"),
    TRANSFER("Bonifico", "bank"),
    PAYPAL("PayPal", "wallet"),
    APP("App / Satispay", "phone"),
    OTHER("Altro", "more"),
}

enum class Frequency(val label: String, val short: String, val months: Int, val weeks: Int = 0) {
    WEEKLY("Ogni settimana", "/sett", 0, 1),
    MONTHLY("Ogni mese", "/mese", 1),
    BIMONTHLY("Ogni 2 mesi", "/2 mesi", 2),
    QUARTERLY("Ogni 3 mesi", "/3 mesi", 3),
    SEMIANNUAL("Ogni 6 mesi", "/6 mesi", 6),
    YEARLY("Ogni anno", "/anno", 12),
    BIENNIAL("Ogni 2 anni", "/2 anni", 24);

    /** Average cost per month of something billed [cents] at this frequency. */
    fun monthlyCents(cents: Long): Long =
        if (weeks > 0) cents * 52 / 12 else cents / months
}

enum class RecurringKind { SUBSCRIPTION, FIXED }

@Entity(tableName = "categories", indices = [Index("parentId")])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    /** ARGB color. */
    val color: Long,
    val type: TxType,
    /** Set for subcategories; null for top level categories. */
    val parentId: Long? = null,
    /** Monthly budget, only for top level expense categories. */
    val budgetCents: Long? = null,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "transactions",
    indices = [Index("date"), Index("categoryId"), Index("recurringId"), Index("memberId")],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TxType,
    val amountCents: Long,
    /** LocalDate.toEpochDay(). */
    val date: Long,
    val categoryId: Long,
    val subcategoryId: Long? = null,
    val payment: PaymentMethod = PaymentMethod.CARD,
    val note: String = "",
    val receiptPath: String? = null,
    /** Set when generated automatically from a fixed expense. */
    val recurringId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** Person of the household the movement belongs to; null when no one was chosen. */
    val memberId: Long? = null,
)

/** Person of the household, used only to tell whose a movement is. All data stays shared. */
@Entity(tableName = "members")
data class MemberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** ARGB color. */
    val color: Long,
    val sortOrder: Int = 0,
)

@Entity(tableName = "recurring")
data class RecurringEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: RecurringKind,
    val name: String,
    val amountCents: Long,
    val categoryId: Long,
    val subcategoryId: Long? = null,
    val payment: PaymentMethod = PaymentMethod.CARD,
    val frequency: Frequency = Frequency.MONTHLY,
    /** First billing date (epoch day); every occurrence is computed from it. */
    val startDate: Long,
    /** Next billing date not yet processed (epoch day). */
    val nextDate: Long,
    /** Key from the brand catalog, if a preset logo was chosen. */
    val brandKey: String? = null,
    /** Custom logo stored in app files, wins over [brandKey]. */
    val imagePath: String? = null,
    val icon: String = "repeat",
    val color: Long,
    /** Register the expense automatically on the billing date. */
    val autoRegister: Boolean = true,
    /** Days before billing to notify; negative disables the reminder. */
    val remindDaysBefore: Int = 2,
    /** Billing date (epoch day) already notified, to avoid duplicates. */
    val notifiedFor: Long? = null,
    val active: Boolean = true,
    val note: String = "",
)
