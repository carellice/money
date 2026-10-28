package com.money.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

/** JSON backup of the whole database. Images (receipts, custom logos) are not included. */
object Backup {
    private const val VERSION = 1

    suspend fun export(repo: MoneyRepository, out: OutputStream) {
        val (categories, transactions, recurring) = repo.snapshot()
        val root = JSONObject()
            .put("app", "Money")
            .put("version", VERSION)
            .put("exportedAt", System.currentTimeMillis())
            .put("categories", JSONArray(categories.map { it.toJson() }))
            .put("transactions", JSONArray(transactions.map { it.toJson() }))
            .put("recurring", JSONArray(recurring.map { it.toJson() }))
        out.bufferedWriter().use { it.write(root.toString(2)) }
    }

    /** Replaces all data with the backup content. Returns the number of movements imported. */
    suspend fun import(repo: MoneyRepository, input: InputStream): Int {
        val root = JSONObject(input.bufferedReader().use { it.readText() })
        require(root.optString("app") == "Money") { "Il file non è un backup di Money" }
        val categories = root.getJSONArray("categories").objects().map { it.toCategory() }
        val transactions = root.getJSONArray("transactions").objects().map { it.toTransaction() }
        val recurring = root.getJSONArray("recurring").objects().map { it.toRecurring() }
        repo.replaceAll(categories, transactions, recurring)
        return transactions.size
    }

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
        .put("payment", payment.name).put("note", note).put("recurringId", recurringId ?: JSONObject.NULL)
        .put("createdAt", createdAt)

    private fun JSONObject.toTransaction() = TransactionEntity(
        id = getLong("id"), type = TxType.valueOf(getString("type")), amountCents = getLong("amountCents"),
        date = getLong("date"), categoryId = getLong("categoryId"), subcategoryId = optLongOrNull("subcategoryId"),
        payment = PaymentMethod.valueOf(getString("payment")), note = optString("note"),
        recurringId = optLongOrNull("recurringId"), createdAt = optLong("createdAt"),
    )

    private fun RecurringEntity.toJson() = JSONObject()
        .put("id", id).put("kind", kind.name).put("name", name).put("amountCents", amountCents)
        .put("categoryId", categoryId).put("subcategoryId", subcategoryId ?: JSONObject.NULL)
        .put("payment", payment.name).put("frequency", frequency.name).put("startDate", startDate)
        .put("nextDate", nextDate).put("brandKey", brandKey ?: JSONObject.NULL).put("icon", icon)
        .put("color", color).put("autoRegister", autoRegister).put("remindDaysBefore", remindDaysBefore)
        .put("active", active).put("note", note)

    private fun JSONObject.toRecurring() = RecurringEntity(
        id = getLong("id"), kind = RecurringKind.valueOf(getString("kind")), name = getString("name"),
        amountCents = getLong("amountCents"), categoryId = getLong("categoryId"),
        subcategoryId = optLongOrNull("subcategoryId"), payment = PaymentMethod.valueOf(getString("payment")),
        frequency = Frequency.valueOf(getString("frequency")), startDate = getLong("startDate"),
        nextDate = getLong("nextDate"), brandKey = optStringOrNull("brandKey"), icon = optString("icon", "repeat"),
        color = getLong("color"), autoRegister = optBoolean("autoRegister", true),
        remindDaysBefore = optInt("remindDaysBefore", 2), active = optBoolean("active", true), note = optString("note"),
    )
}
