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

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.health.openscale.core.data.HealthCheckin
import com.health.openscale.core.data.User
import com.health.openscale.core.healthconnect.ClinicalHealthConnectReader
import com.health.openscale.core.model.MeasurementWithValues
import com.health.openscale.core.service.MedicalReportGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MedicalReportDialog(
    user: User,
    measurements: List<MeasurementWithValues>,
    checkins: List<HealthCheckin>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedMonths by remember { mutableIntStateOf(3) }
    var isGenerating by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isGenerating) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Bilan Médical PDF",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Pour le praticien & le suivi patient",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Choisissez la période à intégrer dans le rapport clinique :",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(14.dp))

                val periodOptions = listOf(
                    1 to "1 mois (Ajustement récent)",
                    3 to "3 mois (Recommandé consultation)",
                    6 to "6 mois (Suivi semestriel)",
                    12 to "1 an (Bilan annuel complet)"
                )

                periodOptions.forEach { (months, label) ->
                    val isSelected = selectedMonths == months
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable(enabled = !isGenerating) { selectedMonths = months },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            if (isSelected) {
                                Text(
                                    text = "✓",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    )
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Ce document intègre automatiquement :",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• Stabilité du poids & % eau corporelle\n• Fréquence des débits & incidents de stomie\n• Échelle EVA douleur & cycle ressenti\n• Sommeil & thermométrie basale (Galaxy Watch)\n• Confort digestif & éviction sans gluten",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isGenerating = true
                    scope.launch(Dispatchers.IO) {
                        try {
                            val calendar = java.util.Calendar.getInstance()
                            val now = calendar.timeInMillis
                            calendar.add(java.util.Calendar.MONTH, -selectedMonths)
                            val startDate = calendar.timeInMillis

                            val reader = ClinicalHealthConnectReader(context)
                            val sleepSummary = reader.readSleepSummary(startDate, now)
                            val tempSummary = reader.readBasalTempSummary(startDate, now)
                            val hrSummary = reader.readHeartRateSummary(startDate, now)
                            val stepsSummary = reader.readStepsSummary(startDate, now)

                            val pdfFile = MedicalReportGenerator.generateReport(
                                context = context,
                                user = user,
                                months = selectedMonths,
                                measurements = measurements,
                                checkins = checkins,
                                sleepSummary = sleepSummary,
                                tempSummary = tempSummary,
                                hrSummary = hrSummary,
                                stepsSummary = stepsSummary
                            )
                            withContext(Dispatchers.Main) {
                                isGenerating = false
                                onDismiss()
                                MedicalReportGenerator.shareReport(context, pdfFile)
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                isGenerating = false
                                Toast.makeText(context, "Erreur génération PDF : ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                enabled = !isGenerating,
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Génération...")
                } else {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Générer & Partager (PDF)")
                }
            }
        },
        dismissButton = {
            if (!isGenerating) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Annuler")
                }
            }
        }
    )
}
