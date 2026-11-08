/*
 * openScale
 * Copyright (C) 2026 openScale contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.health.openscale.core.medication

import java.util.UUID

data class MedicationIntake(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val name: String,
    val dosage: String = "",
    val category: String = "Antalgique",
    val initialPainScore: Int? = null,    // EVA au moment de la prise (0..10)
    val postPainScore: Int? = null,       // EVA évalué après soulagement (0..10)
    val evaluationTimestamp: Long? = null,
    val reliefPercent: Int? = null,       // % de soulagement (ex: 70%)
    val notes: String? = null
)

data class MedicationStats(
    val name: String,
    val totalIntakes: Int,
    val evaluatedIntakes: Int,
    val avgInitialPain: Float,
    val avgPostPain: Float,
    val avgReliefPercent: Int
)
