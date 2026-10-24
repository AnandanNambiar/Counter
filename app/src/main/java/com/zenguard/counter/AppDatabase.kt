package com.zenguard.counter

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [DailyCount::class, Counter::class], version = 3)
abstract class AppDatabase : RoomDatabase() {

    abstract fun dailyCountDao(): DailyCountDao
    abstract fun counterDao(): CounterDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "japa.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .build().also { instance = it }
            }
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE counter ADD COLUMN focusIntervalSec REAL NOT NULL DEFAULT 1.0")
        db.execSQL("ALTER TABLE counter ADD COLUMN focusTargetMalas INTEGER NOT NULL DEFAULT 1")
    }
}