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

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StomaDataLayerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "stoma_catalog.json").delete()
        File(context.filesDir, "stoma_order_history.json").delete()
        File(context.filesDir, "stoma_custom_preset.json").delete()
    }

    @Test
    fun defaultCatalog_hasCleanUtf8AndNoMojibake() {
        val products = StomaCatalog.DEFAULT_PRODUCTS
        assertThat(products).isNotEmpty()

        val mojibakeMarkers = listOf("Ã", "Â", "â€")
        for (p in products) {
            for (field in listOf(p.name, p.detail, p.category, p.ref, p.gtin)) {
                for (marker in mojibakeMarkers) {
                    assertThat(field).doesNotContain(marker)
                }
            }
        }

        val grise = products.first { it.id == "coloplast_114229" }
        assertThat(grise.category).isEqualTo("Mio Click - Fermée 2 pièces")
        assertThat(grise.name).isEqualTo("Poche fermée grise")

        val noire = products.first { it.id == "coloplast_197329" }
        assertThat(noire.category).isEqualTo("Mio Click - Fermée 2 pièces")
        assertThat(noire.name).isEqualTo("Poche fermée noire")

        assertThat(products.first { it.id == "brava_120701" }.detail).contains("Boîte de 20")
        assertThat(products.first { it.id == "brava_120501" }.name).isEqualTo("Pâte en tube (sans alcool)")
        assertThat(products.first { it.id == "convatec_420062" }.detail).contains("17,5 × 17,5 cm")
        assertThat(products.first { it.id == "hollister_7737" }.name).isEqualTo("ADAPT spray retrait d'adhésif")
        assertThat(products.first { it.id == "molnlycke_mesoft" }.name).isEqualTo("Compresses non stériles Mölnlycke")
    }

    @Test
    fun repairMojibake_fixesCorruptedStringsAndLeavesValidUtf8Untouched() {
        assertThat(StomaCatalog.repairMojibake("Mio Click - FermÃ©e 2 piÃ¨ces"))
            .isEqualTo("Mio Click - Fermée 2 pièces")
        assertThat(StomaCatalog.repairMojibake("PÃ¢te en tube (sans alcool)"))
            .isEqualTo("Pâte en tube (sans alcool)")
        assertThat(StomaCatalog.repairMojibake("17,5 Ã\u2014 17,5 cm (BoÃ®te de 10)"))
            .isEqualTo("17,5 × 17,5 cm (Boîte de 10)")
        assertThat(StomaCatalog.repairMojibake("BravaÂ® Support Plus"))
            .isEqualTo("Brava® Support Plus")
        assertThat(StomaCatalog.repairMojibake("Compresses non stÃ©riles MÃ¶lnlycke"))
            .isEqualTo("Compresses non stériles Mölnlycke")

        // Valid UTF-8 must remain unchanged
        assertThat(StomaCatalog.repairMojibake("Fermée 2 pièces")).isEqualTo("Fermée 2 pièces")
        assertThat(StomaCatalog.repairMojibake("Brava® Support Plus")).isEqualTo("Brava® Support Plus")
        assertThat(StomaCatalog.repairMojibake("17,5 × 17,5 cm")).isEqualTo("17,5 × 17,5 cm")
    }

    @Test
    fun getDefaultMonthlyPreset_containsExpectedProducts() {
        val preset = StomaCatalog.getDefaultMonthlyPreset()
        assertThat(preset["coloplast_114229"]).isEqualTo(2)
        assertThat(preset["coloplast_165690"]).isEqualTo(1)
        assertThat(preset["brava_120701"]).isEqualTo(1)
        assertThat(preset["molnlycke_mesoft"]).isEqualTo(1)
    }

    @Test
    fun productRepository_migratesLegacyMojibakeAndMissingPresetQty() {
        val legacyArray = JSONArray().apply {
            put(JSONObject().apply {
                put("id", "coloplast_114229")
                put("ref", "114229")
                put("name", "Poche fermÃ©e gris")
                put("detail", "Anneau 60mm / Maxi 530ml")
                put("gtin", "5701780170109")
                put("category", "Mio Click - FermÃ©e 2 piÃ¨ces")
                put("defaultQty", 1)
            })
            put(JSONObject().apply {
                put("id", "custom_item")
                put("ref", "999")
                put("name", "Compresses MÃ¶lnlycke perso")
                put("detail", "BoÃ®te de 20")
                put("gtin", "12345")
                put("category", "Autre")
                put("defaultQty", 2)
            })
        }
        File(context.filesDir, "stoma_catalog.json").writeText(legacyArray.toString(), Charsets.UTF_8)

        val repo = StomaProductRepository(context)
        val loaded = repo.products.value
        assertThat(loaded).hasSize(2)

        val migratedDefault = loaded[0]
        assertThat(migratedDefault.name).isEqualTo("Poche fermée grise")
        assertThat(migratedDefault.category).isEqualTo("Mio Click - Fermée 2 pièces")
        assertThat(migratedDefault.monthlyPresetQty).isEqualTo(2)

        val migratedCustom = loaded[1]
        assertThat(migratedCustom.name).isEqualTo("Compresses Mölnlycke perso")
        assertThat(migratedCustom.detail).isEqualTo("Boîte de 20")
        assertThat(migratedCustom.monthlyPresetQty).isEqualTo(0)

        // Verify persisted JSON was migrated on disk
        val reloaded = StomaProductRepository(context).products.value
        assertThat(reloaded).isEqualTo(loaded)
    }

    @Test
    fun orderHistoryRepository_savesSortsPersistsAndDeletesOrders() {
        val repo = StomaOrderHistoryRepository(context)
        assertThat(repo.ordersFlow.value).isEmpty()

        val first = repo.saveOrder(
            patientName = "Sophie Martin",
            notes = null,
            items = listOf(
                StomaOrderItemRecord(
                    productId = "coloplast_114229",
                    ref = "114229",
                    name = "Poche fermée grise",
                    detail = "Anneau 60mm / Maxi 530ml",
                    gtin = "5701780170109",
                    quantity = 2
                ),
                StomaOrderItemRecord(
                    productId = "brava_120701",
                    ref = "120701",
                    name = "Demi-lune Standard",
                    detail = "Standard (Boîte de 20)",
                    gtin = "5708932598583",
                    quantity = 1
                )
            )
        )
        assertThat(first.totalBoxes).isEqualTo(3)
        assertThat(first.notes).isNull()

        Thread.sleep(5)

        val second = repo.saveOrder(
            patientName = "Sophie Martin",
            notes = "Livraison pharmacie",
            items = listOf(
                StomaOrderItemRecord(
                    productId = "molnlycke_mesoft",
                    ref = "---",
                    name = "Compresses non stériles Mölnlycke",
                    detail = "Mesoft 10X10",
                    gtin = "7323190152223",
                    quantity = 4
                )
            )
        )
        assertThat(second.totalBoxes).isEqualTo(4)

        // Newest-first ordering
        assertThat(repo.ordersFlow.value.map { it.id }).containsExactly(second.id, first.id).inOrder()

        // Persisted across fresh repository instance
        val reloadedRepo = StomaOrderHistoryRepository(context)
        val reloadedOrders = reloadedRepo.ordersFlow.value
        assertThat(reloadedOrders).hasSize(2)
        assertThat(reloadedOrders[0].id).isEqualTo(second.id)
        assertThat(reloadedOrders[0].notes).isEqualTo("Livraison pharmacie")
        assertThat(reloadedOrders[1].id).isEqualTo(first.id)
        assertThat(reloadedOrders[1].notes).isNull()

        reloadedRepo.deleteOrder(second.id)
        assertThat(reloadedRepo.ordersFlow.value.map { it.id }).containsExactly(first.id)
    }

    @Test
    fun customPreset_persistsAcrossRepositories() {
        val historyRepo = StomaOrderHistoryRepository(context)
        val productRepo = StomaProductRepository(context)

        assertThat(historyRepo.hasCustomPreset()).isFalse()
        assertThat(productRepo.hasCustomPreset()).isFalse()
        assertThat(productRepo.getEffectiveMonthlyPreset()).isEqualTo(StomaCatalog.getDefaultMonthlyPreset())

        historyRepo.saveCustomPreset(
            mapOf(
                "coloplast_114229" to 3,
                "coloplast_197329" to 1,
                "brava_120701" to 0 // zero quantities are filtered out
            )
        )

        assertThat(historyRepo.hasCustomPreset()).isTrue()
        assertThat(productRepo.hasCustomPreset()).isTrue()
        assertThat(historyRepo.getCustomPreset()).containsExactly("coloplast_114229", 3, "coloplast_197329", 1)
        assertThat(productRepo.loadCustomPreset()).containsExactly("coloplast_114229", 3, "coloplast_197329", 1)
        assertThat(productRepo.getEffectiveMonthlyPreset()).containsExactly("coloplast_114229", 3, "coloplast_197329", 1)

        historyRepo.clearCustomPreset()
        assertThat(historyRepo.hasCustomPreset()).isFalse()
        assertThat(productRepo.getEffectiveMonthlyPreset()).isEqualTo(StomaCatalog.getDefaultMonthlyPreset())
    }
}
