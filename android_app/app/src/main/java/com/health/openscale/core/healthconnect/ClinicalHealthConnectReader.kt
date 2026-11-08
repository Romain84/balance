/*
 * openScale
 * Copyright (C) 2026 openScale contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.health.openscale.core.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BasalBodyTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

data class ClinicalSleepSummary(
    val hasData: Boolean = false,
    val avgDurationMinutes: Int = 0,
    val avgAwakeningsPerNight: Float = 0f,
    val totalNights: Int = 0,
    val dailySleepMinutes: Map<LocalDate, Int> = emptyMap(),
    val dailyAwakenings: Map<LocalDate, Int> = emptyMap()
) {
    val formattedAvgDuration: String
        get() {
            if (!hasData || avgDurationMinutes <= 0) return "--"
            val h = avgDurationMinutes / 60
            val m = avgDurationMinutes % 60
            return "${h}h${if (m < 10) "0$m" else "$m"}"
        }
}

data class ClinicalTempSummary(
    val hasData: Boolean = false,
    val avgTempCelsius: Float = 0f,
    val highPhaseDetected: Boolean = false,
    val highPhaseAvgCelsius: Float = 0f,
    val lowPhaseAvgCelsius: Float = 0f,
    val deltaCelsius: Float = 0f,
    val highPhaseDaysWindow: String? = null,
    val dailyTemps: Map<LocalDate, Float> = emptyMap()
)

@Singleton
class ClinicalHealthConnectReader @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun hasSleepPermission(): Boolean {
        if (!isAvailable()) return false
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            granted.contains(HealthPermission.getReadPermission(SleepSessionRecord::class))
        } catch (_: Exception) {
            false
        }
    }

    suspend fun hasTempPermission(): Boolean {
        if (!isAvailable()) return false
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            granted.contains(HealthPermission.getReadPermission(BasalBodyTemperatureRecord::class))
        } catch (_: Exception) {
            false
        }
    }

    suspend fun readSleepSummary(startMillis: Long, endMillis: Long): ClinicalSleepSummary {
        if (!isAvailable()) return ClinicalSleepSummary()

        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val request = ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(
                    Instant.ofEpochMilli(startMillis),
                    Instant.ofEpochMilli(endMillis)
                )
            )
            val response = client.readRecords(request)
            val records = response.records

            if (records.isEmpty()) {
                return ClinicalSleepSummary()
            }

            val zone = ZoneId.systemDefault()
            val dailyMinutes = mutableMapOf<LocalDate, Int>()
            val dailyAwakenings = mutableMapOf<LocalDate, Int>()

            var totalMinutes = 0
            var totalAwakenings = 0

            records.forEach { session ->
                val date = session.startTime.atZone(zone).toLocalDate()
                val durationMin = Duration.between(session.startTime, session.endTime).toMinutes().toInt()

                // Count awakenings in stages if available
                val awakenings = session.stages.count { stage ->
                    stage.stage == SleepSessionRecord.STAGE_TYPE_AWAKE ||
                        stage.stage == SleepSessionRecord.STAGE_TYPE_OUT_OF_BED
                }

                dailyMinutes[date] = (dailyMinutes[date] ?: 0) + durationMin
                dailyAwakenings[date] = (dailyAwakenings[date] ?: 0) + awakenings

                totalMinutes += durationMin
                totalAwakenings += awakenings
            }

            val nightCount = dailyMinutes.size.coerceAtLeast(1)
            val avgMin = totalMinutes / nightCount
            val avgAwake = totalAwakenings.toFloat() / nightCount

            ClinicalSleepSummary(
                hasData = true,
                avgDurationMinutes = avgMin,
                avgAwakeningsPerNight = avgAwake,
                totalNights = dailyMinutes.size,
                dailySleepMinutes = dailyMinutes,
                dailyAwakenings = dailyAwakenings
            )
        } catch (_: Exception) {
            ClinicalSleepSummary()
        }
    }

    suspend fun readBasalTempSummary(startMillis: Long, endMillis: Long): ClinicalTempSummary {
        if (!isAvailable()) return ClinicalTempSummary()

        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val request = ReadRecordsRequest(
                recordType = BasalBodyTemperatureRecord::class,
                timeRangeFilter = TimeRangeFilter.between(
                    Instant.ofEpochMilli(startMillis),
                    Instant.ofEpochMilli(endMillis)
                )
            )
            val response = client.readRecords(request)
            val records = response.records

            if (records.isEmpty()) {
                return ClinicalTempSummary()
            }

            val zone = ZoneId.systemDefault()
            val dailyTemps = mutableMapOf<LocalDate, Float>()

            records.forEach { record ->
                val date = record.time.atZone(zone).toLocalDate()
                val tempC = record.temperature.inCelsius.toFloat()
                dailyTemps[date] = tempC
            }

            if (dailyTemps.isEmpty()) {
                return ClinicalTempSummary()
            }

            val tempsList = dailyTemps.values.toList()
            val avgTemp = tempsList.average().toFloat()

            // Detect biphasic thermal shift (characteristic of luteal phase ovarienne):
            // Temperatures in upper half vs lower half
            val sortedTemps = tempsList.sorted()
            val lowHalf = sortedTemps.take(sortedTemps.size / 2)
            val highHalf = sortedTemps.takeLast(sortedTemps.size / 2)

            val lowAvg = if (lowHalf.isNotEmpty()) lowHalf.average().toFloat() else avgTemp
            val highAvg = if (highHalf.isNotEmpty()) highHalf.average().toFloat() else avgTemp
            val delta = highAvg - lowAvg

            // A shift of >= 0.25°C to 0.5°C indicates an active thermal luteal shift
            val isHighPhaseDetected = delta >= 0.25f

            ClinicalTempSummary(
                hasData = true,
                avgTempCelsius = avgTemp,
                highPhaseDetected = isHighPhaseDetected,
                highPhaseAvgCelsius = highAvg,
                lowPhaseAvgCelsius = lowAvg,
                deltaCelsius = delta,
                highPhaseDaysWindow = if (isHighPhaseDetected) "Phase lutéale active (+${String.format(Locale.FRANCE, "%.2f", delta)}°C)" else null,
                dailyTemps = dailyTemps
            )
        } catch (_: Exception) {
            ClinicalTempSummary()
        }
    }
}
