package com.zenguard.counter

data class CounterSummary(
    val id: Int,
    val name: String,
    val today: Int,
    val lifetime: Int
)