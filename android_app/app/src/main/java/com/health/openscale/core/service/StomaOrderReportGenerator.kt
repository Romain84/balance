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
import com.health.openscale.core.data.StomaProduct
import java.io.File
import java.io.FileOutputStream
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StomaOrderReportGenerator(private val context: Context) {

    companion object {
        private const val PAGE_WIDTH = 595  // Standard A4 at 72 dpi
        private const val PAGE_HEIGHT = 842

        private const val MARGIN_H = 24f
        private const val TABLE_LEFT = MARGIN_H
        private const val TABLE_RIGHT = PAGE_WIDTH - MARGIN_H

        private const val COL_REF_W = 64f
        private const val COL_GTIN_W = 100f
        private const val COL_QTY_W = 46f
        private const val COL_DESIGNATION_W = TABLE_RIGHT - TABLE_LEFT - COL_REF_W - COL_GTIN_W - COL_QTY_W

        private const val TABLE_HEADER_H = 24f
        private const val ROW_H = 32f

        private const val FOOTER_DIVIDER_Y = PAGE_HEIGHT - 36f
        private const val FOOTER_TEXT_Y = PAGE_HEIGHT - 18f
        private const val CONTENT_BOTTOM_LIMIT = PAGE_HEIGHT - 46f

        private const val HEADER_SUBTITLE = "Matériel de Stomathérapie"
        private const val FOOTER_TEXT = "Document généré via openScale Santé"
    }

    data class OrderItem(
        val product: StomaProduct,
        val quantity: Int
    )

    private data class PagePlan(
        val isFirstPage: Boolean,
        val itemStartIndex: Int,
        val itemEndIndex: Int, // exclusive
        val drawSummary: Boolean
    )

    fun generateOrderPdf(
        patientName: String,
        items: List<OrderItem>,
        notes: String? = null
    ): File {
        val now = Date()
        val displayDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
        val todayStr = displayDateFormat.format(now)
        val dateSlug = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now)
        val displayPatientName = patientName.ifBlank { "Non renseigné" }.uppercase(Locale.FRANCE)

        val notesPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9.2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val wrappedNotes = if (!notes.isNullOrBlank()) {
            wrapText(notes, TABLE_RIGHT - TABLE_LEFT - 24f, notesPaint)
        } else {
            emptyList()
        }
        val notesCardH = if (wrappedNotes.isNotEmpty()) {
            28f + wrappedNotes.size * 13f + 8f
        } else {
            0f
        }
        val summaryBlockH = computeSummaryHeight(notesCardH)

        val pages = buildPagePlans(items.size, summaryBlockH)
        val totalPages = pages.size

        val pdfDocument = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val totalQty = items.sumOf { it.quantity }

        pages.forEachIndexed { pageIndex, plan ->
            val pageNumber = pageIndex + 1
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            var y: Float
            if (plan.isFirstPage) {
                y = drawFirstPageHeaderAndPatientCard(canvas, paint, displayPatientName, todayStr)
            } else {
                y = drawContinuationHeader(canvas, paint, displayPatientName, todayStr)
            }

            val hasTableRows = plan.itemEndIndex > plan.itemStartIndex
            val drawEmptyPlaceholder = plan.isFirstPage && items.isEmpty()

            if (hasTableRows || drawEmptyPlaceholder) {
                paint.color = Color.rgb(0, 54, 91)
                paint.textSize = 12f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textAlign = Paint.Align.LEFT
                val sectionLabel = if (plan.isFirstPage) {
                    "ARTICLES COMMANDÉS (${items.size})"
                } else {
                    "ARTICLES COMMANDÉS (SUITE)"
                }
                canvas.drawText(sectionLabel, TABLE_LEFT + 2f, y, paint)
                y += 12f

                drawTableHeader(canvas, paint, y)
                y += TABLE_HEADER_H

                if (drawEmptyPlaceholder) {
                    drawEmptyTableRow(canvas, paint, y)
                    y += ROW_H
                } else {
                    for (idx in plan.itemStartIndex until plan.itemEndIndex) {
                        drawTableRow(canvas, paint, y, idx, items[idx])
                        y += ROW_H
                    }
                }
            }

            if (plan.drawSummary) {
                y += 6f
                drawTotalBar(canvas, paint, y, totalQty)
                y += 28f

                if (wrappedNotes.isNotEmpty()) {
                    y += 14f
                    drawNotesCard(canvas, paint, y, notesCardH, wrappedNotes)
                    y += notesCardH
                }

                y += 14f
                drawSignatureBox(canvas, paint, y)
            }

            drawFooter(canvas, paint, pageNumber, totalPages)
            pdfDocument.finishPage(page)
        }

        val ordersDir = File(context.cacheDir, "reports")
        if (!ordersDir.exists()) ordersDir.mkdirs()

        val safeName = sanitizeFilenamePart(patientName)
        val file = File(ordersDir, "commande_stomie_${safeName}_${dateSlug}.pdf")
        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        return file
    }

    fun buildPlainTextSummary(
        patientName: String,
        items: List<OrderItem>,
        notes: String? = null
    ): String {
        val todayStr = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date())
        val displayPatient = patientName.ifBlank { "Non renseigné" }.uppercase(Locale.FRANCE)
        val totalBoxes = items.sumOf { it.quantity }

        return buildString {
            appendLine("BON DE COMMANDE — $HEADER_SUBTITLE")
            appendLine("Date : $todayStr")
            appendLine("Patient(e) : $displayPatient")
            appendLine()
            appendLine("Articles commandés (${items.size}) :")
            if (items.isEmpty()) {
                appendLine("- Aucun article sélectionné")
            } else {
                items.forEach { item ->
                    val p = item.product
                    val refPart = if (p.ref.isNotBlank()) "[Réf. ${p.ref}] " else ""
                    val detailPart = if (p.detail.isNotBlank()) " — ${p.detail}" else ""
                    val gtinPart = if (p.gtin.isNotBlank()) " (GTIN/CIP : ${p.gtin})" else ""
                    appendLine("- $refPart${p.name}$detailPart$gtinPart × ${item.quantity}")
                }
            }
            appendLine()
            appendLine("Total : $totalBoxes boîte(s) / unité(s)")
            if (!notes.isNullOrBlank()) {
                appendLine()
                appendLine("Notes : ${notes.trim()}")
            }
            appendLine()
            append(FOOTER_TEXT)
        }
    }

    fun shareOrder(file: File) {
        val uri = getFileUri(file)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Bon de Commande $HEADER_SUBTITLE - ${file.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooser = Intent.createChooser(intent, "Transmettre le bon de commande").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun sharePlainText(
        patientName: String,
        items: List<OrderItem>,
        notes: String? = null
    ) {
        val summary = buildPlainTextSummary(patientName, items, notes)
        val todayStr = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date())
        val displayPatient = patientName.ifBlank { "Patient" }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Commande $HEADER_SUBTITLE - $displayPatient ($todayStr)")
            putExtra(Intent.EXTRA_TEXT, summary)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooser = Intent.createChooser(intent, "Partager la commande (SMS / WhatsApp / E-mail)").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun sendByEmail(
        patientName: String,
        items: List<OrderItem>,
        notes: String? = null,
        attachmentFile: File? = null,
        recipientEmail: String? = null
    ) {
        val summary = buildPlainTextSummary(patientName, items, notes)
        val todayStr = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date())
        val displayPatient = patientName.ifBlank { "Patient" }
        val hasAttachment = attachmentFile != null && attachmentFile.exists()

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (hasAttachment) "application/pdf" else "message/rfc822"
            if (!recipientEmail.isNullOrBlank()) {
                putExtra(Intent.EXTRA_EMAIL, arrayOf(recipientEmail.trim()))
            }
            putExtra(Intent.EXTRA_SUBJECT, "Bon de Commande $HEADER_SUBTITLE - $displayPatient ($todayStr)")
            putExtra(Intent.EXTRA_TEXT, summary)
            if (hasAttachment && attachmentFile != null) {
                val uri = getFileUri(attachmentFile)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooser = Intent.createChooser(intent, "Envoyer la commande par e-mail").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun getFileUri(file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.file_provider",
            file
        )
    }

    private fun sanitizeFilenamePart(raw: String): String {
        val normalized = Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        return normalized
            .replace(Regex("[^a-zA-Z0-9]+"), "_")
            .trim('_')
            .ifBlank { "patient" }
    }

    private fun computeSummaryHeight(notesCardH: Float): Float {
        // Total bar (6 top gap + 28 height) + optional Notes (14 gap + notesCardH) + Signature (14 gap + 78 height)
        val notesPart = if (notesCardH > 0f) 14f + notesCardH else 0f
        return 6f + 28f + notesPart + 14f + 78f
    }

    private fun buildPagePlans(itemCount: Int, summaryBlockH: Float): List<PagePlan> {
        val plans = mutableListOf<PagePlan>()

        // First page vertical budget before table rows:
        // Header (74) + gap (18) + Patient Card (62) + gap (18) + Section title (12) + Table header (24) = 208f
        val firstPageRowsStartY = 208f
        // Continuation page vertical budget before table rows:
        // Header (50) + gap (18) + Section title (10) + Table header (24) = 102f
        val contPageRowsStartY = 102f

        if (itemCount == 0) {
            val afterPlaceholderY = firstPageRowsStartY + ROW_H
            if (afterPlaceholderY + summaryBlockH <= CONTENT_BOTTOM_LIMIT) {
                plans.add(PagePlan(isFirstPage = true, itemStartIndex = 0, itemEndIndex = 0, drawSummary = true))
            } else {
                plans.add(PagePlan(isFirstPage = true, itemStartIndex = 0, itemEndIndex = 0, drawSummary = false))
                plans.add(PagePlan(isFirstPage = false, itemStartIndex = 0, itemEndIndex = 0, drawSummary = true))
            }
            return plans
        }

        var cursor = 0
        var isFirst = true
        while (cursor < itemCount) {
            val startY = if (isFirst) firstPageRowsStartY else contPageRowsStartY
            val maxRowsOnPage = ((CONTENT_BOTTOM_LIMIT - startY) / ROW_H).toInt().coerceAtLeast(1)
            val end = minOf(cursor + maxRowsOnPage, itemCount)
            val rowsEndY = startY + (end - cursor) * ROW_H

            val isLastItemsPage = (end == itemCount)
            val summaryFitsHere = isLastItemsPage && (rowsEndY + summaryBlockH <= CONTENT_BOTTOM_LIMIT)

            plans.add(
                PagePlan(
                    isFirstPage = isFirst,
                    itemStartIndex = cursor,
                    itemEndIndex = end,
                    drawSummary = summaryFitsHere
                )
            )

            if (isLastItemsPage && !summaryFitsHere) {
                plans.add(
                    PagePlan(
                        isFirstPage = false,
                        itemStartIndex = end,
                        itemEndIndex = end,
                        drawSummary = true
                    )
                )
            }

            cursor = end
            isFirst = false
        }

        return plans
    }

    private fun drawFirstPageHeaderAndPatientCard(
        canvas: Canvas,
        paint: Paint,
        displayPatientName: String,
        todayStr: String
    ): Float {
        // 1. Header Bar (Coloplast Navy #00365B)
        paint.color = Color.rgb(0, 54, 91)
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 68f, paint)

        // 2. Teal Accent Line (#009DA8)
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
        canvas.drawText(HEADER_SUBTITLE, PAGE_WIDTH / 2f, 54f, paint)

        paint.textAlign = Paint.Align.LEFT

        // 3. Patient Information Card
        val cardTop = 92f
        paint.color = Color.rgb(240, 246, 250)
        val patientCard = RectF(TABLE_LEFT, cardTop, TABLE_RIGHT, cardTop + 62f)
        canvas.drawRoundRect(patientCard, 8f, 8f, paint)

        paint.color = Color.rgb(0, 54, 91)
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("PATIENT(E) :", 38f, cardTop + 24f, paint)

        paint.color = Color.rgb(30, 40, 55)
        paint.textSize = 12f
        drawTruncatedText(canvas, displayPatientName, 125f, cardTop + 24f, TABLE_RIGHT - 139f, paint)

        paint.textSize = 9.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = Color.rgb(80, 100, 120)
        canvas.drawText("Date de la commande : $todayStr", 38f, cardTop + 44f, paint)
        canvas.drawText("Prescription / Suivi : Ordonnance en cours", PAGE_WIDTH - 230f, cardTop + 44f, paint)

        return cardTop + 80f // 172f
    }

    private fun drawContinuationHeader(
        canvas: Canvas,
        paint: Paint,
        displayPatientName: String,
        todayStr: String
    ): Float {
        // Compact Navy Bar
        paint.color = Color.rgb(0, 54, 91)
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 46f, paint)

        // Teal Accent Line
        paint.color = Color.rgb(0, 157, 168)
        canvas.drawRect(0f, 46f, PAGE_WIDTH.toFloat(), 50f, paint)

        paint.color = Color.WHITE
        paint.textSize = 13f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("BON DE COMMANDE (SUITE)", TABLE_LEFT, 22f, paint)

        paint.color = Color.rgb(180, 235, 240)
        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText(HEADER_SUBTITLE, TABLE_LEFT, 36f, paint)

        paint.color = Color.WHITE
        paint.textSize = 8.5f
        paint.textAlign = Paint.Align.RIGHT
        drawTruncatedText(
            canvas = canvas,
            text = "$displayPatientName • $todayStr",
            x = TABLE_RIGHT,
            y = 29f,
            maxWidth = 240f,
            paint = paint,
            alignRight = true
        )
        paint.textAlign = Paint.Align.LEFT

        return 68f
    }

    private fun drawTableHeader(canvas: Canvas, paint: Paint, y: Float) {
        paint.color = Color.rgb(0, 54, 91)
        canvas.drawRect(TABLE_LEFT, y, TABLE_RIGHT, y + TABLE_HEADER_H, paint)

        paint.color = Color.WHITE
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("RÉF.", TABLE_LEFT + 8f, y + 16f, paint)
        canvas.drawText("DÉSIGNATION & FORMAT", TABLE_LEFT + COL_REF_W + 8f, y + 16f, paint)
        canvas.drawText("CODE GTIN / CIP", TABLE_LEFT + COL_REF_W + COL_DESIGNATION_W + 8f, y + 16f, paint)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("QTÉ", TABLE_RIGHT - (COL_QTY_W / 2f), y + 16f, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawTableRow(canvas: Canvas, paint: Paint, y: Float, idx: Int, item: OrderItem) {
        paint.color = if (idx % 2 == 0) Color.WHITE else Color.rgb(243, 248, 252)
        canvas.drawRect(TABLE_LEFT, y, TABLE_RIGHT, y + ROW_H, paint)

        paint.color = Color.rgb(215, 230, 240)
        paint.strokeWidth = 0.8f
        paint.style = Paint.Style.STROKE
        canvas.drawRect(TABLE_LEFT, y, TABLE_RIGHT, y + ROW_H, paint)
        paint.style = Paint.Style.FILL

        // Reference
        paint.color = Color.rgb(0, 54, 91)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 9f
        drawTruncatedText(canvas, item.product.ref, TABLE_LEFT + 8f, y + 18f, COL_REF_W - 12f, paint)

        // Name & Detail
        paint.color = Color.rgb(25, 35, 45)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 9f
        drawTruncatedText(
            canvas,
            item.product.name,
            TABLE_LEFT + COL_REF_W + 8f,
            y + 14f,
            COL_DESIGNATION_W - 14f,
            paint
        )

        paint.color = Color.rgb(0, 105, 92)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 8f
        drawTruncatedText(
            canvas,
            item.product.detail,
            TABLE_LEFT + COL_REF_W + 8f,
            y + 25f,
            COL_DESIGNATION_W - 14f,
            paint
        )

        // GTIN / CIP
        paint.color = Color.rgb(90, 110, 130)
        paint.textSize = 8.5f
        drawTruncatedText(
            canvas,
            item.product.gtin,
            TABLE_LEFT + COL_REF_W + COL_DESIGNATION_W + 8f,
            y + 18f,
            COL_GTIN_W - 12f,
            paint
        )

        // Quantity
        paint.color = Color.rgb(0, 54, 91)
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("${item.quantity}", TABLE_RIGHT - (COL_QTY_W / 2f), y + 19f, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawEmptyTableRow(canvas: Canvas, paint: Paint, y: Float) {
        paint.color = Color.WHITE
        canvas.drawRect(TABLE_LEFT, y, TABLE_RIGHT, y + ROW_H, paint)

        paint.color = Color.rgb(215, 230, 240)
        paint.strokeWidth = 0.8f
        paint.style = Paint.Style.STROKE
        canvas.drawRect(TABLE_LEFT, y, TABLE_RIGHT, y + ROW_H, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.rgb(110, 125, 140)
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Aucun article sélectionné", (TABLE_LEFT + TABLE_RIGHT) / 2f, y + 19f, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawTotalBar(canvas: Canvas, paint: Paint, y: Float, totalQty: Int) {
        paint.color = Color.rgb(230, 242, 248)
        val totalCard = RectF(PAGE_WIDTH - 220f, y, TABLE_RIGHT, y + 28f)
        canvas.drawRoundRect(totalCard, 6f, 6f, paint)

        paint.color = Color.rgb(0, 54, 91)
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("TOTAL BOÎTES / UNITÉS :", PAGE_WIDTH - 210f, y + 18f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("$totalQty", TABLE_RIGHT - 12f, y + 18f, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawNotesCard(
        canvas: Canvas,
        paint: Paint,
        y: Float,
        cardH: Float,
        wrappedNotes: List<String>
    ) {
        paint.color = Color.rgb(245, 247, 248)
        val notesCard = RectF(TABLE_LEFT, y, TABLE_RIGHT, y + cardH)
        canvas.drawRoundRect(notesCard, 6f, 6f, paint)

        paint.color = Color.rgb(0, 54, 91)
        paint.textSize = 9.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("NOTES & CONSIGNES PARTICULIÈRES :", TABLE_LEFT + 12f, y + 18f, paint)

        paint.color = Color.rgb(40, 50, 60)
        paint.textSize = 9.2f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        var lineY = y + 33f
        wrappedNotes.forEach { line ->
            canvas.drawText(line, TABLE_LEFT + 12f, lineY, paint)
            lineY += 13f
        }
    }

    private fun drawSignatureBox(canvas: Canvas, paint: Paint, y: Float) {
        val stampBox = RectF(PAGE_WIDTH - 220f, y, TABLE_RIGHT, y + 78f)
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
    }

    private fun drawFooter(canvas: Canvas, paint: Paint, pageNumber: Int, totalPages: Int) {
        paint.color = Color.rgb(215, 228, 236)
        paint.strokeWidth = 0.8f
        canvas.drawLine(TABLE_LEFT, FOOTER_DIVIDER_Y, TABLE_RIGHT, FOOTER_DIVIDER_Y, paint)

        paint.color = Color.rgb(120, 140, 155)
        paint.textSize = 8f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(FOOTER_TEXT, TABLE_LEFT, FOOTER_TEXT_Y, paint)

        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Page $pageNumber / $totalPages", TABLE_RIGHT, FOOTER_TEXT_Y, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawTruncatedText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        maxWidth: Float,
        paint: Paint,
        alignRight: Boolean = false
    ) {
        if (text.isEmpty() || maxWidth <= 0f) return
        val measured = paint.measureText(text)
        val output = if (measured <= maxWidth || measured <= 0f) {
            text
        } else {
            val ellipsis = "…"
            val available = (maxWidth - paint.measureText(ellipsis)).coerceAtLeast(0f)
            val count = paint.breakText(text, true, available, null)
            if (count > 0) {
                text.substring(0, count).trimEnd() + ellipsis
            } else {
                val approxChars = (maxWidth / (paint.textSize * 0.55f)).toInt().coerceIn(1, text.length)
                if (approxChars < text.length) text.substring(0, approxChars).trimEnd() + ellipsis else text
            }
        }
        paint.textAlign = if (alignRight) Paint.Align.RIGHT else Paint.Align.LEFT
        canvas.drawText(output, x, y, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    private fun wrapText(text: String, maxWidth: Float, paint: Paint): List<String> {
        if (text.isBlank() || maxWidth <= 0f) return emptyList()
        val result = mutableListOf<String>()
        text.trim().split('\n').forEach { paragraph ->
            val trimmedPara = paragraph.trim()
            if (trimmedPara.isEmpty()) return@forEach
            var start = 0
            while (start < trimmedPara.length) {
                val count = paint.breakText(trimmedPara, start, trimmedPara.length, true, maxWidth, null)
                var end = if (count > 0) {
                    start + count
                } else {
                    val approxChars = (maxWidth / (paint.textSize * 0.55f)).toInt().coerceAtLeast(20)
                    minOf(start + approxChars, trimmedPara.length)
                }
                if (end < trimmedPara.length) {
                    val lastSpace = trimmedPara.lastIndexOf(' ', end - 1)
                    if (lastSpace > start) {
                        end = lastSpace + 1
                    }
                }
                val line = trimmedPara.substring(start, end).trim()
                if (line.isNotEmpty()) {
                    result.add(line)
                }
                start = end
                while (start < trimmedPara.length && trimmedPara[start] == ' ') {
                    start++
                }
            }
        }
        return result
    }
}
