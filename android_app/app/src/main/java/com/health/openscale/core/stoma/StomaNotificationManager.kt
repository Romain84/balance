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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.health.openscale.MainActivity
import com.health.openscale.R

object StomaNotificationManager {

    const val CHANNEL_ID = "stoma_hydration_channel"
    const val NOTIFICATION_ID = 2026

    const val ACTION_ADD_WATER_250 = "com.health.openscale.stoma.ACTION_ADD_WATER_250"
    const val ACTION_ADD_WATER_500 = "com.health.openscale.stoma.ACTION_ADD_WATER_500"
    const val ACTION_LOG_POUCH = "com.health.openscale.stoma.ACTION_LOG_POUCH"
    const val ACTION_LOG_SUPPORT = "com.health.openscale.stoma.ACTION_LOG_SUPPORT"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Hydratation & Stomie en direct"
            val descriptionText = "Bandeau permanent sur l'écran de verrouillage pour le suivi de l'eau et de la stomie"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showOrUpdateNotification(context: Context, summary: DailyStomaSummary) {
        createNotificationChannel(context)

        val targetMl = summary.targetWaterMl.coerceAtLeast(1)
        val currentMl = summary.totalWaterMl
        val pct = ((currentMl.toDouble() / targetMl) * 100).toInt().coerceIn(0, 100)

        val statusBadge = when (summary.clinicalStatus) {
            ClinicalHydrationStatus.OPTIMAL -> "🟢 Équilibre"
            ClinicalHydrationStatus.WARNING -> "🟠 Vigilance"
            ClinicalHydrationStatus.CRITICAL -> "🔴 Alerte"
        }

        val supportText = when {
            summary.lastSupportChangeTimestamp == null -> "Non renseigné"
            summary.daysSinceLastSupportChange == 0 -> "Changé aujourd'hui"
            summary.daysSinceLastSupportChange == 1 -> "Changé hier (1j)"
            else -> "Il y a ${summary.daysSinceLastSupportChange} jours"
        }

        // Tap on notification -> open app
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            100,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action PendingIntents
        val pWater250 = createActionPendingIntent(context, ACTION_ADD_WATER_250, 101)
        val pWater500 = createActionPendingIntent(context, ACTION_ADD_WATER_500, 102)
        val pPouch = createActionPendingIntent(context, ACTION_LOG_POUCH, 103)
        val pSupport = createActionPendingIntent(context, ACTION_LOG_SUPPORT, 104)

        val contentTitle = "Hydratation & Stomie • $statusBadge"
        val contentText = "💧 ${String.format(java.util.Locale.FRANCE, "%.2f", currentMl / 1000.0)} L / ${String.format(java.util.Locale.FRANCE, "%.2f", targetMl / 1000.0)} L ($pct%) • ${summary.pouchChangeCount} poche(s)"

        val bigText = buildString {
            append("💧 Eau bue : ${String.format(java.util.Locale.FRANCE, "%.2f", currentMl / 1000.0)} L sur ${String.format(java.util.Locale.FRANCE, "%.2f", targetMl / 1000.0)} L ($pct%)\n")
            append("🚽 Poches aujourd'hui : ${summary.pouchChangeCount}\n")
            append("🛡️ Support complet : $supportText\n")
            append("💡 Conseil : ${summary.clinicalAdvice}")
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_water)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setProgress(targetMl, currentMl.coerceAtMost(targetMl), false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentPendingIntent)
            .addAction(R.drawable.ic_water, "+250 ml 💧", pWater250)
            .addAction(R.drawable.ic_water, "+500 ml 🥤", pWater500)
            .addAction(R.drawable.ic_widget_circle, "🚽 Poche", pPouch)
            .addAction(R.drawable.ic_widget_circle, "🛡️ Support", pSupport)
            .build()

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun cancelNotification(context: Context) {
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun createActionPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, StomaNotificationReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
