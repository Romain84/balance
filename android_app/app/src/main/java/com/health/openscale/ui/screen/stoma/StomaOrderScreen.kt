/*
 * openScale
 * Copyright (C) 2026 openScale contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.health.openscale.ui.screen.stoma

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.health.openscale.core.data.StomaCatalog
import com.health.openscale.core.data.StomaOrderHistoryEntry
import com.health.openscale.core.data.StomaOrderHistoryRepository
import com.health.openscale.core.data.StomaOrderItemRecord
import com.health.openscale.core.data.StomaProduct
import com.health.openscale.core.data.StomaProductRepository
import com.health.openscale.core.service.StomaOrderReportGenerator
import com.health.openscale.core.stoma.StomaEvent
import com.health.openscale.core.stoma.StomaEventType
import com.health.openscale.core.stoma.StomaTrackingRepository
import com.health.openscale.ui.shared.SharedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

private val ColoplastBlue = Color(0xFF00365B)
private val EmeraldGreen = Color(0xFF00695C)
private val TurquoiseTeal = Color(0xFF009DA8)
private val SoftTealBg = Color(0xFFEAF7F8)
private val SoftBlueBg = Color(0xFFF0F4F8)

private const val THIRTY_DAYS_MS = 30L * 24L * 3600L * 1000L
private const val ONE_DAY_MS = 24.0 * 3600.0 * 1000.0

private data class StomaConsumptionEstimate(
    val eventCount: Int,
    val pouchOnlyCount: Int,
    val supportCount: Int,
    val observedDays: Int,
    val pouchesPerDay: Double,
    val waferWearDays: Double?,
    val recommendedPouches30d: Int,
    val recommendedSupports30d: Int
)

private fun computeMonthlyEstimate(
    events: List<StomaEvent>,
    nowMillis: Long = System.currentTimeMillis()
): StomaConsumptionEstimate? {
    val windowStart = nowMillis - THIRTY_DAYS_MS
    val recent = events.filter {
        it.timestamp in windowStart..nowMillis &&
            (it.type == StomaEventType.POUCH_CHANGE || it.type == StomaEventType.SUPPORT_CHANGE)
    }
    if (recent.size < 2) return null

    val pouchOnlyCount = recent.count { it.type == StomaEventType.POUCH_CHANGE }
    val supportEvents = recent.filter { it.type == StomaEventType.SUPPORT_CHANGE }.sortedBy { it.timestamp }
    val supportCount = supportEvents.size

    // A complete support change also replaces the pouch.
    val totalPouchesUsed = pouchOnlyCount + supportCount
    val oldestTs = recent.minOf { it.timestamp }
    val spanDays = ((nowMillis - oldestTs).toDouble() / ONE_DAY_MS).coerceIn(1.0, 30.0)
    val pouchesPerDay = totalPouchesUsed / spanDays

    val waferWearDays = if (supportCount >= 2) {
        val supportSpanDays = (supportEvents.last().timestamp - supportEvents.first().timestamp).toDouble() / ONE_DAY_MS
        (supportSpanDays / (supportCount - 1)).coerceAtLeast(0.5)
    } else {
        null
    }

    val recommendedPouches30d = ceil(pouchesPerDay * 30.0).toInt().coerceAtLeast(1)
    val recommendedSupports30d = when {
        waferWearDays != null -> ceil(30.0 / waferWearDays).toInt().coerceAtLeast(1)
        supportCount >= 1 -> ceil((supportCount / spanDays) * 30.0).toInt().coerceAtLeast(1)
        else -> 10
    }

    return StomaConsumptionEstimate(
        eventCount = recent.size,
        pouchOnlyCount = pouchOnlyCount,
        supportCount = supportCount,
        observedDays = spanDays.roundToInt().coerceAtLeast(1),
        pouchesPerDay = pouchesPerDay,
        waferWearDays = waferWearDays,
        recommendedPouches30d = recommendedPouches30d,
        recommendedSupports30d = recommendedSupports30d
    )
}

private fun buildOrderItems(
    products: List<StomaProduct>,
    quantities: Map<String, Int>
): List<StomaOrderReportGenerator.OrderItem> {
    return products.mapNotNull { product ->
        val qty = quantities[product.id] ?: 0
        if (qty > 0) StomaOrderReportGenerator.OrderItem(product, qty) else null
    }
}

private fun toOrderItemRecords(
    orderItems: List<StomaOrderReportGenerator.OrderItem>
): List<StomaOrderItemRecord> {
    return orderItems.map { item ->
        StomaOrderItemRecord(
            productId = item.product.id,
            ref = item.product.ref,
            name = item.product.name,
            detail = item.product.detail,
            gtin = item.product.gtin,
            quantity = item.quantity
        )
    }
}

private fun historyEntryToOrderItems(
    entry: StomaOrderHistoryEntry
): List<StomaOrderReportGenerator.OrderItem> {
    return entry.items.map { rec ->
        StomaOrderReportGenerator.OrderItem(
            product = StomaProduct(
                id = rec.productId,
                ref = rec.ref,
                name = rec.name,
                detail = rec.detail,
                gtin = rec.gtin,
                category = ""
            ),
            quantity = rec.quantity
        )
    }
}

private fun copyTextToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StomaOrderScreen(
    navController: NavController,
    sharedViewModel: SharedViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activeUser by sharedViewModel.selectedUser.collectAsState()

    val repository = remember { StomaProductRepository(context) }
    val historyRepo = remember { StomaOrderHistoryRepository(context) }
    val trackingRepo = remember { StomaTrackingRepository(context) }
    val generator = remember { StomaOrderReportGenerator(context) }

    val allProducts by repository.products.collectAsState()
    val orderHistory by historyRepo.ordersFlow.collectAsState()
    val customPreset by historyRepo.customPresetFlow.collectAsState()
    val trackingEvents by trackingRepo.eventsFlow.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var patientName by remember(activeUser) {
        mutableStateOf(activeUser?.name ?: "")
    }
    var notes by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    // Map of product id -> quantity
    val quantities = remember { mutableStateMapOf<String, Int>() }
    var isBusy by remember { mutableStateOf(false) }
    var isCartExpanded by remember { mutableStateOf(false) }

    // Dialog states
    var showEditDialog by remember { mutableStateOf(false) }
    var productToEdit by remember { mutableStateOf<StomaProduct?>(null) }
    var productToDelete by remember { mutableStateOf<StomaProduct?>(null) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showTopMenu by remember { mutableStateOf(false) }
    var quantityDialogProduct by remember { mutableStateOf<StomaProduct?>(null) }
    var historyEntryToDelete by remember { mutableStateOf<StomaOrderHistoryEntry?>(null) }

    val categories = remember(allProducts) {
        listOf("Tous") + allProducts.map { it.category }.distinct()
    }

    val filteredProducts = remember(allProducts, searchQuery, selectedCategory) {
        allProducts.filter { prod ->
            val matchesCategory = selectedCategory == null || selectedCategory == "Tous" || prod.category == selectedCategory
            val matchesSearch = searchQuery.isBlank() ||
                prod.name.contains(searchQuery, ignoreCase = true) ||
                prod.ref.contains(searchQuery, ignoreCase = true) ||
                prod.detail.contains(searchQuery, ignoreCase = true) ||
                prod.gtin.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    val selectedOrderItems = remember(allProducts, quantities.toMap()) {
        buildOrderItems(allProducts, quantities)
    }
    val totalSelectedItems = selectedOrderItems.size
    val totalUnits = selectedOrderItems.sumOf { it.quantity }

    val consumptionEstimate = remember(trackingEvents) {
        computeMonthlyEstimate(trackingEvents)
    }

    val hasCustomPreset = customPreset.isNotEmpty()
    val effectiveMonthlyPreset = remember(customPreset) {
        if (customPreset.isNotEmpty()) customPreset else StomaCatalog.getDefaultMonthlyPreset()
    }

    fun applyPreset(multiplier: Int) {
        quantities.clear()
        var appliedCount = 0
        allProducts.forEach { prod ->
            val baseQty = effectiveMonthlyPreset[prod.id] ?: 0
            if (baseQty > 0) {
                quantities[prod.id] = baseQty * multiplier
                appliedCount++
            }
        }
        val msg = when {
            appliedCount == 0 -> "Aucun article du modèle trouvé dans le catalogue"
            multiplier == 2 -> "Quantités sur 2 mois appliquées (×2) ✓"
            hasCustomPreset -> "Modèle mensuel personnalisé appliqué ✓"
            else -> "Commande mensuelle type appliquée ✓"
        }
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    fun launchOrderWithPdf(
        errorPrefix: String,
        onReady: (file: File, patient: String, items: List<StomaOrderReportGenerator.OrderItem>, cleanNotes: String?) -> Unit
    ) {
        if (selectedOrderItems.isEmpty()) {
            Toast.makeText(context, "Sélectionnez au moins un article", Toast.LENGTH_SHORT).show()
            return
        }
        val cleanNotes = notes.takeIf { it.isNotBlank() }
        val itemsSnapshot = selectedOrderItems.toList()
        val patientSnapshot = patientName
        isBusy = true
        scope.launch(Dispatchers.IO) {
            try {
                val file = generator.generateOrderPdf(
                    patientName = patientSnapshot,
                    items = itemsSnapshot,
                    notes = cleanNotes
                )
                historyRepo.saveOrder(
                    patientName = patientSnapshot,
                    notes = cleanNotes,
                    items = toOrderItemRecords(itemsSnapshot)
                )
                withContext(Dispatchers.Main) {
                    isBusy = false
                    onReady(file, patientSnapshot, itemsSnapshot, cleanNotes)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isBusy = false
                    Toast.makeText(context, "$errorPrefix : ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun handleGenerateAndSharePdf() {
        launchOrderWithPdf("Erreur génération PDF") { file, _, _, _ ->
            generator.shareOrder(file)
        }
    }

    fun handleCopySummary() {
        if (selectedOrderItems.isEmpty()) {
            Toast.makeText(context, "Sélectionnez au moins un article", Toast.LENGTH_SHORT).show()
            return
        }
        val cleanNotes = notes.takeIf { it.isNotBlank() }
        val itemsSnapshot = selectedOrderItems.toList()
        val patientSnapshot = patientName
        val summary = generator.buildPlainTextSummary(
            patientName = patientSnapshot,
            items = itemsSnapshot,
            notes = cleanNotes
        )
        copyTextToClipboard(context, "Commande Matériel Stomie", summary)
        scope.launch(Dispatchers.IO) {
            historyRepo.saveOrder(
                patientName = patientSnapshot,
                notes = cleanNotes,
                items = toOrderItemRecords(itemsSnapshot)
            )
        }
        Toast.makeText(context, "Texte copié pour SMS / WhatsApp & sauvegardé ✓", Toast.LENGTH_SHORT).show()
    }

    fun handleSendEmail() {
        launchOrderWithPdf("Erreur préparation e-mail") { file, patient, items, cleanNotes ->
            generator.sendByEmail(
                patientName = patient,
                items = items,
                notes = cleanNotes,
                attachmentFile = file
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Commande de Matériel",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Catalogue & Bon Pharmacie",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour"
                        )
                    }
                },
                actions = {
                    if (totalSelectedItems > 0) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = TurquoiseTeal,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text(
                                text = "$totalSelectedItems réf. • $totalUnits",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    IconButton(onClick = {
                        productToEdit = null
                        showEditDialog = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Ajouter un matériel",
                            tint = ColoplastBlue
                        )
                    }

                    Box {
                        IconButton(onClick = { showTopMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options"
                            )
                        }
                        DropdownMenu(
                            expanded = showTopMenu,
                            onDismissRequest = { showTopMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Ajouter un matériel") },
                                leadingIcon = {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = TurquoiseTeal)
                                },
                                onClick = {
                                    showTopMenu = false
                                    productToEdit = null
                                    showEditDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Restaurer catalogue par défaut") },
                                leadingIcon = {
                                    Icon(Icons.Default.Refresh, contentDescription = null, tint = ColoplastBlue)
                                },
                                onClick = {
                                    showTopMenu = false
                                    showResetConfirmDialog = true
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (selectedTab == 0) {
                OrderBottomActionBar(
                    selectedItems = selectedOrderItems,
                    totalSelectedItems = totalSelectedItems,
                    totalUnits = totalUnits,
                    isCartExpanded = isCartExpanded,
                    isBusy = isBusy,
                    onToggleCartExpanded = { isCartExpanded = !isCartExpanded },
                    onRemoveItem = { prodId -> quantities[prodId] = 0 },
                    onGeneratePdf = ::handleGenerateAndSharePdf,
                    onCopyText = ::handleCopySummary,
                    onSendEmail = ::handleSendEmail
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Segmented Tab Switch
            StomaSegmentedTabSwitch(
                selectedTab = selectedTab,
                orderCount = orderHistory.size,
                onSelectTab = { selectedTab = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (selectedTab == 0) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Patient Header Card
                    item {
                        PatientInfoCard(
                            patientName = patientName,
                            notes = notes,
                            onPatientNameChange = { patientName = it },
                            onNotesChange = { notes = it }
                        )
                    }

                    // Consumption Estimator Card (30-day rolling tracking)
                    item {
                        StomaConsumptionEstimatorCard(estimate = consumptionEstimate)
                    }

                    // Quick Order Presets Bar (1-click)
                    item {
                        QuickOrderPresetsBar(
                            hasCustomPreset = hasCustomPreset,
                            hasSelection = totalSelectedItems > 0,
                            onApplyMonthly = { applyPreset(multiplier = 1) },
                            onApplyTwoMonths = { applyPreset(multiplier = 2) },
                            onSaveAsPreset = {
                                val snapshot = quantities.filterValues { it > 0 }.toMap()
                                if (snapshot.isEmpty()) {
                                    Toast.makeText(
                                        context,
                                        "Sélectionnez d'abord des articles à sauvegarder",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    historyRepo.saveCustomPreset(snapshot)
                                    Toast.makeText(
                                        context,
                                        "Panier enregistré comme mensuel type ✓",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            onClear = {
                                quantities.clear()
                                isCartExpanded = false
                                Toast.makeText(context, "Panier vidé", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    // Search Bar
                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Rechercher réf, nom, anneau 60mm…") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = TurquoiseTeal
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Effacer")
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TurquoiseTeal,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }

                    // Category Filter Row with counts & selected badges
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(categories) { cat ->
                                val isSelected = (selectedCategory == cat) || (selectedCategory == null && cat == "Tous")
                                val catProducts = if (cat == "Tous") {
                                    allProducts
                                } else {
                                    allProducts.filter { it.category == cat }
                                }
                                val totalInCat = catProducts.size
                                val selectedInCat = catProducts.count { (quantities[it.id] ?: 0) > 0 }

                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedCategory = if (cat == "Tous") null else cat
                                    },
                                    label = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "$cat ($totalInCat)",
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                            if (selectedInCat > 0) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = if (isSelected) TurquoiseTeal else EmeraldGreen
                                                ) {
                                                    Text(
                                                        text = "$selectedInCat",
                                                        color = Color.White,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ColoplastBlue,
                                        selectedLabelColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(20.dp)
                                )
                            }
                        }
                    }

                    // Products List
                    items(filteredProducts, key = { it.id }) { product ->
                        val currentQty = quantities[product.id] ?: 0
                        val isSelected = currentQty > 0

                        StomaProductCard(
                            product = product,
                            quantity = currentQty,
                            isSelected = isSelected,
                            onToggleSelect = {
                                if (isSelected) {
                                    quantities[product.id] = 0
                                } else {
                                    quantities[product.id] = product.defaultQty.coerceAtLeast(1)
                                }
                            },
                            onIncrement = {
                                quantities[product.id] = (quantities[product.id] ?: 0) + 1
                            },
                            onDecrement = {
                                val current = quantities[product.id] ?: 0
                                if (current > 1) {
                                    quantities[product.id] = current - 1
                                } else {
                                    quantities[product.id] = 0
                                }
                            },
                            onEditExactQty = {
                                quantityDialogProduct = product
                            },
                            onEdit = {
                                productToEdit = product
                                showEditDialog = true
                            },
                            onDelete = {
                                productToDelete = product
                            }
                        )
                    }

                    // Quick Add Card at bottom
                    item {
                        OutlinedButton(
                            onClick = {
                                productToEdit = null
                                showEditDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = TurquoiseTeal)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Ajouter un nouveau produit à la liste",
                                color = ColoplastBlue,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            } else {
                // History Tab
                StomaOrderHistoryTab(
                    orders = orderHistory,
                    isBusy = isBusy,
                    onReorder = { entry ->
                        quantities.clear()
                        var matchedCount = 0
                        entry.items.forEach { rec ->
                            val targetProduct = allProducts.find { it.id == rec.productId }
                                ?: if (rec.ref != "---") allProducts.find { it.ref == rec.ref } else null
                            if (targetProduct != null && rec.quantity > 0) {
                                quantities[targetProduct.id] = rec.quantity
                                matchedCount++
                            }
                        }
                        if (entry.patientName.isNotBlank()) {
                            patientName = entry.patientName
                        }
                        if (!entry.notes.isNullOrBlank()) {
                            notes = entry.notes
                        }
                        selectedTab = 0
                        val toastMsg = if (matchedCount == entry.items.size) {
                            "Commande chargée dans le panier ✓"
                        } else {
                            "$matchedCount/${entry.items.size} article(s) chargé(s) dans le panier"
                        }
                        Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                    },
                    onSharePdf = { entry ->
                        isBusy = true
                        scope.launch(Dispatchers.IO) {
                            try {
                                val orderItems = historyEntryToOrderItems(entry)
                                val file = generator.generateOrderPdf(
                                    patientName = entry.patientName,
                                    items = orderItems,
                                    notes = entry.notes
                                )
                                withContext(Dispatchers.Main) {
                                    isBusy = false
                                    generator.shareOrder(file)
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    isBusy = false
                                    Toast.makeText(context, "Erreur PDF : ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    onCopyText = { entry ->
                        val orderItems = historyEntryToOrderItems(entry)
                        val summary = generator.buildPlainTextSummary(
                            patientName = entry.patientName,
                            items = orderItems,
                            notes = entry.notes
                        )
                        copyTextToClipboard(context, "Commande Matériel Stomie", summary)
                        Toast.makeText(context, "Commande copiée dans le presse-papiers ✓", Toast.LENGTH_SHORT).show()
                    },
                    onDelete = { entry ->
                        historyEntryToDelete = entry
                    }
                )
            }
        }
    }

    // Compact Exact Quantity Dialog
    quantityDialogProduct?.let { prod ->
        ExactQuantityDialog(
            productName = prod.name,
            initialQty = quantities[prod.id] ?: 0,
            onDismiss = { quantityDialogProduct = null },
            onConfirm = { newQty ->
                if (newQty <= 0) {
                    quantities[prod.id] = 0
                } else {
                    quantities[prod.id] = newQty
                }
                quantityDialogProduct = null
            }
        )
    }

    // Dialog for Adding or Editing a Product
    if (showEditDialog) {
        StomaProductEditDialog(
            initialProduct = productToEdit,
            existingCategories = allProducts.map { it.category }.distinct(),
            onDismiss = {
                showEditDialog = false
                productToEdit = null
            },
            onSave = { savedProduct ->
                if (productToEdit == null) {
                    repository.addProduct(savedProduct)
                    Toast.makeText(context, "Produit « ${savedProduct.name} » ajouté", Toast.LENGTH_SHORT).show()
                } else {
                    repository.updateProduct(savedProduct)
                    Toast.makeText(context, "Produit « ${savedProduct.name} » modifié", Toast.LENGTH_SHORT).show()
                }
                showEditDialog = false
                productToEdit = null
            }
        )
    }

    // Confirmation Dialog for Deleting a Product
    productToDelete?.let { prod ->
        AlertDialog(
            onDismissRequest = { productToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(text = "Supprimer le produit ?")
            },
            text = {
                Text(
                    text = "Voulez-vous vraiment retirer « ${prod.name} » (Réf. ${prod.ref}) de la liste des produits commandables ?\n\nVous pourrez toujours restaurer le catalogue par défaut depuis le menu."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        quantities.remove(prod.id)
                        repository.deleteProduct(prod.id)
                        Toast.makeText(context, "« ${prod.name} » supprimé", Toast.LENGTH_SHORT).show()
                        productToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Supprimer")
                }
            },
            dismissButton = {
                TextButton(onClick = { productToDelete = null }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Confirmation Dialog for Resetting Catalog to Defaults
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = ColoplastBlue
                )
            },
            title = {
                Text(text = "Restaurer le catalogue d'origine ?")
            },
            text = {
                Text(
                    text = "Cette action rétablira le catalogue par défaut des références de stomie.\nVos personnalisations actuelles du catalogue seront remplacées."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        quantities.clear()
                        repository.resetToDefaults()
                        Toast.makeText(context, "Catalogue par défaut restauré", Toast.LENGTH_SHORT).show()
                        showResetConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColoplastBlue)
                ) {
                    Text("Restaurer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Confirmation Dialog for Deleting a History Entry
    historyEntryToDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { historyEntryToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Supprimer cette commande ?") },
            text = {
                Text("Cette commande archivée (${entry.totalBoxes} boîte(s)) sera retirée de votre historique.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        historyRepo.deleteOrder(entry.id)
                        Toast.makeText(context, "Commande supprimée de l'historique", Toast.LENGTH_SHORT).show()
                        historyEntryToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Supprimer")
                }
            },
            dismissButton = {
                TextButton(onClick = { historyEntryToDelete = null }) {
                    Text("Annuler")
                }
            }
        )
    }
}

@Composable
private fun StomaSegmentedTabSwitch(
    selectedTab: Int,
    orderCount: Int,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = SoftBlueBg,
        border = BorderStroke(1.dp, Color(0xFFD5E1EA))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SegmentedTabItem(
                label = "Catalogue & Panier",
                icon = Icons.Default.ShoppingCart,
                selected = selectedTab == 0,
                onClick = { onSelectTab(0) },
                modifier = Modifier.weight(1f)
            )
            SegmentedTabItem(
                label = "Historique ($orderCount)",
                icon = Icons.Default.History,
                selected = selectedTab == 1,
                onClick = { onSelectTab(1) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SegmentedTabItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) ColoplastBlue else Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color.White else ColoplastBlue,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (selected) Color.White else ColoplastBlue,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PatientInfoCard(
    patientName: String,
    notes: String,
    onPatientNameChange: (String) -> Unit,
    onNotesChange: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SoftBlueBg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(ColoplastBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = "Informations Patient & Prescription",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = ColoplastBlue
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = patientName,
                onValueChange = onPatientNameChange,
                label = { Text("Nom et Prénom du patient") },
                placeholder = { Text("Ex: ANGELIQUE DUPONT") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TurquoiseTeal,
                    focusedLabelColor = TurquoiseTeal
                )
            )

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = notes,
                onValueChange = onNotesChange,
                label = { Text("Consignes particulières / Pharmacie (facultatif)") },
                placeholder = { Text("Ex: Commande mensuelle habituelle") },
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TurquoiseTeal,
                    focusedLabelColor = TurquoiseTeal
                )
            )
        }
    }
}

@Composable
private fun StomaConsumptionEstimatorCard(
    estimate: StomaConsumptionEstimate?
) {
    var expanded by remember { mutableStateOf(true) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, TurquoiseTeal.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(EmeraldGreen.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = null,
                            tint = EmeraldGreen,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Calculateur de besoins (30 jours)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = ColoplastBlue
                        )
                        Text(
                            text = if (estimate != null) {
                                "Basé sur ${estimate.eventCount} soins sur ${estimate.observedDays} j"
                            } else {
                                "Repère mensuel standard"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldGreen
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Réduire" else "Déplier",
                    tint = ColoplastBlue
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    if (estimate != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            EstimatorMetricTile(
                                label = "Poches / jour",
                                value = String.format(Locale.FRANCE, "%.1f / j", estimate.pouchesPerDay),
                                subValue = "~${estimate.recommendedPouches30d} poches / 30 j",
                                accentColor = TurquoiseTeal,
                                modifier = Modifier.weight(1f)
                            )
                            EstimatorMetricTile(
                                label = "Tenue support",
                                value = if (estimate.waferWearDays != null) {
                                    String.format(Locale.FRANCE, "%.1f jours", estimate.waferWearDays)
                                } else {
                                    "${estimate.supportCount} posé(s)"
                                },
                                subValue = "~${estimate.recommendedSupports30d} supports / 30 j",
                                accentColor = EmeraldGreen,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = SoftTealBg,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Enregistrez au moins 2 changements dans l'onglet Stomie pour estimer votre consommation réelle.\nRepère indicatif : ~1 à 2 poches/jour (30 à 45/mois) et 1 support tous les 2 à 3 jours (10 à 15/mois).",
                                style = MaterialTheme.typography.bodySmall,
                                color = ColoplastBlue,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EstimatorMetricTile(
    label: String,
    value: String,
    subValue: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = accentColor.copy(alpha = 0.08f)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = ColoplastBlue
            )
            Text(
                text = subValue,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = accentColor
            )
        }
    }
}

@Composable
private fun QuickOrderPresetsBar(
    hasCustomPreset: Boolean,
    hasSelection: Boolean,
    onApplyMonthly: () -> Unit,
    onApplyTwoMonths: () -> Unit,
    onSaveAsPreset: () -> Unit,
    onClear: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Raccourcis de commande (1 clic)",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = ColoplastBlue
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                PresetActionChip(
                    label = if (hasCustomPreset) "Mensuel type ★" else "Mensuel type",
                    icon = Icons.Default.DateRange,
                    containerColor = ColoplastBlue,
                    contentColor = Color.White,
                    onClick = onApplyMonthly
                )
            }
            item {
                PresetActionChip(
                    label = "2 mois (×2)",
                    icon = Icons.Default.Add,
                    containerColor = TurquoiseTeal,
                    contentColor = Color.White,
                    onClick = onApplyTwoMonths
                )
            }
            item {
                PresetActionChip(
                    label = "Sauvegarder comme type",
                    icon = Icons.Default.Save,
                    containerColor = if (hasSelection) EmeraldGreen.copy(alpha = 0.12f) else Color(0xFFF2F4F6),
                    contentColor = if (hasSelection) EmeraldGreen else Color.Gray,
                    borderColor = if (hasSelection) EmeraldGreen.copy(alpha = 0.45f) else Color(0xFFE0E0E0),
                    onClick = onSaveAsPreset
                )
            }
            item {
                PresetActionChip(
                    label = "Vider",
                    icon = Icons.Default.Clear,
                    containerColor = if (hasSelection) Color(0xFFFFEBEE) else Color(0xFFF2F4F6),
                    contentColor = if (hasSelection) Color(0xFFC62828) else Color.Gray,
                    borderColor = if (hasSelection) Color(0xFFEF9A9A) else Color(0xFFE0E0E0),
                    onClick = onClear
                )
            }
        }
    }
}

@Composable
private fun PresetActionChip(
    label: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    borderColor: Color? = null,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = borderColor?.let { BorderStroke(1.dp, it) },
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = contentColor
            )
        }
    }
}

@Composable
private fun OrderBottomActionBar(
    selectedItems: List<StomaOrderReportGenerator.OrderItem>,
    totalSelectedItems: Int,
    totalUnits: Int,
    isCartExpanded: Boolean,
    isBusy: Boolean,
    onToggleCartExpanded: () -> Unit,
    onRemoveItem: (String) -> Unit,
    onGeneratePdf: () -> Unit,
    onCopyText: () -> Unit,
    onSendEmail: () -> Unit
) {
    Surface(
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (totalSelectedItems > 0) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SoftBlueBg,
                    border = BorderStroke(1.dp, TurquoiseTeal.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleCartExpanded() },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ShoppingCart,
                                    contentDescription = null,
                                    tint = ColoplastBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Panier : $totalSelectedItems réf. • $totalUnits boîte${if (totalUnits > 1) "s" else ""}",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = ColoplastBlue
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isCartExpanded) "Masquer" else "Détails",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TurquoiseTeal
                                )
                                Icon(
                                    imageVector = if (isCartExpanded) {
                                        Icons.Default.KeyboardArrowDown
                                    } else {
                                        Icons.Default.KeyboardArrowUp
                                    },
                                    contentDescription = null,
                                    tint = TurquoiseTeal,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        AnimatedVisibility(visible = isCartExpanded) {
                            Column(
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .heightIn(max = 130.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                HorizontalDivider(color = Color(0xFFD6E3EC))
                                selectedItems.forEach { item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${item.quantity}× ${item.product.name}",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                            color = ColoplastBlue,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { onRemoveItem(item.product.id) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "Retirer",
                                                tint = Color.Gray,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onGeneratePdf,
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (totalSelectedItems > 0) {
                        "Générer & Partager PDF ($totalUnits boîte${if (totalUnits > 1) "s" else ""})"
                    } else {
                        "Générer & Partager PDF"
                    },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCopyText,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    border = BorderStroke(1.dp, TurquoiseTeal)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = TurquoiseTeal,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Copier texte",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = ColoplastBlue,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = onSendEmail,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    border = BorderStroke(1.dp, ColoplastBlue)
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        tint = ColoplastBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "E-mail",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = ColoplastBlue,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun StomaProductCard(
    product: StomaProduct,
    quantity: Int,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onEditExactQty: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val borderColor = if (isSelected) TurquoiseTeal else MaterialTheme.colorScheme.outlineVariant
    val bgColor = if (isSelected) SoftTealBg else MaterialTheme.colorScheme.surface
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable { onToggleSelect() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Checkmark Circle
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) TurquoiseTeal else Color.Transparent)
                        .border(
                            1.5.dp,
                            if (isSelected) TurquoiseTeal else Color(0xFFB0C4D0),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Category Avatar Pill
                val avatarColor = when {
                    product.category.startsWith("Mio") -> ColoplastBlue
                    product.category.startsWith("Brava® Support") -> Color(0xFF0077A8)
                    product.category.startsWith("Brava®") -> TurquoiseTeal
                    product.category.startsWith("Compresses") -> EmeraldGreen
                    else -> Color(0xFF546E7A)
                }
                val avatarLabel = when {
                    product.category.startsWith("Mio") -> "Click"
                    product.category.startsWith("Brava® Support") -> "Supp"
                    product.category.startsWith("Brava®") -> "Brava"
                    product.category.startsWith("Compresses") -> "Cmp"
                    else -> "Autre"
                }

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(avatarColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = avatarLabel,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }

                // Product Details
                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = avatarColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = product.category,
                            color = avatarColor,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (product.detail.isNotBlank()) {
                        Text(
                            text = product.detail,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = TurquoiseTeal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "Réf. ${product.ref} • GTIN ${product.gtin}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Overflow Menu for Edit / Delete
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options de l'article",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Modifier l'article") },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = ColoplastBlue)
                            },
                            onClick = {
                                showMenu = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Supprimer l'article", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            // Stepper (- / clickable quantity pill / +) when selected
            AnimatedVisibility(visible = isSelected) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(Color.White, RoundedCornerShape(10.dp))
                            .border(1.dp, TurquoiseTeal, RoundedCornerShape(10.dp))
                            .padding(horizontal = 2.dp, vertical = 2.dp)
                    ) {
                        IconButton(
                            onClick = onDecrement,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Moins",
                                tint = ColoplastBlue,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SoftBlueBg,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onEditExactQty() }
                        ) {
                            Text(
                                text = "$quantity boîte${if (quantity > 1) "s" else ""}",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = ColoplastBlue,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        IconButton(
                            onClick = onIncrement,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Plus",
                                tint = ColoplastBlue,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StomaOrderHistoryTab(
    orders: List<StomaOrderHistoryEntry>,
    isBusy: Boolean,
    onReorder: (StomaOrderHistoryEntry) -> Unit,
    onSharePdf: (StomaOrderHistoryEntry) -> Unit,
    onCopyText: (StomaOrderHistoryEntry) -> Unit,
    onDelete: (StomaOrderHistoryEntry) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy 'à' HH:mm", Locale.FRANCE) }

    if (orders.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SoftBlueBg),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = TurquoiseTeal,
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = "Aucune commande enregistrée",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = ColoplastBlue
                    )
                    Text(
                        text = "Vos bons de commande PDF, e-mails et messages copiés apparaîtront ici pour être recommandés en 1 clic.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(2.dp)) }
        items(orders, key = { it.id }) { entry ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFDCE6EE)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = dateFormat.format(Date(entry.timestamp)),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = ColoplastBlue
                            )
                            Text(
                                text = entry.patientName.ifBlank { "Patient non renseigné" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = EmeraldGreen.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "${entry.totalBoxes} boîte${if (entry.totalBoxes > 1) "s" else ""}",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = EmeraldGreen,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (!entry.notes.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SoftBlueBg,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Note : ${entry.notes}",
                                style = MaterialTheme.typography.bodySmall,
                                color = ColoplastBlue,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFFEDF2F7))

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        entry.items.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${item.quantity}× ${item.name}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Réf. ${item.ref}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = TurquoiseTeal
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFFEDF2F7))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { onReorder(entry) },
                            enabled = !isBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = ColoplastBlue),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1.3f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Recommander",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }

                        OutlinedButton(
                            onClick = { onSharePdf(entry) },
                            enabled = !isBusy,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, EmeraldGreen),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.weight(0.9f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = EmeraldGreen,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PDF", fontSize = 12.sp, color = EmeraldGreen, fontWeight = FontWeight.SemiBold)
                        }

                        IconButton(
                            onClick = { onCopyText(entry) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copier",
                                tint = TurquoiseTeal,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = { onDelete(entry) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Supprimer",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun ExactQuantityDialog(
    productName: String,
    initialQty: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var qtyText by remember { mutableStateOf(initialQty.coerceAtLeast(1).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Quantité exacte",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = productName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ColoplastBlue
                )
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { input ->
                        qtyText = input.filter { it.isDigit() }.take(3)
                    },
                    label = { Text("Nombre de boîtes (0 pour retirer)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TurquoiseTeal,
                        focusedLabelColor = TurquoiseTeal
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = qtyText.toIntOrNull() ?: 0
                    onConfirm(parsed.coerceIn(0, 99))
                },
                colors = ButtonDefaults.buttonColors(containerColor = ColoplastBlue)
            ) {
                Text("Valider")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}

@Composable
private fun StomaProductEditDialog(
    initialProduct: StomaProduct?,
    existingCategories: List<String>,
    onDismiss: () -> Unit,
    onSave: (StomaProduct) -> Unit
) {
    var name by remember { mutableStateOf(initialProduct?.name ?: "") }
    var category by remember { mutableStateOf(initialProduct?.category ?: "Mio Click - Fermée 2 pièces") }
    var detail by remember { mutableStateOf(initialProduct?.detail ?: "") }
    var ref by remember { mutableStateOf(initialProduct?.ref ?: "") }
    var gtin by remember { mutableStateOf(initialProduct?.gtin ?: "") }

    val defaultCategorySuggestions = listOf(
        "Mio Click - Fermée 2 pièces",
        "Mio Click - Vidable 2 pièces",
        "Brava® Support Plus",
        "Brava®",
        "Compresses",
        "Soins & Pansements",
        "Autre"
    )
    val allCategorySuggestions = (existingCategories + defaultCategorySuggestions).distinct()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialProduct == null) "Ajouter un matériel" else "Modifier le matériel",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Désignation / Nom *") },
                    placeholder = { Text("Ex: Poche fermée maxi 60mm") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Catégorie *") },
                    placeholder = { Text("Ex: Mio Click, Brava, Soins…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(allCategorySuggestions) { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat, fontSize = 11.sp) },
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = detail,
                    onValueChange = { detail = it },
                    label = { Text("Détails / Format (facultatif)") },
                    placeholder = { Text("Ex: Anneau 60mm / Boîte de 30") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = ref,
                    onValueChange = { ref = it },
                    label = { Text("Référence fabricant (facultatif)") },
                    placeholder = { Text("Ex: 114229") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = gtin,
                    onValueChange = { gtin = it },
                    label = { Text("Code GTIN / CIP / ACL (facultatif)") },
                    placeholder = { Text("Ex: 5701780170109") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalRef = ref.trim().ifEmpty { "---" }
                    val finalGtin = gtin.trim().ifEmpty { "---" }
                    val finalCategory = category.trim().ifEmpty { "Autre" }
                    val newProduct = initialProduct?.copy(
                        name = name.trim(),
                        category = finalCategory,
                        detail = detail.trim(),
                        ref = finalRef,
                        gtin = finalGtin
                    ) ?: StomaProduct(
                        name = name.trim(),
                        category = finalCategory,
                        detail = detail.trim(),
                        ref = finalRef,
                        gtin = finalGtin
                    )
                    onSave(newProduct)
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = ColoplastBlue)
            ) {
                Text(if (initialProduct == null) "Ajouter" else "Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}
