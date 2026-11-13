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

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.health.openscale.core.data.StomaProduct
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StomaOrderReportGeneratorTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val generator by lazy { StomaOrderReportGenerator(app) }

    private fun sampleItems(count: Int): List<StomaOrderReportGenerator.OrderItem> {
        return (1..count).map { idx ->
            StomaOrderReportGenerator.OrderItem(
                product = StomaProduct(
                    id = "prod_$idx",
                    ref = "11422$idx",
                    name = "Poche fermée grise $idx",
                    detail = "Anneau 60mm / Maxi 530ml",
                    gtin = "570178017010$idx",
                    category = "Mio Click"
                ),
                quantity = (idx % 3) + 1
            )
        }
    }

    @Test
    fun `generateOrderPdf uses dated safe filename and creates valid single-page PDF`() {
        val dateSlug = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val pdfFile = generator.generateOrderPdf(
            patientName = "Élodie Martin!",
            items = sampleItems(4),
            notes = "Livraison souhaitée avant vendredi."
        )

        assertThat(pdfFile.exists()).isTrue()
        assertThat(pdfFile.length()).isGreaterThan(0L)
        assertThat(pdfFile.name).isEqualTo("commande_stomie_Elodie_Martin_${dateSlug}.pdf")
        assertThat(pdfFile.parentFile?.name).isEqualTo("reports")
    }

    @Test
    fun `generateOrderPdf falls back to patient when name is blank`() {
        val dateSlug = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val pdfFile = generator.generateOrderPdf(
            patientName = "   ",
            items = emptyList(),
            notes = null
        )

        assertThat(pdfFile.exists()).isTrue()
        assertThat(pdfFile.length()).isGreaterThan(0L)
        assertThat(pdfFile.name).isEqualTo("commande_stomie_patient_${dateSlug}.pdf")
    }

    @Test
    fun `generateOrderPdf handles multi-page pagination cleanly for many items`() {
        val singlePageFile = generator.generateOrderPdf(
            patientName = "Sophie Martin",
            items = sampleItems(3),
            notes = null
        )
        val multiPageFile = generator.generateOrderPdf(
            patientName = "Sophie Martin",
            items = sampleItems(28),
            notes = "Note multi-lignes : merci de vérifier le stock de supports semi-convexes 15-40mm et de préparer les boîtes complètes pour le retrait."
        )

        assertThat(multiPageFile.exists()).isTrue()
        assertThat(multiPageFile.length()).isGreaterThan(singlePageFile.length())
    }

    @Test
    fun `buildPlainTextSummary includes all order fields and omits ALD 100 percent`() {
        val items = sampleItems(2)
        val summary = generator.buildPlainTextSummary(
            patientName = "Sophie Martin",
            items = items,
            notes = "Retrait pharmacie demain matin"
        )

        assertThat(summary).contains("BON DE COMMANDE")
        assertThat(summary).contains("Matériel de Stomathérapie")
        assertThat(summary).contains("SOPHIE MARTIN")
        assertThat(summary).contains("114221")
        assertThat(summary).contains("Poche fermée grise 1")
        assertThat(summary).contains("Anneau 60mm / Maxi 530ml")
        assertThat(summary).contains("5701780170101")
        assertThat(summary).contains("Total : 5 boîte(s) / unité(s)")
        assertThat(summary).contains("Retrait pharmacie demain matin")
        assertThat(summary).contains("Document généré via openScale Santé")
        assertThat(summary.uppercase(Locale.FRANCE)).doesNotContain("ALD 100%")
    }

    @Test
    fun `shareOrder uses matching file_provider authority and read URI permission`() {
        val pdfFile = generator.generateOrderPdf(
            patientName = "Sophie Martin",
            items = sampleItems(2)
        )

        generator.shareOrder(pdfFile)

        val chooser = Shadows.shadowOf(app).nextStartedActivity
        assertThat(chooser).isNotNull()
        assertThat(chooser.action).isEqualTo(Intent.ACTION_CHOOSER)

        val sendIntent = IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java)
        assertThat(sendIntent).isNotNull()
        assertThat(sendIntent!!.action).isEqualTo(Intent.ACTION_SEND)
        assertThat(sendIntent.type).isEqualTo("application/pdf")
        assertThat(sendIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .isEqualTo(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        val uri = IntentCompat.getParcelableExtra(sendIntent, Intent.EXTRA_STREAM, Uri::class.java)
        assertThat(uri).isNotNull()
        assertThat(uri!!.authority).isEqualTo("${app.packageName}.file_provider")
        assertThat(sendIntent.getStringExtra(Intent.EXTRA_SUBJECT)?.uppercase(Locale.FRANCE))
            .doesNotContain("ALD 100%")
    }

    @Test
    fun `sendByEmail attaches PDF when provided and populates email extras`() {
        val items = sampleItems(2)
        val pdfFile = generator.generateOrderPdf("Sophie Martin", items)

        generator.sendByEmail(
            patientName = "Sophie Martin",
            items = items,
            notes = "Merci d'avance",
            attachmentFile = pdfFile,
            recipientEmail = "pharmacie@example.fr"
        )

        val chooser = Shadows.shadowOf(app).nextStartedActivity
        assertThat(chooser).isNotNull()
        val sendIntent = IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java)
        assertThat(sendIntent).isNotNull()
        assertThat(sendIntent!!.getStringArrayExtra(Intent.EXTRA_EMAIL))
            .asList()
            .containsExactly("pharmacie@example.fr")
        assertThat(sendIntent.getStringExtra(Intent.EXTRA_TEXT)).contains("Merci d'avance")

        val uri = IntentCompat.getParcelableExtra(sendIntent, Intent.EXTRA_STREAM, Uri::class.java)
        assertThat(uri).isNotNull()
        assertThat(uri!!.authority).isEqualTo("${app.packageName}.file_provider")
    }

    @Test
    fun `sharePlainText launches text share intent without attachment`() {
        generator.sharePlainText(
            patientName = "Sophie Martin",
            items = sampleItems(1),
            notes = null
        )

        val chooser = Shadows.shadowOf(app).nextStartedActivity
        assertThat(chooser).isNotNull()
        val sendIntent = IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java)
        assertThat(sendIntent).isNotNull()
        assertThat(sendIntent!!.type).isEqualTo("text/plain")
        assertThat(sendIntent.getStringExtra(Intent.EXTRA_TEXT)).contains("SOPHIE MARTIN")
    }
}
