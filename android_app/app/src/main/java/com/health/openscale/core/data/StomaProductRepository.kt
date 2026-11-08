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

    private val _products = MutableStateFlow<List<StomaProduct>>(emptyList())
    val products: StateFlow<List<StomaProduct>> = _products.asStateFlow()

    init {
        loadProducts()
    }

    @Synchronized
    fun loadProducts(): List<StomaProduct> {
        val loaded = try {
            if (file.exists()) {
                val jsonStr = file.readText()
                val jsonArray = JSONArray(jsonStr)
                val list = mutableListOf<StomaProduct>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        StomaProduct(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            ref = obj.optString("ref", ""),
                            name = obj.optString("name", ""),
                            detail = obj.optString("detail", ""),
                            gtin = obj.optString("gtin", ""),
                            category = obj.optString("category", "Autre"),
                            defaultQty = obj.optInt("defaultQty", 1)
                        )
                    )
                }
                if (list.isNotEmpty()) list else StomaCatalog.DEFAULT_PRODUCTS
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
        saveInternal(list)
        _products.value = list
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
                }
                jsonArray.put(obj)
            }
            val tempFile = File(context.filesDir, "stoma_catalog.json.tmp")
            tempFile.writeText(jsonArray.toString(2))
            if (tempFile.exists()) {
                tempFile.renameTo(file)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addProduct(product: StomaProduct) {
        val current = _products.value.toMutableList()
        current.add(product)
        saveProducts(current)
    }

    fun updateProduct(product: StomaProduct) {
        val current = _products.value.toMutableList()
        val index = current.indexOfFirst { it.id == product.id }
        if (index >= 0) {
            current[index] = product
        } else {
            current.add(product)
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
}
