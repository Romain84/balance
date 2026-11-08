/*
 * openScale
 * Copyright (C) 2026 openScale contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.health.openscale.ui.screen.drinks

import android.widget.Toast
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.health.openscale.core.stoma.ClinicalHydrationStatus
import com.health.openscale.core.stoma.StomaEventType
import com.health.openscale.core.stoma.StomaNotificationManager
import com.health.openscale.core.stoma.StomaTrackingRepository
import com.health.openscale.ui.shared.SharedViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DrinksScreen(
    navController: NavController,
    sharedViewModel: SharedViewModel
) {
    val context = LocalContext.current
    val stomaRepo = remember { StomaTrackingRepository(context) }
    val todaySummary by stomaRepo.todaySummary.collectAsState()
    val allEvents by stomaRepo.eventsFlow.collectAsState()

    var isLockscreenNotifEnabled by remember {
        mutableStateOf(stomaRepo.isNotificationEnabled())
    }

    var showCustomDialog by remember { mutableStateOf(false) }
    var customAmountText by remember { mutableStateOf("330") }

    val waterEvents = remember(allEvents) {
        allEvents.filter { it.type == StomaEventType.WATER }
            .sortedByDescending { it.timestamp }
    }

    val timeFormat = remember { SimpleDateFormat("dd/MM/yyyy 'à' HH:mm", Locale.FRANCE) }

    LaunchedEffect(Unit) {
        sharedViewModel.setTopBarTitle("Boissons")
        sharedViewModel.setTopBarActions(emptyList())
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(modifier = Modifier.height(2.dp)) }

        // 1. Jauge quotidienne d'eau
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.WaterDrop,
                                contentDescription = null,
                                tint = Color(0xFF009DA8),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Hydratation du Jour",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF00365B)
                            )
                        }

                        val progressRatio = (todaySummary.totalWaterMl.toFloat() / todaySummary.targetWaterMl).coerceIn(0f, 1f)
                        val percent = (progressRatio * 100).toInt()
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (percent >= 100) Color(0xFFE8F5E9) else Color(0xFFE1F5FE)
                        ) {
                            Text(
                                text = "$percent%",
                                color = if (percent >= 100) Color(0xFF2E7D32) else Color(0xFF0277BD),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    // Large figures
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${todaySummary.totalWaterMl}",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF00365B)
                        )
                        Text(
                            text = " / ${todaySummary.targetWaterMl} ml",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 3.dp, start = 4.dp)
                        )
                    }

                    LinearProgressIndicator(
                        progress = { (todaySummary.totalWaterMl.toFloat() / todaySummary.targetWaterMl).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = Color(0xFF009DA8),
                        trackColor = Color(0xFFE0F2F1)
                    )

                    // Clinical Advice
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF5F9FA),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = todaySummary.clinicalAdvice,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF37474F),
                            modifier = Modifier.padding(8.dp)
                        )
                    }

                    // Boutons rapides d'ajout d'eau
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                stomaRepo.addWater(250)
                                if (isLockscreenNotifEnabled) {
                                    StomaNotificationManager.showOrUpdateNotification(context, stomaRepo.getTodaySummary())
                                }
                                Toast.makeText(context, "+ 250 ml d'eau enregistré 💧", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0277BD)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("+ 25 cl", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                stomaRepo.addWater(500)
                                if (isLockscreenNotifEnabled) {
                                    StomaNotificationManager.showOrUpdateNotification(context, stomaRepo.getTodaySummary())
                                }
                                Toast.makeText(context, "+ 500 ml d'eau enregistré 🥤", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00695C)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("+ 50 cl", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { showCustomDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Autre…", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 2. Écran de verrouillage Switch Card
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE8E8E8)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = Color(0xFF00365B),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Écran de verrouillage",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF00365B)
                            )
                            Text(
                                text = "Boutons +25cl / +50cl sans déverrouiller",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }

                    Switch(
                        checked = isLockscreenNotifEnabled,
                        onCheckedChange = { enabled ->
                            isLockscreenNotifEnabled = enabled
                            stomaRepo.setNotificationEnabled(enabled)
                            if (enabled) {
                                StomaNotificationManager.showOrUpdateNotification(context, stomaRepo.getTodaySummary())
                            } else {
                                StomaNotificationManager.cancelNotification(context)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00365B),
                            checkedTrackColor = Color(0xFF009DA8).copy(alpha = 0.5f)
                        )
                    )
                }
            }
        }

        // 3. Historique de toutes les boissons
        item {
            Text(
                text = "HISTORIQUE DES BOISSONS (${waterEvents.size})",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF00365B),
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (waterEvents.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEEEEEE)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Aucune boisson enregistrée pour l'instant.\nUtilisez les boutons +25 cl ou +50 cl pour suivre votre hydratation.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                    }
                }
            }
        } else {
            items(waterEvents, key = { it.id }) { ev ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEAEAEA)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE1F5FE)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.WaterDrop,
                                contentDescription = null,
                                tint = Color(0xFF0277BD),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "+ ${ev.amountMl} ml (${if (ev.amountMl >= 1000) String.format(Locale.FRANCE, "%.1f L", ev.amountMl / 1000f) else "${ev.amountMl / 10} cl"})",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF00365B)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = timeFormat.format(Date(ev.timestamp)),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }

                        IconButton(
                            onClick = {
                                stomaRepo.deleteEvent(ev.id)
                                if (isLockscreenNotifEnabled) {
                                    StomaNotificationManager.showOrUpdateNotification(context, stomaRepo.getTodaySummary())
                                }
                                Toast.makeText(context, "Boisson supprimée", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Supprimer",
                                tint = Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(70.dp)) }
    }

    // Dialog Custom Volume
    if (showCustomDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = { Text("Ajouter une boisson", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Indiquez le volume en millilitres (ml) :", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = customAmountText,
                        onValueChange = { customAmountText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Volume (ml)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = customAmountText.toIntOrNull() ?: 0
                        if (amount > 0) {
                            stomaRepo.addWater(amount)
                            if (isLockscreenNotifEnabled) {
                                StomaNotificationManager.showOrUpdateNotification(context, stomaRepo.getTodaySummary())
                            }
                            Toast.makeText(context, "+ $amount ml d'eau enregistré 💧", Toast.LENGTH_SHORT).show()
                        }
                        showCustomDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00365B))
                ) {
                    Text("Ajouter")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) { Text("Annuler") }
            }
        )
    }
}
