package com.zenguard.counter

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "counter")
data class Counter(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "1.0") val focusIntervalSec: Double = 1.0,
    @ColumnInfo(defaultValue = "1") val focusTargetMalas: Int = 1,
    @ColumnInfo(defaultValue = "108") val dailyTarget: Int = 108
)