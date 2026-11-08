/*
 * openScale
 * Copyright (C) 2026 openScale contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.health.openscale.ui.screen.consultation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.health.openscale.core.data.HealthCheckin
import com.health.openscale.core.data.MeasurementType
import com.health.openscale.core.healthconnect.ClinicalHealthConnectReader
import com.health.openscale.core.healthconnect.ClinicalSleepSummary
import com.health.openscale.core.healthconnect.ClinicalTempSummary
import com.health.openscale.core.medication.MedicationRepository
import com.health.openscale.core.model.MeasurementWithValues
import com.health.openscale.core.stoma.StomaTrackingRepository
import com.health.openscale.ui.navigation.Routes
import com.health.openscale.ui.shared.SharedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorConsultationScreen(
    navController: NavController,
    sharedViewModel: SharedViewModel
) {
    val context = LocalContext.current
    val activeUser by sharedViewModel.selectedUser.collectAsState()
    val medRepository = remember { MedicationRepository(context) }
    val stomaRepository = remember { StomaTrackingRepository(context) }

    var selectedMonths by remember { mutableIntStateOf(3) }
    var measurements by remember { mutableStateOf<List<MeasurementWithValues>>(emptyList()) }
    var checkins by remember { mutableStateOf<List<HealthCheckin>>(emptyList()) }
    var sleepSummary by remember { mutableStateOf(ClinicalSleepSummary()) }
    var tempSummary by remember { mutableStateOf(ClinicalTempSummary()) }

    // Load data for the selected period
    LaunchedEffect(activeUser, selectedMonths) {
        val user = activeUser ?: return@LaunchedEffect
        val cal = Calendar.getInstance()
        val now = cal.timeInMillis
        cal.add(Calendar.MONTH, -selectedMonths)
        val startDate = cal.timeInMillis

        withContext(Dispatchers.IO) {
            val allMeas = sharedViewModel.getMeasurementsForUser(user.id).first()
            val filteredMeas = allMeas.filter { it.measurement.timestamp >= startDate }

            val allCheckins = sharedViewModel.getHealthCheckinsForUser(user.id).first()
            val filteredCheckins = allCheckins.filter { it.timestamp >= startDate }

            val reader = ClinicalHealthConnectReader(context)
            val sSummary = reader.readSleepSummary(startDate, now)
            val tSummary = reader.readBasalTempSummary(startDate, now)

            withContext(Dispatchers.Main) {
                measurements = filteredMeas
                checkins = filteredCheckins
                sleepSummary = sSummary
                tempSummary = tSummary
            }
        }
    }

    val calFilter = Calendar.getInstance().apply { add(Calendar.MONTH, -selectedMonths) }
    val medStats = remember(selectedMonths) { medRepository.computeStats(calFilter.timeInMillis) }
    val stomaHistories = remember(selectedMonths) { stomaRepository.getHistorySummaries(selectedMonths * 30) }

    val weights = measurements.mapNotNull { m ->
        m.values.firstOrNull { it.type.identity == MeasurementType.WEIGHT.identity }?.value?.floatValue
    }
    val waters = measurements.mapNotNull { m ->
        m.values.firstOrNull { it.type.identity == MeasurementType.WATER.identity }?.value?.floatValue
    }

    val avgWeight = if (weights.isNotEmpty()) weights.average().toFloat() else 0f
    val minWeight = weights.minOrNull() ?: 0f
    val maxWeight = weights.maxOrNull() ?: 0f
    val avgWater = if (waters.isNotEmpty()) waters.average().toFloat() else 0f

    val avgDailyWaterIntakeL = if (stomaHistories.isNotEmpty()) {
        val nonZero = stomaHistories.filter { it.totalWaterMl > 0 }
        if (nonZero.isNotEmpty()) nonZero.map { it.totalWaterMl }.average() / 1000.0 else 0.0
    } else 0.0

    val avgPouches = if (stomaHistories.isNotEmpty()) {
        stomaHistories.map { it.pouchChangeCount }.average()
    } else 0.0

    val painScores = checkins.mapNotNull { it.painScore }
    val avgPain = if (painScores.isNotEmpty()) painScores.average().toFloat() else 0f
    val severePainDays = checkins.count { (it.painScore ?: 0) >= 6 }
    val cycleDays = checkins.count { it.cycleSensation == true }
    val stomaIncidents = checkins.count { it.stomaIncident == true }
    val liquidStomaDays = checkins.count { it.stomaOutput == "HIGH_LIQUID" }
    val endoBellyDays = checkins.count { it.bloatingLevel == "ENDO_BELLY" }
    val glutenSuspicions = checkins.count { it.glutenExposure in listOf("SUSPECTED", "CONFIRMED") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Mode Consultation Praticien",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Synthèse clinique décisionnelle",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = { sharedViewModel.openMedicalReportDialog() }) {
                        Icon(Icons.Default.Description, contentDescription = "Exporter Bilan PDF", tint = Color(0xFF00365B))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { navController.navigate(Routes.MEDICATION_LOG) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Medication, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Traitements", maxLines = 1)
                    }

                    Button(
                        onClick = { sharedViewModel.openMedicalReportDialog() },
                        modifier = Modifier.weight(1.4f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00365B))
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Bilan PDF A4", fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(2.dp)) }

            // 1. Patient & Context Clinical Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF00365B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.LocalHospital, contentDescription = null, tint = Color.White)
                            }
                            Column {
                                Text(
                                    text = (activeUser?.name ?: "Patiente").uppercase(),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Text(
                                    text = "Dossier de suivi pluridisciplinaire",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF80DEEA)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("• Colostomie 2 pièces : SenSura® Mio Click 60mm & Brava®", color = Color.White, fontSize = 11.sp)
                                Text("• Endométriose post-hystérectomie : ovaires conservés (cycle ressenti)", color = Color.White, fontSize = 11.sp)
                                Text("• Régime sans gluten strict : suivi Endo-Belly et éviction", color = Color.White, fontSize = 11.sp)
                                Text("• Capteurs Galaxy Watch 6 : sommeil nocturne & température basale", color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // 2. Period Filter Chips (1 month, 3 months, 6 months)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Période :", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    listOf(1 to "1 mois", 3 to "3 mois", 6 to "6 mois").forEach { (m, label) ->
                        FilterChip(
                            selected = selectedMonths == m,
                            onClick = { selectedMonths = m },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF00365B),
                                selectedLabelColor = Color.White
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }
            }

            // 3. Clinical Pillar 1: Stomathérapie & Hydratation
            item {
                ConsultationPillarCard(
                    title = "STOMATHÉRAPIE & ÉQUILIBRE HYDRIQUE",
                    color = Color(0xFF009DA8),
                    metrics = listOf(
                        "Poches / jour" to if (avgPouches > 0) String.format(Locale.FRANCE, "%.1f", avgPouches) else "1 à 2",
                        "Incidents / fuites" to "$stomaIncidents",
                        "Débit liquide élevé" to "$liquidStomaDays j",
                        "Apport hydrique réel" to if (avgDailyWaterIntakeL > 0) String.format(Locale.FRANCE, "%.2f L/j", avgDailyWaterIntakeL) else "Suivi actif",
                        "Taux d'eau corporel" to if (avgWater > 0) String.format(Locale.FRANCE, "%.1f %%", avgWater) else "--",
                        "Poids moyen" to if (avgWeight > 0) String.format(Locale.FRANCE, "%.1f kg", avgWeight) else "--"
                    ),
                    clinicalRemark = if (stomaIncidents == 0 && liquidStomaDays <= 1) {
                        "🟢 Appareillage étanche et transit colostomique stable sur la période."
                    } else {
                        "🟠 Vigilance : $liquidStomaDays jour(s) de haut débit liquide et $stomaIncidents incident(s) répertorié(s)."
                    }
                )
            }

            // 4. Clinical Pillar 2: Algologie, Endométriose & Cycle Ovarien
            item {
                val tempText = if (tempSummary.hasData) {
                    if (tempSummary.highPhaseDetected) "+${String.format(Locale.FRANCE, "%.2f", tempSummary.deltaCelsius)}°C (phase lutéale)" else "Stable"
                } else "Capteur nocturne"

                ConsultationPillarCard(
                    title = "DOULEUR, ENDOMÉTRIOSE & CYCLE OVARIEN",
                    color = Color(0xFF00695C),
                    metrics = listOf(
                        "EVA Douleur moyen" to if (avgPain > 0) String.format(Locale.FRANCE, "%.1f / 10", avgPain) else "Faible",
                        "Crises aiguës (EVA ≥ 6)" to "$severePainDays j",
                        "Cycle ovarien ressenti" to "$cycleDays j",
                        "Thermométrie basale" to tempText,
                        "Sommeil moyen" to if (sleepSummary.hasData) sleepSummary.formattedAvgDuration else "Enregistré",
                        "Micro-réveils" to if (sleepSummary.hasData) String.format(Locale.FRANCE, "%.1f / nuit", sleepSummary.avgAwakeningsPerNight) else "--"
                    ),
                    clinicalRemark = if (severePainDays > 3) {
                        "🟠 Présence de $severePainDays jour(s) de douleurs sévères corrélées aux sensations hormonales ovariennes."
                    } else {
                        "🟢 Bon contrôle global des douleurs pelviennes sur la période observée."
                    }
                )
            }

            // 5. Clinical Pillar 3: Confort Digestif & Éviction Gluten
            item {
                ConsultationPillarCard(
                    title = "CONFORT DIGESTIF & RÉGIME SANS GLUTEN",
                    color = Color(0xFF6A1B9A),
                    metrics = listOf(
                        "Épisodes Endo-Belly" to "$endoBellyDays",
                        "Suspicions gluten" to "$glutenSuspicions",
                        "Tolérance globale" to if (glutenSuspicions == 0) "Excellente" else "Incidents à vérifier"
                    ),
                    clinicalRemark = if (glutenSuspicions == 0) {
                        "🟢 Régime sans gluten strict parfaitement respecté sans crise de ballonnement aigu."
                    } else {
                        "🔴 $glutenSuspicions suspicion(s) de contamination au gluten documentée(s) avec ballonnement associé."
                    }
                )
            }

            // 6. Medication Response Table (Pharmacotherapy)
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Medication, contentDescription = null, tint = Color(0xFF00365B))
                            Text(
                                text = "RÉPONSE THÉRAPEUTIQUE & ANTALGIQUE",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF00365B)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (medStats.isEmpty()) {
                            Text(
                                text = "Aucune prise médicamenteuse enregistrée sur cette période.\nUtilisez le bouton Traitements ci-dessous pour documenter les prises.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        } else {
                            // Table Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF0F4F8), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Molécule", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF00365B), modifier = Modifier.weight(1.5f))
                                Text("Prises", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF00365B), modifier = Modifier.weight(0.8f))
                                Text("EVA Av ➔ Ap", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF00365B), modifier = Modifier.weight(1.4f))
                                Text("Soulagement", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF00365B), modifier = Modifier.weight(1.2f))
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            medStats.forEachIndexed { idx, s ->
                                val reliefColor = when {
                                    s.avgReliefPercent >= 60 -> Color(0xFF1B5E20)
                                    s.avgReliefPercent >= 30 -> Color(0xFFE65100)
                                    else -> Color(0xFFB71C1C)
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(s.name, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.weight(1.5f))
                                    Text("${s.totalIntakes}", fontSize = 12.sp, modifier = Modifier.weight(0.8f))
                                    Text(
                                        text = if (s.evaluatedIntakes > 0) "${String.format(Locale.FRANCE, "%.1f", s.avgInitialPain)} ➔ ${String.format(Locale.FRANCE, "%.1f", s.avgPostPain)}" else "--",
                                        fontSize = 11.sp,
                                        modifier = Modifier.weight(1.4f)
                                    )
                                    Text(
                                        text = if (s.evaluatedIntakes > 0) "${s.avgReliefPercent}%" else "En cours",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = reliefColor,
                                        modifier = Modifier.weight(1.2f)
                                    )
                                }
                                if (idx < medStats.size - 1) {
                                    Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(Color(0xFFF0F0F0)))
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun ConsultationPillarCard(
    title: String,
    color: Color,
    metrics: List<Pair<String, String>>,
    clinicalRemark: String
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = color.copy(alpha = 0.12f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = color,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2-columns grid for metrics
            val chunked = metrics.chunked(2)
            chunked.forEach { rowMetrics ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowMetrics.forEach { (label, value) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF8F9FA),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray, maxLines = 1)
                                Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF263238))
                            }
                        }
                    }
                    if (rowMetrics.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Clinical Observation
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFF5F5F5),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = clinicalRemark,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF37474F),
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}
