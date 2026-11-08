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

/**
 * Health check-in tracking clinical symptoms:
 * - Endometriosis, EVA pain scale (0-10), and perceived hormonal cycle phase (post-hysterectomy)
 * - Ostomy / Stoma transit, liquid output, pouch incidents & leaks
 * - Digestive comfort, bloating ("endo belly") & gluten exposure/suspicion
 */
@Entity(
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Measurement::class,
            parentColumns = ["id"],
            childColumns = ["measurementId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("userId"),
        Index("measurementId"),
        Index("timestamp")
    ]
)
data class HealthCheckin(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val measurementId: Int? = null,

    // 1. Endométriose & Douleur
    val painScore: Int? = null, // 0..10
    val painLocations: String? = null, // e.g. "pelvienne,crampes,lombaires,fatigue"
    val cycleSensation: Boolean? = null, // Supposition phase de cycle hormonal ressenti

    // 2. Stomie & Transit
    val stomaOutput: String? = null, // "NORMAL", "HIGH_LIQUID", "SLOW"
    val stomaIncident: Boolean? = null, // Fuite ou changement imprévu
    val bristolScale: Int? = null, // Échelle de Bristol (1..7)

    // 3. Digestif & Gluten
    val bloatingLevel: String? = null, // "NONE", "MILD", "ENDO_BELLY"
    val glutenExposure: String? = null, // "NONE", "SUSPECTED", "CONFIRMED"

    // 4. Notes libres
    val notes: String? = null
)
