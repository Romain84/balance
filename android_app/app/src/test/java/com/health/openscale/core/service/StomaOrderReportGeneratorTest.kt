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
import android.graphics.Bitmap
import android.graphics.Canvas
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
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StomaOrderReportGeneratorTest {

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
            val content = "%PDF-1.4\n" + finishedPages.joinToString("\n") { "Page $it rendered" }
            out.write(content.toByteArray())
        }

        override fun close() {
            bitmaps.forEach { if (!it.isRecycled) it.recycle() }
            bitmaps.clear()
        }
    }

    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val generator by lazy { StomaOrderReportGenerator(app) { FakePdfDocumentAdapter() } }

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
        val singleDoc = FakePdfDocumentAdapter()
        val singlePageFile = StomaOrderReportGenerator(app) { singleDoc }.generateOrderPdf(
            patientName = "Sophie Martin",
            items = sampleItems(3),
            notes = null
        )
        val singlePageSize = singlePageFile.length()

        val multiDoc = FakePdfDocumentAdapter()
        val multiPageFile = StomaOrderReportGenerator(app) { multiDoc }.generateOrderPdf(
            patientName = "Sophie Martin",
            items = sampleItems(28),
            notes = "Note multi-lignes : merci de vérifier le stock de supports semi-convexes 15-40mm et de préparer les boîtes complètes pour le retrait."
        )

        assertThat(multiPageFile.exists()).isTrue()
        assertThat(singleDoc.finishedPages).containsExactly(1)
        assertThat(multiDoc.finishedPages.size).isGreaterThan(1)
        assertThat(multiPageFile.length()).isGreaterThan(singlePageSize)
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
    fun `shareOrder and sendByEmail use matching file_provider authority and populate intent extras`() {
        val items = sampleItems(2)
        val pdfFile = generator.generateOrderPdf(
            patientName = "Sophie Martin",
            items = items
        )

        // 1. Verify shareOrder uses real FileProvider with matching file_provider authority
        generator.shareOrder(pdfFile)

        val shareChooser = Shadows.shadowOf(app).nextStartedActivity
        assertThat(shareChooser).isNotNull()
        assertThat(shareChooser.action).isEqualTo(Intent.ACTION_CHOOSER)

        val shareIntent = IntentCompat.getParcelableExtra(shareChooser, Intent.EXTRA_INTENT, Intent::class.java)
        assertThat(shareIntent).isNotNull()
        assertThat(shareIntent!!.action).isEqualTo(Intent.ACTION_SEND)
        assertThat(shareIntent.type).isEqualTo("application/pdf")
        assertThat(shareIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .isEqualTo(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        val shareUri = IntentCompat.getParcelableExtra(shareIntent, Intent.EXTRA_STREAM, Uri::class.java)
        assertThat(shareUri).isNotNull()
        assertThat(shareUri!!.authority).isEqualTo("${app.packageName}.file_provider")
        assertThat(shareIntent.getStringExtra(Intent.EXTRA_SUBJECT)?.uppercase(Locale.FRANCE))
            .doesNotContain("ALD 100%")

        // 2. Verify sendByEmail attaches PDF within same Robolectric cacheDir (FileProvider.sCache)
        generator.sendByEmail(
            patientName = "Sophie Martin",
            items = items,
            notes = "Merci d'avance",
            attachmentFile = pdfFile,
            recipientEmail = "pharmacie@example.fr"
        )

        val emailChooser = Shadows.shadowOf(app).nextStartedActivity
        assertThat(emailChooser).isNotNull()
        val emailIntent = IntentCompat.getParcelableExtra(emailChooser, Intent.EXTRA_INTENT, Intent::class.java)
        assertThat(emailIntent).isNotNull()
        assertThat(emailIntent!!.type).isEqualTo("application/pdf")
        assertThat(emailIntent.getStringArrayExtra(Intent.EXTRA_EMAIL))
            .asList()
            .containsExactly("pharmacie@example.fr")
        assertThat(emailIntent.getStringExtra(Intent.EXTRA_TEXT)).contains("Merci d'avance")

        val emailUri = IntentCompat.getParcelableExtra(emailIntent, Intent.EXTRA_STREAM, Uri::class.java)
        assertThat(emailUri).isNotNull()
        assertThat(emailUri!!.authority).isEqualTo("${app.packageName}.file_provider")
    }

    @Test
    fun `sendByEmail without attachment uses email mime type and omits stream extra`() {
        generator.sendByEmail(
            patientName = "Sophie Martin",
            items = sampleItems(1),
            notes = "Sans pièce jointe",
            attachmentFile = null,
            recipientEmail = "pharmacie@example.fr"
        )

        val chooser = Shadows.shadowOf(app).nextStartedActivity
        assertThat(chooser).isNotNull()
        val sendIntent = IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java)
        assertThat(sendIntent).isNotNull()
        assertThat(sendIntent!!.type).isEqualTo("message/rfc822")
        assertThat(sendIntent.getStringExtra(Intent.EXTRA_TEXT)).contains("Sans pièce jointe")
        assertThat(IntentCompat.getParcelableExtra(sendIntent, Intent.EXTRA_STREAM, Uri::class.java)).isNull()
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
