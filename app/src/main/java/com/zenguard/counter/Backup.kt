package com.zenguard.counter

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject

fun buildBackupJson(counters: List<Counter>, days: List<DailyCount>): String {
    val root = JSONObject()
    root.put("version", 1)

    val counterArray = JSONArray()
    counters.forEach { c ->
        counterArray.put(
            JSONObject()
                .put("id", c.id)
                .put("name", c.name)
                .put("createdAt", c.createdAt)
                .put("focusIntervalSec", c.focusIntervalSec)
                .put("focusTargetMalas", c.focusTargetMalas)
                .put("dailyTarget", c.dailyTarget)
        )
    }
    root.put("counters", counterArray)

    val dayArray = JSONArray()
    days.forEach { d ->
        dayArray.put(
            JSONObject()
                .put("counterId", d.counterId)
                .put("date", d.date)
                .put("count", d.count)
        )
    }
    root.put("days", dayArray)

    return root.toString(2)
}

fun parseBackupJson(json: String): Pair<List<Counter>, List<DailyCount>> {
    val root = JSONObject(json)

    val counters = mutableListOf<Counter>()
    val counterArray = root.getJSONArray("counters")
    for (i in 0 until counterArray.length()) {
        val o = counterArray.getJSONObject(i)
        counters += Counter(
            id = o.getInt("id"),
            name = o.getString("name"),
            createdAt = o.getLong("createdAt"),
            focusIntervalSec = o.getDouble("focusIntervalSec"),
            focusTargetMalas = o.getInt("focusTargetMalas"),
            dailyTarget = o.getInt("dailyTarget")
        )
    }

    val days = mutableListOf<DailyCount>()
    val dayArray = root.getJSONArray("days")
    for (i in 0 until dayArray.length()) {
        val o = dayArray.getJSONObject(i)
        days += DailyCount(
            counterId = o.getInt("counterId"),
            date = o.getString("date"),
            count = o.getInt("count")
        )
    }

    return counters to days
}

suspend fun restoreBackup(db: AppDatabase, counters: List<Counter>, days: List<DailyCount>) {
    db.withTransaction {
        db.dailyCountDao().clearAll()
        db.counterDao().clearAll()
        db.counterDao().insertAll(counters)
        db.dailyCountDao().insertAll(days)
    }
}