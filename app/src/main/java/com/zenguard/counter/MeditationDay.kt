package com.zenguard.counter

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "meditation_day")
data class MeditationDay(
    @PrimaryKey val date: String
)