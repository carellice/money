package com.money.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CategoryEntity::class, TransactionEntity::class, RecurringEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class MoneyDatabase : RoomDatabase() {
    abstract fun categories(): CategoryDao
    abstract fun transactions(): TransactionDao
    abstract fun recurring(): RecurringDao

    companion object {
        fun build(context: Context): MoneyDatabase =
            Room.databaseBuilder(context, MoneyDatabase::class.java, "money.db").build()
    }
}
