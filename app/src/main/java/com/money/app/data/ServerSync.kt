package com.money.app.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** How the copy on the phone stands compared with the server. */
data class SyncStatus(val lastSync: Long = 0, val error: String? = null, val syncing: Boolean = false)

/**
 * Server mode: the account on the Money server holds the data, as for the PWA. Room stays as the
 * local copy every screen reads (so it works offline too); every change goes to the server first
 * and the copy is then reloaded from it.
 */
class ServerSync(
    private val db: MoneyDatabase,
    val store: ServerStore,
    private val imagesDir: () -> File,
) {
    val client = ServerClient()
    private val mutex = Mutex()

    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    val config: ServerConfig? get() = store.config.value
    val enabled: Boolean get() = config != null

    /** Reloads the local copy when the server has changed (or always, with [force]). */
    suspend fun pull(force: Boolean = false) = mutex.withLock { pullLocked(requireConfig(), force) }

    /** Sends one change, then reloads the local copy. */
    suspend fun <T> write(block: suspend ServerSync.(ServerConfig) -> T): T = mutex.withLock {
        val config = requireConfig()
        val result = try {
            block(config)
        } catch (e: ServerException) {
            _status.value = _status.value.copy(error = e.message)
            throw e
        }
        runCatching { pullLocked(config, force = false) }
        result
    }

    private fun requireConfig() = config ?: throw ServerException("Server CasaOS non configurato")

    private suspend fun pullLocked(config: ServerConfig, force: Boolean) {
        _status.value = _status.value.copy(syncing = true)
        try {
            val json = client.state(config, if (force) -1 else store.revision)
            if (!json.optBoolean("unchanged")) {
                replaceLocal(fromServer(config, json))
                store.revision = json.getLong("revision")
            }
            _status.value = SyncStatus(lastSync = System.currentTimeMillis())
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
        if (!isServerImage(file.name)) file.renameTo(File(imagesDir(), remote.removePrefix(IMAGES)))
    }

    // --- writes -----------------------------------------------------------------------------

    suspend fun saveTransaction(tx: TransactionEntity): Long = write { config ->
        val image = remoteImage(config, tx.receiptPath, "receipt")
        val id = client.post(config, "/api/transactions", tx.toJson().put("receiptPath", image ?: JSONObject.NULL)).getLong("id")
        adopt(tx.receiptPath, image)
        id
    }

    suspend fun deleteTransaction(id: Long) = write { client.delete(it, "/api/transactions/$id") }

    suspend fun saveRecurring(item: RecurringEntity): Long = write { config ->
        val image = remoteImage(config, item.imagePath, "logo")
        val id = client.post(config, "/api/recurring", item.toJson().put("imagePath", image ?: JSONObject.NULL)).getLong("id")
        adopt(item.imagePath, image)
        id
    }

    suspend fun deleteRecurring(id: Long) = write { client.delete(it, "/api/recurring/$id") }

    suspend fun saveCategory(category: CategoryEntity, subs: List<CategoryEntity>) = write {
        client.post(it, "/api/categories", JSONObject().put("category", category.toJson()).put("subs", JSONArray(subs.map { s -> s.toJson() })))
    }

    /** False when the server refuses because the category is still in use. */
    suspend fun deleteCategory(id: Long): Boolean = write {
        try {
            client.delete(it, "/api/categories/$id")
            true
        } catch (e: ServerException) {
            if (e.status == 409) false else throw e
        }
    }

    suspend fun saveMember(member: MemberEntity) = write { client.post(it, "/api/members", member.toJson()) }

    suspend fun deleteMember(id: Long) = write { client.delete(it, "/api/members/$id") }

    suspend fun saveCustomPayment(method: CustomPaymentEntity): Long =
        write { client.post(it, "/api/payment-methods", method.toJson()).getLong("id") }

    suspend fun deleteCustomPayment(id: Long) = write { client.delete(it, "/api/payment-methods/$id") }

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

    private fun JSONObject.toTransaction() = TransactionEntity(
        id = getLong("id"), type = TxType.valueOf(getString("type")), amountCents = getLong("amountCents"),
        date = getLong("date"), categoryId = getLong("categoryId"), subcategoryId = optLongOrNull("subcategoryId"),
        payment = PaymentMethod.valueOf(getString("payment")), note = optString("note"),
        receiptPath = optStringOrNull("receiptPath"), recurringId = optLongOrNull("recurringId"),
        createdAt = optLong("createdAt"), memberId = optLongOrNull("memberId"), paymentId = optLongOrNull("paymentId"),
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

        /** Names the server gives to uploaded images; the app's own have no random suffix. */
        fun isServerImage(name: String) = name.matches(Regex("(receipt|logo)_\\d+_[0-9a-f]{6}\\.jpg"))
    }
}
