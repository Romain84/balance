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

import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import java.io.OutputStream

/**
 * Abstraction over [android.graphics.pdf.PdfDocument] so production builds emit real native A4 PDF
 * documents while Robolectric JVM unit tests can exercise all layout, pagination, and drawing code
 * without native Skia PDF bindings.
 */
interface PdfPageHandle {
    val canvas: Canvas
}

interface PdfDocumentAdapter {
    fun startPage(width: Int, height: Int, pageNumber: Int): PdfPageHandle
    fun finishPage(page: PdfPageHandle)
    fun writeTo(out: OutputStream)
    fun close()
}

internal class AndroidPdfDocumentAdapter : PdfDocumentAdapter {
    private val document = PdfDocument()

    private class AndroidPageHandle(val page: PdfDocument.Page) : PdfPageHandle {
        override val canvas: Canvas get() = page.canvas
    }

    override fun startPage(width: Int, height: Int, pageNumber: Int): PdfPageHandle {
        val pageInfo = PdfDocument.PageInfo.Builder(width, height, pageNumber).create()
        return AndroidPageHandle(document.startPage(pageInfo))
    }

    override fun finishPage(page: PdfPageHandle) {
        document.finishPage((page as AndroidPageHandle).page)
    }

    override fun writeTo(out: OutputStream) {
        document.writeTo(out)
    }

    override fun close() {
        document.close()
    }
}
