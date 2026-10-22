package com.zenguard.counter

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Insert
import kotlinx.coroutines.flow.Flow

@Dao
interface CounterDao {

    @Query("SELECT * FROM counter ORDER BY createdAt")
    fun observeAll(): Flow<List<Counter>>

    @Insert
    suspend fun add(counter: Counter): Long

    @Query("UPDATE counter SET name = :name WHERE id = :id")
    suspend fun rename(id: Int, name: String)

    @Query("DELETE FROM counter WHERE id = :id")
    suspend fun delete(id: Int)

}