package com.money.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * One change to the account's data. In server mode it goes to the server at once; without a
 * connection it is applied on the phone and waits in the [Outbox]. Rows created offline keep the
 * id the phone gave them ([created]) until the server assigns its own.
 */
sealed interface Change {
    data class SaveTransaction(val tx: TransactionEntity, val created: Boolean = false) : Change
    data class DeleteTransaction(val id: Long) : Change
    data class SaveRecurring(val item: RecurringEntity, val created: Boolean = false) : Change
    data class DeleteRecurring(val id: Long) : Change
    /** [created]: ids of the category and subcategories that the server doesn't have yet. */
    data class SaveCategory(val category: CategoryEntity, val subs: List<CategoryEntity>, val created: Set<Long> = emptySet()) : Change
    data class DeleteCategory(val id: Long) : Change
    data class SaveMember(val member: MemberEntity, val created: Boolean = false) : Change
    data class DeleteMember(val id: Long) : Change
    data class SavePayment(val method: CustomPaymentEntity, val created: Boolean = false) : Change
    data class DeletePayment(val id: Long) : Change
    /** Server image ("/images/...") no longer used. */
    data class DeleteImage(val path: String) : Change
}

/**
 * Changes made while the server couldn't be reached, saved in a file so they survive the app
 * being closed, and sent in order as soon as the server answers again.
 */
class Outbox(private val file: File) {
    private val changes = mutableListOf<JSONObject>()
    /** Ids the server gave to rows created offline: table, then phone id to server id. */
    private val ids = mutableMapOf<String, MutableMap<Long, Long>>()
    /** Images first saved offline: phone path to the path of the same file once renamed for the server. */
    private val paths = mutableMapOf<String, String>()

    init {
        runCatching { if (file.exists()) load(JSONObject(file.readText())) }
    }

    val size: Int @Synchronized get() = changes.size

    @Synchronized fun first(): JSONObject? = changes.firstOrNull()

    @Synchronized fun add(change: JSONObject) {
        changes += change
        save()
    }

    @Synchronized fun removeFirst() {
        if (changes.isNotEmpty()) changes.removeAt(0)
        save()
    }

    @Synchronized fun id(table: String, id: Long): Long = ids[table]?.get(id) ?: id

    @Synchronized fun mapId(table: String, phone: Long, server: Long) {
        ids.getOrPut(table) { mutableMapOf() }[phone] = server
        save()
    }

    @Synchronized fun path(path: String?): String? = path?.let { paths[it] ?: it }

    @Synchronized fun mapPath(phone: String, renamed: String) {
        paths[phone] = renamed
        save()
    }

    /**
     * The phone now holds the server's own ids (reloaded with nothing left to send): the old
     * phone ids could clash with them.
     */
    @Synchronized fun forgetIds() {
        if (changes.isNotEmpty()) return
        ids.clear()
        paths.clear()
        save()
    }

    /** Server mode turned on or off: nothing left to send. */
    @Synchronized fun clear() {
        changes.clear()
        ids.clear()
        paths.clear()
        file.delete()
    }

    private fun load(json: JSONObject) {
        json.optJSONArray("changes")?.let { list -> (0 until list.length()).forEach { changes += list.getJSONObject(it) } }
        json.optJSONObject("ids")?.let { tables ->
            tables.keys().forEach { table ->
                val map = tables.getJSONObject(table)
                ids[table] = map.keys().asSequence().associate { it.toLong() to map.getLong(it) }.toMutableMap()
            }
        }
        json.optJSONObject("paths")?.let { map -> map.keys().forEach { paths[it] = map.getString(it) } }
    }

    private fun save() {
        val json = JSONObject()
            .put("changes", JSONArray(changes))
            .put("ids", JSONObject(ids.mapValues { (_, map) -> JSONObject(map.mapKeys { it.key.toString() }) }))
            .put("paths", JSONObject(paths as Map<*, *>))
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(json.toString())
        temp.renameTo(file)
    }
}
