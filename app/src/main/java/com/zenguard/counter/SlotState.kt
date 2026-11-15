package com.zenguard.counter

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "slot_state")
data class SlotState(
    @PrimaryKey val id: Int = 1,
    val tokens: Int = 0,
    val dryCount: Int = 0
)