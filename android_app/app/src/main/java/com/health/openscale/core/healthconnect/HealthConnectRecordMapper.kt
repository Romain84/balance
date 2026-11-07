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

import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Power
import com.health.openscale.core.data.HealthConnectSyncState
import com.health.openscale.core.data.MeasurementType
import com.health.openscale.core.data.UnitType
import com.health.openscale.core.data.User
import com.health.openscale.core.model.MeasurementWithValues
import com.health.openscale.core.utils.ConverterUtils
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.reflect.KClass

/** Conversion and identity logic independent from Health Connect IPC. */
internal object HealthConnectRecordMapper {
    private const val KCAL_PER_DAY_TO_WATTS = 4184.0 / 86_400.0
    private const val MAPPER_VERSION = "health-connect-payload-v2"

    fun payloadHash(item: MeasurementWithValues, user: User?): String {
        val supportedIdentities = setOf(
            MeasurementType.WEIGHT.identity,
            MeasurementType.BODY_FAT.identity,
            MeasurementType.WATER.identity,
            MeasurementType.LBM.identity,
            MeasurementType.BONE.identity,
            MeasurementType.BMR.identity
        )
        val content = buildList {
            add(MAPPER_VERSION)
            add(item.measurement.id.toString())
            add(item.measurement.userId.toString())
            add(item.measurement.timestamp.toString())
            val time = Instant.ofEpochMilli(item.measurement.timestamp)
            val originalOffset = item.measurement.timeZoneOffsetSeconds
                ?: ZoneId.systemDefault().rules.getOffset(time).totalSeconds
            add(originalOffset.toString())
            add(user?.heightCm?.toRawBits()?.toString() ?: "no-height")
            item.values
                .filter { it.type.identity in supportedIdentities }
                .sortedBy { it.type.identity }
                .forEach { row -> add("${row.type.identity}:${row.type.unit}:${row.value.floatValue?.toRawBits()}") }
        }.joinToString("|")
        return MessageDigest.getInstance("SHA-256").digest(content.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    fun hasCompatibleInput(item: MeasurementWithValues, user: User?): Boolean {
        if (user?.heightCm?.let { it.isFinite() && it > 0f } == true) return true
        return item.values.any { row ->
            when (row.type.identity) {
                MeasurementType.WEIGHT.identity,
                MeasurementType.LBM.identity,
                MeasurementType.BONE.identity,
                MeasurementType.WATER.identity -> row.type.unit.isWeightUnit() || row.type.unit == UnitType.PERCENT
                MeasurementType.BODY_FAT.identity -> row.type.unit.isWeightUnit() || row.type.unit == UnitType.PERCENT
                MeasurementType.BMR.identity -> row.type.unit == UnitType.KCAL
                else -> false
            }
        }
    }

    fun recordsFor(item: MeasurementWithValues, user: User?, recordVersion: Long): List<androidx.health.connect.client.records.Record> {
        val measurement = item.measurement
        val time = Instant.ofEpochMilli(measurement.timestamp)
        val zoneOffset = measurement.timeZoneOffsetSeconds?.let { ZoneOffset.ofTotalSeconds(it) }
            ?: ZoneId.systemDefault().rules.getOffset(time)
        val values = item.values
        val records = mutableListOf<androidx.health.connect.client.records.Record>()

        fun value(identity: String) = values.firstOrNull { it.type.identity == identity }
        fun positiveFloat(identity: String): Pair<Float, UnitType>? {
            val row = value(identity) ?: return null
            val number = row.value.floatValue?.takeIf { it.isFinite() && it > 0f } ?: return null
            return number to row.type.unit
        }
        fun massKilograms(identity: String): Double? {
            val (amount, unit) = positiveFloat(identity) ?: return null
            if (!unit.isWeightUnit()) return null
            val kg = ConverterUtils.toKilogram(amount, unit.toWeightUnit())
            return kg.toDouble().takeIf { it.isFinite() && it > 0.0 && it <= MAX_BODY_MASS_KG }
        }
        fun percentage(identity: String): Double? {
            val (amount, unit) = positiveFloat(identity) ?: return null
            val percent = when {
                unit == UnitType.PERCENT -> amount.toDouble()
                unit.isWeightUnit() -> {
                    val weightKg = massKilograms(MeasurementType.WEIGHT.identity) ?: return null
                    val partKg = ConverterUtils.toKilogram(amount, unit.toWeightUnit()).toDouble()
                    if (!partKg.isFinite() || partKg <= 0.0 || partKg > weightKg) return null
                    partKg / weightKg * 100.0
                }
                else -> return null
            }
            return percent.takeIf { it.isFinite() && it > 0.0 && it <= 100.0 }
        }
        fun bodyWaterKilograms(): Double? {
            val (amount, unit) = positiveFloat(MeasurementType.WATER.identity) ?: return null
            val kilograms = when {
                unit.isWeightUnit() -> ConverterUtils.toKilogram(amount, unit.toWeightUnit()).toDouble()
                unit == UnitType.PERCENT -> {
                    if (amount > 100f) return null
                    val weightKg = massKilograms(MeasurementType.WEIGHT.identity) ?: return null
                    weightKg * amount.toDouble() / 100.0
                }
                else -> return null
            }
            return kilograms.takeIf { it.isFinite() && it > 0.0 && it <= MAX_BODY_MASS_KG }
        }
        fun metadata(type: String) = Metadata.manualEntry(
            clientRecordId = clientRecordId(measurement.userId, measurement.id, type),
            clientRecordVersion = recordVersion.coerceAtLeast(1L)
        )

        massKilograms(MeasurementType.WEIGHT.identity)?.let { kg ->
            records += WeightRecord(time, zoneOffset, Mass.kilograms(kg), metadata("weight"))
        }
        percentage(MeasurementType.BODY_FAT.identity)?.let { fatPercent ->
            records += BodyFatRecord(time, zoneOffset, Percentage(fatPercent), metadata("body_fat"))
        }
        bodyWaterKilograms()?.let { waterKg ->
            records += BodyWaterMassRecord(time, zoneOffset, Mass.kilograms(waterKg), metadata("body_water_mass"))
        }
        massKilograms(MeasurementType.LBM.identity)?.let { kg ->
            records += LeanBodyMassRecord(time, zoneOffset, Mass.kilograms(kg), metadata("lean_body_mass"))
        }
        massKilograms(MeasurementType.BONE.identity)?.let { kg ->
            records += BoneMassRecord(time, zoneOffset, Mass.kilograms(kg), metadata("bone_mass"))
        }
        val bmr = positiveFloat(MeasurementType.BMR.identity)
        if (bmr?.second == UnitType.KCAL && bmr.first <= MAX_BMR_KCAL_PER_DAY) {
            // openScale stores kcal/day; Health Connect represents BMR as power in watts.
            val watts = bmr.first.toDouble() * KCAL_PER_DAY_TO_WATTS
            if (watts.isFinite() && watts > 0.0) {
                records += BasalMetabolicRateRecord(time, zoneOffset, Power.watts(watts), metadata("basal_metabolic_rate"))
            }
        }
        if (user != null && user.heightCm.isFinite() && user.heightCm > 0f && user.heightCm <= MAX_HEIGHT_CM) {
            records += HeightRecord(time, zoneOffset, Length.meters(user.heightCm.toDouble() / 100.0), metadata("height"))
        }
        // BMI has no matching Health Connect record. Keep it in openScale.
        return records
    }

    /** Stable identifier shared by every write, update and delete of one openScale value. */
    fun clientRecordId(userId: Int, measurementId: Int, type: String): String =
        "openscale:$userId:$measurementId:$type"

    /** Every record type openScale may write, with the suffix used in its clientRecordId. */
    val RECORD_TYPES: List<Pair<String, KClass<out Record>>> = listOf(
        "weight" to WeightRecord::class,
        "body_fat" to BodyFatRecord::class,
        "body_water_mass" to BodyWaterMassRecord::class,
        "lean_body_mass" to LeanBodyMassRecord::class,
        "bone_mass" to BoneMassRecord::class,
        "basal_metabolic_rate" to BasalMetabolicRateRecord::class,
        "height" to HeightRecord::class
    )

    /** The clientRecordId suffix of a record built by [recordsFor], or null for foreign records. */
    fun typeSuffixOf(record: Record): String? =
        RECORD_TYPES.firstOrNull { (_, type) -> type.isInstance(record) }?.first

    /**
     * Decides whether a measurement belongs to the history the user chose not to send.
     * Only measurements that already existed when synchronization was enabled
     * ([historyCutoffId]) and were never delivered are excluded; anything created later —
     * including a weigh-in entered afterwards with a past date — is always synchronized.
     */
    fun isExcludedHistory(
        measurementId: Int,
        historyCutoffId: Int?,
        previousStatus: String?,
        fullHistory: Boolean
    ): Boolean {
        if (fullHistory || historyCutoffId == null) return false
        if (measurementId > historyCutoffId) return false
        return previousStatus == null || previousStatus == HealthConnectSyncState.STATUS_SKIPPED
    }

    private const val MAX_BODY_MASS_KG = 1_000.0
    private const val MAX_BMR_KCAL_PER_DAY = 10_000f
    private const val MAX_HEIGHT_CM = 300f
}
