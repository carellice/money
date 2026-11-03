package com.money.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CategoryEntity::class, TransactionEntity::class, RecurringEntity::class, MemberEntity::class, CustomPaymentEntity::class],
    version = 5,
    exportSchema = true,
)
abstract class MoneyDatabase : RoomDatabase() {
    abstract fun categories(): CategoryDao
    abstract fun transactions(): TransactionDao
    abstract fun recurring(): RecurringDao
    abstract fun members(): MemberDao
    abstract fun payments(): CustomPaymentDao

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

        /** Payment methods added by the user. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `payment_methods` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `icon` TEXT NOT NULL, `color` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL)"
                )
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `paymentId` INTEGER")
                db.execSQL("ALTER TABLE `recurring` ADD COLUMN `paymentId` INTEGER")
            }
        }

        /** Settling up between the people of the household. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `settle` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Financings: how many installments and how many are paid. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `recurring` ADD COLUMN `installments` INTEGER")
                db.execSQL("ALTER TABLE `recurring` ADD COLUMN `installmentsPaid` INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun build(context: Context): MoneyDatabase =
            Room.databaseBuilder(context, MoneyDatabase::class.java, "money.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
    }
}
