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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversalAddSheet(
    onDismiss: () -> Unit,
    onLogPouchChange: () -> Unit,
    onLogSupportChange: () -> Unit,
    onAddWater250: () -> Unit,
    onAddWater500: () -> Unit,
    onOpenMedicationDialog: () -> Unit,
    onNewMeasurement: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Ajout Rapide",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF00365B)
                    )
                    Text(
                        text = "Sélectionnez l'action à enregistrer",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Fermer")
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 1. Changement de poche
            QuickActionItem(
                icon = Icons.Default.MedicalServices,
                iconTint = Color(0xFF00695C),
                iconBg = Color(0xFFE0F2F1),
                title = "Changement de poche",
                subtitle = "Poche seule Coloplast 60mm (grise ou noire)",
                onClick = {
                    onLogPouchChange()
                    onDismiss()
                }
            )

            // 2. Changement complet
            QuickActionItem(
                icon = Icons.Default.MedicalServices,
                iconTint = Color(0xFF00365B),
                iconBg = Color(0xFFE1F5FE),
                title = "Changement complet du support",
                subtitle = "Support semi-convexe + anneau Brava + poche",
                onClick = {
                    onLogSupportChange()
                    onDismiss()
                }
            )

            // 3. Verre d'eau 25cl
            QuickActionItem(
                icon = Icons.Default.LocalDrink,
                iconTint = Color(0xFF0277BD),
                iconBg = Color(0xFFE1F5FE),
                title = "1 verre d'eau (25 cl)",
                subtitle = "+ 250 ml d'hydratation",
                onClick = {
                    onAddWater250()
                    onDismiss()
                }
            )

            // 4. Bouteille 50cl
            QuickActionItem(
                icon = Icons.Default.WaterDrop,
                iconTint = Color(0xFF01579B),
                iconBg = Color(0xFFE0F7FA),
                title = "1 bouteille 50 cl",
                subtitle = "+ 500 ml d'hydratation",
                onClick = {
                    onAddWater500()
                    onDismiss()
                }
            )

            // 5. Médicament
            QuickActionItem(
                icon = Icons.Default.Medication,
                iconTint = Color(0xFF2E7D32),
                iconBg = Color(0xFFE8F5E9),
                title = "Prise de médicament",
                subtitle = "Lamaline, Spasfon, Acupan... avec suivi EVA",
                onClick = {
                    onDismiss()
                    onOpenMedicationDialog()
                }
            )

            // 6. Nouvelle pesée manuelle
            QuickActionItem(
                icon = Icons.Default.MonitorWeight,
                iconTint = Color(0xFF6A1B9A),
                iconBg = Color(0xFFF3E5F5),
                title = "Nouvelle pesée manuelle",
                subtitle = "Poids, masse hydrique, impédancemétrie",
                onClick = {
                    onDismiss()
                    onNewMeasurement()
                }
            )
        }
    }
}

@Composable
private fun QuickActionItem(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
