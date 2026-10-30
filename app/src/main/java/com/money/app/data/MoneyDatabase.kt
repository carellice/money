package com.money.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CategoryEntity::class, TransactionEntity::class, RecurringEntity::class, MemberEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class MoneyDatabase : RoomDatabase() {
    abstract fun categories(): CategoryDao
    abstract fun transactions(): TransactionDao
    abstract fun recurring(): RecurringDao
    abstract fun members(): MemberDao

    companion object {
        /** People of the household and whose each movement is. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `members` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `color` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL)"
                )
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `memberId` INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_memberId` ON `transactions` (`memberId`)")
            }
        }

        fun build(context: Context): MoneyDatabase =
            Room.databaseBuilder(context, MoneyDatabase::class.java, "money.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
