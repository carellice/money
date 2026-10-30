package com.money.app.data

import androidx.room.withTransaction
import com.money.app.domain.Recurrence
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

class MoneyRepository(private val db: MoneyDatabase, private val filesDir: File) {
    private val categoryDao = db.categories()
    private val txDao = db.transactions()
    private val recurringDao = db.recurring()
    private val memberDao = db.members()

    val categories: Flow<List<CategoryEntity>> = categoryDao.observeAll()
    val recurring: Flow<List<RecurringEntity>> = recurringDao.observeAll()
    val members: Flow<List<MemberEntity>> = memberDao.observeAll()

    fun transactionsIn(month: YearMonth): Flow<List<TransactionEntity>> =
        transactionsBetween(month.atDay(1), month.atEndOfMonth())

    fun transactionsBetween(from: LocalDate, to: LocalDate): Flow<List<TransactionEntity>> =
        txDao.observeRange(from.toEpochDay(), to.toEpochDay())

    fun recentTransactions(limit: Int): Flow<List<TransactionEntity>> = txDao.observeRecent(limit)

    suspend fun transactionsBetweenOnce(from: LocalDate, to: LocalDate): List<TransactionEntity> =
        txDao.range(from.toEpochDay(), to.toEpochDay())

    suspend fun allCategories(): List<CategoryEntity> = categoryDao.all()
    suspend fun transaction(id: Long) = txDao.get(id)
    suspend fun recurringItem(id: Long) = recurringDao.get(id)

    suspend fun saveTransaction(tx: TransactionEntity): Long = txDao.upsert(tx)

    suspend fun deleteTransaction(tx: TransactionEntity) {
        txDao.delete(tx)
    }

    /** Called when an undo window has passed and the movement is gone for good. */
    fun discardReceipt(tx: TransactionEntity) {
        tx.receiptPath?.let { deleteImage(it) }
    }

    suspend fun saveRecurring(item: RecurringEntity): Long {
        val id = recurringDao.upsert(item)
        return if (item.id == 0L) id else item.id
    }

    suspend fun deleteRecurring(item: RecurringEntity) {
        recurringDao.delete(item)
        item.imagePath?.let { deleteImage(it) }
    }

    /** Saves a top level category with its subcategories (missing ones are removed). */
    suspend fun saveCategory(category: CategoryEntity, subs: List<CategoryEntity>) = db.withTransaction {
        val id = if (category.id == 0L) categoryDao.insert(category) else category.also { categoryDao.upsert(it) }.id
        val existing = categoryDao.all().filter { it.parentId == id }
        val keep = subs.filter { it.id != 0L }.map { it.id }.toSet()
        existing.filter { it.id !in keep }.forEach {
            txDao.clearSubcategory(it.id)
            categoryDao.delete(it)
        }
        subs.forEachIndexed { index, sub ->
            categoryDao.upsert(
                sub.copy(parentId = id, type = category.type, color = category.color, icon = category.icon, sortOrder = index)
            )
        }
    }

    /** Returns false when the category is still used by movements or fixed expenses. */
    suspend fun deleteCategory(category: CategoryEntity): Boolean = db.withTransaction {
        if (txDao.countForCategory(category.id) > 0 || recurringDao.countForCategory(category.id) > 0) {
            return@withTransaction false
        }
        categoryDao.all().filter { it.parentId == category.id }.forEach { txDao.clearSubcategory(it.id) }
        categoryDao.deleteChildren(category.id)
        categoryDao.delete(category)
        true
    }

    suspend fun saveMember(member: MemberEntity) {
        memberDao.upsert(if (member.id == 0L) member.copy(sortOrder = memberDao.all().size) else member)
    }

    /** Movements of the removed person stay, just without an owner. */
    suspend fun deleteMember(member: MemberEntity) = db.withTransaction {
        txDao.clearMember(member.id)
        memberDao.delete(member)
    }

    suspend fun seedIfEmpty() {
        if (categoryDao.count() > 0) return
        db.withTransaction {
            for ((type, list) in listOf(TxType.EXPENSE to defaultExpenseCategories, TxType.INCOME to defaultIncomeCategories)) {
                list.forEachIndexed { index, def ->
                    val id = categoryDao.insert(
                        CategoryEntity(name = def.name, icon = def.icon, color = def.color, type = type, sortOrder = index)
                    )
                    categoryDao.insertAll(def.subs.mapIndexed { i, sub ->
                        CategoryEntity(name = sub, icon = def.icon, color = def.color, type = type, parentId = id, sortOrder = i)
                    })
                }
            }
        }
    }

    /**
     * Registers due fixed expenses up to [today] and advances their next date.
     * Returns the items whose renewal reminder should be shown now.
     */
    suspend fun processRecurring(today: LocalDate = LocalDate.now()): List<RecurringEntity> = db.withTransaction {
        val todayEpoch = today.toEpochDay()
        val reminders = mutableListOf<RecurringEntity>()
        for (original in recurringDao.active()) {
            var next = original.nextDate
            var guard = 0
            while (next <= todayEpoch && guard++ < 400) {
                if (original.autoRegister) {
                    txDao.upsert(
                        TransactionEntity(
                            type = TxType.EXPENSE,
                            amountCents = original.amountCents,
                            date = next,
                            categoryId = original.categoryId,
                            subcategoryId = original.subcategoryId,
                            payment = original.payment,
                            note = original.name,
                            recurringId = original.id,
                        )
                    )
                }
                next = Recurrence.nextAfter(original.startDate, original.frequency, next)
            }
            var item = original.copy(nextDate = next)
            if (item.remindDaysBefore >= 0 && next - todayEpoch <= item.remindDaysBefore && item.notifiedFor != next) {
                reminders += item
                item = item.copy(notifiedFor = next)
            }
            if (item != original) recurringDao.update(item)
        }
        reminders
    }

    // --- images -------------------------------------------------------------------------------

    val imagesDir: File get() = File(filesDir, "images").apply { mkdirs() }

    fun newImageFile(prefix: String): File = File(imagesDir, "${prefix}_${System.currentTimeMillis()}.jpg")

    fun deleteImage(path: String) {
        val file = File(path)
        if (file.parentFile == imagesDir) file.delete()
    }

    // --- backup -------------------------------------------------------------------------------

    data class Snapshot(
        val categories: List<CategoryEntity>,
        val transactions: List<TransactionEntity>,
        val recurring: List<RecurringEntity>,
        val members: List<MemberEntity>,
    )

    suspend fun snapshot() = Snapshot(categoryDao.all(), txDao.all(), recurringDao.all(), memberDao.all())

    suspend fun replaceAll(snapshot: Snapshot) = db.withTransaction {
        txDao.clear()
        recurringDao.clear()
        categoryDao.clear()
        memberDao.clear()
        categoryDao.insertAll(snapshot.categories)
        memberDao.insertAll(snapshot.members)
        txDao.insertAll(snapshot.transactions)
        recurringDao.insertAll(snapshot.recurring)
    }
}
