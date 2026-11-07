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
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

@HiltWorker
class HealthConnectSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncManager: HealthConnectSyncManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val deletedIds = inputData.getIntArray(KEY_DELETED_MEASUREMENT_IDS)
            if (deletedIds != null) {
                val userId = inputData.getInt(KEY_DELETED_USER_ID, 0)
                val retry = syncManager.deleteMeasurements(userId, deletedIds.toList())
                return if (retry && runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
            }
            val measurementId = inputData.getInt(KEY_MEASUREMENT_ID, 0)
            val report = if (measurementId > 0) {
                syncManager.syncMeasurement(measurementId, attempt = runAttemptCount)
            } else {
                syncManager.sync(
                    fullHistory = inputData.getBoolean(KEY_FULL_HISTORY, false),
                    attempt = runAttemptCount
                )
            }
            if (report.retryable && runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            try {
                syncManager.recordUnexpectedFailure(error.message ?: error.javaClass.simpleName)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The work result below still asks WorkManager to retry the delivery.
            }
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_FULL_HISTORY = "full_history"
        const val KEY_MEASUREMENT_ID = "measurement_id"
        const val KEY_DELETED_USER_ID = "deleted_user_id"
        const val KEY_DELETED_MEASUREMENT_IDS = "deleted_measurement_ids"
        private const val MAX_RETRIES = 8
    }
}
