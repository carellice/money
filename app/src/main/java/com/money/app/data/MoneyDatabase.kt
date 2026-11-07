package com.money.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CategoryEntity::class, TransactionEntity::class, RecurringEntity::class, MemberEntity::class, CustomPaymentEntity::class,
        AccountEntity::class, TransferEntity::class, HiddenPaymentEntity::class],
    version = 7,
    exportSchema = true,
)
abstract class MoneyDatabase : RoomDatabase() {
    abstract fun categories(): CategoryDao
    abstract fun transactions(): TransactionDao
    abstract fun recurring(): RecurringDao
    abstract fun members(): MemberDao
    abstract fun payments(): CustomPaymentDao
    abstract fun accounts(): AccountDao
    abstract fun transfers(): TransferDao
    abstract fun hiddenPayments(): HiddenPaymentDao

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

        /** Accounts of the household and the transfers between them. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `icon` TEXT NOT NULL, `color` INTEGER NOT NULL, `methods` TEXT NOT NULL, " +
                        "`balanceCents` INTEGER NOT NULL, `balanceAt` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `transfers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`fromId` INTEGER NOT NULL, `toId` INTEGER NOT NULL, `amountCents` INTEGER NOT NULL, " +
                        "`date` INTEGER NOT NULL, `note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
            }
        }

        /** Built-in payment methods the user removed. */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `hidden_payments` (`method` TEXT NOT NULL, PRIMARY KEY(`method`))")
            }
        }

        fun build(context: Context): MoneyDatabase =
            Room.databaseBuilder(context, MoneyDatabase::class.java, "money.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()
    }
}
