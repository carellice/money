package com.money.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

/** Money of the household (a bank account, the cash...), moved by the payment methods it holds. */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    /** ARGB color. */
    val color: Long,
    /** Comma separated [methodKey]s: "CARD,DEBIT,c3". A method belongs to one account at most. */
    val methods: String = "",
    /** The balance stated at [balanceAt]: movements and transfers registered after it move it. */
    val balanceCents: Long = 0,
    /** Epoch millis. */
    val balanceAt: Long = 0,
    val sortOrder: Int = 0,
) {
    val methodKeys: List<String> get() = if (methods.isEmpty()) emptyList() else methods.split(',')
}

/** Money moved from one account to another: neither an expense nor an income. */
@Entity(tableName = "transfers")
data class TransferEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromId: Long,
    val toId: Long,
    val amountCents: Long,
    /** Epoch day. */
    val date: Long,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

/** "CARD", or "c3" for the custom method 3. */
val PaymentChoice.methodKey: String get() = customId?.let { "c$it" } ?: method.name

/** The account holding [choice], if any. */
fun List<AccountEntity>.accountOf(choice: PaymentChoice): AccountEntity? = firstOrNull { choice.methodKey in it.methodKeys }

/** Movements of [account] registered after its stated balance, so the ones that move it. */
fun List<AccountEntity>.movementsOf(account: AccountEntity, transactions: List<TransactionEntity>) =
    transactions.filter { it.createdAt > account.balanceAt && accountOf(it.paymentChoice)?.id == account.id }

/** Transfers in or out of [account] registered after its stated balance. */
fun List<TransferEntity>.of(account: AccountEntity) =
    filter { it.createdAt > account.balanceAt && (it.fromId == account.id || it.toId == account.id) }

/** What [account] holds now: its stated balance plus what was registered after it. */
fun List<AccountEntity>.balanceOf(account: AccountEntity, transactions: List<TransactionEntity>, transfers: List<TransferEntity>): Long =
    account.balanceCents +
        movementsOf(account, transactions).sumOf { if (it.type == TxType.INCOME) it.amountCents else -it.amountCents } +
        transfers.of(account).sumOf { if (it.toId == account.id) it.amountCents else -it.amountCents }

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY sortOrder, id")
    suspend fun all(): List<AccountEntity>

    @Upsert
    suspend fun upsert(account: AccountEntity): Long

    @Insert
    suspend fun insertAll(accounts: List<AccountEntity>)

    @Delete
    suspend fun delete(account: AccountEntity)

    @Query("DELETE FROM accounts")
    suspend fun clear()
}

@Dao
interface TransferDao {
    @Query("SELECT * FROM transfers ORDER BY date DESC, createdAt DESC")
    fun observeAll(): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfers")
    suspend fun all(): List<TransferEntity>

    @Upsert
    suspend fun upsert(transfer: TransferEntity): Long

    @Insert
    suspend fun insertAll(transfers: List<TransferEntity>)

    @Query("DELETE FROM transfers WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM transfers WHERE fromId = :accountId OR toId = :accountId")
    suspend fun deleteFor(accountId: Long)

    @Query("DELETE FROM transfers")
    suspend fun clear()
}

// --- JSON (same fields as the server and the backup) ------------------------------------------

fun AccountEntity.toJson(): JSONObject = JSONObject()
    .put("id", id).put("name", name).put("icon", icon).put("color", color).put("methods", methods)
    .put("balanceCents", balanceCents).put("balanceAt", balanceAt).put("sortOrder", sortOrder)

fun JSONObject.toAccount() = AccountEntity(
    id = getLong("id"), name = getString("name"), icon = optString("icon", "bank"), color = getLong("color"),
    methods = optString("methods"), balanceCents = optLong("balanceCents"), balanceAt = optLong("balanceAt"),
    sortOrder = optInt("sortOrder"),
)

fun TransferEntity.toJson(): JSONObject = JSONObject()
    .put("id", id).put("fromId", fromId).put("toId", toId).put("amountCents", amountCents).put("date", date)
    .put("note", note).put("createdAt", createdAt)

fun JSONObject.toTransfer() = TransferEntity(
    id = getLong("id"), fromId = getLong("fromId"), toId = getLong("toId"), amountCents = getLong("amountCents"),
    date = getLong("date"), note = optString("note"), createdAt = optLong("createdAt"),
)
