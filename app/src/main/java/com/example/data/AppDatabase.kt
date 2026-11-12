package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Document::class, MediaTaskHistory::class, DubCueState::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun mediaTaskHistoryDao(): MediaTaskHistoryDao
    abstract fun dubCueStateDao(): DubCueStateDao

    companion object {
        /**
         * v1 -> v2: adds the media_tasks_history table.
         * Older 1.x installs may also be missing columns that were later added to
         * the documents table, so each defensive ALTER is best-effort and safe to
         * run twice. Nothing is destructively wiped on upgrade any more.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `media_tasks_history` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`taskType` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`filePath` TEXT, " +
                        "`fileUri` TEXT, " +
                        "`targetLanguage` TEXT NOT NULL, " +
                        "`srtContent` TEXT NOT NULL, " +
                        "`subtitleCount` INTEGER NOT NULL, " +
                        "`currentChunk` INTEGER NOT NULL, " +
                        "`totalChunks` INTEGER NOT NULL, " +
                        "`status` TEXT NOT NULL, " +
                        "`savedOutputPath` TEXT, " +
                        "`durationMs` INTEGER NOT NULL, " +
                        "`fileSizeBytes` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL)"
                )
                // Best-effort column backfill for very old 1.x databases.
                tryAddColumn(db, "documents", "speed", "REAL NOT NULL DEFAULT 1.0")
                tryAddColumn(db, "documents", "pitch", "REAL NOT NULL DEFAULT 1.0")
                tryAddColumn(db, "documents", "language", "TEXT NOT NULL DEFAULT 'bn'")
                tryAddColumn(db, "documents", "lastModified", "INTEGER NOT NULL DEFAULT 0")
            }

            private fun tryAddColumn(db: SupportSQLiteDatabase, table: String, column: String, decl: String) {
                var exists = false
                try {
                    db.query("PRAGMA table_info(`$table`)").use { cursor ->
                        val nameIdx = cursor.getColumnIndexOrThrow("name")
                        while (cursor.moveToNext()) {
                            if (cursor.getString(nameIdx).equals(column, ignoreCase = true)) {
                                exists = true
                                break
                            }
                        }
                    }
                } catch (_: Exception) {
                    return
                }
                if (!exists) {
                    try {
                        db.execSQL("ALTER TABLE `$table` ADD COLUMN `$column` $decl")
                    } catch (_: Exception) {
                        // Column appeared concurrently or table shape differs; keep going.
                    }
                }
            }
        }

        /**
         * v2 -> v3: adds the dub_cue_states table (locked subtitle blocks and
         * pinned block voices for Dubber V2). Pure addition, no data loss.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `dub_cue_states` (" +
                        "`stateKey` TEXT NOT NULL PRIMARY KEY, " +
                        "`videoKey` TEXT NOT NULL, " +
                        "`cueIndex` INTEGER NOT NULL, " +
                        "`locked` INTEGER NOT NULL DEFAULT 0, " +
                        "`textOverride` TEXT, " +
                        "`voiceName` TEXT, " +
                        "`voicePinned` INTEGER NOT NULL DEFAULT 0)"
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "balaspeak_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    // Only downgrades may wipe data. Upgrades must migrate and keep user history.
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
