package com.zenguard.counter

import java.time.LocalDate

fun computeStreak(done: Set<LocalDate>, today: LocalDate): Int {
    var day = if (today in done) today else today.minusDays(1)
    var streak = 0
    while (true) {
        when {
            day in done -> {
                streak++
                day = day.minusDays(1)
            }
            day.minusDays(1) in done -> day = day.minusDays(1)  // one forgiven gap
            else -> return streak
        }
    }
}