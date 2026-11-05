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

    @Query("SELECT * FROM daily_count WHERE counterId = :counterId AND count > 0 ORDER BY date DESC")
    fun observeHistory(counterId: Int): Flow<List<DailyCount>>

    @Query("""
        SELECT d.date AS date, c.name AS name, d.count AS count
        FROM daily_count d JOIN counter c ON c.id = d.counterId
        WHERE d.count > 0
        ORDER BY d.date DESC, c.name
    """)
    fun observeAllHistory(): Flow<List<HistoryRow>>

    @Query("SELECT count FROM daily_count WHERE counterId = :counterId AND date = :date")
    suspend fun getCount(counterId: Int, date: String): Int?

    @Query("INSERT OR IGNORE INTO daily_count (counterId, date, count) VALUES (:counterId, :date, 0)")
    suspend fun ensureRow(counterId: Int, date: String)

    @Query("UPDATE daily_count SET count = count + 1 WHERE counterId = :counterId AND date = :date")
    suspend fun increment(counterId: Int, date: String)

    @Query("UPDATE daily_count SET count = MAX(count - 1, 0) WHERE counterId = :counterId AND date = :date")
    suspend fun decrement(counterId: Int, date: String)

    @Query("SELECT * FROM daily_count")
    suspend fun getAll(): List<DailyCount>

}