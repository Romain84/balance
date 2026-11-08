/*
 * openScale
 * Copyright (C) 2026 openScale contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.health.openscale.ui.screen.medication

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.health.openscale.core.medication.MedicationIntake
import com.health.openscale.core.medication.MedicationRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationLogScreen(
    navController: NavController,
    sharedViewModel: com.health.openscale.ui.shared.SharedViewModel
) {
    val context = LocalContext.current
    val repository = remember { MedicationRepository(context) }
    val intakes by repository.intakes.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var intakeToEvaluate by remember { mutableStateOf<MedicationIntake?>(null) }
    var intakeToDelete by remember { mutableStateOf<MedicationIntake?>(null) }

    val stats = remember(intakes) { repository.computeStats() }
    val pendingEvaluations = remember(intakes) { repository.getPendingEvaluations() }
    val timeFormat = remember { SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE) }

    LaunchedEffect(Unit) {
        sharedViewModel.setTopBarTitle("Médicaments")
        sharedViewModel.setTopBarActions(emptyList())
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(modifier = Modifier.height(2.dp)) }

            // 0. Bannière Traitements & Antalgie
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF00365B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Medication,
                                    contentDescription = null,
                                    tint = Color(0xFF009DA8),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Traitements & Antalgie",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Suivi de l'efficacité & soulagement EVA à H+1",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                        Button(
                            onClick = { showAddDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF009DA8),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Prendre", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }

            // 1. Pending Evaluations Banner (if any taken recently without evaluation)
            if (pendingEvaluations.isNotEmpty()) {
                item {
                    val pending = pendingEvaluations.first()
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF57C00)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFE65100))
                                Text(
                                    text = "Évaluation de l'efficacité antalgique",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFFE65100)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Prise de ${pending.name} ${pending.dosage} à ${timeFormat.format(Date(pending.timestamp))}\nDouleur initiale déclarée : EVA ${pending.initialPainScore}/10",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF3E2723)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { intakeToEvaluate = pending },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Évaluer le soulagement ressenti (EVA actuelle)", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // 2. Efficacy Statistics per Molecule
            if (stats.isNotEmpty()) {
                item {
                    Text(
                        text = "EFFICACITÉ OBSERVÉE PAR MOLÉCULE",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF00365B)
                    )
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        stats.forEach { s ->
                            val reliefColor = when {
                                s.avgReliefPercent >= 60 -> Color(0xFF1A8B55)
                                s.avgReliefPercent >= 30 -> Color(0xFFF57C00)
                                else -> Color(0xFFD32F2F)
                            }
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = s.name,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = Color(0xFF00365B)
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = reliefColor.copy(alpha = 0.12f)
                                        ) {
                                            Text(
                                                text = if (s.evaluatedIntakes > 0) "${s.avgReliefPercent}% de soulagement" else "En cours d'évaluation",
                                                color = reliefColor,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    if (s.evaluatedIntakes > 0) {
                                        LinearProgressIndicator(
                                            progress = { s.avgReliefPercent / 100f },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp)),
                                            color = reliefColor,
                                            trackColor = Color(0xFFECEFF1)
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "EVA moyen : ${String.format(Locale.FRANCE, "%.1f", s.avgInitialPain)} ➔ ${String.format(Locale.FRANCE, "%.1f", s.avgPostPain)}/10",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF455A64)
                                            )
                                            Text(
                                                text = "${s.totalIntakes} prise(s)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.Gray
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "${s.totalIntakes} prise(s) enregistrée(s)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Chronological History of Intakes
            item {
                Text(
                    text = "HISTORIQUE RÉCENT DES PRISES",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF00365B)
                )
            }

            if (intakes.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF5F5F5),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Aucune prise de traitement enregistrée pour le moment.\nUtilisez le bouton ci-dessous pour ajouter un médicament.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(intakes, key = { it.id }) { item ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (item.postPainScore == null && item.initialPainScore != null) Color(0xFFFFCC80) else Color(0xFFEAEAEA)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { intakeToEvaluate = item }
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE8F5E9)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Medication,
                                    contentDescription = null,
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${item.name} ${item.dosage}".trim(),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF263238)
                                    )
                                    Text(
                                        text = timeFormat.format(Date(item.timestamp)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Gray
                                    )
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                val painSummary = if (item.initialPainScore != null) {
                                    if (item.postPainScore != null) {
                                        "EVA : ${item.initialPainScore}/10 ➔ ${item.postPainScore}/10 (${item.reliefPercent ?: 0}% soulagement) ✏️"
                                    } else {
                                        "EVA initiale : ${item.initialPainScore}/10 • ⏳ Cliquer pour renseigner à H+1"
                                    }
                                } else {
                                    "Prise de confort / traitement de fond"
                                }

                                Text(
                                    text = painSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (item.postPainScore != null) Color(0xFF1B5E20) else Color(0xFFE65100),
                                    fontWeight = if (item.postPainScore == null) FontWeight.SemiBold else FontWeight.Normal
                                )

                                if (!item.notes.isNullOrBlank()) {
                                    Text(
                                        text = item.notes,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF607D8B)
                                    )
                                }
                            }

                            // Action: Evaluate or Delete
                            Column(horizontalAlignment = Alignment.End) {
                                if (item.postPainScore == null && item.initialPainScore != null) {
                                    OutlinedButton(
                                        onClick = { intakeToEvaluate = item },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("Évaluer", fontSize = 11.sp)
                                    }
                                }
                                IconButton(
                                    onClick = { intakeToDelete = item },
                                    modifier = Modifier.size(28.dp)
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
            }

            item { Spacer(modifier = Modifier.height(60.dp)) }
        }
    }

    // Dialog 1: Log New Intake
    if (showAddDialog) {
        AddMedicationIntakeDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, dosage, category, pain, postPain, notes ->
                repository.logIntake(
                    name = name,
                    dosage = dosage,
                    category = category,
                    initialPainScore = pain,
                    postPainScore = postPain,
                    notes = notes
                )
                Toast.makeText(context, "Prise de $name enregistrée", Toast.LENGTH_SHORT).show()
                showAddDialog = false
            }
        )
    }

    // Dialog 2: Evaluate Relief
    intakeToEvaluate?.let { item ->
        EvaluateReliefDialog(
            intake = item,
            onDismiss = { intakeToEvaluate = null },
            onConfirm = { postScore, notes ->
                repository.evaluateIntake(item.id, postScore, notes)
                Toast.makeText(context, "Évaluation enregistrée", Toast.LENGTH_SHORT).show()
                intakeToEvaluate = null
            }
        )
    }

    // Dialog 3: Delete Confirmation
    intakeToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { intakeToDelete = null },
            title = { Text("Supprimer cette prise ?") },
            text = { Text("Voulez-vous supprimer l'enregistrement de ${item.name} ?") },
            confirmButton = {
                Button(
                    onClick = {
                        repository.deleteIntake(item.id)
                        intakeToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Supprimer")
                }
            },
            dismissButton = {
                TextButton(onClick = { intakeToDelete = null }) { Text("Annuler") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMedicationIntakeDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, dosage: String, category: String, painScore: Int?, postPainScore: Int?, notes: String?) -> Unit
) {
    val medicationOptions = listOf(
        "Lamaline",
        "Acupan",
        "Spasfon",
        "Doliprane",
        "Autre"
    )

    var medExpanded by remember { mutableStateOf(false) }
    var selectedName by remember { mutableStateOf("Lamaline") }
    var customName by remember { mutableStateOf("") }

    val dosageOptions = remember(selectedName) {
        when (selectedName) {
            "Lamaline" -> listOf("1 gélule", "2 gélules", "1 suppositoire", "Autre dosage...")
            "Acupan" -> listOf("20 mg (1 ampoule)", "10 mg (1/2 ampoule)", "Autre dosage...")
            "Spasfon" -> listOf("80 mg (2 comprimés)", "40 mg (1 comprimé)", "1 ampoule (buvable / injectable)", "Autre dosage...")
            "Doliprane" -> listOf("1000 mg", "500 mg", "Autre dosage...")
            else -> listOf("1 dose", "2 doses", "Autre dosage...")
        }
    }

    var dosageExpanded by remember { mutableStateOf(false) }
    var selectedDosage by remember { mutableStateOf(dosageOptions.first()) }
    var customDosage by remember { mutableStateOf("") }

    var initialPainScore by remember { mutableFloatStateOf(6f) }
    var evaluateNow by remember { mutableStateOf(false) }
    var postPainScore by remember { mutableFloatStateOf(3f) }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Nouvelle prise de médicament", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Douleur avant prise
                Text(
                    text = "Douleur avant la prise : EVA ${initialPainScore.toInt()}/10",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF00365B)
                )

                Slider(
                    value = initialPainScore,
                    onValueChange = { initialPainScore = it },
                    valueRange = 0f..10f,
                    steps = 9,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF00365B),
                        activeTrackColor = Color(0xFF009DA8)
                    )
                )

                // 2. Nom du médicament (Liste déroulante)
                Text(
                    text = "Nom du médicament :",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                )

                ExposedDropdownMenuBox(
                    expanded = medExpanded,
                    onExpandedChange = { medExpanded = !medExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = if (selectedName == "Autre") "Autre..." else selectedName,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = medExpanded) },
                        modifier = Modifier
                            .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = medExpanded,
                        onDismissRequest = { medExpanded = false }
                    ) {
                        medicationOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    selectedName = option
                                    selectedDosage = when (option) {
                                        "Lamaline" -> "1 gélule"
                                        "Acupan" -> "20 mg (1 ampoule)"
                                        "Spasfon" -> "80 mg (2 comprimés)"
                                        "Doliprane" -> "1000 mg"
                                        else -> "1 dose"
                                    }
                                    medExpanded = false
                                }
                            )
                        }
                    }
                }

                if (selectedName == "Autre") {
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("Nom de la molécule *") },
                        placeholder = { Text("Ex: Tramadol, Célestène...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 3. Dosage (Liste déroulante)
                Text(
                    text = "Dosage :",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                )

                ExposedDropdownMenuBox(
                    expanded = dosageExpanded,
                    onExpandedChange = { dosageExpanded = !dosageExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = if (selectedDosage == "Autre dosage...") "Autre dosage..." else selectedDosage,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dosageExpanded) },
                        modifier = Modifier
                            .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = dosageExpanded,
                        onDismissRequest = { dosageExpanded = false }
                    ) {
                        dosageOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    selectedDosage = option
                                    dosageExpanded = false
                                }
                            )
                        }
                    }
                }

                if (selectedDosage == "Autre dosage..." || selectedName == "Autre") {
                    OutlinedTextField(
                        value = customDosage,
                        onValueChange = { customDosage = it },
                        label = { Text("Préciser le dosage") },
                        placeholder = { Text("Ex: 1 sachet, 15 gouttes...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 4. Douleur après 1h (Optionnelle dès la prise ou ultérieure)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF5F9FA),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Renseigner la douleur après 1h :",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = Color(0xFF00365B),
                                modifier = Modifier.weight(1f)
                            )
                            androidx.compose.material3.Switch(
                                checked = evaluateNow,
                                onCheckedChange = { evaluateNow = it }
                            )
                        }

                        if (evaluateNow) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Douleur après 1h d'action (EVA : ${postPainScore.toInt()}/10) :",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF1B5E20)
                            )
                            Slider(
                                value = postPainScore,
                                onValueChange = { postPainScore = it },
                                valueRange = 0f..10f,
                                steps = 9,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF1B5E20),
                                    activeTrackColor = Color(0xFF2E7D32)
                                )
                            )
                        } else {
                            Text(
                                text = "Vous pourrez cliquer à tout moment sur la ligne dans l'historique pour renseigner l'EVA après coup.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }

                // 5. Remarques
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Remarques / Symptôme (facultatif)") },
                    placeholder = { Text("Ex: Crampes pelviennes, coliques...") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = if (selectedName == "Autre") customName.trim() else selectedName
                    val finalDosage = if (selectedDosage == "Autre dosage..." || selectedName == "Autre") customDosage.trim() else selectedDosage
                    if (finalName.isNotBlank()) {
                        onConfirm(
                            finalName,
                            finalDosage,
                            "Antalgique",
                            initialPainScore.toInt(),
                            if (evaluateNow) postPainScore.toInt() else null,
                            notes.takeIf { it.isNotBlank() }
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00365B))
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

@Composable
private fun EvaluateReliefDialog(
    intake: MedicationIntake,
    onDismiss: () -> Unit,
    onConfirm: (postPainScore: Int, notes: String?) -> Unit
) {
    val initial = intake.initialPainScore ?: 5
    var postScore by remember { mutableFloatStateOf((intake.postPainScore ?: initial.coerceAtMost(3)).toFloat()) }
    var notes by remember { mutableStateOf(intake.notes.orEmpty()) }

    val delta = (initial - postScore.toInt()).coerceAtLeast(0)
    val relief = if (initial > 0) ((delta.toDouble() / initial) * 100).toInt() else 100

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Évaluer la douleur à H+1", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Traitement pris : ${intake.name} (${intake.dosage})\nDouleur au moment de la prise : EVA $initial/10",
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = "Douleur après 1h d'action (EVA : ${postScore.toInt()}/10) :",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF00365B)
                )

                Slider(
                    value = postScore,
                    onValueChange = { postScore = it },
                    valueRange = 0f..10f,
                    steps = 9,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF00365B),
                        activeTrackColor = Color(0xFF1A8B55)
                    )
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFE8F5E9),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Soulagement estimé : $relief%",
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(8.dp)
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Commentaire (tolérance, effets indésirables)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(postScore.toInt(), notes.takeIf { it.isNotBlank() })
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A8B55))
            ) {
                Text("Enregistrer l'évaluation")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}
