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
import androidx.health.connect.client.records.WeightRecord
import com.google.common.truth.Truth.assertThat
import com.health.openscale.core.data.HealthConnectSyncState
import com.health.openscale.core.data.Measurement
import com.health.openscale.core.data.MeasurementType
import com.health.openscale.core.data.MeasurementValue
import com.health.openscale.core.data.ActivityLevel
import com.health.openscale.core.data.GenderType
import com.health.openscale.core.data.UnitType
import com.health.openscale.core.data.User
import com.health.openscale.core.model.MeasurementValueWithType
import com.health.openscale.core.model.MeasurementWithValues
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class HealthConnectRecordMapperTest {
    @Test
    fun mapsSupportedMetricsWithStableIdsAndPreservesTimeZone() {
        val timestamp = Instant.parse("2025-01-02T03:04:05Z").toEpochMilli()
        val item = measurement(
            timestamp,
            listOf(
                value(MeasurementType.WEIGHT.identity, UnitType.LB, 154.32358f),
                value(MeasurementType.BODY_FAT.identity, UnitType.PERCENT, 21.5f),
                value(MeasurementType.LBM.identity, UnitType.KG, 55f),
                value(MeasurementType.BONE.identity, UnitType.LB, 6.61387f),
                value(MeasurementType.BMR.identity, UnitType.KCAL, 1800f),
                value(MeasurementType.WATER.identity, UnitType.PERCENT, 52f),
                value(MeasurementType.BMI.identity, UnitType.NONE, 23f)
            )
        )

        val records = HealthConnectRecordMapper.recordsFor(item, user = null, recordVersion = 3L)

        assertThat(records).hasSize(6)
        assertThat(records.map { it.javaClass }).containsExactly(
            WeightRecord::class.java,
            BodyFatRecord::class.java,
            BodyWaterMassRecord::class.java,
            LeanBodyMassRecord::class.java,
            BoneMassRecord::class.java,
            BasalMetabolicRateRecord::class.java
        ).inOrder()
        val clientRecordIds = listOf(
            (records[0] as WeightRecord).metadata.clientRecordId,
            (records[1] as BodyFatRecord).metadata.clientRecordId,
            (records[2] as BodyWaterMassRecord).metadata.clientRecordId,
            (records[3] as LeanBodyMassRecord).metadata.clientRecordId,
            (records[4] as BoneMassRecord).metadata.clientRecordId,
            (records[5] as BasalMetabolicRateRecord).metadata.clientRecordId
        )
        assertThat(clientRecordIds).containsExactly(
            "openscale:7:41:weight",
            "openscale:7:41:body_fat",
            "openscale:7:41:body_water_mass",
            "openscale:7:41:lean_body_mass",
            "openscale:7:41:bone_mass",
            "openscale:7:41:basal_metabolic_rate"
        ).inOrder()
        val recordVersions = listOf(
            (records[0] as WeightRecord).metadata.clientRecordVersion,
            (records[1] as BodyFatRecord).metadata.clientRecordVersion,
            (records[2] as BodyWaterMassRecord).metadata.clientRecordVersion,
            (records[3] as LeanBodyMassRecord).metadata.clientRecordVersion,
            (records[4] as BoneMassRecord).metadata.clientRecordVersion,
            (records[5] as BasalMetabolicRateRecord).metadata.clientRecordVersion
        )
        assertThat(recordVersions.distinct()).containsExactly(3L)
        val weight = records.first() as WeightRecord
        assertThat(weight.weight.inKilograms).isWithin(0.001).of(70.0)
        assertThat(weight.time).isEqualTo(Instant.ofEpochMilli(timestamp))
        assertThat(weight.zoneOffset).isEqualTo(ZoneOffset.ofHours(1))
        assertThat((records[1] as BodyFatRecord).percentage.value).isWithin(0.001).of(21.5)
        assertThat((records[2] as BodyWaterMassRecord).mass.inKilograms).isWithin(0.001).of(36.4)
        val basalMetabolicRate = records.last() as BasalMetabolicRateRecord
        assertThat(basalMetabolicRate.basalMetabolicRate.inWatts).isWithin(0.001).of(1800.0 * 4184.0 / 86_400.0)
    }

    @Test
    fun derivesFatPercentageAndWaterMassOnlyFromTheSameWeighInWeight() {
        val item = measurement(
            Instant.parse("2025-01-02T03:04:05Z").toEpochMilli(),
            listOf(
                value(MeasurementType.WEIGHT.identity, UnitType.LB, 200f),
                value(MeasurementType.BODY_FAT.identity, UnitType.LB, 40f),
                value(MeasurementType.WATER.identity, UnitType.PERCENT, 50f)
            )
        )

        val records = HealthConnectRecordMapper.recordsFor(item, user = null, recordVersion = 1L)
        val fat = records.filterIsInstance<BodyFatRecord>().single()
        val water = records.filterIsInstance<BodyWaterMassRecord>().single()

        assertThat(fat.percentage.value).isWithin(0.001).of(20.0)
        assertThat(water.mass.inKilograms).isWithin(0.001).of(200.0 / 2.20462 / 2.0)
    }

    @Test
    fun sendsAvailableProfileHeightAtTheMeasurementTime() {
        val timestamp = Instant.parse("2025-01-02T03:04:05Z").toEpochMilli()
        val user = User(
            id = 7,
            name = "User",
            birthDate = 0L,
            gender = GenderType.FEMALE,
            heightCm = 172.5f,
            activityLevel = ActivityLevel.MODERATE,
            useAssistedWeighing = false
        )

        val records = HealthConnectRecordMapper.recordsFor(measurement(timestamp, emptyList()), user, 1L)

        val height = records.filterIsInstance<HeightRecord>().single()
        assertThat(height.height.inMeters).isWithin(0.001).of(1.725)
        assertThat(height.time).isEqualTo(Instant.ofEpochMilli(timestamp))
        assertThat(height.metadata.clientRecordId).isEqualTo("openscale:7:41:height")
    }

    @Test
    fun doesNotDeriveBodyMassPercentagesWithoutWeightFromThatMeasurement() {
        val item = measurement(
            Instant.parse("2025-01-02T03:04:05Z").toEpochMilli(),
            listOf(
                value(MeasurementType.BODY_FAT.identity, UnitType.LB, 30f),
                value(MeasurementType.WATER.identity, UnitType.PERCENT, 50f)
            )
        )

        assertThat(HealthConnectRecordMapper.recordsFor(item, user = null, recordVersion = 1L)).isEmpty()
    }

    @Test
    fun hashIgnoresBmiButChangesWhenSyncedValuesChange() {
        val timestamp = Instant.parse("2025-01-02T03:04:05Z").toEpochMilli()
        val baseline = measurement(
            timestamp,
            listOf(
                value(MeasurementType.WEIGHT.identity, UnitType.KG, 70f),
                value(MeasurementType.WATER.identity, UnitType.PERCENT, 50f),
                value(MeasurementType.BMI.identity, UnitType.NONE, 22f)
            )
        )
        val bmiChanged = baseline.copy(
            values = listOf(
                value(MeasurementType.WEIGHT.identity, UnitType.KG, 70f),
                value(MeasurementType.WATER.identity, UnitType.PERCENT, 50f),
                value(MeasurementType.BMI.identity, UnitType.NONE, 24f)
            )
        )
        val bodyWaterChanged = baseline.copy(
            values = listOf(
                value(MeasurementType.WEIGHT.identity, UnitType.KG, 70f),
                value(MeasurementType.WATER.identity, UnitType.PERCENT, 60f),
                value(MeasurementType.BMI.identity, UnitType.NONE, 22f)
            )
        )
        val supportedValueChanged = baseline.copy(
            values = listOf(
                value(MeasurementType.WEIGHT.identity, UnitType.KG, 71f),
                value(MeasurementType.WATER.identity, UnitType.PERCENT, 50f),
                value(MeasurementType.BMI.identity, UnitType.NONE, 22f)
            )
        )

        val originalHash = HealthConnectRecordMapper.payloadHash(baseline, user = null)
        assertThat(HealthConnectRecordMapper.payloadHash(bmiChanged, user = null))
            .isEqualTo(originalHash)
        assertThat(HealthConnectRecordMapper.payloadHash(bodyWaterChanged, user = null))
            .isNotEqualTo(originalHash)
        assertThat(HealthConnectRecordMapper.payloadHash(supportedValueChanged, user = null))
            .isNotEqualTo(originalHash)
    }

    @Test
    fun formatsClientRecordIdCorrectly() {
        assertThat(HealthConnectRecordMapper.clientRecordId(7, 41, "weight"))
            .isEqualTo("openscale:7:41:weight")
        assertThat(HealthConnectRecordMapper.clientRecordId(1, 100, "height"))
            .isEqualTo("openscale:1:100:height")
    }

    @Test
    fun mapsRecordSuffixAndCoversAllRecordTypes() {
        val timestamp = Instant.parse("2025-01-02T03:04:05Z").toEpochMilli()
        val item = measurement(
            timestamp,
            listOf(
                value(MeasurementType.WEIGHT.identity, UnitType.KG, 70f),
                value(MeasurementType.BODY_FAT.identity, UnitType.PERCENT, 20f),
                value(MeasurementType.WATER.identity, UnitType.KG, 35f),
                value(MeasurementType.LBM.identity, UnitType.KG, 55f),
                value(MeasurementType.BONE.identity, UnitType.KG, 3f),
                value(MeasurementType.BMR.identity, UnitType.KCAL, 1600f)
            )
        )
        val user = User(
            id = 7,
            name = "User",
            birthDate = 0L,
            gender = GenderType.MALE,
            heightCm = 175f,
            activityLevel = ActivityLevel.MODERATE,
            useAssistedWeighing = false
        )
        val records = HealthConnectRecordMapper.recordsFor(item, user, 1L)

        val suffixes = records.map { HealthConnectRecordMapper.typeSuffixOf(it) }
        assertThat(suffixes).containsExactly(
            "weight",
            "body_fat",
            "body_water_mass",
            "lean_body_mass",
            "bone_mass",
            "basal_metabolic_rate",
            "height"
        ).inOrder()

        val registeredSuffixes = HealthConnectRecordMapper.RECORD_TYPES.map { it.first }
        assertThat(registeredSuffixes).containsExactly(
            "weight",
            "body_fat",
            "body_water_mass",
            "lean_body_mass",
            "bone_mass",
            "basal_metabolic_rate",
            "height"
        ).inOrder()
    }

    @Test
    fun excludesHistoryOnlyWhenAppropriate() {
        // When full history is requested, never exclude
        assertThat(HealthConnectRecordMapper.isExcludedHistory(10, 50, null, fullHistory = true)).isFalse()
        assertThat(HealthConnectRecordMapper.isExcludedHistory(10, 50, HealthConnectSyncState.STATUS_SKIPPED, fullHistory = true)).isFalse()

        // When no cutoff exists, never exclude
        assertThat(HealthConnectRecordMapper.isExcludedHistory(10, null, null, fullHistory = false)).isFalse()

        // New measurement created after cutoff is never excluded
        assertThat(HealthConnectRecordMapper.isExcludedHistory(51, 50, null, fullHistory = false)).isFalse()
        assertThat(HealthConnectRecordMapper.isExcludedHistory(100, 50, null, fullHistory = false)).isFalse()

        // Historical measurement (id <= cutoff) with no previous sync or already skipped is excluded
        assertThat(HealthConnectRecordMapper.isExcludedHistory(50, 50, null, fullHistory = false)).isTrue()
        assertThat(HealthConnectRecordMapper.isExcludedHistory(10, 50, null, fullHistory = false)).isTrue()
        assertThat(HealthConnectRecordMapper.isExcludedHistory(10, 50, HealthConnectSyncState.STATUS_SKIPPED, fullHistory = false)).isTrue()

        // Historical measurement that was already synced or queued is not excluded
        assertThat(HealthConnectRecordMapper.isExcludedHistory(10, 50, HealthConnectSyncState.STATUS_SYNCED, fullHistory = false)).isFalse()
        assertThat(HealthConnectRecordMapper.isExcludedHistory(10, 50, HealthConnectSyncState.STATUS_PENDING, fullHistory = false)).isFalse()
    }

    private fun measurement(timestamp: Long, values: List<MeasurementValueWithType>) =
        MeasurementWithValues(
            measurement = Measurement(
                id = 41,
                userId = 7,
                timestamp = timestamp,
                timeZoneOffsetSeconds = 3600
            ),
            values = values
        )

    private fun value(identity: String, unit: UnitType, amount: Float) =
        MeasurementValueWithType(
            value = MeasurementValue(
                measurementId = 41,
                typeId = identity.hashCode(),
                floatValue = amount
            ),
            type = MeasurementType(identity = identity, unit = unit)
        )
}
