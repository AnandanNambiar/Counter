package com.zenguard.counter

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reward")
data class Reward(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val tier: Int   // 0 = small, 1 = medium, 2 = jackpot
)