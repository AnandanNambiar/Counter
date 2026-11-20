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

    @Query("SELECT * FROM reward")
    suspend fun getAll(): List<Reward>

    @Query("DELETE FROM reward")
    suspend fun clearAll()

    @Insert
    suspend fun insertAll(list: List<Reward>)
}