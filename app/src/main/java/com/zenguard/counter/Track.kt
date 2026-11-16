package com.zenguard.counter

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "track")
data class Track(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val filePath: String,
    val durationSec: Int,
    val requiredLevel: Int = 0   // 0 = starter, 1 = 7 days, 2 = 21 days, 3 = 40 days
)