package com.zenguard.counter

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SlotStateDao {
    @Query("SELECT * FROM slot_state WHERE id = 1")
    fun observe(): Flow<SlotState?>

    @Query("SELECT * FROM slot_state WHERE id = 1")
    suspend fun get(): SlotState?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun ensureRow(state: SlotState = SlotState())

    @Query("UPDATE slot_state SET tokens = tokens + 1 WHERE id = 1")
    suspend fun addToken()

    @Upsert
    suspend fun save(state: SlotState)
}