/*
 * openScale
 * Copyright (C) 2026 openScale contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.health.openscale.core.stoma

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class StomaNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val repository = StomaTrackingRepository(context.applicationContext)

        when (action) {
            StomaNotificationManager.ACTION_ADD_WATER_250 -> {
                repository.addWater(250)
            }
            StomaNotificationManager.ACTION_ADD_WATER_500 -> {
                repository.addWater(500)
            }
            StomaNotificationManager.ACTION_LOG_POUCH -> {
                repository.logPouchChange()
            }
            StomaNotificationManager.ACTION_LOG_SUPPORT -> {
                repository.logSupportChange()
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // If notification is disabled in preferences, don't show
                if (!repository.isNotificationEnabled()) {
                    return
                }
            }
            else -> return
        }

        if (repository.isNotificationEnabled()) {
            val summary = repository.getTodaySummary()
            StomaNotificationManager.showOrUpdateNotification(context.applicationContext, summary)
        }
    }
}
