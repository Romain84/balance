/*
 * openScale
 * Copyright (C) 2026 openScale contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.health.openscale.ui.screen.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.health.openscale.core.stoma.ClinicalHydrationStatus
import com.health.openscale.core.stoma.DailyStomaSummary
import java.util.Locale

@Composable
fun StomaHydrationLiveCard(
    summary: DailyStomaSummary,
    isNotificationEnabled: Boolean,
    onAddWater: (Int) -> Unit,
    onLogPouch: () -> Unit,
    onLogSupport: () -> Unit,
    onToggleNotification: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val targetMl = summary.targetWaterMl.coerceAtLeast(1)
    val currentMl = summary.totalWaterMl
    val progress = (currentMl.toFloat() / targetMl).coerceIn(0f, 1f)
    val pct = (progress * 100).toInt()

    val statusColor = when (summary.clinicalStatus) {
        ClinicalHydrationStatus.OPTIMAL -> Color(0xFF1A8B55) // Emerald Green
        ClinicalHydrationStatus.WARNING -> Color(0xFFF57C00) // Amber / Orange
        ClinicalHydrationStatus.CRITICAL -> Color(0xFFD32F2F) // Red
    }

    val statusBadge = when (summary.clinicalStatus) {
        ClinicalHydrationStatus.OPTIMAL -> "🟢 Équilibre optimal"
        ClinicalHydrationStatus.WARNING -> "🟠 Vigilance clinique"
        ClinicalHydrationStatus.CRITICAL -> "🔴 Alerte déshydratation"
    }

    val supportDaysText = when {
        summary.lastSupportChangeTimestamp == null -> "Non renseigné"
        summary.daysSinceLastSupportChange == 0 -> "Changé aujourd'hui"
        summary.daysSinceLastSupportChange == 1 -> "Changé hier (1j)"
        else -> "Il y a ${summary.daysSinceLastSupportChange} jours"
    }

    val supportIsOld = summary.daysSinceLastSupportChange >= 3

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF0F7F9)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Title & Clinical Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF009DA8)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "💧",
                            fontSize = 16.sp
                        )
                    }
                    Column {
                        Text(
                            text = "Hydratation & Stomie",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF00365B)
                        )
                        Text(
                            text = "Suivi en direct au fil de l'eau",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusColor.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = statusBadge,
                        color = statusColor,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Hydration Progress Section
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Eau bue aujourd'hui :",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFF263238)
                        )
                        Text(
                            text = "${String.format(Locale.FRANCE, "%.2f", currentMl / 1000.0)} L / ${String.format(Locale.FRANCE, "%.2f", targetMl / 1000.0)} L ($pct%)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF0077A8)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = Color(0xFF009DA8),
                        trackColor = Color(0xFFE0F2F1)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Fast Action Buttons (+250ml, +500ml)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onAddWater(250) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF009DA8)
                            )
                        ) {
                            Text("+250 ml 💧", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { onAddWater(500) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0077A8)
                            )
                        ) {
                            Text("+500 ml 🥤", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Stoma Events Row (Poche seule & Changement Support complet)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Card Poche seule
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "🚽 Poche seule",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF00365B)
                        )
                        Text(
                            text = "${summary.pouchChangeCount} changée(s) auj.",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFF37474F)
                        )
                        OutlinedButton(
                            onClick = onLogPouch,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Poche", fontSize = 12.sp)
                        }
                    }
                }

                // Card Support complet
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (supportIsOld) Color(0xFFFFF3E0) else Color.White,
                    border = if (supportIsOld) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF57C00)) else null,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "🛡️ Support complet",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (supportIsOld) Color(0xFFE65100) else Color(0xFF00365B)
                        )
                        Text(
                            text = supportDaysText,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (supportIsOld) Color(0xFFE65100) else Color(0xFF37474F)
                        )
                        Button(
                            onClick = onLogSupport,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (supportIsOld) Color(0xFFF57C00) else Color(0xFF00365B)
                            )
                        ) {
                            Text("Remplacer", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Clinical Advice Box
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = statusColor.copy(alpha = 0.08f),
                border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (summary.clinicalStatus == ClinicalHydrationStatus.OPTIMAL) Icons.Default.Info else Icons.Default.Warning,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = summary.clinicalAdvice,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF263238)
                    )
                }
            }

            // Lock Screen Notification Switch Row
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Afficher sur l'écran de verrouillage",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFF00365B)
                        )
                        Text(
                            text = "Boutons cliquables sans déverrouiller le téléphone",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = isNotificationEnabled,
                        onCheckedChange = onToggleNotification,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF009DA8)
                        )
                    )
                }
            }
        }
    }
}
