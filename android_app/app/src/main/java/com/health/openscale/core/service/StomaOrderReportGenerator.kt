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
import androidx.core.content.FileProvider
import com.health.openscale.core.data.StomaProduct
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StomaOrderReportGenerator(private val context: Context) {

    companion object {
        private const val PAGE_WIDTH = 595  // Standard A4 at 72 dpi
        private const val PAGE_HEIGHT = 842
    }

    data class OrderItem(
        val product: StomaProduct,
        val quantity: Int
    )

    fun generateOrderPdf(
        patientName: String,
        items: List<OrderItem>,
        notes: String? = null
    ): File {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
        val todayStr = dateFormat.format(Date())

        // 1. Header Bar (Coloplast Navy)
        paint.color = Color.rgb(0, 54, 91)
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 68f, paint)

        // 2. Teal Line
        paint.color = Color.rgb(0, 157, 168)
        canvas.drawRect(0f, 68f, PAGE_WIDTH.toFloat(), 74f, paint)

        // Title in Header
        paint.color = Color.WHITE
        paint.textSize = 20f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("BON DE COMMANDE", PAGE_WIDTH / 2f, 38f, paint)

        paint.textSize = 9.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Matériel de Stomathérapie • SenSura® Mio Click & Brava®", PAGE_WIDTH / 2f, 54f, paint)

        paint.textAlign = Paint.Align.LEFT

        // 3. Patient Information Card
        var y = 92f
        paint.color = Color.rgb(240, 246, 250)
        val patientCard = RectF(24f, y, PAGE_WIDTH - 24f, y + 62f)
        canvas.drawRoundRect(patientCard, 8f, 8f, paint)

        paint.color = Color.rgb(0, 54, 91)
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("PATIENT(E) :", 38f, y + 24f, paint)

        paint.color = Color.rgb(30, 40, 55)
        paint.textSize = 12f
        canvas.drawText(patientName.ifBlank { "Non renseigné" }.uppercase(), 125f, y + 24f, paint)

        paint.textSize = 9.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = Color.rgb(80, 100, 120)
        canvas.drawText("Date de la commande : $todayStr", 38f, y + 44f, paint)
        canvas.drawText("Prescription / Suivi : Ordonnance en cours", PAGE_WIDTH - 230f, y + 44f, paint)

        // 4. Products Table
        y += 80f
        paint.color = Color.rgb(0, 54, 91)
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("ARTICLES COMMANDÉS (${items.size})", 26f, y, paint)

        y += 12f
        val tableLeft = 24f
        val tableRight = PAGE_WIDTH - 24f
        val colRefW = 60f
        val colGtinW = 95f
        val colQtyW = 45f
        val colDesignationW = tableRight - tableLeft - colRefW - colGtinW - colQtyW

        // Table Header
        paint.color = Color.rgb(0, 54, 91)
        canvas.drawRect(tableLeft, y, tableRight, y + 24f, paint)

        paint.color = Color.WHITE
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("RÉF.", tableLeft + 8f, y + 16f, paint)
        canvas.drawText("DÉSIGNATION & FORMAT", tableLeft + colRefW + 8f, y + 16f, paint)
        canvas.drawText("CODE GTIN / CIP", tableLeft + colRefW + colDesignationW + 8f, y + 16f, paint)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("QTÉ", tableRight - (colQtyW / 2f), y + 16f, paint)
        paint.textAlign = Paint.Align.LEFT

        y += 24f

        // Table Rows
        var totalQty = 0
        items.forEachIndexed { idx, item ->
            val rowH = 32f
            paint.color = if (idx % 2 == 0) Color.WHITE else Color.rgb(243, 248, 252)
            canvas.drawRect(tableLeft, y, tableRight, y + rowH, paint)

            // Outer / inner border line
            paint.color = Color.rgb(215, 230, 240)
            paint.strokeWidth = 0.8f
            paint.style = Paint.Style.STROKE
            canvas.drawRect(tableLeft, y, tableRight, y + rowH, paint)
            paint.style = Paint.Style.FILL

            // Reference
            paint.color = Color.rgb(0, 54, 91)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 9f
            canvas.drawText(item.product.ref, tableLeft + 8f, y + 16f, paint)

            // Name & Detail
            paint.color = Color.rgb(25, 35, 45)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(item.product.name, tableLeft + colRefW + 8f, y + 14f, paint)

            paint.color = Color.rgb(0, 130, 140)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = 8f
            canvas.drawText(item.product.detail, tableLeft + colRefW + 8f, y + 25f, paint)

            // GTIN
            paint.color = Color.rgb(90, 110, 130)
            paint.textSize = 8.5f
            canvas.drawText(item.product.gtin, tableLeft + colRefW + colDesignationW + 8f, y + 18f, paint)

            // Quantity Badge
            paint.color = Color.rgb(0, 54, 91)
            paint.textSize = 10f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${item.quantity}", tableRight - (colQtyW / 2f), y + 18f, paint)
            paint.textAlign = Paint.Align.LEFT

            totalQty += item.quantity
            y += rowH
        }

        // 5. Total Bar
        y += 6f
        paint.color = Color.rgb(230, 242, 248)
        val totalCard = RectF(PAGE_WIDTH - 220f, y, tableRight, y + 28f)
        canvas.drawRoundRect(totalCard, 6f, 6f, paint)

        paint.color = Color.rgb(0, 54, 91)
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("TOTAL BOÎTES / UNITÉS :", PAGE_WIDTH - 210f, y + 18f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("$totalQty", tableRight - 12f, y + 18f, paint)
        paint.textAlign = Paint.Align.LEFT

        // 6. Notes / Observations section
        y += 42f
        if (!notes.isNullOrBlank()) {
            paint.color = Color.rgb(245, 247, 248)
            val notesCard = RectF(tableLeft, y, tableRight, y + 45f)
            canvas.drawRoundRect(notesCard, 6f, 6f, paint)

            paint.color = Color.rgb(0, 54, 91)
            paint.textSize = 9.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("NOTES & CONSIGNES PARTICULIÈRES :", tableLeft + 12f, y + 18f, paint)

            paint.color = Color.rgb(40, 50, 60)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText(notes, tableLeft + 12f, y + 34f, paint)
            y += 55f
        }

        // 7. Signature / Stamp Box
        y += 10f
        val stampBox = RectF(PAGE_WIDTH - 220f, y, tableRight, y + 80f)
        paint.color = Color.rgb(245, 245, 245)
        canvas.drawRoundRect(stampBox, 6f, 6f, paint)
        paint.color = Color.rgb(200, 215, 225)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(stampBox, 6f, 6f, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.rgb(100, 120, 135)
        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Date et Signature / Tampon :", PAGE_WIDTH - 210f, y + 18f, paint)

        // 8. Footer
        paint.color = Color.rgb(120, 140, 155)
        paint.textSize = 8f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Coloplast France — Document généré automatiquement via openScale Santé", PAGE_WIDTH / 2f, PAGE_HEIGHT - 20f, paint)

        pdfDocument.finishPage(page)

        // Save File
        val ordersDir = File(context.cacheDir, "reports")
        if (!ordersDir.exists()) ordersDir.mkdirs()

        val safeName = patientName.replace(Regex("[^a-zA-Z0-9]"), "_").ifBlank { "patient" }
        val file = File(ordersDir, "commande_${safeName}_coloplast.pdf")
        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        return file
    }

    fun shareOrder(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Bon de Commande Matériel de Stomie - ${file.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooser = Intent.createChooser(intent, "Transmettre le bon de commande").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
