package com.zenguard.counter

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.LocalDate
import java.time.LocalTime

class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ignoreQuiet = inputData.getBoolean("ignoreQuiet", false)
        val hour = LocalTime.now().hour
        if (!ignoreQuiet && (hour >= 22 || hour < 6)) return Result.success()

        val db = AppDatabase.get(applicationContext)
        val today = LocalDate.now().toString()

        val below = db.counterDao().countersBelowTarget(today)
        if (below.isNotEmpty()) {
            val only = below.singleOrNull()
            Notifier.show(
                applicationContext,
                "Japa Counter",
                "Still to do today: " + below.joinToString(", ") { it.name },
                counterId = only?.id,
                counterName = only?.name
            )
        }

        if (db.meditationDayDao().countOn(today) == 0) {
            Notifier.show(
                applicationContext,
                "Meditate",
                "A quiet session is waiting whenever you're ready.",
                openMeditate = true,
                notificationId = 2
            )
        }
        return Result.success()
    }
}