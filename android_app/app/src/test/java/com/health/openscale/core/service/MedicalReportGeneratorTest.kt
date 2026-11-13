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
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.health.openscale.core.data.ActivityLevel
import com.health.openscale.core.data.GenderType
import com.health.openscale.core.data.HealthCheckin
import com.health.openscale.core.data.MeasurementType
import com.health.openscale.core.data.User
import com.health.openscale.core.healthconnect.ClinicalHeartRateSummary
import com.health.openscale.core.healthconnect.ClinicalHrvSummary
import com.health.openscale.core.healthconnect.ClinicalSleepSummary
import com.health.openscale.core.healthconnect.ClinicalSpo2Summary
import com.health.openscale.core.healthconnect.ClinicalStepsSummary
import com.health.openscale.core.healthconnect.ClinicalTempSummary
import com.health.openscale.testutil.Fixtures
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.OutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MedicalReportGeneratorTest {

    private class FakePdfDocumentAdapter : PdfDocumentAdapter {
        private val bitmaps = mutableListOf<Bitmap>()
        val finishedPages = mutableListOf<Int>()

        private class Page(val pageNumber: Int, override val canvas: Canvas) : PdfPageHandle

        override fun startPage(width: Int, height: Int, pageNumber: Int): PdfPageHandle {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmaps.add(bitmap)
            return Page(pageNumber, Canvas(bitmap))
        }

        override fun finishPage(page: PdfPageHandle) {
            finishedPages.add((page as Page).pageNumber)
        }

        override fun writeTo(out: OutputStream) {
            val content = "%PDF-1.4\n" + finishedPages.joinToString("\n") { "Page $it" }
            out.write(content.toByteArray())
        }

        override fun close() {
            bitmaps.forEach { if (!it.isRecycled) it.recycle() }
            bitmaps.clear()
        }
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val testUser = User(
        id = 1,
        name = "Sophie Martin",
        birthDate = 0L,
        gender = GenderType.FEMALE,
        heightCm = 168.0f,
        activityLevel = ActivityLevel.MODERATE,
        useAssistedWeighing = false
    )

    @Test
    fun `generateReport produces valid non-empty PDF file with default data`() {
        val fakeDoc = FakePdfDocumentAdapter()
        val pdfFile = MedicalReportGenerator.generateReport(
            context = context,
            user = testUser,
            months = 3,
            measurements = emptyList(),
            checkins = emptyList(),
            documentFactory = { fakeDoc }
        )

        assertThat(pdfFile).isNotNull()
        assertThat(pdfFile.exists()).isTrue()
        assertThat(pdfFile.length()).isGreaterThan(0L)
        assertThat(pdfFile.name).contains("Sophie_Martin")
        assertThat(fakeDoc.finishedPages).containsExactly(1, 2).inOrder()
    }

    @Test
    fun `generateReport handles 7 days period correctly`() {
        val fakeDoc = FakePdfDocumentAdapter()
        val pdfFile = MedicalReportGenerator.generateReport(
            context = context,
            user = testUser,
            months = 0,
            measurements = emptyList(),
            checkins = emptyList(),
            periodDays = 7,
            documentFactory = { fakeDoc }
        )

        assertThat(pdfFile).isNotNull()
        assertThat(pdfFile.exists()).isTrue()
        assertThat(pdfFile.length()).isGreaterThan(0L)
        assertThat(fakeDoc.finishedPages).containsExactly(1, 2).inOrder()
    }

    @Test
    fun `generateReport succeeds with clinical data and health connect summaries`() {
        val fakeDoc = FakePdfDocumentAdapter()
        val now = System.currentTimeMillis()
        val weightType = MeasurementType(id = 1, identity = MeasurementType.WEIGHT.identity)
        val waterType = MeasurementType(id = 2, identity = MeasurementType.WATER.identity)

        val mwv = Fixtures.mwv(
            measurementId = 1,
            timestamp = now,
            values = listOf(
                Fixtures.valueWithType(weightType, 62.5f, measurementId = 1),
                Fixtures.valueWithType(waterType, 53.0f, measurementId = 1)
            )
        )

        val checkin = HealthCheckin(
            id = 1,
            userId = 1,
            timestamp = now,
            painScore = 3,
            cycleSensation = true,
            stomaIncident = false,
            bristolScale = 4,
            bloatingLevel = "NONE",
            glutenExposure = "NONE"
        )

        val sleep = ClinicalSleepSummary(hasData = true, avgDurationMinutes = 450, avgAwakeningsPerNight = 1.2f, totalNights = 10)
        val temp = ClinicalTempSummary(hasData = true, avgTempCelsius = 36.6f, deltaCelsius = 0.35f, highPhaseDetected = true)
        val hr = ClinicalHeartRateSummary(hasData = true, avgRestingBpm = 62, minBpm = 55, maxBpm = 75)
        val hrv = ClinicalHrvSummary(hasData = true, avgRmssdMs = 45.0f, minRmssdMs = 32.0f, maxRmssdMs = 60.0f)
        val spo2 = ClinicalSpo2Summary(hasData = true, avgSpo2Percent = 97.5f, minSpo2Percent = 95.0f)
        val steps = ClinicalStepsSummary(hasData = true, avgDailySteps = 6500, totalSteps = 65000, daysRecorded = 10)

        val pdfFile = MedicalReportGenerator.generateReport(
            context = context,
            user = testUser,
            months = 1,
            measurements = listOf(mwv),
            checkins = listOf(checkin),
            sleepSummary = sleep,
            tempSummary = temp,
            hrSummary = hr,
            stepsSummary = steps,
            hrvSummary = hrv,
            spo2Summary = spo2,
            specialist = "Gastro-entérologue",
            consultationMotif = "Bilan trimestriel colostomie & endométriose",
            documentFactory = { fakeDoc }
        )

        assertThat(pdfFile).isNotNull()
        assertThat(pdfFile.exists()).isTrue()
        assertThat(pdfFile.length()).isGreaterThan(0L)
        assertThat(fakeDoc.finishedPages).containsExactly(1, 2).inOrder()
    }
}
