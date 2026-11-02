package com.money.app.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** How the copy on the phone stands compared with the server; [pending] changes are waiting to be sent. */
data class SyncStatus(val lastSync: Long = 0, val error: String? = null, val syncing: Boolean = false, val pending: Int = 0)

/**
 * Server mode: the account on the Money server holds the data, as for the PWA. Room stays as the
 * local copy every screen reads (so it works offline too); every change goes to the server first
 * and the copy is then reloaded from it. Without a connection changes are applied on the phone
 * and wait in the [outbox]: they are sent first, in order, before anything else is sent or
 * reloaded, so a reload never wipes them.
 */
class ServerSync(
    private val db: MoneyDatabase,
    val store: ServerStore,
    outboxFile: File,
    private val imagesDir: () -> File,
) {
    val client = ServerClient(store.connectivity)
    private val mutex = Mutex()
    private val outbox = Outbox(outboxFile)

    private val _status = MutableStateFlow(SyncStatus(pending = outbox.size))
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** What happened to the changes made offline, shown as a message. */
    val notices: SharedFlow<String> = _notices

    val config: ServerConfig? get() = store.config.value
    val enabled: Boolean get() = config != null

    /** Reloads the local copy when the server has changed (or always, with [force]). */
    suspend fun pull(force: Boolean = false) = mutex.withLock { pullLocked(requireConfig(), force) }

    /** Sends the changes still waiting, then [change]; returns the id the server gave the row. */
    suspend fun submit(change: Change): Long = write { send(it, change) }

    /** Keeps [change], already applied on the phone, until the server can be reached. */
    fun enqueue(change: Change) {
        val first = outbox.size == 0
        outbox.add(encode(change))
        _status.value = _status.value.copy(pending = outbox.size)
        if (first) _notices.tryEmit("Server non raggiungibile: salvato sul telefono, lo invio appena torna la connessione")
    }

    /** Server mode turned on or off: changes for the previous account are dropped. */
    fun forget() {
        outbox.clear()
        _status.value = SyncStatus()
    }

    /** Sends one change (after the ones still waiting), then reloads the local copy. */
    suspend fun <T> write(block: suspend ServerSync.(ServerConfig) -> T): T = mutex.withLock {
        val config = requireConfig()
        val result = try {
            flushLocked(config)
            block(config)
        } catch (e: ServerException) {
            _status.value = _status.value.copy(error = e.message)
            throw e
        }
        runCatching { pullLocked(config, force = false) }
        result
    }

    private fun requireConfig() = config ?: throw ServerException("Server CasaOS non configurato", status = -1)

    /**
     * Sends the changes made offline, oldest first. Stops (throwing) while the server can't be
     * reached or the session has expired; a change the server refuses is dropped with a message.
     */
    private suspend fun flushLocked(config: ServerConfig) {
        while (true) {
            val json = outbox.first() ?: break
            try {
                send(config, decode(json))
            } catch (e: ServerException) {
                if (e.offline || e.status == 401) throw e
                _notices.tryEmit("Una modifica fatta senza connessione non è stata accettata dal server: ${e.message}")
            } catch (e: Exception) {
                _notices.tryEmit("Una modifica fatta senza connessione non si è potuta inviare: ${e.message}")
            }
            outbox.removeFirst()
            _status.value = _status.value.copy(pending = outbox.size)
        }
    }

    private suspend fun pullLocked(config: ServerConfig, force: Boolean) {
        _status.value = _status.value.copy(syncing = true)
        try {
            flushLocked(config)
            val json = client.state(config, if (force) -1 else store.revision)
            if (!json.optBoolean("unchanged")) {
                replaceLocal(fromServer(config, json))
                outbox.forgetIds()
                store.revision = json.getLong("revision")
            }
            _status.value = SyncStatus(lastSync = System.currentTimeMillis(), pending = outbox.size)
        } catch (e: ServerException) {
            _status.value = _status.value.copy(error = e.message, syncing = false)
            throw e
        }
    }

    /** Movements the account already has on the server, to ask which data to keep. */
    suspend fun serverMovements(config: ServerConfig): Int = client.state(config, -1).getJSONArray("transactions").length()

    /** Every field as stored on the phone, image paths included: the copy kept from before server mode. */
    fun encode(snapshot: MoneyRepository.Snapshot): String = JSONObject()
        .put("categories", JSONArray(snapshot.categories.map { it.toJson() }))
        .put("transactions", JSONArray(snapshot.transactions.map { it.toJson().put("receiptPath", it.receiptPath ?: JSONObject.NULL) }))
        .put("recurring", JSONArray(snapshot.recurring.map { it.toJson().put("imagePath", it.imagePath ?: JSONObject.NULL) }))
        .put("members", JSONArray(snapshot.members.map { it.toJson() }))
        .put("paymentMethods", JSONArray(snapshot.payments.map { it.toJson() }))
        .toString()

    fun decode(text: String): MoneyRepository.Snapshot {
        val json = JSONObject(text)
        return MoneyRepository.Snapshot(
            json.getJSONArray("categories").objects().map { it.toCategory() },
            json.getJSONArray("transactions").objects().map { it.toTransaction() },
            json.getJSONArray("recurring").objects().map { it.toRecurring() },
            json.getJSONArray("members").objects().map { it.toMember() },
            json.payments(),
        )
    }

    /** Servers older than custom payment methods don't send them. */
    private fun JSONObject.payments() = optJSONArray("paymentMethods")?.objects()?.map { it.toPayment() }.orEmpty()

    private suspend fun replaceLocal(snapshot: MoneyRepository.Snapshot) = db.withTransaction {
        db.transactions().clear()
        db.recurring().clear()
        db.categories().clear()
        db.members().clear()
        db.payments().clear()
        db.categories().insertAll(snapshot.categories)
        db.members().insertAll(snapshot.members)
        db.payments().insertAll(snapshot.payments)
        db.transactions().insertAll(snapshot.transactions)
        db.recurring().insertAll(snapshot.recurring)
    }

    /** Server rows with their images downloaded next to the app's own. */
    private suspend fun fromServer(config: ServerConfig, json: JSONObject) = MoneyRepository.Snapshot(
        json.getJSONArray("categories").objects().map { it.toCategory() },
        json.getJSONArray("transactions").objects().map { it.toTransaction().let { tx -> tx.copy(receiptPath = localImage(config, tx.receiptPath)) } },
        json.getJSONArray("recurring").objects().map { it.toRecurring().let { r -> r.copy(imagePath = localImage(config, r.imagePath)) } },
        json.getJSONArray("members").objects().map { it.toMember() },
        json.payments(),
    )

    private suspend fun localImage(config: ServerConfig, path: String?): String? {
        if (path == null || !path.startsWith(IMAGES)) return null
        val name = path.removePrefix(IMAGES)
        if (!name.matches(Regex("[\\w.-]+"))) return null
        val file = File(imagesDir(), name)
        if (!file.exists()) runCatching { client.downloadImage(config, path, file) }
        return file.path
    }

    /** Server path of a local image, uploading it when the server doesn't have it yet. */
    suspend fun remoteImage(config: ServerConfig, local: String?, prefix: String): String? {
        if (local == null) return null
        val file = File(local)
        if (isServerImage(file.name)) return IMAGES + file.name
        if (!file.exists()) return null
        return client.uploadImage(config, file, prefix)
    }

    /** Once the server has saved the row, the local file takes the server name (so it isn't sent again). */
    private fun adopt(local: String?, remote: String?) {
        if (local == null || remote == null) return
        val file = File(local)
        if (isServerImage(file.name)) return
        val renamed = File(imagesDir(), remote.removePrefix(IMAGES))
        // Changes still waiting may point to the old name.
        if (file.renameTo(renamed)) outbox.mapPath(local, renamed.path)
    }

    // --- writes -----------------------------------------------------------------------------

    /** Sends [change] with the ids the server gave to rows created offline; returns the row's server id. */
    private suspend fun send(config: ServerConfig, change: Change): Long = when (change) {
        is Change.SaveTransaction -> {
            val tx = remap(change.tx)
            val image = remoteImage(config, tx.receiptPath, "receipt")
            val body = tx.copy(id = if (change.created) 0 else tx.id).toJson().put("receiptPath", image ?: JSONObject.NULL)
            val id = client.post(config, "/api/transactions", body).getLong("id")
            adopt(tx.receiptPath, image)
            if (change.created) outbox.mapId(TRANSACTIONS, change.tx.id, id)
            id
        }
        is Change.DeleteTransaction -> delete(config, "/api/transactions/", outbox.id(TRANSACTIONS, change.id))
        is Change.SettleTransactions -> {
            val ids = change.ids.map { outbox.id(TRANSACTIONS, it) }
            client.post(config, "/api/transactions/settle", JSONObject().put("ids", JSONArray(ids)))
            0
        }
        is Change.SaveRecurring -> {
            val item = remap(change.item)
            val image = remoteImage(config, item.imagePath, "logo")
            val body = item.copy(id = if (change.created) 0 else item.id).toJson().put("imagePath", image ?: JSONObject.NULL)
            val id = client.post(config, "/api/recurring", body).getLong("id")
            adopt(item.imagePath, image)
            if (change.created) outbox.mapId(RECURRING, change.item.id, id)
            id
        }
        is Change.DeleteRecurring -> delete(config, "/api/recurring/", outbox.id(RECURRING, change.id))
        is Change.SaveCategory -> {
            fun body(c: CategoryEntity) =
                (if (c.id in change.created) c.copy(id = 0) else c.copy(id = outbox.id(CATEGORIES, c.id))).toJson()
            val result = client.post(
                config, "/api/categories",
                JSONObject().put("category", body(change.category)).put("subs", JSONArray(change.subs.map { body(it) })),
            )
            // Servers before 1.3.0 don't say which ids they gave.
            val id = result.optLong("id", change.category.id)
            if (change.category.id in change.created) outbox.mapId(CATEGORIES, change.category.id, id)
            result.optJSONArray("subIds")?.let { subIds ->
                change.subs.forEachIndexed { i, sub ->
                    if (sub.id in change.created && i < subIds.length()) outbox.mapId(CATEGORIES, sub.id, subIds.getLong(i))
                }
            }
            id
        }
        is Change.DeleteCategory -> {
            val id = outbox.id(CATEGORIES, change.id)
            client.delete(config, "/api/categories/$id")
            id
        }
        is Change.SaveMember -> {
            val member = change.member.copy(id = if (change.created) 0 else outbox.id(MEMBERS, change.member.id))
            val id = client.post(config, "/api/members", member.toJson()).optLong("id", member.id)
            if (change.created) outbox.mapId(MEMBERS, change.member.id, id)
            id
        }
        is Change.DeleteMember -> delete(config, "/api/members/", outbox.id(MEMBERS, change.id))
        is Change.SavePayment -> {
            val method = change.method.copy(id = if (change.created) 0 else outbox.id(PAYMENTS, change.method.id))
            val id = client.post(config, "/api/payment-methods", method.toJson()).getLong("id")
            if (change.created) outbox.mapId(PAYMENTS, change.method.id, id)
            id
        }
        is Change.DeletePayment -> delete(config, "/api/payment-methods/", outbox.id(PAYMENTS, change.id))
        is Change.DeleteImage -> {
            client.deleteImage(config, change.path)
            0
        }
    }

    /** Deleting what is already gone is fine. */
    private suspend fun delete(config: ServerConfig, path: String, id: Long): Long {
        try {
            client.delete(config, path + id)
        } catch (e: ServerException) {
            if (e.status != 404) throw e
        }
        return id
    }

    private fun remap(tx: TransactionEntity) = tx.copy(
        id = outbox.id(TRANSACTIONS, tx.id),
        categoryId = outbox.id(CATEGORIES, tx.categoryId),
        subcategoryId = tx.subcategoryId?.let { outbox.id(CATEGORIES, it) },
        recurringId = tx.recurringId?.let { outbox.id(RECURRING, it) },
        memberId = tx.memberId?.let { outbox.id(MEMBERS, it) },
        paymentId = tx.paymentId?.let { outbox.id(PAYMENTS, it) },
        receiptPath = outbox.path(tx.receiptPath),
    )

    private fun remap(item: RecurringEntity) = item.copy(
        id = outbox.id(RECURRING, item.id),
        categoryId = outbox.id(CATEGORIES, item.categoryId),
        subcategoryId = item.subcategoryId?.let { outbox.id(CATEGORIES, it) },
        paymentId = item.paymentId?.let { outbox.id(PAYMENTS, it) },
        imagePath = outbox.path(item.imagePath),
    )

    // --- changes kept in the outbox (rows with every field, image paths on the phone) --------

    private fun encode(change: Change): JSONObject = when (change) {
        is Change.SaveTransaction -> JSONObject().put("type", "transaction").put("created", change.created)
            .put("row", change.tx.toJson().put("receiptPath", change.tx.receiptPath ?: JSONObject.NULL))
        is Change.DeleteTransaction -> JSONObject().put("type", "transaction.delete").put("id", change.id)
        is Change.SettleTransactions -> JSONObject().put("type", "transaction.settle").put("ids", JSONArray(change.ids))
        is Change.SaveRecurring -> JSONObject().put("type", "recurring").put("created", change.created)
            .put("row", change.item.toJson().put("imagePath", change.item.imagePath ?: JSONObject.NULL))
        is Change.DeleteRecurring -> JSONObject().put("type", "recurring.delete").put("id", change.id)
        is Change.SaveCategory -> JSONObject().put("type", "category").put("row", change.category.toJson())
            .put("subs", JSONArray(change.subs.map { it.toJson() })).put("createdIds", JSONArray(change.created.toList()))
        is Change.DeleteCategory -> JSONObject().put("type", "category.delete").put("id", change.id)
        is Change.SaveMember -> JSONObject().put("type", "member").put("created", change.created).put("row", change.member.toJson())
        is Change.DeleteMember -> JSONObject().put("type", "member.delete").put("id", change.id)
        is Change.SavePayment -> JSONObject().put("type", "payment").put("created", change.created).put("row", change.method.toJson())
        is Change.DeletePayment -> JSONObject().put("type", "payment.delete").put("id", change.id)
        is Change.DeleteImage -> JSONObject().put("type", "image.delete").put("path", change.path)
    }

    private fun decode(json: JSONObject): Change {
        val created = json.optBoolean("created")
        val id = json.optLong("id")
        return when (val type = json.getString("type")) {
            "transaction" -> Change.SaveTransaction(json.getJSONObject("row").toTransaction(), created)
            "transaction.delete" -> Change.DeleteTransaction(id)
            "transaction.settle" -> Change.SettleTransactions(json.getJSONArray("ids").let { ids -> (0 until ids.length()).map { ids.getLong(it) } })
            "recurring" -> Change.SaveRecurring(json.getJSONObject("row").toRecurring(), created)
            "recurring.delete" -> Change.DeleteRecurring(id)
            "category" -> Change.SaveCategory(
                json.getJSONObject("row").toCategory(),
                json.getJSONArray("subs").objects().map { it.toCategory() },
                json.getJSONArray("createdIds").let { ids -> (0 until ids.length()).map { ids.getLong(it) }.toSet() },
            )
            "category.delete" -> Change.DeleteCategory(id)
            "member" -> Change.SaveMember(json.getJSONObject("row").toMember(), created)
            "member.delete" -> Change.DeleteMember(id)
            "payment" -> Change.SavePayment(json.getJSONObject("row").toPayment(), created)
            "payment.delete" -> Change.DeletePayment(id)
            "image.delete" -> Change.DeleteImage(json.getString("path"))
            else -> throw IllegalArgumentException("modifica sconosciuta ($type)")
        }
    }

    /** Replaces the account's data with [snapshot], images included. */
    suspend fun upload(snapshot: MoneyRepository.Snapshot) = write { config ->
        val root = JSONObject()
            .put("app", "Money")
            .put("version", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put("categories", JSONArray(snapshot.categories.map { it.toJson() }))
            .put("transactions", JSONArray(snapshot.transactions.map { it.toJson() }))
            .put("recurring", JSONArray(snapshot.recurring.map { it.toJson() }))
            .put("members", JSONArray(snapshot.members.map { it.toJson() }))
            .put("paymentMethods", JSONArray(snapshot.payments.map { it.toJson() }))
        client.importBackup(config, root.toString())
        // The backup format leaves images out: send them one by one, keeping the same ids.
        snapshot.transactions.filter { it.receiptPath != null }.forEach { tx ->
            remoteImage(config, tx.receiptPath, "receipt")?.let {
                client.post(config, "/api/transactions", tx.toJson().put("receiptPath", it))
                adopt(tx.receiptPath, it)
            }
        }
        snapshot.recurring.filter { it.imagePath != null }.forEach { item ->
            remoteImage(config, item.imagePath, "logo")?.let {
                client.post(config, "/api/recurring", item.toJson().put("imagePath", it))
                adopt(item.imagePath, it)
            }
        }
    }

    // --- JSON (same fields as the server's model) -----------------------------------------------

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.optLongOrNull(key: String) = if (!has(key) || isNull(key)) null else getLong(key)
    private fun JSONObject.optStringOrNull(key: String) = if (!has(key) || isNull(key)) null else getString(key)

    private fun CategoryEntity.toJson() = JSONObject()
        .put("id", id).put("name", name).put("icon", icon).put("color", color).put("type", type.name)
        .put("parentId", parentId ?: JSONObject.NULL).put("budgetCents", budgetCents ?: JSONObject.NULL)
        .put("sortOrder", sortOrder)

    private fun JSONObject.toCategory() = CategoryEntity(
        id = getLong("id"), name = getString("name"), icon = getString("icon"), color = getLong("color"),
        type = TxType.valueOf(getString("type")), parentId = optLongOrNull("parentId"),
        budgetCents = optLongOrNull("budgetCents"), sortOrder = optInt("sortOrder"),
    )

    private fun TransactionEntity.toJson() = JSONObject()
        .put("id", id).put("type", type.name).put("amountCents", amountCents).put("date", date)
        .put("categoryId", categoryId).put("subcategoryId", subcategoryId ?: JSONObject.NULL)
        .put("payment", payment.name).put("note", note).put("receiptPath", JSONObject.NULL)
        .put("recurringId", recurringId ?: JSONObject.NULL).put("createdAt", createdAt)
        .put("memberId", memberId ?: JSONObject.NULL).put("paymentId", paymentId ?: JSONObject.NULL)
        .put("settle", settle)

    private fun JSONObject.toTransaction() = TransactionEntity(
        id = getLong("id"), type = TxType.valueOf(getString("type")), amountCents = getLong("amountCents"),
        date = getLong("date"), categoryId = getLong("categoryId"), subcategoryId = optLongOrNull("subcategoryId"),
        payment = PaymentMethod.valueOf(getString("payment")), note = optString("note"),
        receiptPath = optStringOrNull("receiptPath"), recurringId = optLongOrNull("recurringId"),
        createdAt = optLong("createdAt"), memberId = optLongOrNull("memberId"), paymentId = optLongOrNull("paymentId"),
        settle = optInt("settle"),
    )

    private fun MemberEntity.toJson() = JSONObject()
        .put("id", id).put("name", name).put("color", color).put("sortOrder", sortOrder)

    private fun JSONObject.toMember() = MemberEntity(
        id = getLong("id"), name = getString("name"), color = getLong("color"), sortOrder = optInt("sortOrder"),
    )

    private fun CustomPaymentEntity.toJson() = JSONObject()
        .put("id", id).put("name", name).put("icon", icon).put("color", color).put("sortOrder", sortOrder)

    private fun JSONObject.toPayment() = CustomPaymentEntity(
        id = getLong("id"), name = getString("name"), icon = optString("icon", "card"), color = getLong("color"),
        sortOrder = optInt("sortOrder"),
    )

    private fun RecurringEntity.toJson() = JSONObject()
        .put("id", id).put("kind", kind.name).put("name", name).put("amountCents", amountCents)
        .put("categoryId", categoryId).put("subcategoryId", subcategoryId ?: JSONObject.NULL)
        .put("payment", payment.name).put("frequency", frequency.name).put("startDate", startDate)
        .put("nextDate", nextDate).put("brandKey", brandKey ?: JSONObject.NULL).put("imagePath", JSONObject.NULL)
        .put("icon", icon).put("color", color).put("autoRegister", autoRegister)
        .put("remindDaysBefore", remindDaysBefore).put("notifiedFor", notifiedFor ?: JSONObject.NULL)
        .put("active", active).put("note", note).put("paymentId", paymentId ?: JSONObject.NULL)

    private fun JSONObject.toRecurring() = RecurringEntity(
        id = getLong("id"), kind = RecurringKind.valueOf(getString("kind")), name = getString("name"),
        amountCents = getLong("amountCents"), categoryId = getLong("categoryId"),
        subcategoryId = optLongOrNull("subcategoryId"), payment = PaymentMethod.valueOf(getString("payment")),
        frequency = Frequency.valueOf(getString("frequency")), startDate = getLong("startDate"),
        nextDate = getLong("nextDate"), brandKey = optStringOrNull("brandKey"),
        imagePath = optStringOrNull("imagePath"), icon = optString("icon", "repeat"), color = getLong("color"),
        autoRegister = optBoolean("autoRegister", true), remindDaysBefore = optInt("remindDaysBefore", 2),
        notifiedFor = optLongOrNull("notifiedFor"), active = optBoolean("active", true), note = optString("note"),
        paymentId = optLongOrNull("paymentId"),
    )

    companion object {
        private const val IMAGES = "/images/"
        private const val TRANSACTIONS = "transactions"
        private const val RECURRING = "recurring"
        private const val CATEGORIES = "categories"
        private const val MEMBERS = "members"
        private const val PAYMENTS = "payments"

        /** Names the server gives to uploaded images; the app's own have no random suffix. */
        fun isServerImage(name: String) = name.matches(Regex("(receipt|logo)_\\d+_[0-9a-f]{6}\\.jpg"))
    }
}
