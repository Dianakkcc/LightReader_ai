package com.lightreader.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [BookEntity::class, ProgressEntity::class, BookmarkEntity::class, FontEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao
    abstract fun progressDao(): ProgressDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun fontDao(): FontDao

    companion object {
        /** v2 adds the flag that marks fonts shipped inside the app. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE fonts ADD COLUMN isBuiltin INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /**
         * v3 splits the shelf into 书籍 / 漫画. Everything defaults to 书籍 and the comic archives
         * that are already on the shelf are moved to the comic shelf, which is what they would have
         * been imported as.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE books ADD COLUMN shelfKind TEXT NOT NULL DEFAULT 'book'",
                )
                database.execSQL("UPDATE books SET shelfKind = 'comic' WHERE format = 'comic'")
            }
        }

        fun create(context: Context): AppDatabase = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "lightreader.db",
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            // A missing migration must fail visibly instead of silently erasing the library.
            .build()
    }
}
