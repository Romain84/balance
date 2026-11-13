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

import java.io.File
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.UUID

data class StomaProduct(
    val id: String = UUID.randomUUID().toString(),
    val ref: String,
    val name: String,
    val detail: String,
    val gtin: String,
    val category: String,
    val defaultQty: Int = 1,
    val monthlyPresetQty: Int = 0
)

object StomaCatalog {
    private val MOJIBAKE_MARKERS = listOf("Ã", "Â", "â€")
    private val WINDOWS_1252: Charset = Charset.forName("windows-1252")

    val DEFAULT_PRODUCTS = listOf(
        StomaProduct(
            id = "coloplast_114229",
            category = "Mio Click - Fermée 2 pièces",
            name = "Poche fermée grise",
            detail = "Anneau 60mm / Maxi 530ml",
            ref = "114229",
            gtin = "5701780170109",
            monthlyPresetQty = 2
        ),
        StomaProduct(
            id = "coloplast_197329",
            category = "Mio Click - Fermée 2 pièces",
            name = "Poche fermée noire",
            detail = "Anneau 60mm / Maxi 530ml",
            ref = "197329",
            gtin = "5701780377041",
            monthlyPresetQty = 0
        ),
        StomaProduct(
            id = "coloplast_165690",
            category = "Mio Click - Fermée 2 pièces",
            name = "Support Semi-Convexe",
            detail = "Anneau 60mm / 15-40mm",
            ref = "165690",
            gtin = "5708932612425",
            monthlyPresetQty = 1
        ),
        StomaProduct(
            id = "brava_120701",
            category = "Brava® Support Plus",
            name = "Demi-lune Standard",
            detail = "Standard (Boîte de 20)",
            ref = "120701",
            gtin = "5708932598583",
            monthlyPresetQty = 1
        ),
        StomaProduct(
            id = "brava_120760",
            category = "Brava® Support Plus",
            name = "Demi-lune XL",
            detail = "XL (Boîte de 20)",
            ref = "120760",
            gtin = "5708932676144",
            monthlyPresetQty = 0
        ),
        StomaProduct(
            id = "brava_120501",
            category = "Brava®",
            name = "Pâte en tube (sans alcool)",
            detail = "Tube 60g",
            ref = "120501",
            gtin = "5708932740579",
            monthlyPresetQty = 1
        ),
        StomaProduct(
            id = "brava_019075",
            category = "Brava®",
            name = "Poudre",
            detail = "Flacon 25g",
            ref = "019075",
            gtin = "5708932721400",
            monthlyPresetQty = 0
        ),
        StomaProduct(
            id = "brava_004230",
            category = "Brava®",
            name = "Ceinture Mio Brava",
            detail = "Standard 100cm",
            ref = "004230",
            gtin = "5708932135252",
            monthlyPresetQty = 0
        ),
        StomaProduct(
            id = "brava_120381",
            category = "Brava®",
            name = "Anneau Coloplast",
            detail = "2.5mm / 18mm int / 64mm ext",
            ref = "120381",
            gtin = "5701780173940",
            monthlyPresetQty = 0
        ),
        StomaProduct(
            id = "coloplast_335380",
            category = "Autre",
            name = "Comfeel",
            detail = "22,5 × 22,5 cm (Boîte de 10)",
            ref = "335380",
            gtin = "5708932844741",
            monthlyPresetQty = 0
        ),
        StomaProduct(
            id = "convatec_420062",
            category = "Autre",
            name = "Duoderm",
            detail = "17,5 × 17,5 cm (Boîte de 10)",
            ref = "420062",
            gtin = "420062",
            monthlyPresetQty = 0
        ),
        StomaProduct(
            id = "hollister_7737",
            category = "Autre",
            name = "ADAPT spray retrait d'adhésif",
            detail = "50ml",
            ref = "7737",
            gtin = "---",
            monthlyPresetQty = 1
        ),
        StomaProduct(
            id = "hollister_8815",
            category = "Autre",
            name = "Anneau Hollister",
            detail = "48mm / 2.3mm",
            ref = "8815",
            gtin = "3401060164577",
            monthlyPresetQty = 0
        ),
        StomaProduct(
            id = "molnlycke_mesoft",
            category = "Compresses",
            name = "Compresses non stériles Mölnlycke",
            detail = "Mesoft 10X10",
            ref = "---",
            gtin = "7323190152223",
            monthlyPresetQty = 1
        )
    )

    val PRODUCTS get() = DEFAULT_PRODUCTS

    /**
     * Returns the default monthly order preset mapping product IDs to recommended monthly box counts.
     */
    fun getDefaultMonthlyPreset(): Map<String, Int> =
        DEFAULT_PRODUCTS
            .filter { it.monthlyPresetQty > 0 }
            .associate { it.id to it.monthlyPresetQty }

    /**
     * Repairs UTF-8 strings that were mis-decoded as Windows-1252 / Latin-1 (mojibake).
     */
    fun repairMojibake(input: String): String {
        if (input.isEmpty() || input.none { it.code > 127 }) return input
        var current = input
        repeat(2) {
            if (MOJIBAKE_MARKERS.none { current.contains(it) }) return current
            val decoded = decodeWindows1252AsUtf8(current) ?: return current
            if (decoded == current || decoded.length >= current.length) return current
            current = decoded
        }
        return current
    }

    internal fun normalizeProduct(product: StomaProduct): StomaProduct {
        val cleanName = when (val repaired = repairMojibake(product.name)) {
            "Poche fermée gris" -> if (product.id == "coloplast_114229") "Poche fermée grise" else repaired
            "Poche fermée noir" -> if (product.id == "coloplast_197329") "Poche fermée noire" else repaired
            else -> repaired
        }
        val cleanCategory = when (val repaired = repairMojibake(product.category)) {
            "Mio Click - Fermée 2 pieces" -> "Mio Click - Fermée 2 pièces"
            else -> repaired
        }
        val cleanDetail = when (val repaired = repairMojibake(product.detail)) {
            "22.5 X 22.5 cm (Boîte de 10)" -> if (product.id == "coloplast_335380") "22,5 × 22,5 cm (Boîte de 10)" else repaired
            else -> repaired
        }
        return product.copy(
            ref = repairMojibake(product.ref),
            name = cleanName,
            detail = cleanDetail,
            gtin = repairMojibake(product.gtin),
            category = cleanCategory
        )
    }

    private fun decodeWindows1252AsUtf8(text: String): String? {
        return try {
            val encoder = WINDOWS_1252.newEncoder()
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .onMalformedInput(CodingErrorAction.REPORT)
            val byteBuffer = encoder.encode(CharBuffer.wrap(text))
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(byteBuffer).toString()
        } catch (_: CharacterCodingException) {
            null
        }
    }
}

internal fun writeTextAtomically(targetFile: File, tempFile: File, content: String) {
    tempFile.writeText(content, Charsets.UTF_8)
    if (!tempFile.renameTo(targetFile)) {
        tempFile.copyTo(targetFile, overwrite = true)
        tempFile.delete()
    }
}
