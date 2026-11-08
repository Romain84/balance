/*
 * openScale
 * Copyright (C) 2026 openScale contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.health.openscale.core.stoma

import java.util.UUID

enum class StomaEventType {
    WATER,          // Water intake (amountMl)
    POUCH_CHANGE,   // Simple pouch change (changement / vidange de poche)
    SUPPORT_CHANGE  // Complete appliance change (support semi-convexe + anneau + poche)
}

data class StomaEvent(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val type: StomaEventType,
    val amountMl: Int = 0,
    val note: String? = null
)

enum class ClinicalHydrationStatus {
    OPTIMAL,  // 🟢 Équilibre optimal
    WARNING,  // 🟠 Vigilance (apport insuffisant ou support > 3 jours)
    CRITICAL  // 🔴 Alerte déshydratation aigu ou fuite répétée
}

data class DailyStomaSummary(
    val dateKey: String, // "YYYY-MM-DD"
    val totalWaterMl: Int,
    val targetWaterMl: Int = 2500,
    val pouchChangeCount: Int,
    val lastSupportChangeTimestamp: Long?,
    val daysSinceLastSupportChange: Int,
    val clinicalStatus: ClinicalHydrationStatus,
    val clinicalAdvice: String
)
