package com.money.app.data

import androidx.room.withTransaction
import com.money.app.domain.Recurrence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

class MoneyRepository(
    private val db: MoneyDatabase,
    private val filesDir: File,
    serverStore: ServerStore,
    private val scope: CoroutineScope,
) {
    private val categoryDao = db.categories()
    private val txDao = db.transactions()
    private val recurringDao = db.recurring()
    private val memberDao = db.members()
    private val paymentDao = db.payments()

    /** Server mode (data on the CasaOS server, as for the PWA); off keeps everything on the phone. */
    val server = ServerSync(db, serverStore) { imagesDir }

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** Changes the server refused or couldn't receive, shown as a message. */
    val errors: SharedFlow<String> = _errors

    /** Runs [remote] in server mode, [local] otherwise; a failed remote change returns [failed]. */
    private suspend fun <T> either(failed: T, remote: suspend ServerSync.() -> T, local: suspend () -> T): T {
        if (!server.enabled) return local()
        return try {
            server.remote()
        } catch (e: Exception) {
            _errors.tryEmit("Modifica non salvata: ${e.message ?: "errore del server"}")
            failed
        }
    }

    /** Reloads the local copy from the server; does nothing on the phone only. */
    suspend fun refresh(force: Boolean = false) {
        if (server.enabled) runCatching { server.pull(force) }
    }

    val categories: Flow<List<CategoryEntity>> = categoryDao.observeAll()
    val recurring: Flow<List<RecurringEntity>> = recurringDao.observeAll()
    val members: Flow<List<MemberEntity>> = memberDao.observeAll()
    val customPayments: Flow<List<CustomPaymentEntity>> = paymentDao.observeAll()

    fun transactionsIn(month: YearMonth): Flow<List<TransactionEntity>> =
        transactionsBetween(month.atDay(1), month.atEndOfMonth())

    fun transactionsBetween(from: LocalDate, to: LocalDate): Flow<List<TransactionEntity>> =
        txDao.observeRange(from.toEpochDay(), to.toEpochDay())

    fun recentTransactions(limit: Int): Flow<List<TransactionEntity>> = txDao.observeRecent(limit)

    suspend fun transactionsBetweenOnce(from: LocalDate, to: LocalDate): List<TransactionEntity> =
        txDao.range(from.toEpochDay(), to.toEpochDay())

    suspend fun allCategories(): List<CategoryEntity> = categoryDao.all()
    suspend fun payments(): Payments = Payments(paymentDao.all())
    suspend fun transaction(id: Long) = txDao.get(id)
    suspend fun recurringItem(id: Long) = recurringDao.get(id)

    /** Returns the id of the movement, or -1 when the server didn't save it. */
    suspend fun saveTransaction(tx: TransactionEntity): Long =
        either(-1L, { saveTransaction(tx) }) { txDao.upsert(tx).let { if (tx.id == 0L) it else tx.id } }

    suspend fun deleteTransaction(tx: TransactionEntity) {
        either(Unit, { deleteTransaction(tx.id) }) { txDao.delete(tx) }
    }

    /** Called when an undo window has passed and the movement is gone for good. */
    fun discardReceipt(tx: TransactionEntity) {
        tx.receiptPath?.let { deleteImage(it) }
    }

    /** Returns the id of the fixed expense, or -1 when the server didn't save it. */
    suspend fun saveRecurring(item: RecurringEntity): Long = either(-1L, { saveRecurring(item) }) {
        val id = recurringDao.upsert(item)
        if (item.id == 0L) id else item.id
    }

    suspend fun deleteRecurring(item: RecurringEntity) {
        either(Unit, { deleteRecurring(item.id) }) { recurringDao.delete(item) }
        item.imagePath?.let { deleteImage(it) }
    }

    /** Saves a top level category with its subcategories (missing ones are removed). */
    suspend fun saveCategory(category: CategoryEntity, subs: List<CategoryEntity>) {
        either(Unit, { saveCategory(category, subs) }) { saveCategoryLocally(category, subs) }
    }

    private suspend fun saveCategoryLocally(category: CategoryEntity, subs: List<CategoryEntity>) = db.withTransaction {
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
    suspend fun deleteCategory(category: CategoryEntity): Boolean =
        // A change the server couldn't receive is reported as such, not as "category in use".
        either(true, { deleteCategory(category.id) }) { deleteCategoryLocally(category) }

    private suspend fun deleteCategoryLocally(category: CategoryEntity): Boolean = db.withTransaction {
        if (txDao.countForCategory(category.id) > 0 || recurringDao.countForCategory(category.id) > 0) {
            return@withTransaction false
        }
        categoryDao.all().filter { it.parentId == category.id }.forEach { txDao.clearSubcategory(it.id) }
        categoryDao.deleteChildren(category.id)
        categoryDao.delete(category)
        true
    }

    suspend fun saveMember(member: MemberEntity) {
        either(Unit, { saveMember(member) }) {
            memberDao.upsert(if (member.id == 0L) member.copy(sortOrder = memberDao.all().size) else member)
        }
    }

    /** Movements of the removed person stay, just without an owner. */
    suspend fun deleteMember(member: MemberEntity) {
        either(Unit, { deleteMember(member.id) }) {
            db.withTransaction {
                txDao.clearMember(member.id)
                memberDao.delete(member)
            }
        }
    }

    /** Returns the id of the payment method, or -1 when the server didn't save it. */
    suspend fun saveCustomPayment(method: CustomPaymentEntity): Long = either(-1L, { saveCustomPayment(method) }) {
        val id = paymentDao.upsert(if (method.id == 0L) method.copy(sortOrder = paymentDao.all().size) else method)
        if (method.id == 0L) id else method.id
    }

    /** Movements and fixed expenses paid that way stay, as "Altro". */
    suspend fun deleteCustomPayment(method: CustomPaymentEntity) {
        either(Unit, { deleteCustomPayment(method.id) }) {
            db.withTransaction {
                txDao.clearPayment(method.id)
                recurringDao.clearPayment(method.id)
                paymentDao.delete(method)
            }
        }
    }

    /** The server seeds new accounts itself. */
    suspend fun seedIfEmpty() {
        if (server.enabled || categoryDao.count() > 0) return
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
    suspend fun processRecurring(today: LocalDate = LocalDate.now()): List<RecurringEntity> =
        if (server.enabled) serverReminders() else processRecurringLocally(today)

    /**
     * In server mode the server registers fixed expenses and marks reminders as due
     * (notifiedFor = next billing date): the phone only shows each of them once.
     */
    private suspend fun serverReminders(): List<RecurringEntity> {
        runCatching { server.pull() }.onFailure { return emptyList() }
        val due = recurringDao.active().filter { it.remindDaysBefore >= 0 && it.notifiedFor == it.nextDate }
        val keys = due.map { "${it.id}:${it.nextDate}" }.toSet()
        val shown = server.store.shownReminders
        server.store.shownReminders = keys
        return due.filter { "${it.id}:${it.nextDate}" !in shown }
    }

    private suspend fun processRecurringLocally(today: LocalDate): List<RecurringEntity> = db.withTransaction {
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
                            paymentId = original.paymentId,
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

    /** Data the phone had before server mode was turned on, to go back to it when turning it off. */
    val localCopy: File get() = File(filesDir, "dati-prima-del-server.json")

    val imagesDir: File get() = File(filesDir, "images").apply { mkdirs() }

    fun newImageFile(prefix: String): File = File(imagesDir, "${prefix}_${System.currentTimeMillis()}.jpg")

    fun deleteImage(path: String) {
        val file = File(path)
        if (file.parentFile != imagesDir) return
        file.delete()
        val config = server.config
        if (config != null && ServerSync.isServerImage(file.name)) {
            scope.launch { runCatching { server.client.deleteImage(config, "/images/${file.name}") } }
        }
    }

    // --- backup -------------------------------------------------------------------------------

    data class Snapshot(
        val categories: List<CategoryEntity>,
        val transactions: List<TransactionEntity>,
        val recurring: List<RecurringEntity>,
        val members: List<MemberEntity>,
        val payments: List<CustomPaymentEntity> = emptyList(),
    )

    suspend fun snapshot() =
        Snapshot(categoryDao.all(), txDao.all(), recurringDao.all(), memberDao.all(), paymentDao.all())

    /** In server mode the backup replaces the account's data on the server. */
    suspend fun replaceAll(snapshot: Snapshot) {
        if (server.enabled) server.upload(snapshot) else replaceLocally(snapshot)
    }

    suspend fun replaceLocally(snapshot: Snapshot) = db.withTransaction {
        txDao.clear()
        recurringDao.clear()
        categoryDao.clear()
        memberDao.clear()
        paymentDao.clear()
        categoryDao.insertAll(snapshot.categories)
        memberDao.insertAll(snapshot.members)
        paymentDao.insertAll(snapshot.payments)
        txDao.insertAll(snapshot.transactions)
        recurringDao.insertAll(snapshot.recurring)
    }
}
