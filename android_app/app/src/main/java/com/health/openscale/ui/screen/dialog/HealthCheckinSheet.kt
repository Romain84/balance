/*
 * openScale
 * Copyright (C) 2026 openScale contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.health.openscale.ui.screen.dialog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.health.openscale.core.data.HealthCheckin
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HealthCheckinSheet(
    userId: Int,
    measurementId: Int? = null,
    existingCheckin: HealthCheckin? = null,
    isStomaEnabled: Boolean = true,
    isEndoEnabled: Boolean = true,
    isGlutenEnabled: Boolean = true,
    onDismiss: () -> Unit,
    onSave: (HealthCheckin) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var painScoreFloat by remember { mutableFloatStateOf((existingCheckin?.painScore ?: 2).toFloat()) }
    val painScore = painScoreFloat.roundToInt()

    var selectedLocations by remember {
        mutableStateOf(
            existingCheckin?.painLocations?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
                ?: emptySet()
        )
    }

    // Supposition cycle menstruel / hormonal ressenti (Oui / Non)
    var cycleSensation by remember { mutableStateOf(existingCheckin?.cycleSensation ?: false) }

    // Stomie
    var stomaOutput by remember { mutableStateOf(existingCheckin?.stomaOutput ?: "NORMAL") }
    var stomaIncident by remember { mutableStateOf(existingCheckin?.stomaIncident ?: false) }
    var bristolScale by remember { mutableStateOf<Int?>(existingCheckin?.bristolScale) }

    // Gluten & Digestif
    var bloatingLevel by remember { mutableStateOf(existingCheckin?.bloatingLevel ?: "NONE") }
    var glutenExposure by remember { mutableStateOf(existingCheckin?.glutenExposure ?: "NONE") }

    var notes by remember { mutableStateOf(existingCheckin?.notes.orEmpty()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalHospital,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Check-in Santé du Jour",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Suivi digestif, stomie & cycle hormonal",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Fermer")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. ENDOMÉTRIOSE & DOULEUR
            if (isEndoEnabled) {
                SectionCard(title = "Endométriose & Douleur", badgeColor = Color(0xFFD81B60)) {
                    // Échelle EVA
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Échelle de douleur (EVA)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        val painColor = when {
                            painScore <= 3 -> Color(0xFF4CAF50)
                            painScore <= 6 -> Color(0xFFFB8C00)
                            else -> Color(0xFFE53935)
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = painColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "$painScore / 10",
                                color = painColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    Slider(
                        value = painScoreFloat,
                        onValueChange = { painScoreFloat = it },
                        valueRange = 0f..10f,
                        steps = 9,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    // Pain symptoms
                    Text("Symptômes ressentis :", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Pelvienne", "Crampes", "Lombaires", "Fatigue intense").forEach { tag ->
                            val selected = selectedLocations.contains(tag)
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    selectedLocations = if (selected) selectedLocations - tag else selectedLocations + tag
                                },
                                label = { Text(tag) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Cycle menstruel ressenti (sensation hormonale) : Oui / Non
                    Column {
                        Text(
                            text = "Cycle menstruel ressenti (sensation hormonale) :",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Fluctuations hormonales ou sensations ovariennes ressenties (post-hystérectomie)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ChoicePill(
                                label = "Non",
                                selected = !cycleSensation,
                                onClick = { cycleSensation = false }
                            )
                            ChoicePill(
                                label = "Oui (Phase ressentie) 🌸",
                                selected = cycleSensation,
                                onClick = { cycleSensation = true }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // 2. STOMIE & TRANSIT
            if (isStomaEnabled) {
                SectionCard(title = "Stomie & Équilibre Hydrique", badgeColor = Color(0xFF00897B)) {
                    Text("Débit de la stomie :", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoicePill("Normal", stomaOutput == "NORMAL") { stomaOutput = "NORMAL" }
                        ChoicePill("Élevé / Liquide ⚠️", stomaOutput == "HIGH_LIQUID", isAlert = true) { stomaOutput = "HIGH_LIQUID" }
                        ChoicePill("Ralenti", stomaOutput == "SLOW") { stomaOutput = "SLOW" }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Apparence des selles (Échelle de Bristol) :", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    val bristolOptions = listOf(
                        1 to "Type 1 : Noisettes dures",
                        2 to "Type 2 : Saucisse grumeleuse",
                        3 to "Type 3 : Saucisse craquelée",
                        4 to "Type 4 : Saucisse lisse (Idéal)",
                        5 to "Type 5 : Morceaux mous",
                        6 to "Type 6 : Pâteux / Duveteux",
                        7 to "Type 7 : Entièrement liquide 🚨"
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        bristolOptions.forEach { (typeNum, label) ->
                            val selected = bristolScale == typeNum
                            val isAlert = selected && (typeNum == 7 || typeNum == 1)
                            ChoicePill(
                                label = label,
                                selected = selected,
                                isAlert = isAlert,
                                onClick = {
                                    bristolScale = if (selected) null else typeNum
                                    if (typeNum == 7) stomaOutput = "HIGH_LIQUID"
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Appareillage & Poche :", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoicePill("Poche stable ✓", !stomaIncident) { stomaIncident = false }
                        ChoicePill("Fuite / Changement imprévu ⚠️", stomaIncident, isAlert = true) { stomaIncident = true }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // 3. DIGESTIF & SANS GLUTEN
            if (isGlutenEnabled) {
                SectionCard(title = "Digestif & Éviction Gluten", badgeColor = Color(0xFFF57C00)) {
                    Text("Confort abdominal / Gonflement :", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoicePill("Confort ✓", bloatingLevel == "NONE") { bloatingLevel = "NONE" }
                        ChoicePill("Ballonné", bloatingLevel == "MILD") { bloatingLevel = "MILD" }
                        ChoicePill("Endo-Belly 🚨", bloatingLevel == "ENDO_BELLY", isAlert = true) { bloatingLevel = "ENDO_BELLY" }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Régime Sans Gluten :", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoicePill("100% Sans gluten", glutenExposure == "NONE") { glutenExposure = "NONE" }
                        ChoicePill("Suspicion / Traces ⚠️", glutenExposure == "SUSPECTED", isAlert = true) { glutenExposure = "SUSPECTED" }
                        ChoicePill("Écart avéré", glutenExposure == "CONFIRMED", isAlert = true) { glutenExposure = "CONFIRMED" }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Notes libres
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes libres pour la consultation (facultatif)") },
                placeholder = { Text("Ex: Repas suspect, crampes nocturnes, perte d'énergie...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Passer")
                }

                Button(
                    onClick = {
                        val checkin = HealthCheckin(
                            id = existingCheckin?.id ?: 0,
                            userId = userId,
                            timestamp = existingCheckin?.timestamp ?: System.currentTimeMillis(),
                            measurementId = measurementId ?: existingCheckin?.measurementId,
                            painScore = if (isEndoEnabled) painScore else null,
                            painLocations = if (isEndoEnabled && selectedLocations.isNotEmpty()) selectedLocations.joinToString(",") else null,
                            cycleSensation = if (isEndoEnabled) cycleSensation else null,
                            stomaOutput = if (isStomaEnabled) stomaOutput else null,
                            stomaIncident = if (isStomaEnabled) stomaIncident else null,
                            bristolScale = if (isStomaEnabled) bristolScale else null,
                            bloatingLevel = if (isGlutenEnabled) bloatingLevel else null,
                            glutenExposure = if (isGlutenEnabled) glutenExposure else null,
                            notes = notes.trim().ifEmpty { null }
                        )
                        onSave(checkin)
                    },
                    modifier = Modifier.weight(1.5f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Enregistrer")
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    badgeColor: Color,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp, 16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(badgeColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun ChoicePill(
    label: String,
    selected: Boolean,
    isAlert: Boolean = false,
    onClick: () -> Unit
) {
    val containerColor = when {
        selected && isAlert -> Color(0xFFD32F2F)
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surface
    }
    val contentColor = when {
        selected -> Color.White
        isAlert -> Color(0xFFD32F2F)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        shadowElevation = if (selected) 2.dp else 0.dp,
        border = if (!selected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)) else null
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = contentColor
        )
    }
}
