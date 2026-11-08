/*
 * openScale
 * Copyright (C) 2026 openScale contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.health.openscale.ui.screen.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import com.health.openscale.R
import com.health.openscale.core.healthconnect.ClinicalHealthConnectReader
import com.health.openscale.core.healthconnect.ClinicalHeartRateSummary
import com.health.openscale.core.healthconnect.ClinicalSleepSummary
import com.health.openscale.core.healthconnect.ClinicalStepsSummary
import com.health.openscale.core.healthconnect.ClinicalTempSummary
import com.health.openscale.core.healthconnect.HealthConnectSyncManager
import com.health.openscale.ui.shared.SharedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HealthConnectSettingsScreen(
    sharedViewModel: SharedViewModel,
    viewModel: HealthConnectSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var fullHistory by rememberSaveable { mutableStateOf(state.settings.lastSyncAt == null) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val permissionsLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (!state.settings.enabled) {
            viewModel.onPermissionResult(granted, fullHistoryOnEnable = fullHistory)
        } else {
            viewModel.requestPermissionsAgain(granted)
        }
    }
    val manageAccessLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refresh()
    }

    val reader = remember { ClinicalHealthConnectReader(context) }
    var watchSleep by remember { mutableStateOf(ClinicalSleepSummary()) }
    var watchTemp by remember { mutableStateOf(ClinicalTempSummary()) }
    var watchHr by remember { mutableStateOf(ClinicalHeartRateSummary()) }
    var watchSteps by remember { mutableStateOf(ClinicalStepsSummary()) }
    var isReadingWatch by remember { mutableStateOf(false) }

    val loadWatchData: () -> Unit = {
        coroutineScope.launch {
            isReadingWatch = true
            withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -7) }
                val start = cal.timeInMillis
                val s = reader.readSleepSummary(start, now)
                val t = reader.readBasalTempSummary(start, now)
                val hr = reader.readHeartRateSummary(start, now)
                val st = reader.readStepsSummary(start, now)
                withContext(Dispatchers.Main) {
                    watchSleep = s
                    watchTemp = t
                    watchHr = hr
                    watchSteps = st
                    isReadingWatch = false
                }
            }
        }
    }

    val title = stringResource(R.string.health_connect_title)
    LaunchedEffect(Unit) {
        sharedViewModel.setTopBarTitle(title)
        sharedViewModel.setTopBarActions(emptyList())
        viewModel.refresh()
    }
    LaunchedEffect(state.settings.lastSyncAt) {
        if (state.settings.lastSyncAt != null) fullHistory = false
    }
    LaunchedEffect(state.hasPermissions, state.hasAnyPermission) {
        if (state.hasAnyPermission) {
            loadWatchData()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Main Activation Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Synchronisation Bidirectionnelle",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = connectionStatus(state.sdkStatus, state.hasPermissions, state.hasAnyPermission),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.settings.enabled,
                    enabled = state.sdkStatus == HealthConnectClient.SDK_AVAILABLE,
                    onCheckedChange = { enabled ->
                        if (enabled) permissionsLauncher.launch(HealthConnectSyncManager.REQUIRED_PERMISSIONS)
                        else viewModel.disable()
                    }
                )
            }
        }

        // Global Permission Actions
        if (state.sdkStatus == HealthConnectClient.SDK_AVAILABLE) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!state.hasPermissions) {
                    Button(
                        onClick = { permissionsLauncher.launch(HealthConnectSyncManager.REQUIRED_PERMISSIONS) },
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00365B))
                    ) {
                        Text("Tout autoriser (2 sens)", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
                OutlinedButton(
                    onClick = {
                        manageAccessLauncher.launch(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Gérer l'accès", fontSize = 12.sp, maxLines = 1)
                }
            }
        }

        // -------------------------------------------------------------------------
        // FLUX 1 : ÉMISSION (openScale -> Health Connect / Samsung Health)
        // -------------------------------------------------------------------------
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE3F2FD)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = Color(0xFF1976D2), modifier = Modifier.size(18.dp))
                    }
                    Column {
                        Text(
                            text = "FLUX 1 : ÉMISSION VERS HEALTH CONNECT",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF1976D2)
                        )
                        Text(
                            text = "openScale ➔ Connexion Santé ➔ Samsung Health",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF9FAFB),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("• Données envoyées : Poids, Eau corporelle, Masse grasse, Masse osseuse, Masse musculaire, Métabolisme", fontSize = 11.sp, color = Color(0xFF37474F))
                        Text(
                            text = "• Pesées synchronisées : ${state.counts.synced} | En attente : ${state.counts.pending} | Échecs : ${state.counts.failed}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF263238)
                        )
                        val lastSyncText = state.settings.lastSyncAt?.let {
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))
                        } ?: "Jamais"
                        Text("• Dernière synchro : $lastSyncText", fontSize = 11.sp, color = Color.Gray)
                    }
                }

                if (state.settings.total > 0 && state.settings.progress < state.settings.total) {
                    LinearProgressIndicator(
                        progress = { state.settings.progress.toFloat() / state.settings.total.toFloat() },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = fullHistory, onCheckedChange = { fullHistory = it })
                        Text("Tout l'historique", fontSize = 12.sp)
                    }
                    Button(
                        onClick = { viewModel.syncNow(fullHistory) },
                        enabled = state.settings.enabled && state.hasAnyPermission,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Envoyer les pesées", fontSize = 12.sp)
                    }
                }
            }
        }

        // -------------------------------------------------------------------------
        // FLUX 2 : RÉCEPTION (Montre Samsung Galaxy Watch 6 -> openScale Santé)
        // -------------------------------------------------------------------------
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF009DA8).copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE0F7FA)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = Color(0xFF00838F), modifier = Modifier.size(18.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "FLUX 2 : RÉCEPTION MONTRE CONNECTÉE",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF00838F)
                        )
                        Text(
                            text = "Galaxy Watch 6 ➔ Samsung Health ➔ openScale",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    OutlinedButton(
                        onClick = { loadWatchData() },
                        shape = RoundedCornerShape(8.dp),
                        enabled = !isReadingWatch
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualiser", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isReadingWatch) "Lecture…" else "Lire", fontSize = 11.sp)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF0FDF4),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBBF7D0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Biomarqueurs récents reçus (derniers 7 jours) :", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF166534))

                        // Sommeil
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("🌙 Sommeil nocturne :", fontSize = 11.sp, color = Color(0xFF37474F))
                            Text(
                                text = if (watchSleep.hasData) "${watchSleep.formattedAvgDuration} (${String.format(Locale.FRANCE, "%.1f", watchSleep.avgAwakeningsPerNight)} réveils)" else "En attente de synchro",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (watchSleep.hasData) Color(0xFF1B5E20) else Color.Gray
                            )
                        }

                        // Température cutanée basale
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("🌡️ Température basale :", fontSize = 11.sp, color = Color(0xFF37474F))
                            Text(
                                text = if (watchTemp.hasData) "${String.format(Locale.FRANCE, "%.2f", watchTemp.avgTempCelsius)}°C ${if (watchTemp.highPhaseDetected) "(Phase lutéale)" else ""}" else "Capteur nocturne",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (watchTemp.hasData) Color(0xFF1B5E20) else Color.Gray
                            )
                        }

                        // Fréquence cardiaque
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("❤️ Fréquence cardiaque :", fontSize = 11.sp, color = Color(0xFF37474F))
                            Text(
                                text = if (watchHr.hasData) "${watchHr.avgRestingBpm} bpm au repos (${watchHr.minBpm}-${watchHr.maxBpm})" else "Capteur actif",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (watchHr.hasData) Color(0xFF1B5E20) else Color.Gray
                            )
                        }

                        // Activité physique
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("👟 Activité quotidienne :", fontSize = 11.sp, color = Color(0xFF37474F))
                            Text(
                                text = if (watchSteps.hasData) "${String.format(Locale.FRANCE, "%,d", watchSteps.avgDailySteps)} pas/j" else "Enregistré",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (watchSteps.hasData) Color(0xFF1B5E20) else Color.Gray
                            )
                        }
                    }
                }

                Text(
                    text = "Ces biomarqueurs enrichissent automatiquement le Mode Consultation Praticien et le Bilan Médical PDF A4.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun connectionStatus(
    sdkStatus: Int,
    hasPermissions: Boolean,
    hasAnyPermission: Boolean
): String = when {
    sdkStatus == HealthConnectClient.SDK_AVAILABLE && hasPermissions -> stringResource(R.string.health_connect_status_connected)
    sdkStatus == HealthConnectClient.SDK_AVAILABLE && hasAnyPermission -> "Connecté (autorisations actives)"
    sdkStatus == HealthConnectClient.SDK_AVAILABLE -> stringResource(R.string.health_connect_status_permissions)
    sdkStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> stringResource(R.string.health_connect_status_update)
    else -> stringResource(R.string.health_connect_status_unavailable)
}