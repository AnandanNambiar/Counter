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
}