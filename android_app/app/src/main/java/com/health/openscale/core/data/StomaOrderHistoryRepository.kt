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

data class StomaOrderItemRecord(
    val productId: String,
    val ref: String,
    val name: String,
    val detail: String,
    val gtin: String,
    val quantity: Int
)

data class StomaOrderHistoryEntry(
    val id: String,
    val timestamp: Long,
    val patientName: String,
    val notes: String?,
    val items: List<StomaOrderItemRecord>,
    val totalBoxes: Int
)

internal object StomaCustomPresetStore {
    private const val FILE_NAME = "stoma_custom_preset.json"
    private const val TEMP_FILE_NAME = "stoma_custom_preset.json.tmp"

    @Synchronized
    fun save(filesDir: File, quantities: Map<String, Int>): Map<String, Int> {
        val cleaned = quantities.filterValues { it > 0 }
        val file = File(filesDir, FILE_NAME)
        if (cleaned.isEmpty()) {
            if (file.exists()) {
                file.delete()
            }
            return emptyMap()
        }
        try {
            val jsonObj = JSONObject()
            for ((productId, qty) in cleaned) {
                jsonObj.put(productId, qty)
            }
            writeTextAtomically(file, File(filesDir, TEMP_FILE_NAME), jsonObj.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return cleaned
    }

    @Synchronized
    fun load(filesDir: File): Map<String, Int> {
        val file = File(filesDir, FILE_NAME)
        if (!file.exists()) return emptyMap()
        return try {
            val jsonObj = JSONObject(file.readText(Charsets.UTF_8))
            val result = linkedMapOf<String, Int>()
            val keys = jsonObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val qty = jsonObj.optInt(key, 0)
                if (qty > 0) {
                    result[key] = qty
                }
            }
            result
        } catch (e: Exception) {
            emptyMap()
        }
    }

    @Synchronized
    fun hasPreset(filesDir: File): Boolean = load(filesDir).isNotEmpty()

    @Synchronized
    fun clear(filesDir: File) {
        val file = File(filesDir, FILE_NAME)
        if (file.exists()) {
            file.delete()
        }
    }
}

@Singleton
class StomaOrderHistoryRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val historyFile: File
        get() = File(context.filesDir, "stoma_order_history.json")

    private val _ordersFlow = MutableStateFlow<List<StomaOrderHistoryEntry>>(emptyList())
    val ordersFlow: StateFlow<List<StomaOrderHistoryEntry>> = _ordersFlow.asStateFlow()

    private val _customPresetFlow = MutableStateFlow<Map<String, Int>>(emptyMap())
    val customPresetFlow: StateFlow<Map<String, Int>> = _customPresetFlow.asStateFlow()

    init {
        loadOrders()
        _customPresetFlow.value = StomaCustomPresetStore.load(context.filesDir)
    }

    @Synchronized
    fun loadOrders(): List<StomaOrderHistoryEntry> {
        val loaded = try {
            if (!historyFile.exists()) {
                emptyList()
            } else {
                val jsonArray = JSONArray(historyFile.readText(Charsets.UTF_8))
                val entries = mutableListOf<StomaOrderHistoryEntry>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val itemsArray = obj.optJSONArray("items") ?: JSONArray()
                    val items = mutableListOf<StomaOrderItemRecord>()
                    for (j in 0 until itemsArray.length()) {
                        val itemObj = itemsArray.getJSONObject(j)
                        val qty = itemObj.optInt("quantity", 0)
                        if (qty > 0) {
                            items.add(
                                StomaOrderItemRecord(
                                    productId = itemObj.optString("productId", ""),
                                    ref = StomaCatalog.repairMojibake(itemObj.optString("ref", "")),
                                    name = StomaCatalog.repairMojibake(itemObj.optString("name", "")),
                                    detail = StomaCatalog.repairMojibake(itemObj.optString("detail", "")),
                                    gtin = StomaCatalog.repairMojibake(itemObj.optString("gtin", "")),
                                    quantity = qty
                                )
                            )
                        }
                    }
                    val notes = if (obj.isNull("notes")) {
                        null
                    } else {
                        obj.optString("notes", "").takeIf { it.isNotBlank() }?.let {
                            StomaCatalog.repairMojibake(it)
                        }
                    }
                    val totalBoxes = if (obj.has("totalBoxes")) {
                        obj.optInt("totalBoxes", items.sumOf { it.quantity })
                    } else {
                        items.sumOf { it.quantity }
                    }
                    entries.add(
                        StomaOrderHistoryEntry(
                            id = obj.optString("id", "").ifBlank { UUID.randomUUID().toString() },
                            timestamp = obj.optLong("timestamp", 0L),
                            patientName = StomaCatalog.repairMojibake(obj.optString("patientName", "")),
                            notes = notes,
                            items = items,
                            totalBoxes = totalBoxes
                        )
                    )
                }
                entries.sortedByDescending { it.timestamp }
            }
        } catch (e: Exception) {
            emptyList()
        }
        _ordersFlow.value = loaded
        return loaded
    }

    @Synchronized
    fun saveOrder(
        patientName: String,
        notes: String?,
        items: List<StomaOrderItemRecord>
    ): StomaOrderHistoryEntry {
        val validItems = items
            .filter { it.quantity > 0 }
            .map { item ->
                item.copy(
                    ref = StomaCatalog.repairMojibake(item.ref),
                    name = StomaCatalog.repairMojibake(item.name),
                    detail = StomaCatalog.repairMojibake(item.detail),
                    gtin = StomaCatalog.repairMojibake(item.gtin)
                )
            }
        val cleanNotes = notes?.trim()?.takeIf { it.isNotEmpty() }
        val entry = StomaOrderHistoryEntry(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            patientName = patientName.trim(),
            notes = cleanNotes,
            items = validItems,
            totalBoxes = validItems.sumOf { it.quantity }
        )
        val updated = (listOf(entry) + _ordersFlow.value).sortedByDescending { it.timestamp }
        saveOrdersInternal(updated)
        _ordersFlow.value = updated
        return entry
    }

    @Synchronized
    fun deleteOrder(orderId: String) {
        val updated = _ordersFlow.value.filterNot { it.id == orderId }
        saveOrdersInternal(updated)
        _ordersFlow.value = updated
    }

    @Synchronized
    fun clearHistory() {
        saveOrdersInternal(emptyList())
        _ordersFlow.value = emptyList()
    }

    @Synchronized
    fun saveCustomPreset(quantities: Map<String, Int>) {
        val saved = StomaCustomPresetStore.save(context.filesDir, quantities)
        _customPresetFlow.value = saved
    }

    @Synchronized
    fun getCustomPreset(): Map<String, Int> {
        val loaded = StomaCustomPresetStore.load(context.filesDir)
        _customPresetFlow.value = loaded
        return loaded
    }

    @Synchronized
    fun hasCustomPreset(): Boolean = getCustomPreset().isNotEmpty()

    @Synchronized
    fun clearCustomPreset() {
        StomaCustomPresetStore.clear(context.filesDir)
        _customPresetFlow.value = emptyMap()
    }

    private fun saveOrdersInternal(entries: List<StomaOrderHistoryEntry>) {
        try {
            val jsonArray = JSONArray()
            for (entry in entries) {
                val itemsArray = JSONArray()
                for (item in entry.items) {
                    val itemObj = JSONObject().apply {
                        put("productId", item.productId)
                        put("ref", item.ref)
                        put("name", item.name)
                        put("detail", item.detail)
                        put("gtin", item.gtin)
                        put("quantity", item.quantity)
                    }
                    itemsArray.put(itemObj)
                }
                val entryObj = JSONObject().apply {
                    put("id", entry.id)
                    put("timestamp", entry.timestamp)
                    put("patientName", entry.patientName)
                    if (entry.notes != null) {
                        put("notes", entry.notes)
                    } else {
                        put("notes", JSONObject.NULL)
                    }
                    put("items", itemsArray)
                    put("totalBoxes", entry.totalBoxes)
                }
                jsonArray.put(entryObj)
            }
            val tempFile = File(context.filesDir, "stoma_order_history.json.tmp")
            writeTextAtomically(historyFile, tempFile, jsonArray.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
