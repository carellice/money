package com.money.app.data

import androidx.room.withTransaction
import com.money.app.domain.Recurrence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.merge
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
    val server = ServerSync(db, serverStore, File(filesDir, "modifiche-da-inviare.json")) { imagesDir }

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** Changes the server refused, and what happens to the ones made offline, shown as a message. */
    val errors: Flow<String> = merge(_errors, server.notices)

    /**
     * Runs [remote] in server mode, [local] otherwise. When the server can't be reached, [offline]
     * applies the change on the phone and queues it; without it (or for any other error) the
     * change is not made and [failed] is returned.
     */
    private suspend fun <T> either(
        failed: T,
        remote: suspend ServerSync.() -> T,
        offline: (suspend () -> T)? = null,
        local: suspend () -> T,
    ): T {
        if (!server.enabled) return local()
        return try {
            server.remote()
        } catch (e: ServerException) {
            if (e.offline && offline != null) return offline()
            _errors.tryEmit("Modifica non salvata: ${e.message}")
            failed
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
    suspend fun saveTransaction(tx: TransactionEntity): Long {
        suspend fun local() = txDao.upsert(tx).let { if (tx.id == 0L) it else tx.id }
        return either(-1L, { submit(Change.SaveTransaction(tx)) }, offline = {
            local().also { server.enqueue(Change.SaveTransaction(tx.copy(id = it), created = tx.id == 0L)) }
        }) { local() }
    }

    suspend fun deleteTransaction(tx: TransactionEntity) {
        either(Unit, { submit(Change.DeleteTransaction(tx.id)) }, offline = {
            txDao.delete(tx)
            server.enqueue(Change.DeleteTransaction(tx.id))
        }) { txDao.delete(tx) }
    }

    /** Called when an undo window has passed and the movement is gone for good. */
    fun discardReceipt(tx: TransactionEntity) {
        tx.receiptPath?.let { deleteImage(it) }
    }

    /** Returns the id of the fixed expense, or -1 when the server didn't save it. */
    suspend fun saveRecurring(item: RecurringEntity): Long {
        suspend fun local() = recurringDao.upsert(item).let { if (item.id == 0L) it else item.id }
        return either(-1L, { submit(Change.SaveRecurring(item)) }, offline = {
            local().also { server.enqueue(Change.SaveRecurring(item.copy(id = it), created = item.id == 0L)) }
        }) { local() }
    }

    suspend fun deleteRecurring(item: RecurringEntity) {
        either(Unit, { submit(Change.DeleteRecurring(item.id)) }, offline = {
            recurringDao.delete(item)
            server.enqueue(Change.DeleteRecurring(item.id))
        }) { recurringDao.delete(item) }
        item.imagePath?.let { deleteImage(it) }
    }

    /** Saves a top level category with its subcategories (missing ones are removed). */
    suspend fun saveCategory(category: CategoryEntity, subs: List<CategoryEntity>) {
        either(Unit, { submit(Change.SaveCategory(category, subs)) }, offline = {
            val (id, subIds) = saveCategoryLocally(category, subs)
            val created = buildSet {
                if (category.id == 0L) add(id)
                subs.forEachIndexed { i, sub -> if (sub.id == 0L) add(subIds[i]) }
            }
            server.enqueue(
                Change.SaveCategory(category.copy(id = id), subs.mapIndexed { i, sub -> sub.copy(id = subIds[i], parentId = id) }, created)
            )
        }) { saveCategoryLocally(category, subs) }
    }

    /** Returns the id of the category and of its subcategories, in the order given. */
    private suspend fun saveCategoryLocally(category: CategoryEntity, subs: List<CategoryEntity>) = db.withTransaction {
        val id = if (category.id == 0L) categoryDao.insert(category) else category.also { categoryDao.upsert(it) }.id
        val existing = categoryDao.all().filter { it.parentId == id }
        val keep = subs.filter { it.id != 0L }.map { it.id }.toSet()
        existing.filter { it.id !in keep }.forEach {
            txDao.clearSubcategory(it.id)
            categoryDao.delete(it)
        }
        val subIds = subs.mapIndexed { index, sub ->
            val saved = categoryDao.upsert(
                sub.copy(parentId = id, type = category.type, color = category.color, icon = category.icon, sortOrder = index)
            )
            if (sub.id == 0L) saved else sub.id
        }
        id to subIds
    }

    /** Returns false when the category is still used by movements or fixed expenses. */
    suspend fun deleteCategory(category: CategoryEntity): Boolean =
        // A change the server couldn't receive is reported as such, not as "category in use".
        either(true, {
            try {
                submit(Change.DeleteCategory(category.id))
                true
            } catch (e: ServerException) {
                if (e.status == 409) false else throw e
            }
        }, offline = {
            deleteCategoryLocally(category).also { if (it) server.enqueue(Change.DeleteCategory(category.id)) }
        }) { deleteCategoryLocally(category) }

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
        suspend fun local(): MemberEntity {
            val saved = if (member.id == 0L) member.copy(sortOrder = memberDao.all().size) else member
            return memberDao.upsert(saved).let { saved.copy(id = if (member.id == 0L) it else member.id) }
        }
        either(Unit, { submit(Change.SaveMember(member)) }, offline = {
            server.enqueue(Change.SaveMember(local(), created = member.id == 0L))
        }) { local() }
    }

    /** Movements of the removed person stay, just without an owner. */
    suspend fun deleteMember(member: MemberEntity) {
        suspend fun local() = db.withTransaction {
            txDao.clearMember(member.id)
            memberDao.delete(member)
        }
        either(Unit, { submit(Change.DeleteMember(member.id)) }, offline = {
            local()
            server.enqueue(Change.DeleteMember(member.id))
        }) { local() }
    }

    /** Returns the id of the payment method, or -1 when the server didn't save it. */
    suspend fun saveCustomPayment(method: CustomPaymentEntity): Long {
        suspend fun local(): CustomPaymentEntity {
            val saved = if (method.id == 0L) method.copy(sortOrder = paymentDao.all().size) else method
            return paymentDao.upsert(saved).let { saved.copy(id = if (method.id == 0L) it else method.id) }
        }
        return either(-1L, { submit(Change.SavePayment(method)) }, offline = {
            local().also { server.enqueue(Change.SavePayment(it, created = method.id == 0L)) }.id
        }) { local().id }
    }

    /** Movements and fixed expenses paid that way stay, as "Altro". */
    suspend fun deleteCustomPayment(method: CustomPaymentEntity) {
        suspend fun local() = db.withTransaction {
            txDao.clearPayment(method.id)
            recurringDao.clearPayment(method.id)
            paymentDao.delete(method)
        }
        either(Unit, { submit(Change.DeletePayment(method.id)) }, offline = {
            local()
            server.enqueue(Change.DeletePayment(method.id))
        }) { local() }
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
        if (server.enabled && ServerSync.isServerImage(file.name)) {
            val change = Change.DeleteImage("/images/${file.name}")
            scope.launch {
                try {
                    server.submit(change)
                } catch (e: ServerException) {
                    if (e.offline) server.enqueue(change)
                }
            }
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
