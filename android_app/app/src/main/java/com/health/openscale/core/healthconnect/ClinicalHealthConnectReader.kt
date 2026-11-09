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
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Volume
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
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

data class ClinicalHeartRateSummary(
    val hasData: Boolean = false,
    val avgRestingBpm: Int = 0,
    val minBpm: Int = 0,
    val maxBpm: Int = 0
)

data class ClinicalStepsSummary(
    val hasData: Boolean = false,
    val avgDailySteps: Int = 0,
    val totalSteps: Long = 0,
    val daysRecorded: Int = 0
)

data class ClinicalHrvSummary(
    val hasData: Boolean = false,
    val avgRmssdMs: Float = 0f,
    val minRmssdMs: Float = 0f,
    val maxRmssdMs: Float = 0f
)

data class ClinicalSpo2Summary(
    val hasData: Boolean = false,
    val avgSpo2Percent: Float = 0f,
    val minSpo2Percent: Float = 0f
)

data class HydrationEntry(
    val id: String,
    val clientRecordId: String? = null,
    val timestamp: Long = 0L,
    val amountMl: Int = 0,
    val isExternal: Boolean = false
)

data class NightBiomarkersSummary(
    val hasData: Boolean = false,
    val sleep: ClinicalSleepSummary = ClinicalSleepSummary(),
    val temp: ClinicalTempSummary = ClinicalTempSummary(),
    val heartRate: ClinicalHeartRateSummary = ClinicalHeartRateSummary(),
    val hrv: ClinicalHrvSummary = ClinicalHrvSummary(),
    val spo2: ClinicalSpo2Summary = ClinicalSpo2Summary(),
    val lastSyncTime: Long = System.currentTimeMillis()
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

    suspend fun readHeartRateSummary(startMillis: Long, endMillis: Long): ClinicalHeartRateSummary {
        if (!isAvailable()) return ClinicalHeartRateSummary()

        return try {
            val client = HealthConnectClient.getOrCreate(context)
            // 1. Try to read RestingHeartRateRecord first (highest clinical value for autonomic recovery)
            val restingReq = ReadRecordsRequest(
                recordType = RestingHeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(
                    Instant.ofEpochMilli(startMillis),
                    Instant.ofEpochMilli(endMillis)
                )
            )
            val restingRecords = client.readRecords(restingReq).records
            if (restingRecords.isNotEmpty()) {
                val bpms = restingRecords.map { it.beatsPerMinute.toInt() }
                return ClinicalHeartRateSummary(
                    hasData = true,
                    avgRestingBpm = bpms.average().toInt(),
                    minBpm = bpms.minOrNull() ?: 0,
                    maxBpm = bpms.maxOrNull() ?: 0
                )
            }

            // 2. Fallback to HeartRateRecord samples if no resting records
            val hrReq = ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(
                    Instant.ofEpochMilli(startMillis),
                    Instant.ofEpochMilli(endMillis)
                )
            )
            val hrRecords = client.readRecords(hrReq).records
            val allSamples = hrRecords.flatMap { it.samples }.map { it.beatsPerMinute.toInt() }
            if (allSamples.isNotEmpty()) {
                ClinicalHeartRateSummary(
                    hasData = true,
                    avgRestingBpm = allSamples.average().toInt(),
                    minBpm = allSamples.minOrNull() ?: 0,
                    maxBpm = allSamples.maxOrNull() ?: 0
                )
            } else {
                ClinicalHeartRateSummary()
            }
        } catch (_: Exception) {
            ClinicalHeartRateSummary()
        }
    }

    suspend fun readStepsSummary(startMillis: Long, endMillis: Long): ClinicalStepsSummary {
        if (!isAvailable()) return ClinicalStepsSummary()

        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val request = ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(
                    Instant.ofEpochMilli(startMillis),
                    Instant.ofEpochMilli(endMillis)
                )
            )
            val records = client.readRecords(request).records
            if (records.isEmpty()) return ClinicalStepsSummary()

            val zone = ZoneId.systemDefault()
            val dailySteps = mutableMapOf<LocalDate, Long>()
            var total = 0L

            records.forEach { record ->
                val date = record.startTime.atZone(zone).toLocalDate()
                dailySteps[date] = (dailySteps[date] ?: 0L) + record.count
                total += record.count
            }

            val dayCount = dailySteps.size.coerceAtLeast(1)
            ClinicalStepsSummary(
                hasData = true,
                totalSteps = total,
                avgDailySteps = (total / dayCount).toInt(),
                daysRecorded = dailySteps.size
            )
        } catch (_: Exception) {
            ClinicalStepsSummary()
        }
    }

    suspend fun readHrvSummary(startMillis: Long, endMillis: Long): ClinicalHrvSummary {
        if (!isAvailable()) return ClinicalHrvSummary()
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val request = ReadRecordsRequest(
                recordType = HeartRateVariabilityRmssdRecord::class,
                timeRangeFilter = TimeRangeFilter.between(
                    Instant.ofEpochMilli(startMillis),
                    Instant.ofEpochMilli(endMillis)
                )
            )
            val records = client.readRecords(request).records
            if (records.isEmpty()) return ClinicalHrvSummary()
            val vals = records.map { it.heartRateVariabilityMillis.toFloat() }
            ClinicalHrvSummary(
                hasData = true,
                avgRmssdMs = vals.average().toFloat(),
                minRmssdMs = vals.minOrNull() ?: 0f,
                maxRmssdMs = vals.maxOrNull() ?: 0f
            )
        } catch (_: Exception) {
            ClinicalHrvSummary()
        }
    }

    suspend fun readOxygenSaturationSummary(startMillis: Long, endMillis: Long): ClinicalSpo2Summary {
        if (!isAvailable()) return ClinicalSpo2Summary()
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val request = ReadRecordsRequest(
                recordType = OxygenSaturationRecord::class,
                timeRangeFilter = TimeRangeFilter.between(
                    Instant.ofEpochMilli(startMillis),
                    Instant.ofEpochMilli(endMillis)
                )
            )
            val records = client.readRecords(request).records
            if (records.isEmpty()) return ClinicalSpo2Summary()
            val vals = records.map { it.percentage.value.toFloat() }
            ClinicalSpo2Summary(
                hasData = true,
                avgSpo2Percent = vals.average().toFloat(),
                minSpo2Percent = vals.minOrNull() ?: 0f
            )
        } catch (_: Exception) {
            ClinicalSpo2Summary()
        }
    }

    suspend fun readHydration(startMillis: Long, endMillis: Long): List<HydrationEntry> {
        if (!isAvailable()) return emptyList()
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val request = ReadRecordsRequest(
                recordType = HydrationRecord::class,
                timeRangeFilter = TimeRangeFilter.between(
                    Instant.ofEpochMilli(startMillis),
                    Instant.ofEpochMilli(endMillis)
                )
            )
            val records = client.readRecords(request).records
            records.map { rec ->
                val clientRecordId = rec.metadata.clientRecordId
                val isOwn = clientRecordId?.startsWith("openscale:") == true
                HydrationEntry(
                    id = rec.metadata.id,
                    clientRecordId = clientRecordId,
                    timestamp = rec.startTime.toEpochMilli(),
                    amountMl = (rec.volume.inLiters * 1000.0).toInt(),
                    isExternal = !isOwn
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun writeHydration(amountMl: Int, timestamp: Long, eventId: String): Boolean {
        if (!isAvailable()) return false
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val start = Instant.ofEpochMilli(timestamp)
            val end = start.plusSeconds(1)
            val zone = ZoneId.systemDefault().rules.getOffset(start)
            val record = HydrationRecord(
                startTime = start,
                startZoneOffset = zone,
                endTime = end,
                endZoneOffset = zone,
                volume = Volume.liters(amountMl.toDouble() / 1000.0),
                metadata = Metadata.manualEntry(
                    clientRecordId = "openscale:water:$eventId"
                )
            )
            client.insertRecords(listOf(record))
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun deleteHydration(eventId: String): Boolean {
        if (!isAvailable()) return false
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            client.deleteRecords(
                recordType = HydrationRecord::class,
                recordIdsList = emptyList(),
                clientRecordIdsList = listOf("openscale:water:$eventId")
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun readLastNightBiomarkers(): NightBiomarkersSummary {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 18)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startWindow = cal.timeInMillis
        val sleep = readSleepSummary(startWindow, now)
        val temp = readBasalTempSummary(startWindow, now)
        val hr = readHeartRateSummary(startWindow, now)
        val hrv = readHrvSummary(startWindow, now)
        val spo2 = readOxygenSaturationSummary(startWindow, now)
        val hasData = sleep.hasData || temp.hasData || hr.hasData || hrv.hasData || spo2.hasData
        return NightBiomarkersSummary(
            hasData = hasData,
            sleep = sleep,
            temp = temp,
            heartRate = hr,
            hrv = hrv,
            spo2 = spo2,
            lastSyncTime = now
        )
    }
}
