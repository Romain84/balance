/*
 * openScale
 * Copyright (C) 2026 openScale contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.health.openscale.core.usecase

import com.health.openscale.core.data.HealthCheckin
import com.health.openscale.core.database.DatabaseRepository
import com.health.openscale.core.facade.SettingsFacade
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

data class HealthAssistantSettings(
    val isEnabled: Boolean = false,
    val isStomaEnabled: Boolean = true,
    val isEndoEnabled: Boolean = true,
    val isGlutenEnabled: Boolean = true
)

@Singleton
class HealthCheckinUseCases @Inject constructor(
    private val repository: DatabaseRepository,
    private val settings: SettingsFacade
) {
    // --- Settings per user profile ---

    fun isHealthAssistantEnabled(userId: Int): Flow<Boolean> =
        settings.observeSetting("health_assistant_enabled_$userId", false)

    suspend fun setHealthAssistantEnabled(userId: Int, enabled: Boolean) {
        settings.saveSetting("health_assistant_enabled_$userId", enabled)
    }

    fun isStomaTrackingEnabled(userId: Int): Flow<Boolean> =
        settings.observeSetting("health_stoma_enabled_$userId", true)

    suspend fun setStomaTrackingEnabled(userId: Int, enabled: Boolean) {
        settings.saveSetting("health_stoma_enabled_$userId", enabled)
    }

    fun isEndoTrackingEnabled(userId: Int): Flow<Boolean> =
        settings.observeSetting("health_endo_enabled_$userId", true)

    suspend fun setEndoTrackingEnabled(userId: Int, enabled: Boolean) {
        settings.saveSetting("health_endo_enabled_$userId", enabled)
    }

    fun isGlutenTrackingEnabled(userId: Int): Flow<Boolean> =
        settings.observeSetting("health_gluten_enabled_$userId", true)

    suspend fun setGlutenTrackingEnabled(userId: Int, enabled: Boolean) {
        settings.saveSetting("health_gluten_enabled_$userId", enabled)
    }

    // --- Check-in Operations ---

    suspend fun saveCheckin(checkin: HealthCheckin): Long {
        return if (checkin.id == 0) {
            repository.insertHealthCheckin(checkin)
        } else {
            repository.updateHealthCheckin(checkin)
            checkin.id.toLong()
        }
    }

    suspend fun deleteCheckin(checkin: HealthCheckin) {
        repository.deleteHealthCheckin(checkin)
    }

    fun getCheckinsForUser(userId: Int): Flow<List<HealthCheckin>> =
        repository.getHealthCheckinsForUser(userId)

    fun getCheckinsBetween(userId: Int, start: Long, end: Long): Flow<List<HealthCheckin>> =
        repository.getHealthCheckinsBetween(userId, start, end)

    suspend fun getCheckinsBetweenOnce(userId: Int, start: Long, end: Long): List<HealthCheckin> =
        repository.getHealthCheckinsBetweenOnce(userId, start, end)

    suspend fun getCheckinForMeasurement(measurementId: Int): HealthCheckin? =
        repository.getHealthCheckinForMeasurement(measurementId)

    fun observeCheckinForMeasurement(measurementId: Int): Flow<HealthCheckin?> =
        repository.observeHealthCheckinForMeasurement(measurementId)

    suspend fun getLatestCheckin(userId: Int): HealthCheckin? =
        repository.getLatestHealthCheckin(userId)
}
