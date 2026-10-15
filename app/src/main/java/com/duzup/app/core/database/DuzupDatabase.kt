package com.duzup.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        CategoryEntity::class,
        BillEntity::class,
        OccurrenceEntity::class,
        ReminderEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class DuzupDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun billDao(): BillDao
    abstract fun occurrenceDao(): OccurrenceDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        const val NAME = "duzup.db"

        /** Add Migration objects here when the schema version is bumped; never use destructive fallback. */
        val MIGRATIONS: Array<androidx.room.migration.Migration> = emptyArray()
    }
}
