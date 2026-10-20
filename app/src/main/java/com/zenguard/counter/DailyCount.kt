package com.zenguard.counter

import androidx.room.Entity

@Entity(tableName = "daily_count", primaryKeys = ["counterId", "date"])
data class DailyCount(
    val counterId: Int,
    val date: String,
    val count: Int
)
