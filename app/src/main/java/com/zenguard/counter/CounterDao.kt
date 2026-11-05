package com.zenguard.counter

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Insert
import kotlinx.coroutines.flow.Flow


@Dao
interface CounterDao {

    @Query("SELECT * FROM counter ORDER BY createdAt")
    fun observeAll(): Flow<List<Counter>>

    @Query("""
        SELECT c.id AS id, c.name AS name,
            COALESCE((SELECT count FROM daily_count
                      WHERE counterId = c.id AND date = :today), 0) AS today,
            COALESCE((SELECT SUM(count) FROM daily_count
                      WHERE counterId = c.id), 0) AS lifetime
        FROM counter c
        ORDER BY c.createdAt
    """)
    fun observeSummaries(today: String): Flow<List<CounterSummary>>

    @Insert
    suspend fun add(counter: Counter): Long

    @Query("UPDATE counter SET name = :name WHERE id = :id")
    suspend fun rename(id: Int, name: String)

    @Query("DELETE FROM counter WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("SELECT * FROM counter WHERE id = :id")
    suspend fun get(id: Int): Counter?

    @Query("UPDATE counter SET focusIntervalSec = :sec, focusTargetMalas = :malas WHERE id = :id")
    suspend fun saveFocus(id: Int, sec: Double, malas: Int)

    @Query("UPDATE counter SET dailyTarget = :target WHERE id = :id")
    suspend fun saveTarget(id: Int, target: Int)

    @Query("""
    SELECT c.* FROM counter c
    WHERE c.dailyTarget > COALESCE(
        (SELECT count FROM daily_count WHERE counterId = c.id AND date = :today), 0)
    ORDER BY c.createdAt
""")
    suspend fun countersBelowTarget(today: String): List<Counter>
    }