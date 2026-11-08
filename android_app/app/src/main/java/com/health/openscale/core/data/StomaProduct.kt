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

data class StomaProduct(
    val ref: String,
    val name: String,
    val detail: String,
    val gtin: String,
    val category: String,
    val defaultQty: Int = 1
)

object StomaCatalog {
    val PRODUCTS = listOf(
        StomaProduct(
            category = "Mio Click - Fermée 2 pieces",
            name = "Poche fermée gris",
            detail = "Anneau 60mm / Maxi 530ml",
            ref = "114229",
            gtin = "5701780170109"
        ),
        StomaProduct(
            category = "Mio Click - Fermée 2 pieces",
            name = "Poche fermée noir",
            detail = "Anneau 60mm / Maxi 530ml",
            ref = "197329",
            gtin = "5701780377041"
        ),
        StomaProduct(
            category = "Mio Click - Fermée 2 pieces",
            name = "Support Semi-Convexe",
            detail = "Anneau 60mm / 15-40mm",
            ref = "165690",
            gtin = "5708932612425"
        ),
        StomaProduct(
            category = "Brava® Support Plus",
            name = "Demi-lune Standard",
            detail = "Standard (Boîte de 20)",
            ref = "120701",
            gtin = "5708932598583"
        ),
        StomaProduct(
            category = "Brava® Support Plus",
            name = "Demi-lune XL",
            detail = "XL (Boîte de 20)",
            ref = "120760",
            gtin = "5708932676144"
        ),
        StomaProduct(
            category = "Brava®",
            name = "Pâte en tube (sans alcool)",
            detail = "Tube 60g",
            ref = "120501",
            gtin = "5708932740579"
        ),
        StomaProduct(
            category = "Brava®",
            name = "Poudre",
            detail = "Flacon 25g",
            ref = "019075",
            gtin = "5708932721400"
        ),
        StomaProduct(
            category = "Brava®",
            name = "Ceinture Mio Brava",
            detail = "Standard 100cm",
            ref = "004230",
            gtin = "5708932135252"
        ),
        StomaProduct(
            category = "Brava®",
            name = "Anneau Coloplast",
            detail = "2.5mm / 18mm int / 64mm ext",
            ref = "120381",
            gtin = "5701780173940"
        ),
        StomaProduct(
            category = "Autre",
            name = "Comfeel",
            detail = "22.5 X 22.5 cm (Boîte de 10)",
            ref = "335380",
            gtin = "5708932844741"
        ),
        StomaProduct(
            category = "Autre",
            name = "Duoderm",
            detail = "17,5 × 17,5 cm (Boîte de 10)",
            ref = "420062",
            gtin = "420062"
        ),
        StomaProduct(
            category = "Autre",
            name = "ADAPT spray retrait d'adhésif",
            detail = "50ml",
            ref = "7737",
            gtin = "---"
        ),
        StomaProduct(
            category = "Autre",
            name = "Anneau Hollister",
            detail = "48mm / 2.3mm",
            ref = "8815",
            gtin = "3401060164577"
        ),
        StomaProduct(
            category = "Compresses",
            name = "Compresses non stériles Mölnlycke",
            detail = "Mesoft 10X10",
            ref = "---",
            gtin = "7323190152223"
        )
    )
}
