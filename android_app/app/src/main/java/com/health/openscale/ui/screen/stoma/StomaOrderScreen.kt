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

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.health.openscale.core.data.StomaProduct
import com.health.openscale.core.data.StomaProductRepository
import com.health.openscale.core.service.StomaOrderReportGenerator
import com.health.openscale.ui.shared.SharedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val allProducts by repository.products.collectAsState()

    var patientName by remember(activeUser) {
        mutableStateOf(activeUser?.name ?: "")
    }
    var notes by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    // Map of product id -> quantity
    val quantities = remember { mutableStateMapOf<String, Int>() }
    var isGeneratingPdf by remember { mutableStateOf(false) }

    // Dialog states for Add / Edit / Delete / Reset
    var showEditDialog by remember { mutableStateOf(false) }
    var productToEdit by remember { mutableStateOf<StomaProduct?>(null) }
    var productToDelete by remember { mutableStateOf<StomaProduct?>(null) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showTopMenu by remember { mutableStateOf(false) }

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

    val totalSelectedItems = quantities.values.count { it > 0 }
    val totalUnits = quantities.values.sum()

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
                            text = "SenSura® Mio Click & Brava®",
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
                            color = Color(0xFF009DA8),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text(
                                text = "$totalSelectedItems produit(s)",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Button to Add New Item
                    IconButton(onClick = {
                        productToEdit = null
                        showEditDialog = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Ajouter un matériel",
                            tint = Color(0xFF00365B)
                        )
                    }

                    // Overflow Menu
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
                                    Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF009DA8))
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
                                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF00365B))
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
            Surface(
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Button(
                        onClick = {
                            if (totalSelectedItems == 0) {
                                Toast.makeText(context, "Sélectionnez au moins un article", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isGeneratingPdf = true
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val orderItems = allProducts
                                        .filter { (quantities[it.id] ?: 0) > 0 }
                                        .map { StomaOrderReportGenerator.OrderItem(it, quantities[it.id] ?: 1) }

                                    val generator = StomaOrderReportGenerator(context)
                                    val file = generator.generateOrderPdf(
                                        patientName = patientName,
                                        items = orderItems,
                                        notes = notes.takeIf { it.isNotBlank() }
                                    )
                                    withContext(Dispatchers.Main) {
                                        isGeneratingPdf = false
                                        generator.shareOrder(file)
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        isGeneratingPdf = false
                                        Toast.makeText(context, "Erreur génération: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        enabled = !isGeneratingPdf,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1A8B55)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (totalSelectedItems > 0) {
                                "Générer le Bon de Commande ($totalSelectedItems art. • $totalUnits boîtes)"
                            } else {
                                "Sélectionnez vos articles pour commander"
                            },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Patient Header Card
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF0F4F8)
                    ),
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
                                    .background(Color(0xFF00365B)),
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
                                color = Color(0xFF00365B)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = patientName,
                            onValueChange = { patientName = it },
                            label = { Text("Nom et Prénom du patient") },
                            placeholder = { Text("Ex: ANGELIQUE DUPONT") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF009DA8),
                                focusedLabelColor = Color(0xFF009DA8)
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Consignes particulières / Pharmacie (facultatif)") },
                            placeholder = { Text("Ex: Commande mensuelle habituelle") },
                            maxLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }

            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Rechercher réf, nom, anneau 60mm…") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Color(0xFF009DA8))
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
                        focusedBorderColor = Color(0xFF009DA8),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }

            // Category Filter Row
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { cat ->
                        val isSelected = (selectedCategory == cat) || (selectedCategory == null && cat == "Tous")
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedCategory = if (cat == "Tous") null else cat
                            },
                            label = { Text(cat, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF00365B),
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
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color(0xFF009DA8))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Ajouter un nouveau produit à la liste",
                        color = Color(0xFF00365B),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
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
                    tint = Color(0xFF00365B)
                )
            },
            title = {
                Text(text = "Restaurer le catalogue d'origine ?")
            },
            text = {
                Text(
                    text = "Cette action rétablira la liste d'origine des 14 références de stomie Coloplast, Brava® et Mölnlycke.\nVos personnalisations actuelles seront remplacées."
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00365B))
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
}

@Composable
private fun StomaProductCard(
    product: StomaProduct,
    quantity: Int,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val borderColor = if (isSelected) Color(0xFF009DA8) else MaterialTheme.colorScheme.outlineVariant
    val bgColor = if (isSelected) Color(0xFFEAF7F8) else MaterialTheme.colorScheme.surface
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
                        .background(if (isSelected) Color(0xFF009DA8) else Color.Transparent)
                        .border(
                            1.5.dp,
                            if (isSelected) Color(0xFF009DA8) else Color(0xFFB0C4D0),
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
                    product.category.startsWith("Mio") -> Color(0xFF00365B)
                    product.category.startsWith("Brava® Support") -> Color(0xFF0077A8)
                    product.category.startsWith("Brava®") -> Color(0xFF009DA8)
                    product.category.startsWith("Compresses") -> Color(0xFF607D8B)
                    else -> Color(0xFF3F51B5)
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
                            color = Color(0xFF009DA8),
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
                                Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF00365B))
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

            // Stepper (- / +) when selected
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
                            .border(1.dp, Color(0xFF009DA8), RoundedCornerShape(10.dp))
                            .padding(horizontal = 2.dp, vertical = 2.dp)
                    ) {
                        IconButton(
                            onClick = onDecrement,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Moins",
                                tint = Color(0xFF00365B),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "$quantity boîte(s)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF00365B),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        IconButton(
                            onClick = onIncrement,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Plus",
                                tint = Color(0xFF00365B),
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
private fun StomaProductEditDialog(
    initialProduct: StomaProduct?,
    existingCategories: List<String>,
    onDismiss: () -> Unit,
    onSave: (StomaProduct) -> Unit
) {
    var name by remember { mutableStateOf(initialProduct?.name ?: "") }
    var category by remember { mutableStateOf(initialProduct?.category ?: "Mio Click - Fermée 2 pieces") }
    var detail by remember { mutableStateOf(initialProduct?.detail ?: "") }
    var ref by remember { mutableStateOf(initialProduct?.ref ?: "") }
    var gtin by remember { mutableStateOf(initialProduct?.gtin ?: "") }

    val defaultCategorySuggestions = listOf(
        "Mio Click - Fermée 2 pieces",
        "Mio Click - Vidable 2 pieces",
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
                // Name (required)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Désignation / Nom *") },
                    placeholder = { Text("Ex: Poche fermée maxi 60mm") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                // Category
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Catégorie *") },
                    placeholder = { Text("Ex: Mio Click, Brava, Soins…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                // Category quick pills
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

                // Detail
                OutlinedTextField(
                    value = detail,
                    onValueChange = { detail = it },
                    label = { Text("Détails / Format (facultatif)") },
                    placeholder = { Text("Ex: Anneau 60mm / Boîte de 30") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                // Reference
                OutlinedTextField(
                    value = ref,
                    onValueChange = { ref = it },
                    label = { Text("Référence fabricant (facultatif)") },
                    placeholder = { Text("Ex: 114229") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                // GTIN / CIP
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
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00365B))
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
