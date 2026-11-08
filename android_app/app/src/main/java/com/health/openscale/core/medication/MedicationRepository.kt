/*
 * openScale
 * Copyright (C) 2026 openScale contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.health.openscale.core.medication

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
class MedicationRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val file: File
        get() = File(context.filesDir, "medications_intake.json")

    private val _intakes = MutableStateFlow<List<MedicationIntake>>(emptyList())
    val intakes: StateFlow<List<MedicationIntake>> = _intakes.asStateFlow()

    init {
        loadIntakes()
    }

    @Synchronized
    fun loadIntakes(): List<MedicationIntake> {
        val list = mutableListOf<MedicationIntake>()
        try {
            if (file.exists()) {
                val jsonStr = file.readText()
                val jsonArray = JSONArray(jsonStr)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        MedicationIntake(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                            name = obj.optString("name", ""),
                            dosage = obj.optString("dosage", ""),
                            category = obj.optString("category", "Antalgique"),
                            initialPainScore = if (obj.has("initialPainScore") && !obj.isNull("initialPainScore")) obj.getInt("initialPainScore") else null,
                            postPainScore = if (obj.has("postPainScore") && !obj.isNull("postPainScore")) obj.getInt("postPainScore") else null,
                            evaluationTimestamp = if (obj.has("evaluationTimestamp") && !obj.isNull("evaluationTimestamp")) obj.getLong("evaluationTimestamp") else null,
                            reliefPercent = if (obj.has("reliefPercent") && !obj.isNull("reliefPercent")) obj.getInt("reliefPercent") else null,
                            notes = if (obj.has("notes") && !obj.isNull("notes")) obj.getString("notes") else null
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val sorted = list.sortedByDescending { it.timestamp }
        _intakes.value = sorted
        return sorted
    }

    @Synchronized
    private fun saveIntakes(list: List<MedicationIntake>) {
        try {
            val jsonArray = JSONArray()
            for (item in list) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("timestamp", item.timestamp)
                    put("name", item.name)
                    put("dosage", item.dosage)
                    put("category", item.category)
                    if (item.initialPainScore != null) put("initialPainScore", item.initialPainScore)
                    if (item.postPainScore != null) put("postPainScore", item.postPainScore)
                    if (item.evaluationTimestamp != null) put("evaluationTimestamp", item.evaluationTimestamp)
                    if (item.reliefPercent != null) put("reliefPercent", item.reliefPercent)
                    if (item.notes != null) put("notes", item.notes)
                }
                jsonArray.put(obj)
            }
            val tmp = File(context.filesDir, "medications_intake.json.tmp")
            tmp.writeText(jsonArray.toString(2))
            if (tmp.exists()) {
                tmp.renameTo(file)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _intakes.value = list.sortedByDescending { it.timestamp }
    }

    fun logIntake(
        name: String,
        dosage: String = "",
        category: String = "Antalgique",
        initialPainScore: Int? = null,
        notes: String? = null
    ): MedicationIntake {
        val intake = MedicationIntake(
            name = name,
            dosage = dosage,
            category = category,
            initialPainScore = initialPainScore,
            notes = notes
        )
        val current = _intakes.value.toMutableList()
        current.add(0, intake)
        saveIntakes(current)
        return intake
    }

    fun evaluateIntake(id: String, postPainScore: Int, notes: String? = null) {
        val current = _intakes.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index >= 0) {
            val old = current[index]
            val initial = old.initialPainScore
            val relief = if (initial != null && initial > 0) {
                val delta = (initial - postPainScore).coerceAtLeast(0)
                ((delta.toDouble() / initial) * 100).toInt().coerceIn(0, 100)
            } else if (postPainScore == 0) {
                100
            } else {
                null
            }

            val updated = old.copy(
                postPainScore = postPainScore,
                evaluationTimestamp = System.currentTimeMillis(),
                reliefPercent = relief,
                notes = notes ?: old.notes
            )
            current[index] = updated
            saveIntakes(current)
        }
    }

    fun deleteIntake(id: String) {
        val current = _intakes.value.filterNot { it.id == id }
        saveIntakes(current)
    }

    fun getPendingEvaluations(): List<MedicationIntake> {
        val now = System.currentTimeMillis()
        val sixHoursAgo = now - 6 * 3600 * 1000L
        return _intakes.value.filter {
            it.postPainScore == null && it.timestamp >= sixHoursAgo && it.initialPainScore != null
        }
    }

    fun computeStats(sinceTimestamp: Long? = null): List<MedicationStats> {
        val filtered = if (sinceTimestamp != null) {
            _intakes.value.filter { it.timestamp >= sinceTimestamp }
        } else {
            _intakes.value
        }

        val grouped = filtered.groupBy { it.name.trim().lowercase() }
        val result = mutableListOf<MedicationStats>()

        for ((_, group) in grouped) {
            val displayName = group.first().name
            val total = group.size
            val evaluated = group.filter { it.postPainScore != null && it.initialPainScore != null }

            val avgInitial = if (evaluated.isNotEmpty()) {
                evaluated.mapNotNull { it.initialPainScore }.average().toFloat()
            } else 0f

            val avgPost = if (evaluated.isNotEmpty()) {
                evaluated.mapNotNull { it.postPainScore }.average().toFloat()
            } else 0f

            val avgRelief = if (evaluated.isNotEmpty()) {
                evaluated.mapNotNull { it.reliefPercent }.average().toInt()
            } else 0

            val failures = evaluated.count {
                val init = it.initialPainScore ?: 0
                val post = it.postPainScore ?: init
                (init - post) < 1 || (it.reliefPercent ?: 0) < 20
            }
            val failureRate = if (evaluated.isNotEmpty()) ((failures * 100) / evaluated.size) else 0
            val drop = (avgInitial - avgPost).coerceAtLeast(0f)

            result.add(
                MedicationStats(
                    name = displayName,
                    totalIntakes = total,
                    evaluatedIntakes = evaluated.size,
                    avgInitialPain = avgInitial,
                    avgPostPain = avgPost,
                    avgReliefPercent = avgRelief,
                    avgDrop = drop,
                    failureCount = failures,
                    failurePercent = failureRate
                )
            )
        }

        return result.sortedByDescending { it.totalIntakes }
    }
}
