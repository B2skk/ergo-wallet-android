package org.ergoplatform.android

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.ergoplatform.android.wallet.*

@Database(
    entities = arrayOf(
        WalletConfigDbEntity::class,
        WalletStateDbEntity::class,
        WalletAddressDbEntity::class,
        WalletTokenDbEntity::class
    ), version = 4
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun walletDao(): WalletDbDao

    companion object {

        // For Singleton instantiation
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(context, AppDatabase::class.java, "ergowallet")
                .addMigrations(MIGRATION_1_2)
                .addMigrations(MIGRATION_2_3)
                .addMigrations(MIGRATION_3_4)
                .build()
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE `wallet_states`")
                // taken from AppDatabase_Impl :-)
                database.execSQL("CREATE TABLE IF NOT EXISTS `wallet_states` (`public_address` TEXT NOT NULL, `transactions` INTEGER, `balance` INTEGER, `unconfirmed_balance` INTEGER, PRIMARY KEY(`public_address`))")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE `wallet_states`")
                // taken from AppDatabase_Impl :-)
                database.execSQL("CREATE TABLE IF NOT EXISTS `wallet_states` (`public_address` TEXT NOT NULL, `wallet_first_address` TEXT NOT NULL, `balance` INTEGER, `unconfirmed_balance` INTEGER, PRIMARY KEY(`public_address`))")
                database.execSQL("CREATE TABLE IF NOT EXISTS `wallet_addresses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `wallet_first_address` TEXT NOT NULL, `index` INTEGER NOT NULL, `public_address` TEXT NOT NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS `wallet_tokens` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `public_address` TEXT NOT NULL, `wallet_first_address` TEXT NOT NULL, `token_id` TEXT, `amount` INTEGER, `decimals` INTEGER, `name` TEXT)")
                database.execSQL("ALTER TABLE wallet_configs ADD COLUMN `unfold_tokens` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE wallet_addresses ADD COLUMN `label` TEXT")
            }
        }
    }
}
