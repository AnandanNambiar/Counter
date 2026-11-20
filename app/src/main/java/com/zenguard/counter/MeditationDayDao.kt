package com.zenguard.counter

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MeditationDayDao {
    @Query("SELECT date FROM meditation_day ORDER BY date DESC")
    fun observeDates(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markDone(day: MeditationDay)

    @Query("SELECT COUNT(*) FROM meditation_day WHERE date = :date")
    suspend fun countOn(date: String): Int

    @Query("SELECT * FROM meditation_day")
    suspend fun getAll(): List<MeditationDay>

    @Query("DELETE FROM meditation_day")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(list: List<MeditationDay>)
}