package com.zenguard.counter

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyCountDao {

    @Query("SELECT count FROM daily_count WHERE counterId = :counterId AND date = :date")
    fun observeCount(counterId: Int, date: String): Flow<Int?>

    @Query("SELECT COALESCE(SUM(count), 0) FROM daily_count WHERE counterId = :counterId")
    fun observeLifetime(counterId: Int): Flow<Int>

    @Upsert
    suspend fun save(row: DailyCount)

    @Query("DELETE FROM daily_count WHERE counterId = :counterId AND date = :date")
    suspend fun resetDay(counterId: Int, date: String)

    @Query("DELETE FROM daily_count WHERE counterId = :counterId")
    suspend fun resetAll(counterId: Int)
}