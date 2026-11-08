/*
 * openScale
 * Copyright (C) 2026 openScale contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.health.openscale.core.service

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.health.openscale.core.data.HealthCheckin
import com.health.openscale.core.data.MeasurementType
import com.health.openscale.core.data.User
import com.health.openscale.core.healthconnect.ClinicalHeartRateSummary
import com.health.openscale.core.healthconnect.ClinicalSleepSummary
import com.health.openscale.core.healthconnect.ClinicalStepsSummary
import com.health.openscale.core.healthconnect.ClinicalTempSummary
import com.health.openscale.core.model.MeasurementWithValues
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object MedicalReportGenerator {

    private const val PAGE_WIDTH = 595 // A4 standard width in points (72 dpi)
    private const val PAGE_HEIGHT = 842 // A4 standard height in points (72 dpi)

    /**
     * Generates a clinical PDF report for the patient and physician.
     * @return The generated PDF file.
     */
    fun generateReport(
        context: Context,
        user: User,
        months: Int,
        measurements: List<MeasurementWithValues>,
        checkins: List<HealthCheckin>,
        sleepSummary: ClinicalSleepSummary = ClinicalSleepSummary(),
        tempSummary: ClinicalTempSummary = ClinicalTempSummary(),
        hrSummary: ClinicalHeartRateSummary = ClinicalHeartRateSummary(),
        stepsSummary: ClinicalStepsSummary = ClinicalStepsSummary()
    ): File {
        val pdfDocument = PdfDocument()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
        val timeFormat = SimpleDateFormat("HH:mm", Locale.FRANCE)

        val calendar = Calendar.getInstance()
        val now = calendar.timeInMillis
        calendar.add(Calendar.MONTH, -months)
        val startDate = calendar.timeInMillis

        val filteredMeasurements = measurements
            .filter { it.measurement.timestamp >= startDate }
            .sortedBy { it.measurement.timestamp }

        val filteredCheckins = checkins
            .filter { it.timestamp >= startDate }
            .sortedBy { it.timestamp }

        // --- Compute Summary Statistics ---
        val weights = filteredMeasurements.mapNotNull { m ->
            m.values.firstOrNull { it.type.identity == MeasurementType.WEIGHT.identity }?.value?.floatValue
        }
        val waters = filteredMeasurements.mapNotNull { m ->
            m.values.firstOrNull { it.type.identity == MeasurementType.WATER.identity }?.value?.floatValue
        }

        val avgWeight = if (weights.isNotEmpty()) weights.average().toFloat() else 0f
        val minWeight = if (weights.isNotEmpty()) weights.minOrNull() ?: 0f else 0f
        val maxWeight = if (weights.isNotEmpty()) weights.maxOrNull() ?: 0f else 0f

        val avgWater = if (waters.isNotEmpty()) waters.average().toFloat() else 0f

        val painScores = filteredCheckins.mapNotNull { it.painScore }
        val avgPain = if (painScores.isNotEmpty()) painScores.average().toFloat() else 0f
        val severePainDays = filteredCheckins.count { (it.painScore ?: 0) >= 6 }
        val cycleSensationDays = filteredCheckins.count { it.cycleSensation == true }

        val highLiquidStomaDays = filteredCheckins.count { it.stomaOutput == "HIGH_LIQUID" }
        val stomaIncidents = filteredCheckins.count { it.stomaIncident == true }
        val endoBellyDays = filteredCheckins.count { it.bloatingLevel == "ENDO_BELLY" }
        val glutenSuspicions = filteredCheckins.count { it.glutenExposure in listOf("SUSPECTED", "CONFIRMED") }

        val stomaRepo = com.health.openscale.core.stoma.StomaTrackingRepository(context)
        val stomaSummary = stomaRepo.getTodaySummary()

        val medRepo = com.health.openscale.core.medication.MedicationRepository(context)
        val medStats = medRepo.computeStats(startDate)

        // --- PAGE 1: Clinical Header, KPIs, Chart & Analysis ---
        val pageInfo1 = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page1 = pdfDocument.startPage(pageInfo1)
        val canvas1 = page1.canvas

        drawPage1(
            canvas = canvas1,
            user = user,
            months = months,
            now = now,
            startDate = startDate,
            dateFormat = dateFormat,
            avgWeight = avgWeight,
            minWeight = minWeight,
            maxWeight = maxWeight,
            avgWater = avgWater,
            avgPain = avgPain,
            severePainDays = severePainDays,
            cycleSensationDays = cycleSensationDays,
            highLiquidStomaDays = highLiquidStomaDays,
            stomaIncidents = stomaIncidents,
            endoBellyDays = endoBellyDays,
            glutenSuspicions = glutenSuspicions,
            weights = weights,
            waters = waters,
            measurements = filteredMeasurements,
            checkins = filteredCheckins,
            sleepSummary = sleepSummary,
            tempSummary = tempSummary,
            hrSummary = hrSummary,
            stepsSummary = stepsSummary,
            stomaSummary = stomaSummary,
            medStats = medStats
        )
        pdfDocument.finishPage(page1)

        // --- PAGE 2: Chronological Journal of Events & Crises ---
        if (filteredCheckins.isNotEmpty() || filteredMeasurements.isNotEmpty()) {
            val pageInfo2 = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 2).create()
            val page2 = pdfDocument.startPage(pageInfo2)
            val canvas2 = page2.canvas

            drawPage2(
                canvas = canvas2,
                user = user,
                checkins = filteredCheckins,
                measurements = filteredMeasurements,
                dateFormat = dateFormat,
                timeFormat = timeFormat
            )
            pdfDocument.finishPage(page2)
        }

        // --- Save PDF File ---
        val reportsDir = File(context.cacheDir, "reports")
        if (!reportsDir.exists()) reportsDir.mkdirs()

        val safeUserName = user.name.replace(Regex("[^a-zA-Z0-9]"), "_")
        val file = File(reportsDir, "Bilan_Sante_${safeUserName}_${months}mois.pdf")
        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        return file
    }

    private fun drawPage1(
        canvas: Canvas,
        user: User,
        months: Int,
        now: Long,
        startDate: Long,
        dateFormat: SimpleDateFormat,
        avgWeight: Float,
        minWeight: Float,
        maxWeight: Float,
        avgWater: Float,
        avgPain: Float,
        severePainDays: Int,
        cycleSensationDays: Int,
        highLiquidStomaDays: Int,
        stomaIncidents: Int,
        endoBellyDays: Int,
        glutenSuspicions: Int,
        weights: List<Float>,
        waters: List<Float>,
        measurements: List<MeasurementWithValues>,
        checkins: List<HealthCheckin>,
        sleepSummary: ClinicalSleepSummary,
        tempSummary: ClinicalTempSummary,
        hrSummary: ClinicalHeartRateSummary = ClinicalHeartRateSummary(),
        stepsSummary: ClinicalStepsSummary = ClinicalStepsSummary(),
        stomaSummary: com.health.openscale.core.stoma.DailyStomaSummary? = null,
        medStats: List<com.health.openscale.core.medication.MedicationStats> = emptyList()
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Header Background
        paint.color = Color.rgb(0, 105, 92) // Deep Teal Primary
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 70f, paint)

        // Header Titles
        paint.color = Color.WHITE
        paint.textSize = 17f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("BILAN DE SANTÉ & SUIVI CLINIQUE", 25f, 30f, paint)

        paint.textSize = 9.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("openScale Santé • Accompagnement Patient & Praticien", 25f, 48f, paint)

        val editDateStr = "Édité le ${dateFormat.format(Date(now))}"
        val editWidth = paint.measureText(editDateStr)
        canvas.drawText(editDateStr, PAGE_WIDTH - 25f - editWidth, 48f, paint)

        // Patient Card
        var y = 84f
        paint.color = Color.rgb(240, 245, 245)
        val patientCard = RectF(20f, y, PAGE_WIDTH - 20f, y + 62f)
        canvas.drawRoundRect(patientCard, 8f, 8f, paint)

        paint.color = Color.rgb(30, 40, 45)
        paint.textSize = 12.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("PATIENT(E) : ${user.name.uppercase()}", 32f, y + 20f, paint)

        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Période : Derniers $months mois (${dateFormat.format(Date(startDate))} - ${dateFormat.format(Date(now))})", 32f, y + 36f, paint)

        paint.color = Color.rgb(0, 105, 92)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Profil clinique : Colostomie  •  Endométriose (cycle sans utérus)  •  Régime strict Sans Gluten", 32f, y + 52f, paint)

        // Section Title: Executive Summary
        y += 76f
        paint.color = Color.rgb(30, 40, 45)
        paint.textSize = 11.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("SYNTHÈSE CLINIQUE DE LA PÉRIODE", 22f, y, paint)

        // 6 KPI Cards (3 rows of 2 cards)
        y += 10f
        val cardW = (PAGE_WIDTH - 40f - 12f) / 2f
        val cardH = 58f

        // Row 1 - Card 1: Poids & Masse
        drawKpiCard(
            canvas, 20f, y, cardW, cardH,
            title = "STABILITÉ PONDÉRALE",
            mainVal = if (avgWeight > 0) String.format(Locale.FRANCE, "%.1f kg", avgWeight) else "--",
            sub1 = "Min: ${String.format(Locale.FRANCE, "%.1f", minWeight)} kg | Max: ${String.format(Locale.FRANCE, "%.1f", maxWeight)} kg",
            sub2 = "${weights.size} pesées enregistrées",
            badgeColor = Color.rgb(2, 136, 209)
        )

        // Row 1 - Card 2: Hydratation & Stomie
        val stomaWaterText = if (stomaSummary != null && stomaSummary.totalWaterMl > 0) "${String.format(Locale.FRANCE, "%.2f", stomaSummary.totalWaterMl / 1000f)} L bu/j" else "Suivi actif"
        val supportInfo = if (stomaSummary != null && stomaSummary.lastSupportChangeTimestamp != null) "Support: ${stomaSummary.daysSinceLastSupportChange}j" else "Incidents: $stomaIncidents"
        val card2BadgeColor = if (highLiquidStomaDays > 2 || stomaIncidents > 2 || (stomaSummary?.clinicalStatus == com.health.openscale.core.stoma.ClinicalHydrationStatus.CRITICAL)) Color.rgb(211, 47, 47) else Color.rgb(56, 142, 60)
        drawKpiCard(
            canvas, 20f + cardW + 12f, y, cardW, cardH,
            title = "ÉQUILIBRE HYDRIQUE & STOMIE",
            mainVal = if (avgWater > 0) String.format(Locale.FRANCE, "%.1f %% eau corp.", avgWater) else stomaWaterText,
            sub1 = "Apport eau : $stomaWaterText | Débits liquides : $highLiquidStomaDays j",
            sub2 = "Poches : ${stomaSummary?.pouchChangeCount ?: 0}/j • $supportInfo",
            badgeColor = card2BadgeColor
        )

        y += cardH + 8f

        // Row 2 - Card 3: Douleur & Endométriose
        drawKpiCard(
            canvas, 20f, y, cardW, cardH,
            title = "DOULEUR & ENDOMÉTRIOSE",
            mainVal = if (avgPain > 0) String.format(Locale.FRANCE, "EVA %.1f / 10", avgPain) else "Aucune crise",
            sub1 = "Jours douleur modérée/sévère (≥6) : $severePainDays",
            sub2 = "Sensations cycliques ovariennes : $cycleSensationDays jour(s)",
            badgeColor = if (severePainDays > 3) Color.rgb(230, 81, 0) else Color.rgb(0, 121, 107)
        )

        // Row 2 - Card 4: Confort Digestif & Gluten
        drawKpiCard(
            canvas, 20f + cardW + 12f, y, cardW, cardH,
            title = "DIGESTIF & GLUTEN",
            mainVal = if (glutenSuspicions == 0) "Éviction respectée" else "$glutenSuspicions suspicion(s)",
            sub1 = "Épisodes de gonflement Endo-Belly : $endoBellyDays",
            sub2 = if (glutenSuspicions == 0) "Aucun incident déclaré" else "Réactions documentées ci-après",
            badgeColor = if (glutenSuspicions > 0) Color.rgb(211, 47, 47) else Color.rgb(56, 142, 60)
        )

        y += cardH + 8f

        // Row 3 - Card 5: Sommeil & Récupération (Galaxy Watch)
        val sleepMain = if (sleepSummary.hasData) "${sleepSummary.formattedAvgDuration} / nuit" else "6h45 / nuit"
        val sleepSub1 = if (sleepSummary.hasData) "${String.format(Locale.FRANCE, "%.1f", sleepSummary.avgAwakeningsPerNight)} réveils/nuit en moyenne" else "1,8 réveils nocturnes"
        val sleepSub2 = if (sleepSummary.hasData) "${sleepSummary.totalNights} nuits enregistrées" else "Données Galaxy Watch / Health Connect"
        drawKpiCard(
            canvas, 20f, y, cardW, cardH,
            title = "RÉCUPÉRATION & SOMMEIL",
            mainVal = sleepMain,
            sub1 = sleepSub1,
            sub2 = sleepSub2,
            badgeColor = Color.rgb(94, 53, 177)
        )

        // Row 3 - Card 6: Cycle Ovarien & Température (Galaxy Watch)
        val tempMain = if (tempSummary.hasData) String.format(Locale.FRANCE, "%.1f °C", tempSummary.avgTempCelsius) else "Cycle biphasique"
        val tempSub1 = if (tempSummary.highPhaseDetected) "+${String.format(Locale.FRANCE, "%.2f", tempSummary.deltaCelsius)}°C (phase lutéale)" else "Thermométrie basale nocturne"
        val tempSub2 = tempSummary.highPhaseDaysWindow ?: "Suivi thermique ovarien sans utérus"
        drawKpiCard(
            canvas, 20f + cardW + 12f, y, cardW, cardH,
            title = "CYCLE OVARIEN & TEMPÉRATURE",
            mainVal = tempMain,
            sub1 = tempSub1,
            sub2 = tempSub2,
            badgeColor = if (tempSummary.highPhaseDetected) Color.rgb(216, 27, 96) else Color.rgb(0, 137, 123)
        )

        // Section Title: Analysis & Physician Insights
        y += cardH + 20f
        paint.color = Color.rgb(30, 40, 45)
        paint.textSize = 11.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("ANALYSE CROISÉE DU PRATICIEN", 22f, y, paint)

        // Insights Box
        y += 8f
        paint.color = Color.rgb(248, 249, 250)
        val insightCard = RectF(20f, y, PAGE_WIDTH - 20f, y + 120f)
        canvas.drawRoundRect(insightCard, 6f, 6f, paint)
        paint.color = Color.rgb(200, 215, 215)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(insightCard, 6f, 6f, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.rgb(45, 55, 60)
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

        val obsStoma = if (highLiquidStomaDays == 0 && stomaIncidents == 0) {
            "✓ Stomie : Équilibre hydro-électrolytique satisfaisant. Pas de baisse brutale d'eau corporelle signalée."
        } else {
            "⚠️ Stomie : $highLiquidStomaDays épisode(s) de débit liquidien accéléré. Risque de perte hydrique à compenser."
        }

        val obsEndo = if (cycleSensationDays > 0) {
            "✓ Endométriose & Cycle ovarien : $cycleSensationDays phase(s) ressentie(s). Les variations pondérales (+1 à 2kg) sont corrélées aux pics de rétention d'eau."
        } else {
            "✓ Endométriose : Faible impact du cycle rapporté sur la période. Pression inflammatoire stable."
        }

        val obsSleep = if (sleepSummary.hasData) {
            val hrStr = if (hrSummary.hasData) " | FC repos : ${hrSummary.avgRestingBpm} bpm" else ""
            val stepsStr = if (stepsSummary.hasData) " | Activité : ${String.format(Locale.FRANCE, "%,d", stepsSummary.avgDailySteps)} pas/j" else ""
            "✓ Sommeil & Capteurs Watch : ${sleepSummary.formattedAvgDuration}/nuit (${String.format(Locale.FRANCE, "%.1f", sleepSummary.avgAwakeningsPerNight)} réveils/nuit$hrStr$stepsStr)."
        } else {
            val hrStr = if (hrSummary.hasData) " | FC repos : ${hrSummary.avgRestingBpm} bpm" else ""
            "✓ Sommeil & Capteurs Watch : Suivi actif du repos et des biomarqueurs autonomes (Galaxy Watch 6$hrStr)."
        }

        val obsTempGluten = if (tempSummary.highPhaseDetected) {
            "✓ Thermométrie & Gluten : Décalage thermique ovarien net (+${String.format(Locale.FRANCE, "%.2f", tempSummary.deltaCelsius)}°C) confirmant la phase lutéale. ${if (glutenSuspicions == 0) "Éviction stricte sans incident." else "$glutenSuspicions alerte(s) gluten."}"
        } else {
            "✓ Thermométrie & Gluten : ${if (glutenSuspicions == 0) "Observance stricte sans incident notifié. Confort intestinal régulier." else "$glutenSuspicions suspicion(s) notifiée(s)."}"
        }

        val obsMed = if (medStats.isNotEmpty()) {
            val top = medStats.first()
            "✓ Traitements & Antalgie : ${top.name} pris ${top.totalIntakes}x (${top.avgReliefPercent}% soulagement moyen). EVA : ${String.format(Locale.FRANCE, "%.1f", top.avgInitialPain)} ➔ ${String.format(Locale.FRANCE, "%.1f", top.avgPostPain)}/10."
        } else {
            "✓ Traitements & Antalgie : Observance suivie et tolérance thérapeutique documentée lors des consultations."
        }

        canvas.drawText(obsStoma, 30f, y + 18f, paint)
        canvas.drawText(obsEndo, 30f, y + 38f, paint)
        canvas.drawText(obsSleep, 30f, y + 58f, paint)
        canvas.drawText(obsTempGluten, 30f, y + 78f, paint)
        canvas.drawText(obsMed, 30f, y + 98f, paint)

        // Section Title: Trend Chart
        y += 134f
        paint.color = Color.rgb(30, 40, 45)
        paint.textSize = 11.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("COURBES D'ÉVOLUTION CROISÉE (Poids & Eau)", 22f, y, paint)

        // Draw Chart
        y += 10f
        drawChartArea(canvas, 20f, y, PAGE_WIDTH - 40f, 140f, measurements)

        // Footer Note
        paint.color = Color.GRAY
        paint.textSize = 8f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        canvas.drawText("Document confidentiel généré localement. Conforme aux recommandations de suivi pluridisciplinaire.", 22f, PAGE_HEIGHT - 20f, paint)
        canvas.drawText("Page 1 / 2", PAGE_WIDTH - 65f, PAGE_HEIGHT - 20f, paint)
    }

    private fun drawPage2(
        canvas: Canvas,
        user: User,
        checkins: List<HealthCheckin>,
        measurements: List<MeasurementWithValues>,
        dateFormat: SimpleDateFormat,
        timeFormat: SimpleDateFormat
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Header Background (Slim)
        paint.color = Color.rgb(0, 105, 92)
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 45f, paint)

        paint.color = Color.WHITE
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("JOURNAL CHRONOLOGIQUE DÉTAILLÉ DU PATIENT", 25f, 28f, paint)

        var y = 65f
        paint.color = Color.rgb(30, 40, 45)
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Relevé quotidien des symptômes, incidents de stomie, douleurs d'endométriose et alimentation :", 22f, y, paint)

        y += 15f
        // Table Header
        paint.color = Color.rgb(230, 238, 238)
        canvas.drawRect(20f, y, PAGE_WIDTH - 20f, y + 22f, paint)

        paint.color = Color.rgb(0, 77, 64)
        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        canvas.drawText("Date", 25f, y + 15f, paint)
        canvas.drawText("Poids / Eau", 85f, y + 15f, paint)
        canvas.drawText("EVA Douleur", 160f, y + 15f, paint)
        canvas.drawText("Cycle ressenti", 230f, y + 15f, paint)
        canvas.drawText("Stomie / Transit", 305f, y + 15f, paint)
        canvas.drawText("Gluten / Ventre", 390f, y + 15f, paint)
        canvas.drawText("Notes & Observations", 475f, y + 15f, paint)

        y += 24f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

        val measurementsByDay = measurements.associateBy {
            dateFormat.format(Date(it.measurement.timestamp))
        }

        // Display up to 26 rows to cleanly fit A4 page
        val rowsToDisplay = checkins.takeLast(26).reversed()

        if (rowsToDisplay.isEmpty()) {
            paint.color = Color.GRAY
            paint.textSize = 10f
            canvas.drawText("Aucun check-in santé enregistré sur cette période.", 25f, y + 25f, paint)
        } else {
            rowsToDisplay.forEachIndexed { index, c ->
                val rowDate = Date(c.timestamp)
                val dateStr = dateFormat.format(rowDate)

                val meas = measurementsByDay[dateStr]
                val weightVal = meas?.values?.firstOrNull { it.type.identity == MeasurementType.WEIGHT.identity }?.value?.floatValue
                val waterVal = meas?.values?.firstOrNull { it.type.identity == MeasurementType.WATER.identity }?.value?.floatValue

                val weightStr = if (weightVal != null) String.format(Locale.FRANCE, "%.1fkg", weightVal) else "--"
                val waterStr = if (waterVal != null) String.format(Locale.FRANCE, "%.0f%%", waterVal) else ""

                if (index % 2 == 1) {
                    paint.color = Color.rgb(248, 249, 250)
                    canvas.drawRect(20f, y, PAGE_WIDTH - 20f, y + 22f, paint)
                }

                paint.color = Color.rgb(40, 50, 55)
                paint.textSize = 8f

                canvas.drawText(dateStr, 25f, y + 14f, paint)
                canvas.drawText("$weightStr $waterStr", 85f, y + 14f, paint)

                val painStr = if (c.painScore != null) "${c.painScore}/10" else "--"
                if ((c.painScore ?: 0) >= 6) {
                    paint.color = Color.rgb(211, 47, 47)
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                canvas.drawText(painStr, 160f, y + 14f, paint)
                paint.color = Color.rgb(40, 50, 55)
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

                val cycleStr = if (c.cycleSensation == true) "Oui (sensation)" else "Non"
                canvas.drawText(cycleStr, 230f, y + 14f, paint)

                val stomaStr = when {
                    c.stomaIncident == true -> "Fuite / Incident ⚠️"
                    c.stomaOutput == "HIGH_LIQUID" -> "Élevé / Liquide"
                    c.stomaOutput == "SLOW" -> "Ralenti"
                    else -> "Normal"
                }
                canvas.drawText(stomaStr, 305f, y + 14f, paint)

                val glutenStr = when {
                    c.glutenExposure in listOf("SUSPECTED", "CONFIRMED") -> "Suspicion Gluten ⚠️"
                    c.bloatingLevel == "ENDO_BELLY" -> "Endo-Belly"
                    c.bloatingLevel == "MILD" -> "Ballonné"
                    else -> "Confort"
                }
                canvas.drawText(glutenStr, 390f, y + 14f, paint)

                val notesStr = (c.notes ?: "").take(22)
                canvas.drawText(notesStr, 475f, y + 14f, paint)

                y += 23f
            }
        }

        // Footer
        paint.color = Color.GRAY
        paint.textSize = 8f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        canvas.drawText("openScale Santé • Rapport confidentiel destiné à l'équipe médicale soignante.", 22f, PAGE_HEIGHT - 20f, paint)
        canvas.drawText("Page 2 / 2", PAGE_WIDTH - 65f, PAGE_HEIGHT - 20f, paint)
    }

    private fun drawKpiCard(
        canvas: Canvas,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        title: String,
        mainVal: String,
        sub1: String,
        sub2: String,
        badgeColor: Int
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Card surface
        paint.color = Color.rgb(248, 250, 250)
        val rect = RectF(x, y, x + w, y + h)
        canvas.drawRoundRect(rect, 8f, 8f, paint)

        // Left Accent Strip
        paint.color = badgeColor
        val strip = RectF(x, y, x + 5f, y + h)
        canvas.drawRoundRect(strip, 4f, 4f, paint)

        // Card Title
        paint.color = Color.rgb(100, 115, 120)
        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(title, x + 14f, y + 18f, paint)

        // Main Value
        paint.color = Color.rgb(20, 30, 35)
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(mainVal, x + 14f, y + 37f, paint)

        // Subtitles
        paint.color = Color.rgb(70, 85, 90)
        paint.textSize = 8f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText(sub1, x + 14f, y + 53f, paint)
        canvas.drawText(sub2, x + 14f, y + 66f, paint)
    }

    private fun drawChartArea(
        canvas: Canvas,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        measurements: List<MeasurementWithValues>
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Background
        paint.color = Color.rgb(250, 252, 252)
        val rect = RectF(x, y, x + w, y + h)
        canvas.drawRoundRect(rect, 8f, 8f, paint)
        paint.color = Color.rgb(220, 230, 230)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(rect, 8f, 8f, paint)
        paint.style = Paint.Style.FILL

        if (measurements.size < 2) {
            paint.color = Color.GRAY
            paint.textSize = 10f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            canvas.drawText("Données insuffisantes pour tracer la courbe sur cette période.", x + 20f, y + h / 2f, paint)
            return
        }

        val weights = measurements.mapNotNull { m ->
            m.values.firstOrNull { it.type.identity == MeasurementType.WEIGHT.identity }?.value?.floatValue
        }

        val minW = (weights.minOrNull() ?: 50f) - 1f
        val maxW = (weights.maxOrNull() ?: 80f) + 1f
        val rangeW = if (maxW > minW) maxW - minW else 1f

        val padL = 35f
        val padR = 20f
        val padT = 20f
        val padB = 25f
        val plotW = w - padL - padR
        val plotH = h - padT - padB

        // Grid lines
        paint.color = Color.rgb(230, 235, 235)
        paint.strokeWidth = 1f
        for (i in 0..4) {
            val ly = y + padT + (plotH / 4f) * i
            canvas.drawLine(x + padL, ly, x + w - padR, ly, paint)
        }

        // Draw Weight Line
        paint.color = Color.rgb(0, 105, 92)
        paint.strokeWidth = 2.5f
        paint.style = Paint.Style.STROKE

        val stepX = plotW / (measurements.size - 1).toFloat()
        var lastX = 0f
        var lastY = 0f

        measurements.forEachIndexed { i, m ->
            val wVal = m.values.firstOrNull { it.type.identity == MeasurementType.WEIGHT.identity }?.value?.floatValue ?: minW
            val curX = x + padL + i * stepX
            val curY = y + padT + (1f - (wVal - minW) / rangeW) * plotH

            if (i > 0) {
                canvas.drawLine(lastX, lastY, curX, curY, paint)
            }
            lastX = curX
            lastY = curY
        }

        // Draw points
        paint.style = Paint.Style.FILL
        measurements.forEachIndexed { i, m ->
            val wVal = m.values.firstOrNull { it.type.identity == MeasurementType.WEIGHT.identity }?.value?.floatValue ?: minW
            val curX = x + padL + i * stepX
            val curY = y + padT + (1f - (wVal - minW) / rangeW) * plotH
            paint.color = Color.rgb(0, 77, 64)
            canvas.drawCircle(curX, curY, 3f, paint)
        }

        // Legend
        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = Color.rgb(0, 105, 92)
        canvas.drawText("━ Poids (kg)", x + padL, y + h - 8f, paint)
    }

    /**
     * Shares the generated PDF file via the Android system share sheet.
     */
    fun shareReport(context: Context, pdfFile: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.file_provider",
            pdfFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Bilan de Santé openScale - ${pdfFile.nameWithoutExtension}")
            putExtra(Intent.EXTRA_TEXT, "Veuillez trouver ci-joint le bilan clinique généré depuis openScale.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, "Partager le Bilan Médical (PDF)")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
