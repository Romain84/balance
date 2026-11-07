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
    val unsupported: Int = 0,
    val skipped: Int = 0
)

@Dao
interface HealthConnectSyncDao {
    @Query("SELECT * FROM HealthConnectSyncState WHERE measurementId = :measurementId")
    suspend fun get(measurementId: Int): HealthConnectSyncState?

    @Upsert
    suspend fun save(state: HealthConnectSyncState)

    /** Highest measurement id; marks where the "existing history" ends when sync is enabled. */
    @Query("SELECT COALESCE(MAX(id), 0) FROM Measurement")
    suspend fun maxMeasurementId(): Int

    /**
     * Counts every measurement, not only those that already have a state row: a weigh-in that
     * was never attempted (e.g. saved while the worker could not run) is pending too, and a
     * synced measurement edited since its last delivery is pending again.
     */
    @Query(
        "SELECT " +
            "COALESCE(SUM(CASE WHEN s.status = 'SYNCED' AND s.lastSyncedRevision >= m.revision THEN 1 ELSE 0 END), 0) AS synced, " +
            "COALESCE(SUM(CASE WHEN s.status IS NULL OR s.status = 'PENDING' OR (s.status = 'SYNCED' AND s.lastSyncedRevision < m.revision) THEN 1 ELSE 0 END), 0) AS pending, " +
            "COALESCE(SUM(CASE WHEN s.status IN ('ERROR', 'INVALID') THEN 1 ELSE 0 END), 0) AS failed, " +
            "COALESCE(SUM(CASE WHEN s.status = 'UNSUPPORTED' THEN 1 ELSE 0 END), 0) AS unsupported, " +
            "COALESCE(SUM(CASE WHEN s.status = 'SKIPPED' THEN 1 ELSE 0 END), 0) AS skipped " +
            "FROM Measurement m " +
            "LEFT JOIN HealthConnectSyncState s ON s.measurementId = m.id"
    )
    fun observeCounts(): Flow<HealthConnectSyncCounts>
}
