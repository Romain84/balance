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
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.health.openscale.core.data.HealthCheckin
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthCheckinDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(checkin: HealthCheckin): Long

    @Update
    suspend fun update(checkin: HealthCheckin)

    @Delete
    suspend fun delete(checkin: HealthCheckin)

    @Query("SELECT * FROM HealthCheckin WHERE userId = :userId ORDER BY timestamp DESC")
    fun getCheckinsForUser(userId: Int): Flow<List<HealthCheckin>>

    @Query("SELECT * FROM HealthCheckin WHERE userId = :userId AND timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    fun getCheckinsBetween(userId: Int, startTime: Long, endTime: Long): Flow<List<HealthCheckin>>

    @Query("SELECT * FROM HealthCheckin WHERE userId = :userId AND timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    suspend fun getCheckinsBetweenOnce(userId: Int, startTime: Long, endTime: Long): List<HealthCheckin>

    @Query("SELECT * FROM HealthCheckin WHERE measurementId = :measurementId LIMIT 1")
    suspend fun getCheckinForMeasurement(measurementId: Int): HealthCheckin?

    @Query("SELECT * FROM HealthCheckin WHERE measurementId = :measurementId LIMIT 1")
    fun observeCheckinForMeasurement(measurementId: Int): Flow<HealthCheckin?>

    @Query("SELECT * FROM HealthCheckin WHERE userId = :userId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestCheckin(userId: Int): HealthCheckin?
}
