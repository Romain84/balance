/*
 * openScale
 * Copyright (C) 2026 openScale contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.health.openscale.core.stoma

import android.content.Context
import android.content.SharedPreferences
import com.health.openscale.core.healthconnect.ClinicalHealthConnectReader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StomaTrackingRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("stoma_tracking_prefs", Context.MODE_PRIVATE)

    private val healthConnectReader = ClinicalHealthConnectReader(context.applicationContext)

    private val file: File
        get() = File(context.filesDir, "stoma_tracking_events.json")

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private val _events = mutableListOf<StomaEvent>()
    private val _eventsFlow = MutableStateFlow<List<StomaEvent>>(emptyList())
    val eventsFlow: StateFlow<List<StomaEvent>> = _eventsFlow.asStateFlow()

    private val _todaySummary = MutableStateFlow(computeSummaryForDate(System.currentTimeMillis()))
    val todaySummary: StateFlow<DailyStomaSummary> = _todaySummary.asStateFlow()

    init {
        loadEvents()
        refreshSummary()
    }

    @Synchronized
    private fun loadEvents() {
        _events.clear()
        try {
            if (file.exists()) {
                val jsonStr = file.readText()
                val jsonArray = JSONArray(jsonStr)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val typeStr = obj.optString("type", StomaEventType.WATER.name)
                    val type = try {
                        StomaEventType.valueOf(typeStr)
                    } catch (_: Exception) {
                        StomaEventType.WATER
                    }
                    _events.add(
                        StomaEvent(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                            type = type,
                            amountMl = obj.optInt("amountMl", 0),
                            note = obj.optString("note", null)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _eventsFlow.value = _events.toList()
    }

    @Synchronized
    private fun saveEvents() {
        try {
            val jsonArray = JSONArray()
            for (ev in _events) {
                val obj = JSONObject().apply {
                    put("id", ev.id)
                    put("timestamp", ev.timestamp)
                    put("type", ev.type.name)
                    put("amountMl", ev.amountMl)
                    if (ev.note != null) put("note", ev.note)
                }
                jsonArray.put(obj)
            }
            val tmp = File(context.filesDir, "stoma_tracking_events.json.tmp")
            tmp.writeText(jsonArray.toString())
            if (tmp.exists()) {
                tmp.renameTo(file)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _eventsFlow.value = _events.toList()
    }

    @Synchronized
    fun addWater(amountMl: Int) {
        val event = StomaEvent(
            type = StomaEventType.WATER,
            amountMl = amountMl
        )
        _events.add(event)
        saveEvents()
        refreshSummary()

        // Sync to Health Connect in background
        CoroutineScope(Dispatchers.IO).launch {
            try {
                healthConnectReader.writeHydration(amountMl, event.timestamp, event.id)
            } catch (_: Exception) {}
        }
    }

    @Synchronized
    fun logPouchChange(note: String? = null) {
        _events.add(
            StomaEvent(
                type = StomaEventType.POUCH_CHANGE,
                note = note
            )
        )
        saveEvents()
        refreshSummary()
    }

    @Synchronized
    fun logSupportChange(note: String? = null) {
        _events.add(
            StomaEvent(
                type = StomaEventType.SUPPORT_CHANGE,
                note = note
            )
        )
        saveEvents()
        refreshSummary()
    }

    @Synchronized
    fun deleteEvent(id: String): Boolean {
        val removed = _events.removeAll { it.id == id }
        if (removed) {
            saveEvents()
            refreshSummary()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    healthConnectReader.deleteHydration(id)
                } catch (_: Exception) {}
            }
        }
        return removed
    }

    suspend fun syncWithHealthConnect(): Int {
        return withContext(Dispatchers.IO) {
            try {
                if (!healthConnectReader.isAvailable()) return@withContext 0
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val startOfDay = cal.timeInMillis
                val endOfDay = System.currentTimeMillis()

                val hcEntries = healthConnectReader.readHydration(startOfDay, endOfDay)
                var importedCount = 0

                val pendingWrites = synchronized(this@StomaTrackingRepository) {
                    val existingHcIds = _events.map { it.id }.toSet()

                    // Import drinks from external sources (e.g. Galaxy Watch / Samsung Health)
                    hcEntries.filter { it.isExternal }.forEach { hc ->
                        val importedId = "hc_${hc.id}"
                        if (importedId !in existingHcIds && hc.amountMl > 0) {
                            _events.add(
                                StomaEvent(
                                    id = importedId,
                                    timestamp = hc.timestamp,
                                    type = StomaEventType.WATER,
                                    amountMl = hc.amountMl,
                                    note = "Montre (Samsung Health)"
                                )
                            )
                            importedCount++
                        }
                    }

                    // Push any local water events of today that aren't yet in Health Connect
                    val existingClientIds = hcEntries.mapNotNull { it.clientRecordId }.toSet()
                    val todayLocalWater = _events.filter {
                        it.type == StomaEventType.WATER &&
                            it.timestamp >= startOfDay &&
                            !it.id.startsWith("hc_")
                    }

                    if (importedCount > 0) {
                        saveEvents()
                        refreshSummary()
                    }

                    todayLocalWater.filter {
                        val clientId = "openscale:water:${it.id}"
                        clientId !in existingClientIds && it.amountMl > 0
                    }
                }

                pendingWrites.forEach { ev ->
                    healthConnectReader.writeHydration(ev.amountMl, ev.timestamp, ev.id)
                }
                importedCount
            } catch (_: Exception) {
                0
            }
        }
    }

    @Synchronized
    fun undoLastEvent(): Boolean {
        if (_events.isNotEmpty()) {
            _events.removeAt(_events.size - 1)
            saveEvents()
            refreshSummary()
            return true
        }
        return false
    }

    @Synchronized
    fun getAllEvents(): List<StomaEvent> {
        return _events.toList()
    }

    fun isNotificationEnabled(): Boolean {
        return prefs.getBoolean("lockscreen_notification_enabled", true)
    }

    fun setNotificationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("lockscreen_notification_enabled", enabled).apply()
    }

    fun getTargetWaterMl(): Int {
        return prefs.getInt("target_water_ml", 2500)
    }

    fun setTargetWaterMl(target: Int) {
        prefs.edit().putInt("target_water_ml", target).apply()
        refreshSummary()
    }

    fun refreshSummary() {
        _todaySummary.value = computeSummaryForDate(System.currentTimeMillis())
    }

    fun getTodaySummary(): DailyStomaSummary {
        val s = computeSummaryForDate(System.currentTimeMillis())
        _todaySummary.value = s
        return s
    }

    @Synchronized
    fun computeSummaryForDate(timestamp: Long): DailyStomaSummary {
        val dateKey = dateFormat.format(Date(timestamp))
        val targetWater = getTargetWaterMl()

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis
        val endOfDay = startOfDay + 24 * 3600 * 1000L - 1

        val todayEvents = _events.filter { it.timestamp in startOfDay..endOfDay }

        val waterToday = todayEvents.filter { it.type == StomaEventType.WATER }.sumOf { it.amountMl }
        val pouchCount = todayEvents.count { it.type == StomaEventType.POUCH_CHANGE }

        // Last support change across all history
        val lastSupportEvent = _events.filter { it.type == StomaEventType.SUPPORT_CHANGE }
            .maxByOrNull { it.timestamp }
        val lastSupportTime = lastSupportEvent?.timestamp

        val daysSinceSupport = if (lastSupportTime != null) {
            val diffMs = (System.currentTimeMillis() - lastSupportTime).coerceAtLeast(0L)
            (diffMs / (24 * 3600 * 1000L)).toInt()
        } else {
            0
        }

        // Clinical Analysis
        val hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val status: ClinicalHydrationStatus
        val advice: String

        if (pouchCount >= 4 && waterToday < 1000) {
            status = ClinicalHydrationStatus.CRITICAL
            advice = "Alerte déshydratation : volume de selles élevé et apport hydrique faible (< 1 L) ! Buvez une solution de réhydratation ou bouillon."
        } else if (daysSinceSupport >= 3) {
            status = ClinicalHydrationStatus.WARNING
            advice = "Support posé depuis $daysSinceSupport jours : prévoir le changement complet pour protéger la peau péristomiale."
        } else if (hourOfDay >= 15 && waterToday < 900) {
            status = ClinicalHydrationStatus.WARNING
            advice = "Hydratation insuffisante pour l'heure actuelle (< 900 ml) : compensez les pertes hydriques de la stomie."
        } else {
            status = ClinicalHydrationStatus.OPTIMAL
            advice = "Équilibre hydrique et protection de stomie optimaux."
        }

        return DailyStomaSummary(
            dateKey = dateKey,
            totalWaterMl = waterToday,
            targetWaterMl = targetWater,
            pouchChangeCount = pouchCount,
            lastSupportChangeTimestamp = lastSupportTime,
            daysSinceLastSupportChange = daysSinceSupport,
            clinicalStatus = status,
            clinicalAdvice = advice
        )
    }

    @Synchronized
    fun getHistorySummaries(days: Int): List<DailyStomaSummary> {
        val list = mutableListOf<DailyStomaSummary>()
        val cal = Calendar.getInstance()
        for (i in 0 until days) {
            val time = cal.timeInMillis
            list.add(computeSummaryForDate(time))
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return list
    }
}
