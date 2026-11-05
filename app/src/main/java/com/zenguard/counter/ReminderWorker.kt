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
        val below = db.counterDao().countersBelowTarget(LocalDate.now().toString())
        if (below.isEmpty()) return Result.success()

        val only = below.singleOrNull()
        Notifier.show(
            applicationContext,
            "Japa Counter",
            "Still to do today: " + below.joinToString(", ") { it.name },
            counterId = only?.id,
            counterName = only?.name
        )
        return Result.success()
    }
}