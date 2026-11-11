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

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.health.openscale.core.data.User
import com.health.openscale.core.healthconnect.ClinicalHealthConnectReader
import com.health.openscale.core.healthconnect.ClinicalHeartRateSummary
import com.health.openscale.core.healthconnect.ClinicalHrvSummary
import com.health.openscale.core.healthconnect.ClinicalSleepSummary
import com.health.openscale.core.healthconnect.ClinicalSpo2Summary
import com.health.openscale.core.healthconnect.ClinicalStepsSummary
import com.health.openscale.core.healthconnect.ClinicalTempSummary
import com.health.openscale.core.medication.MedicationRepository
import com.health.openscale.core.medication.MedicationStats
import com.health.openscale.core.model.MeasurementWithValues
import com.health.openscale.core.service.MedicalReportGenerator
import com.health.openscale.core.stoma.StomaEventType
import com.health.openscale.core.stoma.StomaTrackingRepository
import com.health.openscale.ui.navigation.Routes
import com.health.openscale.ui.shared.SharedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

// ---------------------------------------------------------------------------
// Clinical Palette & Enums
// ---------------------------------------------------------------------------

private val ColoplastBlue = Color(0xFF00365B)
private val ColoplastEmerald = Color(0xFF00695C)
private val ColoplastTurquoise = Color(0xFF009DA8)
private val AlertRed = Color(0xFFC62828)
private val AlertOrange = Color(0xFFE65100)
private val OptimalGreen = Color(0xFF2E7D32)

enum class ClinicalPeriod(val days: Int, val label: String, val subtitle: String) {
    WEEK_7D(7, "7 jours", "Semaine récente"),
    MONTH_30D(30, "30 jours", "Cycle mensuel"),
    QUARTER_90D(90, "3 mois", "Bilan trimestriel ALD")
}

enum class ClinicalAlertLevel(
    val label: String,
    val badgeColor: Color,
    val containerColor: Color,
    val borderColor: Color
) {
    OPTIMAL(
        label = "ÉQUILIBRÉ & STABLE",
        badgeColor = OptimalGreen,
        containerColor = Color(0xFFE8F5E9),
        borderColor = Color(0xFFA5D6A7)
    ),
    WARNING(
        label = "VIGILANCE CLINIQUE ACTIVE",
        badgeColor = AlertOrange,
        containerColor = Color(0xFFFFF3E0),
        borderColor = Color(0xFFFFCC80)
    ),
    CRITICAL(
        label = "ACTION CLINIQUE REQUISE",
        badgeColor = AlertRed,
        containerColor = Color(0xFFFFEBEE),
        borderColor = Color(0xFFEF9A9A)
    )
}

data class ClinicalFlag(
    val level: ClinicalAlertLevel,
    val category: String, // "HYDRATATION", "DOULEUR", "STOMIE", "SOMMEIL", "POIDS"
    val message: String,
    val recommendation: String
)

// ---------------------------------------------------------------------------
// DoctorConsultationScreen Composable
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorConsultationScreen(
    navController: NavController,
    sharedViewModel: SharedViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activeUser by sharedViewModel.selectedUser.collectAsState()
    val medRepository = remember { MedicationRepository(context) }
    val stomaRepository = remember { StomaTrackingRepository(context) }

    var selectedPeriod by remember { mutableStateOf(ClinicalPeriod.MONTH_30D) }
    var isDirectSharing by remember { mutableStateOf(false) }

    var measurements by remember { mutableStateOf<List<MeasurementWithValues>>(emptyList()) }
    var checkins by remember { mutableStateOf<List<HealthCheckin>>(emptyList()) }
    var sleepSummary by remember { mutableStateOf(ClinicalSleepSummary()) }
    var tempSummary by remember { mutableStateOf(ClinicalTempSummary()) }
    var heartRateSummary by remember { mutableStateOf(ClinicalHeartRateSummary()) }
    var hrvSummary by remember { mutableStateOf(ClinicalHrvSummary()) }
    var spo2Summary by remember { mutableStateOf(ClinicalSpo2Summary()) }
    var stepsSummary by remember { mutableStateOf(ClinicalStepsSummary()) }

    val allStomaEvents by stomaRepository.eventsFlow.collectAsState()

    // Load data for the selected period
    LaunchedEffect(activeUser, selectedPeriod) {
        val user = activeUser ?: return@LaunchedEffect
        val cal = Calendar.getInstance()
        val now = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, -selectedPeriod.days)
        val startDate = cal.timeInMillis

        withContext(Dispatchers.IO) {
            val allMeas = sharedViewModel.getMeasurementsForUser(user.id).first()
            val filteredMeas = allMeas.filter { it.measurement.timestamp >= startDate }

            val allCheckins = sharedViewModel.getHealthCheckinsForUser(user.id).first()
            val filteredCheckins = allCheckins.filter { it.timestamp >= startDate }

            val reader = ClinicalHealthConnectReader(context)
            val sSummary = reader.readSleepSummary(startDate, now)
            val tSummary = reader.readBasalTempSummary(startDate, now)
            val hrSummary = reader.readHeartRateSummary(startDate, now)
            val hSummary = reader.readHrvSummary(startDate, now)
            val spSummary = reader.readOxygenSaturationSummary(startDate, now)
            val stSummary = reader.readStepsSummary(startDate, now)

            withContext(Dispatchers.Main) {
                measurements = filteredMeas
                checkins = filteredCheckins
                sleepSummary = sSummary
                tempSummary = tSummary
                heartRateSummary = hrSummary
                hrvSummary = hSummary
                spo2Summary = spSummary
                stepsSummary = stSummary
            }
        }
    }

    val calFilter = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -selectedPeriod.days) }
    val startDate = calFilter.timeInMillis
    val medStats = remember(selectedPeriod) { medRepository.computeStats(startDate) }
    val stomaHistories = remember(selectedPeriod) { stomaRepository.getHistorySummaries(selectedPeriod.days) }

    // Anthropometry
    val weights = measurements.mapNotNull { m ->
        m.values.firstOrNull { it.type.identity == MeasurementType.WEIGHT.identity }?.value?.floatValue
    }
    val waters = measurements.mapNotNull { m ->
        m.values.firstOrNull { it.type.identity == MeasurementType.WATER.identity }?.value?.floatValue
    }

    val avgWeight = if (weights.isNotEmpty()) weights.average().toFloat() else 0f
    val minWeight = weights.minOrNull() ?: 0f
    val maxWeight = weights.maxOrNull() ?: 0f
    val newestWeight = weights.firstOrNull() ?: 0f
    val oldestWeight = weights.lastOrNull() ?: 0f
    val deltaWeight = if (weights.size >= 2) newestWeight - oldestWeight else 0f
    val avgWater = if (waters.isNotEmpty()) waters.average().toFloat() else 0f
    val heightM = (activeUser?.heightCm ?: 0f) / 100f
    val bmi = if (heightM > 0f && avgWeight > 0f) avgWeight / (heightM * heightM) else 0f

    // Gastro & Stoma
    val avgDailyWaterIntakeL = if (stomaHistories.isNotEmpty()) {
        val nonZero = stomaHistories.filter { it.totalWaterMl > 0 }
        if (nonZero.isNotEmpty()) nonZero.map { it.totalWaterMl }.average() / 1000.0 else 0.0
    } else 0.0

    val avgPouches = if (stomaHistories.isNotEmpty()) {
        stomaHistories.map { it.pouchChangeCount }.average()
    } else 0.0

    val periodSupportChanges = remember(allStomaEvents, selectedPeriod) {
        allStomaEvents.filter { it.type == StomaEventType.SUPPORT_CHANGE && it.timestamp >= startDate }
    }
    val supportCount = periodSupportChanges.size
    val avgWaferWearDays = if (supportCount > 0) {
        selectedPeriod.days.toDouble() / supportCount
    } else {
        0.0
    }

    val bristolScores = checkins.mapNotNull { it.bristolScale }
    val avgBristol = if (bristolScores.isNotEmpty()) bristolScores.average().toFloat() else 0f
    val bristolConstipated = bristolScores.count { it in 1..2 }
    val bristolOptimal = bristolScores.count { it in 3..5 }
    val bristolLiquid = bristolScores.count { it in 6..7 }

    val stomaIncidents = checkins.count { it.stomaIncident == true }
    val liquidStomaDays = checkins.count { it.stomaOutput == "HIGH_LIQUID" || (it.bristolScale ?: 0) >= 6 }
    val endoBellyDays = checkins.count { it.bloatingLevel == "ENDO_BELLY" }
    val glutenSuspicions = checkins.count { it.glutenExposure in listOf("SUSPECTED", "CONFIRMED") }

    // Algology & Ovarian Cycle
    val painScores = checkins.mapNotNull { it.painScore }
    val avgPain = if (painScores.isNotEmpty()) painScores.average().toFloat() else 0f
    val maxPain = painScores.maxOrNull() ?: 0
    val severePainDays = checkins.count { (it.painScore ?: 0) >= 6 }
    val cycleDays = checkins.count { it.cycleSensation == true }

    // Medication aggregated efficacy
    val totalMedEvaluated = medStats.sumOf { it.evaluatedIntakes }
    val avgDeltaEva = if (totalMedEvaluated > 0) {
        medStats.filter { it.evaluatedIntakes > 0 }.map { it.avgDrop * it.evaluatedIntakes }.sum() / totalMedEvaluated
    } else 0f
    val overallReliefPercent = if (totalMedEvaluated > 0) {
        (medStats.filter { it.evaluatedIntakes > 0 }.map { it.avgReliefPercent * it.evaluatedIntakes }.sum() / totalMedEvaluated).toInt()
    } else 0

    // Triage & Clinical Flags
    val clinicalFlags = remember(
        selectedPeriod,
        avgDailyWaterIntakeL,
        avgPain,
        maxPain,
        severePainDays,
        medStats,
        stomaIncidents,
        liquidStomaDays,
        avgWaferWearDays,
        glutenSuspicions,
        endoBellyDays,
        spo2Summary,
        sleepSummary,
        weights,
        deltaWeight
    ) {
        buildClinicalFlags(
            period = selectedPeriod,
            waterIntakeL = avgDailyWaterIntakeL,
            avgPain = avgPain,
            maxPain = maxPain,
            severePainDays = severePainDays,
            medStats = medStats,
            stomaIncidents = stomaIncidents,
            liquidStomaDays = liquidStomaDays,
            avgWaferWearDays = avgWaferWearDays,
            glutenSuspicions = glutenSuspicions,
            endoBellyDays = endoBellyDays,
            spo2Summary = spo2Summary,
            sleepSummary = sleepSummary,
            weightsCount = weights.size,
            deltaWeight = deltaWeight
        )
    }

    val globalStatus = when {
        clinicalFlags.any { it.level == ClinicalAlertLevel.CRITICAL } -> ClinicalAlertLevel.CRITICAL
        clinicalFlags.any { it.level == ClinicalAlertLevel.WARNING } -> ClinicalAlertLevel.WARNING
        else -> ClinicalAlertLevel.OPTIMAL
    }

    val executiveSummary = remember(
        selectedPeriod,
        globalStatus,
        avgDailyWaterIntakeL,
        avgPain,
        overallReliefPercent,
        tempSummary,
        stomaIncidents
    ) {
        buildExecutiveSummary(
            period = selectedPeriod,
            status = globalStatus,
            waterL = avgDailyWaterIntakeL,
            avgPain = avgPain,
            reliefPercent = overallReliefPercent,
            tempSummary = tempSummary,
            stomaIncidents = stomaIncidents
        )
    }

    // Direct Report Share function
    val triggerDirectShare = {
        val user = activeUser
        if (user != null && !isDirectSharing) {
            isDirectSharing = true
            scope.launch(Dispatchers.IO) {
                try {
                    val monthsParam = when (selectedPeriod) {
                        ClinicalPeriod.WEEK_7D -> 1
                        ClinicalPeriod.MONTH_30D -> 1
                        ClinicalPeriod.QUARTER_90D -> 3
                    }
                    val pdfFile = MedicalReportGenerator.generateReport(
                        context = context,
                        user = user,
                        months = monthsParam,
                        measurements = measurements,
                        checkins = checkins,
                        sleepSummary = sleepSummary,
                        tempSummary = tempSummary,
                        hrSummary = heartRateSummary,
                        stepsSummary = stepsSummary,
                        hrvSummary = hrvSummary,
                        spo2Summary = spo2Summary,
                        specialist = "Médecin traitant",
                        consultationMotif = "Mode Consultation Praticien (${selectedPeriod.label}) - Bilan d'observance",
                        periodDays = selectedPeriod.days
                    )
                    withContext(Dispatchers.Main) {
                        isDirectSharing = false
                        MedicalReportGenerator.shareReport(context, pdfFile)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isDirectSharing = false
                        Toast.makeText(context, "Erreur export : ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

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
                            text = "Synthèse clinique décisionnelle · ${selectedPeriod.label}",
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
                    IconButton(onClick = triggerDirectShare, enabled = !isDirectSharing) {
                        if (isDirectSharing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = ColoplastBlue)
                        } else {
                            Icon(Icons.Default.Share, contentDescription = "Partager Bilan PDF", tint = ColoplastBlue)
                        }
                    }
                    IconButton(onClick = { sharedViewModel.openMedicalReportDialog() }) {
                        Icon(Icons.Default.Description, contentDescription = "Exporter Bilan PDF A4", tint = ColoplastBlue)
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
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { navController.navigate(Routes.MEDICATION_LOG) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Medication, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Traitements", maxLines = 1, fontSize = 12.sp)
                    }

                    FilledTonalButton(
                        onClick = triggerDirectShare,
                        enabled = !isDirectSharing,
                        modifier = Modifier.weight(1.1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = ColoplastEmerald.copy(alpha = 0.15f),
                            contentColor = ColoplastEmerald
                        )
                    ) {
                        if (isDirectSharing) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = ColoplastEmerald)
                        } else {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Partager", fontWeight = FontWeight.Bold, maxLines = 1, fontSize = 12.sp)
                        }
                    }

                    Button(
                        onClick = { sharedViewModel.openMedicalReportDialog() },
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ColoplastBlue)
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Bilan PDF A4", fontWeight = FontWeight.Bold, maxLines = 1, fontSize = 12.sp)
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(modifier = Modifier.height(2.dp)) }

            // 1. Patient Dossier ALD Card
            item {
                PatientDossierBanner(user = activeUser)
            }

            // 2. Period Filter Chips
            item {
                PeriodSelectionRow(
                    selectedPeriod = selectedPeriod,
                    onSelectPeriod = { selectedPeriod = it }
                )
            }

            // 3. Hero Clinical Summary Banner (Status + 4 Executive KPIs + Flags)
            item {
                HeroClinicalSummaryBanner(
                    status = globalStatus,
                    executiveSummary = executiveSummary,
                    waterL = avgDailyWaterIntakeL,
                    avgPain = avgPain,
                    maxPain = maxPain,
                    severeDays = severePainDays,
                    reliefPercent = overallReliefPercent,
                    deltaEva = avgDeltaEva,
                    avgWeight = avgWeight,
                    deltaWeight = deltaWeight,
                    bmi = bmi,
                    flags = clinicalFlags
                )
            }

            // 4. Clinical Pillar 1: Gastro-entérologie & Stomie
            item {
                PillarGastroStomieCard(
                    avgBristol = avgBristol,
                    bristolConstipated = bristolConstipated,
                    bristolOptimal = bristolOptimal,
                    bristolLiquid = bristolLiquid,
                    avgPouches = avgPouches,
                    avgWaferWearDays = avgWaferWearDays,
                    waterIntakeL = avgDailyWaterIntakeL,
                    bodyWaterPercent = avgWater,
                    stomaIncidents = stomaIncidents,
                    liquidDays = liquidStomaDays,
                    endoBellyDays = endoBellyDays,
                    glutenSuspicions = glutenSuspicions
                )
            }

            // 5. Clinical Pillar 2: Algologie & Cycle Ovarien
            item {
                PillarAlgologieOvarienCard(
                    avgPain = avgPain,
                    maxPain = maxPain,
                    severePainDays = severePainDays,
                    cycleDays = cycleDays,
                    tempSummary = tempSummary,
                    medEvaluated = totalMedEvaluated,
                    deltaEva = avgDeltaEva,
                    reliefPercent = overallReliefPercent
                )
            }

            // 6. Clinical Pillar 3: Système Autonome & Sommeil (Galaxy Watch 6)
            item {
                PillarSystemeAutonomeCard(
                    sleepSummary = sleepSummary,
                    hrSummary = heartRateSummary,
                    hrvSummary = hrvSummary,
                    spo2Summary = spo2Summary,
                    stepsSummary = stepsSummary
                )
            }

            // 7. Clinical Pillar 4: Anthropométrie & Équilibre Corporel
            item {
                PillarAnthropometrieCard(
                    avgWeight = avgWeight,
                    minWeight = minWeight,
                    maxWeight = maxWeight,
                    deltaWeight = deltaWeight,
                    bmi = bmi,
                    bodyWaterPercent = avgWater,
                    measurementsCount = weights.size
                )
            }

            // 8. Pharmacotherapy Table
            item {
                PharmacotherapyTableCard(
                    medStats = medStats,
                    onManageMedications = { navController.navigate(Routes.MEDICATION_LOG) }
                )
            }

            item { Spacer(modifier = Modifier.height(30.dp)) }
        }
    }

    // Modal dialog for customizable report export
    val showMedicalReportDialog by sharedViewModel.showMedicalReportDialog.collectAsState()
    if (showMedicalReportDialog && activeUser != null) {
        val userCheckins by sharedViewModel.getHealthCheckinsForUser(activeUser!!.id).collectAsState(initial = emptyList())
        val userMeasurements by sharedViewModel.getMeasurementsForUser(activeUser!!.id).collectAsState(initial = emptyList())
        com.health.openscale.ui.screen.dialog.MedicalReportDialog(
            user = activeUser!!,
            measurements = userMeasurements,
            checkins = userCheckins,
            onDismiss = { sharedViewModel.dismissMedicalReportDialog() }
        )
    }
}

// ---------------------------------------------------------------------------
// Component 1: Patient Dossier Banner
// ---------------------------------------------------------------------------

@Composable
private fun PatientDossierBanner(user: User?) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ColoplastBlue),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.LocalHospital, contentDescription = null, tint = Color.White)
                }
                Column {
                    Text(
                        text = (user?.name ?: "Patiente").uppercase(),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Text(
                        text = "Dossier Médical Pluridisciplinaire · ALD Colostomie & Endométriose",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB2EBF2)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.White.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("• Colostomie 2 pièces : SenSura® Mio Click 60mm & Brava® Support Plus", color = Color.White, fontSize = 11.sp)
                    Text("• Endométriose post-hystérectomie : ovaires conservés (sensations hormonales)", color = Color.White, fontSize = 11.sp)
                    Text("• Régime sans gluten strict : suivi du ballonnement Endo-Belly et éviction", color = Color.White, fontSize = 11.sp)
                    Text("• Télésurveillance Galaxy Watch 6 : sommeil, thermométrie basale, VRC & SpO2", color = Color.White, fontSize = 11.sp)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Component 2: Period Selection Row
// ---------------------------------------------------------------------------

@Composable
private fun PeriodSelectionRow(
    selectedPeriod: ClinicalPeriod,
    onSelectPeriod: (ClinicalPeriod) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Période clinique :",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = selectedPeriod.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ClinicalPeriod.values().forEach { period ->
                val isSelected = selectedPeriod == period
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectPeriod(period) },
                    label = {
                        Text(
                            text = period.label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ColoplastBlue,
                        selectedLabelColor = Color.White
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Component 3: Hero Clinical Summary Banner
// ---------------------------------------------------------------------------

@Composable
private fun HeroClinicalSummaryBanner(
    status: ClinicalAlertLevel,
    executiveSummary: String,
    waterL: Double,
    avgPain: Float,
    maxPain: Int,
    severeDays: Int,
    reliefPercent: Int,
    deltaEva: Float,
    avgWeight: Float,
    deltaWeight: Float,
    bmi: Float,
    flags: List<ClinicalFlag>
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = status.containerColor),
        border = BorderStroke(1.5.dp, status.borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Status badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = status.badgeColor
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val icon = when (status) {
                            ClinicalAlertLevel.OPTIMAL -> Icons.Default.Check
                            ClinicalAlertLevel.WARNING -> Icons.Default.Info
                            ClinicalAlertLevel.CRITICAL -> Icons.Default.Warning
                        }
                        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Text(
                            text = status.label,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }

                Text(
                    text = "Synthèse Exécutive",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.DarkGray,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Executive Summary text
            Text(
                text = executiveSummary,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                color = Color(0xFF1E293B),
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 4 Executive KPI Quick-Badges (Grid 2x2)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ExecutiveKpiPill(
                        modifier = Modifier.weight(1f),
                        icon = "💧",
                        title = "Hydratation",
                        value = if (waterL > 0) String.format(Locale.FRANCE, "%.2f L/j", waterL) else "--",
                        subtitle = "Cible ≥ 2.0 L/j",
                        isAlert = waterL in 0.001..1.99
                    )

                    ExecutiveKpiPill(
                        modifier = Modifier.weight(1f),
                        icon = "💊",
                        title = "Antalgiques",
                        value = if (reliefPercent > 0) "$reliefPercent% soulagé" else "Prises actives",
                        subtitle = if (deltaEva > 0) "Δ -${String.format(Locale.FRANCE, "%.1f", deltaEva)} pts" else "À évaluer",
                        isAlert = reliefPercent in 1..49
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ExecutiveKpiPill(
                        modifier = Modifier.weight(1f),
                        icon = "⚡",
                        title = "Douleur EVA",
                        value = if (avgPain > 0) String.format(Locale.FRANCE, "%.1f / 10", avgPain) else "Contrôlée",
                        subtitle = "Pic: $maxPain · $severeDays j ≥ 6",
                        isAlert = avgPain >= 4f || severeDays >= 3
                    )

                    ExecutiveKpiPill(
                        modifier = Modifier.weight(1f),
                        icon = "⚖️",
                        title = "Pondéral",
                        value = if (avgWeight > 0) String.format(Locale.FRANCE, "%.1f kg", avgWeight) else "--",
                        subtitle = "${if (deltaWeight >= 0) "+${String.format(Locale.FRANCE, "%.1f", deltaWeight)}" else String.format(Locale.FRANCE, "%.1f", deltaWeight)} kg · IMC ${if (bmi > 0) String.format(Locale.FRANCE, "%.1f", bmi) else "--"}",
                        isAlert = abs(deltaWeight) >= 2.5f
                    )
                }
            }

            // Clinical Flags / Priority Alerts List (if any warning or critical)
            if (flags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Alertes & Recommandations Praticien :",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF334155)
                )
                Spacer(modifier = Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    flags.forEach { flag ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, flag.level.borderColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                val flagIcon = if (flag.level == ClinicalAlertLevel.CRITICAL) Icons.Default.Warning else Icons.Default.Info
                                Icon(
                                    imageVector = flagIcon,
                                    contentDescription = null,
                                    tint = flag.level.badgeColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        text = flag.message,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = flag.level.badgeColor
                                    )
                                    Text(
                                        text = "➔ ${flag.recommendation}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF334155),
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExecutiveKpiPill(
    modifier: Modifier = Modifier,
    icon: String,
    title: String,
    value: String,
    subtitle: String,
    isAlert: Boolean
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = Color.White,
        border = BorderStroke(1.dp, if (isAlert) AlertOrange.copy(alpha = 0.5f) else Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(icon, fontSize = 11.sp)
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = if (isAlert) AlertOrange else Color(0xFF1E293B),
                maxLines = 1
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = Color(0xFF64748B),
                maxLines = 1
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Component 4: Pillar 1 - Gastro-entérologie & Stomie
// ---------------------------------------------------------------------------

@Composable
private fun PillarGastroStomieCard(
    avgBristol: Float,
    bristolConstipated: Int,
    bristolOptimal: Int,
    bristolLiquid: Int,
    avgPouches: Double,
    avgWaferWearDays: Double,
    waterIntakeL: Double,
    bodyWaterPercent: Float,
    stomaIncidents: Int,
    liquidDays: Int,
    endoBellyDays: Int,
    glutenSuspicions: Int
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, ColoplastTurquoise.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = ColoplastTurquoise.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "1. GASTRO-ENTÉROLOGIE & COLOSTOMIE",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = ColoplastTurquoise,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = "SenSura® Mio Click 60mm",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Metrics Grid
            val metrics = listOf(
                "Bristol Moyen" to if (avgBristol > 0) String.format(Locale.FRANCE, "%.1f / 7", avgBristol) else "Suivi actif",
                "Apport Hydrique Réel" to if (waterIntakeL > 0) String.format(Locale.FRANCE, "%.2f L/j", waterIntakeL) else "À documenter",
                "Rythme Poches" to if (avgPouches > 0) String.format(Locale.FRANCE, "%.1f / jour", avgPouches) else "1 à 2 /j",
                "Port Moyen Support" to if (avgWaferWearDays > 0) String.format(Locale.FRANCE, "%.1f jours", avgWaferWearDays) else "2 à 3 jours",
                "Fuites / Incidents" to "$stomaIncidents",
                "Haut Débit Liquide" to "$liquidDays j",
                "Épisodes Endo-Belly" to "$endoBellyDays j",
                "Suspicions Gluten" to "$glutenSuspicions",
                "Eau Corporelle (Masse)" to if (bodyWaterPercent > 0) String.format(Locale.FRANCE, "%.1f %%", bodyWaterPercent) else "--"
            )

            metrics.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { (label, value) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF8FAFC),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray, maxLines = 1)
                                Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF1E293B))
                            }
                        }
                    }
                    if (row.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Bristol Distribution Breakdown Mini-card
            val totalBristol = bristolConstipated + bristolOptimal + bristolLiquid
            if (totalBristol > 0) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF1F5F9),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "Distribution Transit Stomique (Échelle de Bristol) :",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF334155)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("• Type 1-2 (Ralenti) : $bristolConstipated j", fontSize = 10.sp, color = if (bristolConstipated > 0) AlertOrange else Color.Gray)
                            Text("• Type 3-5 (Optimal) : $bristolOptimal j", fontSize = 10.sp, color = OptimalGreen, fontWeight = FontWeight.Bold)
                            Text("• Type 6-7 (Liquide) : $bristolLiquid j", fontSize = 10.sp, color = if (bristolLiquid > 0) AlertRed else Color.Gray)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Clinical Remark
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFF8FAFC),
                modifier = Modifier.fillMaxWidth()
            ) {
                val remark = when {
                    stomaIncidents == 0 && liquidDays <= 1 && glutenSuspicions == 0 ->
                        "🟢 Appareillage SenSura® étanche, transit stomique régulier et régime sans gluten scrupuleusement respecté."
                    stomaIncidents >= 2 ->
                        "🔴 Vigilance : $stomaIncidents incident(s) de fuite répertorié(s). Vérifier la découpe du support 15-40mm et la protection Brava®."
                    liquidDays >= 3 ->
                        "🟠 Vigilance : $liquidDays jours de débit liquide élevé. Risque de fuite et déshydratation à surveiller."
                    glutenSuspicions > 0 ->
                        "🟠 $glutenSuspicions suspicion(s) de gluten avec ballonnement associé. Renforcer l'éviction."
                    else ->
                        "🟢 Transit globalement stable sous surveillance hydrique active."
                }
                Text(
                    text = remark,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF334155),
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Component 5: Pillar 2 - Algologie & Cycle Ovarien (Post-Hystérectomie)
// ---------------------------------------------------------------------------

@Composable
private fun PillarAlgologieOvarienCard(
    avgPain: Float,
    maxPain: Int,
    severePainDays: Int,
    cycleDays: Int,
    tempSummary: ClinicalTempSummary,
    medEvaluated: Int,
    deltaEva: Float,
    reliefPercent: Int
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, ColoplastEmerald.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = ColoplastEmerald.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "2. ALGOLOGIE & CYCLE OVARIEN",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = ColoplastEmerald,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = "Post-Hystérectomie (Ovaires conservés)",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            val tempText = if (tempSummary.hasData) {
                if (tempSummary.highPhaseDetected) "+${String.format(Locale.FRANCE, "%.2f", tempSummary.deltaCelsius)}°C (phase lutéale)" else "Stable"
            } else "Capteur nocturne"

            val metrics = listOf(
                "EVA Douleur Moyen" to if (avgPain > 0) String.format(Locale.FRANCE, "%.1f / 10", avgPain) else "Bien contrôlé",
                "Pic Algique Maximal" to if (maxPain > 0) "$maxPain / 10" else "--",
                "Crises Aiguës (EVA ≥ 6)" to "$severePainDays j",
                "Cycle Menstruel Ressenti" to "$cycleDays j (sensation hormonale)",
                "Thermométrie Basale" to tempText,
                "Δ Douleur Post-Prise" to if (medEvaluated > 0) "-${String.format(Locale.FRANCE, "%.1f", deltaEva)} pts" else "À évaluer",
                "Soulagement Antalgique" to if (medEvaluated > 0) "$reliefPercent %" else "En cours",
                "Prises Médicamenteuses" to "$medEvaluated évaluée(s)"
            )

            metrics.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { (label, value) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF8FAFC),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray, maxLines = 1)
                                Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF1E293B))
                            }
                        }
                    }
                    if (row.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Clinical Observation
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFF8FAFC),
                modifier = Modifier.fillMaxWidth()
            ) {
                val remark = when {
                    severePainDays > 3 ->
                        "🟠 Recrudescence douloureuse : $severePainDays jour(s) de douleur aiguë (EVA ≥ 6) coïncidant avec les sensations de cycle ovarien."
                    tempSummary.highPhaseDetected && cycleDays > 0 ->
                        "🟢 Corrélation clinique validée : le décalage thermique basale (+${String.format(Locale.FRANCE, "%.2f", tempSummary.deltaCelsius)}°C) objective la phase lutéale ovulatoire en l'absence de repères utérins."
                    reliefPercent in 1..49 ->
                        "🟠 Efficacité antalgique modérée ($reliefPercent%). Une optimisation de la molécule de palier 2 est conseillée."
                    else ->
                        "🟢 Contrôle satisfaisant des algies pelviennes avec une bonne réponse thérapeutique globale."
                }
                Text(
                    text = remark,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF334155),
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Component 6: Pillar 3 - Système Autonome & Sommeil (Galaxy Watch 6)
// ---------------------------------------------------------------------------

@Composable
private fun PillarSystemeAutonomeCard(
    sleepSummary: ClinicalSleepSummary,
    hrSummary: ClinicalHeartRateSummary,
    hrvSummary: ClinicalHrvSummary,
    spo2Summary: ClinicalSpo2Summary,
    stepsSummary: ClinicalStepsSummary
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, ColoplastBlue.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = ColoplastBlue.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "3. SYSTÈME AUTONOME & SOMMEIL",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = ColoplastBlue,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = "Samsung Galaxy Watch 6",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            val metrics = listOf(
                "Sommeil Nocturne" to if (sleepSummary.hasData) sleepSummary.formattedAvgDuration else "Enregistré",
                "Micro-Réveils" to if (sleepSummary.hasData) String.format(Locale.FRANCE, "%.1f / nuit", sleepSummary.avgAwakeningsPerNight) else "--",
                "VFC Moyenne (HRV)" to if (hrvSummary.hasData && hrvSummary.avgRmssdMs > 0) "${hrvSummary.avgRmssdMs.toInt()} ms (RMSSD)" else "--",
                "FC Repos Moyenne" to if (hrSummary.hasData && hrSummary.avgRestingBpm > 0) "${hrSummary.avgRestingBpm} bpm" else "--",
                "Oxygénation SpO2" to if (spo2Summary.hasData && spo2Summary.avgSpo2Percent > 0) "${spo2Summary.avgSpo2Percent.toInt()} % (min: ${spo2Summary.minSpo2Percent.toInt()}%)" else "--",
                "Activité Podométrique" to if (stepsSummary.hasData && stepsSummary.avgDailySteps > 0) String.format(Locale.FRANCE, "%,d pas/j", stepsSummary.avgDailySteps) else "--"
            )

            metrics.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { (label, value) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF8FAFC),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray, maxLines = 1)
                                Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF1E293B))
                            }
                        }
                    }
                    if (row.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Clinical Observation
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFF8FAFC),
                modifier = Modifier.fillMaxWidth()
            ) {
                val remark = when {
                    spo2Summary.hasData && spo2Summary.avgSpo2Percent in 0.1f..92f ->
                        "🔴 Alerte : SpO2 nocturne moyenne basse (${spo2Summary.avgSpo2Percent.toInt()}%). Évaluer un dépistage d'apnées du sommeil."
                    sleepSummary.hasData && sleepSummary.avgAwakeningsPerNight >= 3.5f ->
                        "🟠 Fragmentation du sommeil : ${String.format(Locale.FRANCE, "%.1f", sleepSummary.avgAwakeningsPerNight)} réveils/nuit en moyenne, possiblement corrélés aux vidanges ou tiraillements."
                    hrvSummary.hasData && hrvSummary.avgRmssdMs >= 40f ->
                        "🟢 Tonus parasympathique préservé (VRC ${hrvSummary.avgRmssdMs.toInt()} ms) et bonne récupération neuro-végétative."
                    else ->
                        "🟢 Données physiologiques nocturnes stables sur la période observée."
                }
                Text(
                    text = remark,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF334155),
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Component 7: Pillar 4 - Anthropométrie & Équilibre Corporel
// ---------------------------------------------------------------------------

@Composable
private fun PillarAnthropometrieCard(
    avgWeight: Float,
    minWeight: Float,
    maxWeight: Float,
    deltaWeight: Float,
    bmi: Float,
    bodyWaterPercent: Float,
    measurementsCount: Int
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF334155).copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "4. ANTHROPOMÉTRIE & ÉQUILIBRE CORPOREL",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF334155),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = "$measurementsCount pesée(s)",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            val bmiCategory = when {
                bmi <= 0f -> "--"
                bmi < 18.5f -> "Insuffisance"
                bmi < 25.0f -> "Normal"
                bmi < 30.0f -> "Surpoids"
                else -> "Obésité"
            }

            val metrics = listOf(
                "Poids Moyen" to if (avgWeight > 0) String.format(Locale.FRANCE, "%.1f kg", avgWeight) else "--",
                "Évolution Pondérale (Δ)" to if (measurementsCount >= 2) "${if (deltaWeight >= 0) "+${String.format(Locale.FRANCE, "%.1f", deltaWeight)}" else String.format(Locale.FRANCE, "%.1f", deltaWeight)} kg" else "Stable",
                "Étendue (Min - Max)" to if (minWeight > 0) "${String.format(Locale.FRANCE, "%.1f", minWeight)} - ${String.format(Locale.FRANCE, "%.1f", maxWeight)} kg" else "--",
                "IMC Estimé" to if (bmi > 0) "${String.format(Locale.FRANCE, "%.1f", bmi)} ($bmiCategory)" else "--",
                "Taux d'Eau Corporel" to if (bodyWaterPercent > 0) String.format(Locale.FRANCE, "%.1f %%", bodyWaterPercent) else "--",
                "Stabilité Nutritionnelle" to if (abs(deltaWeight) <= 1.0f) "Optimale" else "À surveiller"
            )

            metrics.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { (label, value) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF8FAFC),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray, maxLines = 1)
                                Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF1E293B))
                            }
                        }
                    }
                    if (row.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Clinical Observation
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFF8FAFC),
                modifier = Modifier.fillMaxWidth()
            ) {
                val remark = when {
                    abs(deltaWeight) >= 2.5f ->
                        "🟠 Fluctuation pondérale rapide (${if (deltaWeight > 0) "+$deltaWeight" else "$deltaWeight"} kg) : vérifier l'absence d'œdème ou de déshydratation péristomiale."
                    avgWeight > 0 ->
                        "🟢 Poids stable sur la période, reflétant un bon état nutritionnel global."
                    else ->
                        "ℹ️ Aucune pesée sur la période. Poursuivre le suivi régulier sur la balance Bluetooth."
                }
                Text(
                    text = remark,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF334155),
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Component 8: Pharmacotherapy & Medication Response Table
// ---------------------------------------------------------------------------

@Composable
private fun PharmacotherapyTableCard(
    medStats: List<MedicationStats>,
    onManageMedications: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Medication, contentDescription = null, tint = ColoplastBlue)
                    Text(
                        text = "RÉPONSE PHARMACOLOGIQUE & ANTALGIQUE",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = ColoplastBlue
                    )
                }

                Text(
                    text = "${medStats.size} molécule(s)",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (medStats.isEmpty()) {
                Text(
                    text = "Aucune prise médicamenteuse enregistrée sur cette période.\nUtilisez le bouton Traitements ci-dessous pour documenter les prises et évaluer le soulagement à 1h.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            } else {
                // Table Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF1F5F9), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Molécule", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = ColoplastBlue, modifier = Modifier.weight(1.5f))
                    Text("Prises", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = ColoplastBlue, modifier = Modifier.weight(0.8f))
                    Text("EVA Av ➔ 1h", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = ColoplastBlue, modifier = Modifier.weight(1.4f))
                    Text("Soulagement", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = ColoplastBlue, modifier = Modifier.weight(1.2f))
                }

                Spacer(modifier = Modifier.height(4.dp))

                medStats.forEachIndexed { idx, s ->
                    val reliefColor = when {
                        s.avgReliefPercent >= 60 -> OptimalGreen
                        s.avgReliefPercent >= 30 -> AlertOrange
                        else -> AlertRed
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1.5f)) {
                            Text(s.name, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
                            if (s.failureCount > 0) {
                                Text("${s.failureCount} échec(s)", fontSize = 10.sp, color = AlertRed)
                            }
                        }
                        Text("${s.totalIntakes}", fontSize = 12.sp, modifier = Modifier.weight(0.8f))
                        Text(
                            text = if (s.evaluatedIntakes > 0) "${String.format(Locale.FRANCE, "%.1f", s.avgInitialPain)} ➔ ${String.format(Locale.FRANCE, "%.1f", s.avgPostPain)}" else "--",
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1.4f)
                        )
                        Column(modifier = Modifier.weight(1.2f)) {
                            Text(
                                text = if (s.evaluatedIntakes > 0) "${s.avgReliefPercent}%" else "En cours",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = reliefColor
                            )
                            if (s.evaluatedIntakes > 0 && s.avgDrop > 0f) {
                                Text(
                                    text = "Δ -${String.format(Locale.FRANCE, "%.1f", s.avgDrop)}",
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                    if (idx < medStats.size - 1) {
                        Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(Color(0xFFF1F5F9)))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Clinical Calculations & Summary Helpers
// ---------------------------------------------------------------------------

private fun buildClinicalFlags(
    period: ClinicalPeriod,
    waterIntakeL: Double,
    avgPain: Float,
    maxPain: Int,
    severePainDays: Int,
    medStats: List<MedicationStats>,
    stomaIncidents: Int,
    liquidStomaDays: Int,
    avgWaferWearDays: Double,
    glutenSuspicions: Int,
    endoBellyDays: Int,
    spo2Summary: ClinicalSpo2Summary,
    sleepSummary: ClinicalSleepSummary,
    weightsCount: Int,
    deltaWeight: Float
): List<ClinicalFlag> {
    val flags = mutableListOf<ClinicalFlag>()

    // 1. Hydration
    if (waterIntakeL in 0.001..1.49) {
        flags.add(
            ClinicalFlag(
                level = ClinicalAlertLevel.CRITICAL,
                category = "HYDRATATION",
                message = "Hydratation moyenne insuffisante (${String.format(Locale.FRANCE, "%.2f", waterIntakeL)} L/j < 1.5 L/j)",
                recommendation = "Compenser d'urgence les pertes colostomiques avec des solutés d'hydratation ou bouillons salés pour éviter l'hypovolémie."
            )
        )
    } else if (waterIntakeL in 1.50..1.99) {
        flags.add(
            ClinicalFlag(
                level = ClinicalAlertLevel.WARNING,
                category = "HYDRATATION",
                message = "Hydratation sous la cible (${String.format(Locale.FRANCE, "%.2f", waterIntakeL)} L/j < 2.0 L/j)",
                recommendation = "Objectif patient : augmenter la consommation d'eau et boissons électrolytiques à 2.0 L/j minimum."
            )
        )
    }

    // 2. Pain & Failures
    val totalEvaluated = medStats.sumOf { it.evaluatedIntakes }
    val totalFailures = medStats.sumOf { it.failureCount }
    if (avgPain >= 6.0f) {
        flags.add(
            ClinicalFlag(
                level = ClinicalAlertLevel.CRITICAL,
                category = "DOULEUR",
                message = "Douleur pelvienne sévère non contrôlée (EVA moyen ${String.format(Locale.FRANCE, "%.1f", avgPain)}/10)",
                recommendation = "Réévaluation urgente du protocole antalgique de palier 2/3 et consultation algologique."
            )
        )
    } else if (totalEvaluated >= 3 && (totalFailures * 100 / totalEvaluated) >= 50) {
        flags.add(
            ClinicalFlag(
                level = ClinicalAlertLevel.CRITICAL,
                category = "DOULEUR",
                message = "Échec thérapeutique fréquent ($totalFailures prises sans soulagement significatif)",
                recommendation = "Inefficacité des molécules actuelles sur les crises pelviennes : réadapter la classe pharmacologique."
            )
        )
    } else if (severePainDays >= 3 || maxPain >= 7) {
        flags.add(
            ClinicalFlag(
                level = ClinicalAlertLevel.WARNING,
                category = "DOULEUR",
                message = "Pics algiques aigus récurrents (Pic ${maxPain}/10, $severePainDays jour(s) EVA ≥ 6)",
                recommendation = "Favoriser la prise précoce des antalgiques dès les prodromes de crise hormonale."
            )
        )
    }

    // 3. Stoma incidents & output
    val incidentThreshold = if (period == ClinicalPeriod.WEEK_7D) 2 else 4
    if (stomaIncidents >= incidentThreshold) {
        flags.add(
            ClinicalFlag(
                level = ClinicalAlertLevel.CRITICAL,
                category = "STOMIE",
                message = "Incidents péristomiaux répétés ($stomaIncidents fuite(s) ou décollement(s))",
                recommendation = "Risque d'altération cutanée. Vérifier la découpe du support SenSura® Mio Click 60mm et l'application des anneaux Brava®."
            )
        )
    } else if (liquidStomaDays >= 3) {
        flags.add(
            ClinicalFlag(
                level = ClinicalAlertLevel.WARNING,
                category = "STOMIE",
                message = "Transit colostomique liquide à haut débit ($liquidStomaDays jour(s) de selles liquides)",
                recommendation = "Surveiller les pertes hydro-électrolytiques et limiter les aliments accélérateurs de transit."
            )
        )
    }

    if (avgWaferWearDays > 0.0 && (avgWaferWearDays < 1.0 || avgWaferWearDays > 4.0)) {
        flags.add(
            ClinicalFlag(
                level = ClinicalAlertLevel.WARNING,
                category = "STOMIE",
                message = "Rythme de port du support inadapté (${String.format(Locale.FRANCE, "%.1f", avgWaferWearDays)} j / support)",
                recommendation = "Cible recommandée : changement complet du support tous les 2 à 3 jours (48-72h)."
            )
        )
    }

    // 4. Digestive & Gluten
    if (glutenSuspicions >= 1 || endoBellyDays >= 2) {
        flags.add(
            ClinicalFlag(
                level = ClinicalAlertLevel.WARNING,
                category = "DIGESTIF",
                message = "Intolérance digestive ($endoBellyDays j Endo-Belly, $glutenSuspicions suspicion(s) gluten)",
                recommendation = "Vérifier le respect strict du régime sans gluten et les contaminations croisées."
            )
        )
    }

    // 5. Autonomic & SpO2
    if (spo2Summary.hasData && spo2Summary.avgSpo2Percent in 0.1f..92f) {
        flags.add(
            ClinicalFlag(
                level = ClinicalAlertLevel.CRITICAL,
                category = "SOMMEIL",
                message = "Désaturation nocturne SpO2 moyenne basse (${spo2Summary.avgSpo2Percent.toInt()} %)",
                recommendation = "Recherche d'apnées obstructives du sommeil ou d'hypoxie nocturne avec le médecin traitant."
            )
        )
    } else if (sleepSummary.hasData && (sleepSummary.avgDurationMinutes in 1..359 || sleepSummary.avgAwakeningsPerNight >= 4.0f)) {
        flags.add(
            ClinicalFlag(
                level = ClinicalAlertLevel.WARNING,
                category = "SOMMEIL",
                message = "Sommeil nocturne fragmenté (${sleepSummary.formattedAvgDuration}, ${String.format(Locale.FRANCE, "%.1f", sleepSummary.avgAwakeningsPerNight)} réveils/nuit)",
                recommendation = "Fractionnement du sommeil possiblement lié aux tiraillements ou à la gestion de la stomie."
            )
        )
    }

    // 6. Weight variance
    if (weightsCount >= 2 && abs(deltaWeight) >= 2.5f) {
        flags.add(
            ClinicalFlag(
                level = ClinicalAlertLevel.WARNING,
                category = "POIDS",
                message = "Variation pondérale rapide (${if (deltaWeight > 0) "+${String.format(Locale.FRANCE, "%.1f", deltaWeight)}" else String.format(Locale.FRANCE, "%.1f", deltaWeight)} kg sur ${period.label})",
                recommendation = "Vérifier l'absence de rétention hydrique ou de dénutrition."
            )
        )
    }

    return flags
}

private fun buildExecutiveSummary(
    period: ClinicalPeriod,
    status: ClinicalAlertLevel,
    waterL: Double,
    avgPain: Float,
    reliefPercent: Int,
    tempSummary: ClinicalTempSummary,
    stomaIncidents: Int
): String {
    return buildString {
        append("Sur la période ${period.label} : ")
        when (status) {
            ClinicalAlertLevel.OPTIMAL -> append("tableau clinique équilibré et bien contrôlé. ")
            ClinicalAlertLevel.WARNING -> append("points de vigilance identifiés nécessitant une surveillance attentive. ")
            ClinicalAlertLevel.CRITICAL -> append("alerte clinique active requérant un ajustement thérapeutique. ")
        }
        if (waterL > 0) {
            append("Hydratation moyenne à ${String.format(Locale.FRANCE, "%.2f L/j", waterL)}. ")
        }
        append("Douleur moyenne EVA ${String.format(Locale.FRANCE, "%.1f", avgPain)}/10")
        if (reliefPercent > 0) {
            append(" avec $reliefPercent% de soulagement post-antalgique. ")
        } else {
            append(". ")
        }
        if (tempSummary.hasData && tempSummary.highPhaseDetected) {
            append("Cycle ovarien objectivé (+${String.format(Locale.FRANCE, "%.2f", tempSummary.deltaCelsius)}°C phase lutéale). ")
        }
        if (stomaIncidents > 0) {
            append("$stomaIncidents incident(s) de stomie répertorié(s). ")
        } else {
            append("Appareillage SenSura® étanche sans fuite.")
        }
    }
}
