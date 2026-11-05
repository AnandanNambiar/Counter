package com.zenguard.counter

const val STREAK_GOAL = 108
const val LIFETIME_GOAL = 10_000

data class Milestone(val key: String, val title: String)

fun reachedMilestones(streak: Int, lifetime: Int): List<Milestone> {
    val list = mutableListOf<Milestone>()
    if (streak >= STREAK_GOAL) list += Milestone("streak", "$STREAK_GOAL-day streak")
    if (lifetime >= LIFETIME_GOAL) list += Milestone("lifetime", "$LIFETIME_GOAL counts")
    return list
}