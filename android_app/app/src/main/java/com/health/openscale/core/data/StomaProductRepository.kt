/*
 * openScale
 * Copyright (C) 2026 openScale contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.health.openscale.core.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StomaProductRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val file: File
        get() = File(context.filesDir, "stoma_catalog.json")

    private val defaultProductsById: Map<String, StomaProduct> by lazy {
        StomaCatalog.DEFAULT_PRODUCTS.associateBy { it.id }
    }

    private val _products = MutableStateFlow<List<StomaProduct>>(emptyList())
    val products: StateFlow<List<StomaProduct>> = _products.asStateFlow()

    init {
        loadProducts()
    }

    @Synchronized
    fun loadProducts(): List<StomaProduct> {
        val loaded = try {
            if (file.exists()) {
                val jsonStr = file.readText(Charsets.UTF_8)
                val jsonArray = JSONArray(jsonStr)
                val list = mutableListOf<StomaProduct>()
                var needsMigration = false
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val id = if (obj.isNull("id")) {
                        UUID.randomUUID().toString()
                    } else {
                        obj.optString("id", "").ifBlank { UUID.randomUUID().toString() }
                    }
                    val hasPresetField = obj.has("monthlyPresetQty")
                    val presetQty = if (hasPresetField) {
                        obj.optInt("monthlyPresetQty", 0)
                    } else {
                        needsMigration = true
                        defaultProductsById[id]?.monthlyPresetQty ?: 0
                    }
                    val rawProduct = StomaProduct(
                        id = id,
                        ref = obj.optString("ref", ""),
                        name = obj.optString("name", ""),
                        detail = obj.optString("detail", ""),
                        gtin = obj.optString("gtin", ""),
                        category = obj.optString("category", "Autre"),
                        defaultQty = obj.optInt("defaultQty", 1),
                        monthlyPresetQty = presetQty
                    )
                    val normalized = StomaCatalog.normalizeProduct(rawProduct)
                    if (normalized != rawProduct) {
                        needsMigration = true
                    }
                    list.add(normalized)
                }
                if (list.isNotEmpty()) {
                    if (needsMigration) {
                        saveInternal(list)
                    }
                    list
                } else {
                    StomaCatalog.DEFAULT_PRODUCTS
                }
            } else {
                saveInternal(StomaCatalog.DEFAULT_PRODUCTS)
                StomaCatalog.DEFAULT_PRODUCTS
            }
        } catch (e: Exception) {
            StomaCatalog.DEFAULT_PRODUCTS
        }
        _products.value = loaded
        return loaded
    }

    @Synchronized
    fun saveProducts(list: List<StomaProduct>) {
        val normalized = list.map { StomaCatalog.normalizeProduct(it) }
        saveInternal(normalized)
        _products.value = normalized
    }

    private fun saveInternal(list: List<StomaProduct>) {
        try {
            val jsonArray = JSONArray()
            for (p in list) {
                val obj = JSONObject().apply {
                    put("id", p.id)
                    put("ref", p.ref)
                    put("name", p.name)
                    put("detail", p.detail)
                    put("gtin", p.gtin)
                    put("category", p.category)
                    put("defaultQty", p.defaultQty)
                    put("monthlyPresetQty", p.monthlyPresetQty)
                }
                jsonArray.put(obj)
            }
            val tempFile = File(context.filesDir, "stoma_catalog.json.tmp")
            writeTextAtomically(file, tempFile, jsonArray.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addProduct(product: StomaProduct) {
        val current = _products.value.toMutableList()
        current.add(StomaCatalog.normalizeProduct(product))
        saveProducts(current)
    }

    fun updateProduct(product: StomaProduct) {
        val normalized = StomaCatalog.normalizeProduct(product)
        val current = _products.value.toMutableList()
        val index = current.indexOfFirst { it.id == normalized.id }
        if (index >= 0) {
            current[index] = normalized
        } else {
            current.add(normalized)
        }
        saveProducts(current)
    }

    fun deleteProduct(productId: String) {
        val current = _products.value.filterNot { it.id == productId }
        saveProducts(current)
    }

    fun resetToDefaults() {
        saveProducts(StomaCatalog.DEFAULT_PRODUCTS)
    }

    fun saveCustomPreset(quantities: Map<String, Int>) {
        StomaCustomPresetStore.save(context.filesDir, quantities)
    }

    fun loadCustomPreset(): Map<String, Int> =
        StomaCustomPresetStore.load(context.filesDir)

    fun hasCustomPreset(): Boolean =
        StomaCustomPresetStore.hasPreset(context.filesDir)

    fun getEffectiveMonthlyPreset(): Map<String, Int> =
        loadCustomPreset().ifEmpty { StomaCatalog.getDefaultMonthlyPreset() }
}
