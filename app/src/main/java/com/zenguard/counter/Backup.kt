package com.zenguard.counter

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject

data class BackupData(
    val version: Int,
    val counters: List<Counter>,
    val days: List<DailyCount>,
    val rewards: List<Reward>,
    val slot: SlotState?,
    val tracks: List<Track>,
    val meditationDays: List<MeditationDay>
)

suspend fun buildBackupJson(db: AppDatabase): String {
    val root = JSONObject()
    root.put("version", 2)

    val counterArray = JSONArray()
    db.counterDao().getAll().forEach { c ->
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
    db.dailyCountDao().getAll().forEach { d ->
        dayArray.put(
            JSONObject()
                .put("counterId", d.counterId)
                .put("date", d.date)
                .put("count", d.count)
        )
    }
    root.put("days", dayArray)

    val rewardArray = JSONArray()
    db.rewardDao().getAll().forEach { r ->
        rewardArray.put(
            JSONObject().put("id", r.id).put("name", r.name).put("tier", r.tier)
        )
    }
    root.put("rewards", rewardArray)

    db.slotStateDao().get()?.let {
        root.put("slot", JSONObject().put("tokens", it.tokens).put("dryCount", it.dryCount))
    }

    val trackArray = JSONArray()
    db.trackDao().getAll().forEach { t ->
        trackArray.put(
            JSONObject()
                .put("id", t.id)
                .put("name", t.name)
                .put("filePath", t.filePath)
                .put("durationSec", t.durationSec)
                .put("requiredLevel", t.requiredLevel)
        )
    }
    root.put("tracks", trackArray)

    val medArray = JSONArray()
    db.meditationDayDao().getAll().forEach { medArray.put(it.date) }
    root.put("meditationDays", medArray)

    return root.toString(2)
}

fun parseBackupJson(json: String): BackupData {
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

    val rewards = mutableListOf<Reward>()
    root.optJSONArray("rewards")?.let { arr ->
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            rewards += Reward(id = o.getInt("id"), name = o.getString("name"), tier = o.getInt("tier"))
        }
    }

    val slot = root.optJSONObject("slot")?.let {
        SlotState(id = 1, tokens = it.getInt("tokens"), dryCount = it.getInt("dryCount"))
    }

    val tracks = mutableListOf<Track>()
    root.optJSONArray("tracks")?.let { arr ->
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            tracks += Track(
                id = o.getInt("id"),
                name = o.getString("name"),
                filePath = o.getString("filePath"),
                durationSec = o.getInt("durationSec"),
                requiredLevel = o.getInt("requiredLevel")
            )
        }
    }

    val medDays = mutableListOf<MeditationDay>()
    root.optJSONArray("meditationDays")?.let { arr ->
        for (i in 0 until arr.length()) medDays += MeditationDay(arr.getString(i))
    }

    return BackupData(root.optInt("version", 1), counters, days, rewards, slot, tracks, medDays)
}

suspend fun restoreBackup(db: AppDatabase, b: BackupData) {
    db.withTransaction {
        db.dailyCountDao().clearAll()
        db.counterDao().clearAll()
        db.counterDao().insertAll(b.counters)
        db.dailyCountDao().insertAll(b.days)
        if (b.version >= 2) {
            db.rewardDao().clearAll()
            db.trackDao().clearAll()
            db.meditationDayDao().clearAll()
            db.rewardDao().insertAll(b.rewards)
            db.trackDao().insertAll(b.tracks)
            db.meditationDayDao().insertAll(b.meditationDays)
            b.slot?.let { db.slotStateDao().save(it) }
        }
    }
}