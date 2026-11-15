package com.zenguard.counter

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RewardDao {
    @Query("SELECT * FROM reward ORDER BY tier, id")
    fun observeAll(): Flow<List<Reward>>

    @Insert
    suspend fun add(reward: Reward): Long

    @Query("DELETE FROM reward WHERE id = :id")
    suspend fun delete(id: Int)
}