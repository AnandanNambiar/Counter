package com.zenguard.counter

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyCountDao {

    @Query("SELECT count FROM daily_count WHERE counterId = :counterId AND date = :date")
    fun observeCount(counterId: Int, date: String): Flow<Int?>

    @Upsert
    suspend fun save(row: DailyCount)
}