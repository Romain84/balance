/*
 * openScale
 * Copyright (C) 2026 openScale contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.health.openscale.core.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Local delivery state. Health data itself remains in openScale and Health Connect. */
@Entity(
    foreignKeys = [
        ForeignKey(
            entity = Measurement::class,
            parentColumns = ["id"],
            childColumns = ["measurementId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("status")]
)
data class HealthConnectSyncState(
    @PrimaryKey val measurementId: Int,
    val status: String = STATUS_PENDING,
    val lastSyncedRevision: Long = 0L,
    val lastSyncedAt: Long? = null,
    val lastAttemptAt: Long? = null,
    val lastError: String? = null,
    val syncedRecords: Int = 0,
    val payloadHash: String? = null,
    val clientRecordVersion: Long = 0L
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_SYNCED = "SYNCED"
        const val STATUS_ERROR = "ERROR"
        const val STATUS_UNSUPPORTED = "UNSUPPORTED"
        const val STATUS_INVALID = "INVALID"
    }
}
