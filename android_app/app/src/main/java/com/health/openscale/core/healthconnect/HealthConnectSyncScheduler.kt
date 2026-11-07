/*
 * openScale
 * Copyright (C) 2026 openScale contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.health.openscale.core.healthconnect

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectSyncScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun enqueue(fullHistory: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<HealthConnectSyncWorker>()
            .setInputData(
                Data.Builder()
                    .putBoolean(HealthConnectSyncWorker.KEY_FULL_HISTORY, fullHistory)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    fun enqueueMeasurement(measurementId: Int) {
        val request = OneTimeWorkRequestBuilder<HealthConnectSyncWorker>()
            .setInputData(
                Data.Builder()
                    .putInt(HealthConnectSyncWorker.KEY_MEASUREMENT_ID, measurementId)
                    .build()
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    /**
     * Queues removal of the Health Connect records of deleted measurements. Ids are split into
     * small batches so each request stays well below WorkManager's 10 KB input limit.
     */
    fun enqueueDeletion(userId: Int, measurementIds: Collection<Int>) {
        if (measurementIds.isEmpty()) return
        val workManager = WorkManager.getInstance(context)
        measurementIds.chunked(DELETION_CHUNK_SIZE).forEach { chunk ->
            val request = OneTimeWorkRequestBuilder<HealthConnectSyncWorker>()
                .setInputData(
                    Data.Builder()
                        .putInt(HealthConnectSyncWorker.KEY_DELETED_USER_ID, userId)
                        .putIntArray(HealthConnectSyncWorker.KEY_DELETED_MEASUREMENT_IDS, chunk.toIntArray())
                        .build()
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }

    fun scheduleRecoverySync() {
        val request = PeriodicWorkRequestBuilder<HealthConnectSyncWorker>(12, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancelRecoverySync() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PERIODIC_WORK_NAME)
    }

    fun cancelAll() {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
        workManager.cancelUniqueWork(UNIQUE_PERIODIC_WORK_NAME)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "health_connect_measurement_sync"
        const val UNIQUE_PERIODIC_WORK_NAME = "health_connect_recovery_sync"
        private const val DELETION_CHUNK_SIZE = 400
    }
}
