package com.zenguard.counter

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM track ORDER BY requiredLevel, id")
    fun observeAll(): Flow<List<Track>>

    @Insert
    suspend fun add(track: Track): Long

    @Query("DELETE FROM track WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("SELECT * FROM track")
    suspend fun getAll(): List<Track>

    @Query("DELETE FROM track")
    suspend fun clearAll()

    @Insert
    suspend fun insertAll(list: List<Track>)
}