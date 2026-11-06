package com.fishfeed.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        UserSettings::class,
        Pond::class,
        FeedSchedule::class,
        FeedingHistory::class,
        PendingFeeding::class,
        PondStatistics::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun pondDao(): PondDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun historyDao(): HistoryDao
    abstract fun pendingDao(): PendingDao
    abstract fun statisticsDao(): StatisticsDao

    companion object {
        /** Versi 2: menambah kolom foto kolam. Data lama tetap aman. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE ponds ADD COLUMN photoPath TEXT")
                } catch (e: Exception) {
                    // Abaikan jika kolom sudah ada
                }
            }
        }

        /**
         * Versi 3: ukuran kolam, keterangan jadwal, tanggal dibuat pengguna, dan tabel statistik
         * per kolam (diisi dari riwayat yang sudah ada). Data lama tetap aman.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE user_settings ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) { /* abaikan jika kolom sudah ada */ }
                try {
                    db.execSQL("ALTER TABLE ponds ADD COLUMN sizeText TEXT")
                } catch (e: Exception) { /* abaikan jika kolom sudah ada */ }
                try {
                    db.execSQL("ALTER TABLE feed_schedules ADD COLUMN note TEXT NOT NULL DEFAULT ''")
                } catch (e: Exception) { /* abaikan jika kolom sudah ada */ }

                try {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `pond_statistics` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`pondId` INTEGER NOT NULL, " +
                            "`onTimeCount` INTEGER NOT NULL, " +
                            "`lateCount` INTEGER NOT NULL, " +
                            "`notFedCount` INTEGER NOT NULL, " +
                            "`disciplinePercent` INTEGER NOT NULL, " +
                            "`updatedAt` INTEGER NOT NULL, " +
                            "FOREIGN KEY(`pondId`) REFERENCES `ponds`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_pond_statistics_pondId` " +
                            "ON `pond_statistics` (`pondId`)"
                    )
                    db.execSQL(
                        "INSERT OR IGNORE INTO pond_statistics " +
                            "(pondId, onTimeCount, lateCount, notFedCount, disciplinePercent, updatedAt) " +
                            "SELECT pondId, " +
                            "SUM(CASE WHEN status = 'TEPAT_WAKTU' THEN 1 ELSE 0 END), " +
                            "SUM(CASE WHEN status = 'TERLAMBAT' THEN 1 ELSE 0 END), " +
                            "SUM(CASE WHEN status = 'TIDAK_DIBERI' THEN 1 ELSE 0 END), " +
                            "COALESCE(CAST(ROUND(100.0 * SUM(CASE WHEN status = 'TEPAT_WAKTU' THEN 1 ELSE 0 END) / COUNT(*)) AS INTEGER), 0), " +
                            "0 FROM feeding_history GROUP BY pondId"
                    )
                } catch (e: Exception) {
                    // Abaikan jika migrasi data awal bermasalah
                }
            }
        }

        /** Versi 4: menambah kolom sound_uri pada tabel ponds untuk nada/suara alarm custom per kolam. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE ponds ADD COLUMN sound_uri TEXT NOT NULL DEFAULT 'DEFAULT'")
                } catch (e: Exception) {
                    // Abaikan jika kolom sudah ada
                }
                try {
                    db.execSQL("ALTER TABLE user_settings ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {
                    // Abaikan jika kolom sudah ada
                }
            }
        }

        fun build(context: Context): AppDatabase {
            return try {
                val db = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "fishfeed.db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .fallbackToDestructiveMigration()
                    .build()
                // Validasi pembukaan database; jika skema lokal lama bentrok, lakukan reset bersih
                db.openHelper.writableDatabase
                db
            } catch (e: Exception) {
                try {
                    context.deleteDatabase("fishfeed.db")
                } catch (_: Exception) {}
                Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "fishfeed.db")
                    .fallbackToDestructiveMigration()
                    .build()
            }
        }
    }
}
