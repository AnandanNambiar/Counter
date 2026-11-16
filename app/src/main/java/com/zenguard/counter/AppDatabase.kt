package com.zenguard.counter

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DailyCount::class, Counter::class, Reward::class, SlotState::class, Track::class],
    version = 7
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun dailyCountDao(): DailyCountDao
    abstract fun counterDao(): CounterDao
    abstract fun rewardDao(): RewardDao
    abstract fun slotStateDao(): SlotStateDao
    abstract fun trackDao(): TrackDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "japa.db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
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

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE counter ADD COLUMN dailyTarget INTEGER NOT NULL DEFAULT 108")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS reward (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "tier INTEGER NOT NULL)"
        )
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS slot_state (" +
                    "id INTEGER PRIMARY KEY NOT NULL, " +
                    "tokens INTEGER NOT NULL, " +
                    "dryCount INTEGER NOT NULL)"
        )
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS track (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "filePath TEXT NOT NULL, " +
                    "durationSec INTEGER NOT NULL, " +
                    "requiredLevel INTEGER NOT NULL)"
        )
    }
}