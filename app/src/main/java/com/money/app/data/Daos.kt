package com.money.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories")
    suspend fun all(): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Insert
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Upsert
    suspend fun upsert(category: CategoryEntity): Long

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE parentId = :parentId")
    suspend fun deleteChildren(parentId: Long)

    @Query("DELETE FROM categories")
    suspend fun clear()
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE date BETWEEN :from AND :to ORDER BY date DESC, createdAt DESC")
    fun observeRange(from: Long, to: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE recurringId = :recurringId ORDER BY date DESC, createdAt DESC")
    fun observeByRecurring(recurringId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY date DESC, createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE date BETWEEN :from AND :to ORDER BY date, createdAt")
    suspend fun range(from: Long, to: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions")
    suspend fun all(): List<TransactionEntity>

    /** The ones that move account balances stated at [since] or later. */
    @Query("SELECT * FROM transactions WHERE createdAt > :since")
    fun observeCreatedAfter(since: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: Long): TransactionEntity?

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :categoryId OR subcategoryId = :categoryId")
    suspend fun countForCategory(categoryId: Long): Int

    @Upsert
    suspend fun upsert(tx: TransactionEntity): Long

    @Insert
    suspend fun insertAll(txs: List<TransactionEntity>)

    @Delete
    suspend fun delete(tx: TransactionEntity)

    @Query("UPDATE transactions SET subcategoryId = NULL WHERE subcategoryId = :subId")
    suspend fun clearSubcategory(subId: Long)

    @Query("UPDATE transactions SET memberId = NULL WHERE memberId = :memberId")
    suspend fun clearMember(memberId: Long)

    @Query("UPDATE transactions SET paymentId = NULL WHERE paymentId = :paymentId")
    suspend fun clearPayment(paymentId: Long)

    /** Moves what was paid with [fromMethod]/[fromId] (a custom id, or null for a built-in method) to [toMethod]/[toId]. */
    @Query(
        "UPDATE transactions SET payment = :toMethod, paymentId = :toId " +
            "WHERE (:fromId IS NOT NULL AND paymentId = :fromId) OR (:fromId IS NULL AND paymentId IS NULL AND payment = :fromMethod)"
    )
    suspend fun reassignPayment(fromMethod: String, fromId: Long?, toMethod: String, toId: Long?)

    @Query("SELECT COUNT(*) FROM transactions WHERE (:fromId IS NOT NULL AND paymentId = :fromId) OR (:fromId IS NULL AND paymentId IS NULL AND payment = :fromMethod)")
    suspend fun countForPayment(fromMethod: String, fromId: Long?): Int

    /** Settled up: these expenses don't count again. */
    @Query("UPDATE transactions SET settle = 2 WHERE settle = 0 AND id IN (:ids)")
    suspend fun markSettled(ids: List<Long>)

    @Query("DELETE FROM transactions")
    suspend fun clear()
}

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring ORDER BY active DESC, nextDate")
    fun observeAll(): Flow<List<RecurringEntity>>

    @Query("SELECT * FROM recurring WHERE active = 1")
    suspend fun active(): List<RecurringEntity>

    @Query("SELECT * FROM recurring")
    suspend fun all(): List<RecurringEntity>

    @Query("SELECT * FROM recurring WHERE id = :id")
    suspend fun get(id: Long): RecurringEntity?

    @Query("SELECT COUNT(*) FROM recurring WHERE categoryId = :categoryId OR subcategoryId = :categoryId")
    suspend fun countForCategory(categoryId: Long): Int

    @Upsert
    suspend fun upsert(item: RecurringEntity): Long

    @Update
    suspend fun update(item: RecurringEntity)

    @Insert
    suspend fun insertAll(items: List<RecurringEntity>)

    @Delete
    suspend fun delete(item: RecurringEntity)

    @Query("UPDATE recurring SET paymentId = NULL WHERE paymentId = :paymentId")
    suspend fun clearPayment(paymentId: Long)

    @Query(
        "UPDATE recurring SET payment = :toMethod, paymentId = :toId " +
            "WHERE (:fromId IS NOT NULL AND paymentId = :fromId) OR (:fromId IS NULL AND paymentId IS NULL AND payment = :fromMethod)"
    )
    suspend fun reassignPayment(fromMethod: String, fromId: Long?, toMethod: String, toId: Long?)

    @Query("SELECT COUNT(*) FROM recurring WHERE (:fromId IS NOT NULL AND paymentId = :fromId) OR (:fromId IS NULL AND paymentId IS NULL AND payment = :fromMethod)")
    suspend fun countForPayment(fromMethod: String, fromId: Long?): Int

    @Query("DELETE FROM recurring")
    suspend fun clear()
}

@Dao
interface MemberDao {
    @Query("SELECT * FROM members ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members ORDER BY sortOrder, id")
    suspend fun all(): List<MemberEntity>

    @Upsert
    suspend fun upsert(member: MemberEntity): Long

    @Insert
    suspend fun insertAll(members: List<MemberEntity>)

    @Delete
    suspend fun delete(member: MemberEntity)

    @Query("DELETE FROM members")
    suspend fun clear()
}

@Dao
interface HiddenPaymentDao {
    @Query("SELECT * FROM hidden_payments")
    fun observeAll(): Flow<List<HiddenPaymentEntity>>

    @Query("SELECT * FROM hidden_payments")
    suspend fun all(): List<HiddenPaymentEntity>

    @Upsert
    suspend fun insertAll(methods: List<HiddenPaymentEntity>)

    @Query("DELETE FROM hidden_payments")
    suspend fun clear()
}

@Dao
interface CustomPaymentDao {
    @Query("SELECT * FROM payment_methods ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<CustomPaymentEntity>>

    @Query("SELECT * FROM payment_methods ORDER BY sortOrder, id")
    suspend fun all(): List<CustomPaymentEntity>

    @Upsert
    suspend fun upsert(method: CustomPaymentEntity): Long

    @Insert
    suspend fun insertAll(methods: List<CustomPaymentEntity>)

    @Delete
    suspend fun delete(method: CustomPaymentEntity)

    @Query("DELETE FROM payment_methods")
    suspend fun clear()
}
