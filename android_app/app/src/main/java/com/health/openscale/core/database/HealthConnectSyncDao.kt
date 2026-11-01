/*
 * openScale
 * Copyright (C) 2026 openScale contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.health.openscale.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.health.openscale.core.data.HealthConnectSyncState
import kotlinx.coroutines.flow.Flow

data class HealthConnectSyncCounts(
    val synced: Int,
    val pending: Int,
    val failed: Int,
    val unsupported: Int = 0
)

@Dao
interface HealthConnectSyncDao {
    @Query("SELECT * FROM HealthConnectSyncState WHERE measurementId = :measurementId")
    suspend fun get(measurementId: Int): HealthConnectSyncState?

    @Upsert
    suspend fun save(state: HealthConnectSyncState)

    @Query(
        "SELECT " +
            "COALESCE(SUM(CASE WHEN status = 'SYNCED' THEN 1 ELSE 0 END), 0) AS synced, " +
            "COALESCE(SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END), 0) AS pending, " +
            "COALESCE(SUM(CASE WHEN status IN ('ERROR', 'INVALID') THEN 1 ELSE 0 END), 0) AS failed, " +
            "COALESCE(SUM(CASE WHEN status = 'UNSUPPORTED' THEN 1 ELSE 0 END), 0) AS unsupported " +
            "FROM HealthConnectSyncState"
    )
    fun observeCounts(): Flow<HealthConnectSyncCounts>
}
