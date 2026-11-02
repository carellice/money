package com.money.app.data

import androidx.room.ColumnInfo
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

    /** Cost over a year of something billed [cents] at this frequency. */
    fun yearlyCents(cents: Long): Long =
        if (weeks > 0) cents * 52 else cents * 12 / months
}

/** Order of the fixed expenses list; paused ones always go last. */
enum class RecurringSort(val label: String) {
    NEXT("Prossimo addebito"),
    NAME("Nome"),
    COST_HIGH("Costo più alto"),
    COST_LOW("Costo più basso"),
    PAYMENT("Metodo di pagamento"),
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
    /** Custom payment method; when set, [payment] is [PaymentMethod.OTHER]. */
    val paymentId: Long? = null,
    /** How the expense counts when settling up between people: one of [Settle]. */
    @ColumnInfo(defaultValue = "0") val settle: Int = Settle.OPEN,
)

/** States of [TransactionEntity.settle]. */
object Settle {
    /** Counts the next time people settle up. */
    const val OPEN = 0
    /** Never counts: the payer keeps it for themselves. */
    const val EXCLUDED = 1
    /** Already settled up: counted once, not again. */
    const val SETTLED = 2
}

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
    /** Custom payment method; when set, [payment] is [PaymentMethod.OTHER]. */
    val paymentId: Long? = null,
)

/** Payment method added by the user (a specific card, a voucher...), next to the built-in ones. */
@Entity(tableName = "payment_methods")
data class CustomPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    /** ARGB color. */
    val color: Long,
    val sortOrder: Int = 0,
)

/**
 * What something was paid with: a built-in [method], or the custom one [customId]
 * (stored as [PaymentMethod.OTHER], so versions without custom methods still read it).
 */
data class PaymentChoice(val method: PaymentMethod, val customId: Long? = null) {
    companion object {
        fun custom(id: Long) = PaymentChoice(PaymentMethod.OTHER, id)
    }
}

val TransactionEntity.paymentChoice get() = PaymentChoice(payment, paymentId)
val RecurringEntity.paymentChoice get() = PaymentChoice(payment, paymentId)

/** Names and icons of every payment method, custom ones included. */
class Payments(custom: List<CustomPaymentEntity>) {
    val custom: Map<Long, CustomPaymentEntity> = custom.associateBy { it.id }

    fun label(choice: PaymentChoice): String =
        choice.customId?.let { custom[it]?.name } ?: choice.method.label

    fun icon(choice: PaymentChoice): String =
        choice.customId?.let { custom[it]?.icon } ?: choice.method.icon

    fun label(tx: TransactionEntity) = label(tx.paymentChoice)

    companion object {
        val builtIn = Payments(emptyList())
    }
}
