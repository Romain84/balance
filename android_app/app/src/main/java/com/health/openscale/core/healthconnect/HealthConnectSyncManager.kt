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
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BasalBodyTemperatureRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import com.health.openscale.core.data.HealthConnectSyncState
import com.health.openscale.core.data.User
import com.health.openscale.core.database.DatabaseRepository
import com.health.openscale.core.database.HealthConnectSyncDao
import com.health.openscale.core.database.HealthConnectSyncCounts
import com.health.openscale.core.model.MeasurementWithValues
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.healthConnectDataStore by preferencesDataStore(name = "health_connect_sync")

data class HealthConnectSettings(
    val enabled: Boolean = false,
    val lastSyncAt: Long? = null,
    val progress: Int = 0,
    val total: Int = 0,
    val lastMessage: String? = null
)

data class HealthConnectSyncReport(
    val processed: Int = 0,
    val synced: Int = 0,
    val failed: Int = 0,
    val waitingForPermissions: Boolean = false,
    val unavailable: Boolean = false,
    val retryable: Boolean = false
)

@Singleton
class HealthConnectSyncManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val databaseRepository: DatabaseRepository,
    private val syncDao: HealthConnectSyncDao
) {
    private val syncMutex = Mutex()
    private val enabledKey = booleanPreferencesKey("enabled")
    private val lastSyncKey = longPreferencesKey("last_sync_at")
    private val progressKey = intPreferencesKey("progress")
    private val totalKey = intPreferencesKey("total")
    private val messageKey = stringPreferencesKey("last_message")
    private val historyCutoffKey = intPreferencesKey("history_cutoff_id")

    val settings: Flow<HealthConnectSettings> = context.healthConnectDataStore.data.map { values ->
        val explicit = values[enabledKey]
        val isAutoEnabled = explicit ?: (availability() == HealthConnectClient.SDK_AVAILABLE && grantedPermissions().any { it in WRITE_PERMISSIONS })
        HealthConnectSettings(
            enabled = isAutoEnabled,
            lastSyncAt = values[lastSyncKey],
            progress = values[progressKey] ?: 0,
            total = values[totalKey] ?: 0,
            lastMessage = values[messageKey]
        )
    }

    val counts: Flow<HealthConnectSyncCounts> = syncDao.observeCounts()

    fun availability(): Int = HealthConnectClient.getSdkStatus(context)

    suspend fun grantedPermissions(): Set<String> {
        if (availability() != HealthConnectClient.SDK_AVAILABLE) return emptySet()
        return try {
            HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptySet()
        }
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.healthConnectDataStore.edit { it[enabledKey] = enabled }
        if (enabled) {
            context.healthConnectDataStore.edit { it.remove(messageKey) }
        }
    }

    /**
     * Records which measurements form the existing history when synchronization is switched on.
     * With [includeHistory] everything is eligible; otherwise only measurements created from now
     * on are sent, and older ones are reported as excluded until a full synchronization is run.
     */
    suspend fun setHistoryScope(includeHistory: Boolean) {
        val cutoff = if (includeHistory) null else syncDao.maxMeasurementId()
        context.healthConnectDataStore.edit {
            if (cutoff == null) it.remove(historyCutoffKey) else it[historyCutoffKey] = cutoff
        }
    }

    private suspend fun historyCutoffId(): Int? =
        context.healthConnectDataStore.data.map { it[historyCutoffKey] }.first()

    suspend fun isEnabled(): Boolean {
        val stored = context.healthConnectDataStore.data.map { it[enabledKey] }.first()
        if (stored != null) return stored
        return try {
            if (availability() != HealthConnectClient.SDK_AVAILABLE) return false
            grantedPermissions().any { it in WRITE_PERMISSIONS }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun refreshPermissions(granted: Set<String>) {
        if (granted.containsAll(REQUIRED_PERMISSIONS)) {
            context.healthConnectDataStore.edit { it.remove(messageKey) }
        }
    }

    suspend fun sync(fullHistory: Boolean, attempt: Int = 0): HealthConnectSyncReport =
        syncMutex.withLock { syncInternal(fullHistory, attempt, onlyMeasurementId = null) }

    suspend fun syncMeasurement(measurementId: Int, attempt: Int = 0): HealthConnectSyncReport =
        syncMutex.withLock {
            syncInternal(fullHistory = false, attempt = attempt, onlyMeasurementId = measurementId)
        }

    suspend fun recordUnexpectedFailure(message: String) {
        writeMessage(message.take(240))
    }

    /**
     * Removes the Health Connect records written for measurements that were deleted in openScale.
     * The rows are already gone locally, so the records are addressed by their stable
     * clientRecordIds. Returns true when a retry could succeed later.
     */
    suspend fun deleteMeasurements(userId: Int, measurementIds: List<Int>): Boolean =
        syncMutex.withLock {
            if (measurementIds.isEmpty() || !isEnabled()) return@withLock false
            if (availability() != HealthConnectClient.SDK_AVAILABLE) return@withLock true
            val client = HealthConnectClient.getOrCreate(context)
            val granted = try {
                client.permissionController.getGrantedPermissions()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return@withLock true
            }
            var retry = false
            for ((suffix, type) in HealthConnectRecordMapper.RECORD_TYPES) {
                if (HealthPermission.getWritePermission(type) !in granted) continue
                measurementIds.chunked(DELETE_BATCH_SIZE).forEach { batch ->
                    val ids = batch.map { HealthConnectRecordMapper.clientRecordId(userId, it, suffix) }
                    try {
                        client.deleteRecords(type, emptyList(), ids)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        if (error !is IllegalArgumentException && error !is SecurityException) retry = true
                    }
                }
            }
            retry
        }

    /**
     * Deletes the records of a measurement whose value disappeared since the previous delivery
     * (e.g. body fat removed while editing), so Health Connect never keeps a stale value.
     */
    private suspend fun deleteStaleRecords(
        client: HealthConnectClient,
        granted: Set<String>,
        item: MeasurementWithValues,
        written: List<androidx.health.connect.client.records.Record>
    ) {
        val current = written.mapNotNull { HealthConnectRecordMapper.typeSuffixOf(it) }.toSet()
        for ((suffix, type) in HealthConnectRecordMapper.RECORD_TYPES) {
            if (suffix in current || HealthPermission.getWritePermission(type) !in granted) continue
            val id = HealthConnectRecordMapper.clientRecordId(
                item.measurement.userId, item.measurement.id, suffix
            )
            try {
                client.deleteRecords(type, emptyList(), listOf(id))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Best effort: a missing record or a transient error must not fail the delivery.
            }
        }
    }

    private suspend fun syncInternal(
        fullHistory: Boolean,
        attempt: Int,
        onlyMeasurementId: Int?
    ): HealthConnectSyncReport {
        val isEnabled = isEnabled()
        if (!isEnabled) return HealthConnectSyncReport()

        if (availability() != HealthConnectClient.SDK_AVAILABLE) {
            writeMessage("Health Connect is unavailable on this device.")
            return HealthConnectSyncReport(unavailable = true, retryable = true)
        }

        val client = HealthConnectClient.getOrCreate(context)
        val granted = try {
            client.permissionController.getGrantedPermissions()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            writeMessage(error.message ?: "Could not check Health Connect permissions.")
            return HealthConnectSyncReport(waitingForPermissions = true)
        }
        var waitingForPermissions = granted.none { it in WRITE_PERMISSIONS }
        if (waitingForPermissions) writeMessage("Health Connect write permissions are missing.")

        val measurements = if (onlyMeasurementId == null) {
            databaseRepository.getAllMeasurementsWithValues()
        } else {
            listOfNotNull(databaseRepository.getMeasurementWithValuesById(onlyMeasurementId).first())
        }
        val usersById = if (onlyMeasurementId == null) {
            databaseRepository.getAllUsers().first().associateBy { it.id }
        } else {
            val userId = measurements.firstOrNull()?.measurement?.userId
            if (userId == null) emptyMap()
            else databaseRepository.getUserById(userId).first()?.let { mapOf(it.id to it) } ?: emptyMap()
        }
        context.healthConnectDataStore.edit {
            it[progressKey] = 0
            it[totalKey] = measurements.size
            it.remove(messageKey)
        }

        var processed = 0
        var synced = 0
        var failed = 0
        var retryable = false
        val historyCutoffId = historyCutoffId()
        for (item in measurements) {
            val measurement = item.measurement
            val previous = syncDao.get(measurement.id)
            if (HealthConnectRecordMapper.isExcludedHistory(
                    measurementId = measurement.id,
                    historyCutoffId = historyCutoffId,
                    previousStatus = previous?.status,
                    fullHistory = fullHistory
                )
            ) {
                if (previous == null) {
                    syncDao.save(
                        HealthConnectSyncState(
                            measurementId = measurement.id,
                            status = HealthConnectSyncState.STATUS_SKIPPED,
                            lastSyncedRevision = measurement.revision,
                            lastAttemptAt = System.currentTimeMillis()
                        )
                    )
                }
                processed++
                updateProgress(processed, measurements.size)
                continue
            }
            val user = usersById[measurement.userId]
            val payloadHash = payloadHash(item, user)
            val alreadyCurrent = previous != null && previous.payloadHash == payloadHash &&
                previous.status in setOf(
                    HealthConnectSyncState.STATUS_SYNCED,
                    HealthConnectSyncState.STATUS_UNSUPPORTED,
                    HealthConnectSyncState.STATUS_INVALID
                )
            if (!fullHistory && alreadyCurrent) {
                if (previous.lastSyncedRevision != measurement.revision) {
                    syncDao.save(previous.copy(lastSyncedRevision = measurement.revision))
                }
                processed++
                updateProgress(processed, measurements.size)
                continue
            }

            val recordVersion = if (previous?.payloadHash == payloadHash) {
                maxOf(measurement.revision, previous.clientRecordVersion)
            } else {
                maxOf(measurement.revision, (previous?.clientRecordVersion ?: 0L) + 1L)
            }.coerceAtLeast(1L)
            val records = recordsFor(item, user, recordVersion)
            if (records.isEmpty()) {
                val invalid = hasCompatibleInput(item, user)
                syncDao.save(
                    HealthConnectSyncState(
                        measurementId = measurement.id,
                        status = if (invalid) HealthConnectSyncState.STATUS_INVALID else HealthConnectSyncState.STATUS_UNSUPPORTED,
                        lastSyncedRevision = if (invalid) (previous?.lastSyncedRevision ?: 0L) else measurement.revision,
                        lastSyncedAt = previous?.lastSyncedAt,
                        lastAttemptAt = System.currentTimeMillis(),
                        lastError = if (invalid) "Compatible data is incomplete or invalid." else null,
                        syncedRecords = 0,
                        payloadHash = payloadHash,
                        clientRecordVersion = recordVersion
                    )
                )
                processed++
                updateProgress(processed, measurements.size)
                continue
            }

            val writableRecords = records.filter { hasWritePermission(granted, it) }
            val missingPermissions = writableRecords.size != records.size
            if (missingPermissions) waitingForPermissions = true
            if (writableRecords.isEmpty()) {
                syncDao.save(
                    HealthConnectSyncState(
                        measurementId = measurement.id,
                        status = HealthConnectSyncState.STATUS_PENDING,
                        lastSyncedRevision = previous?.lastSyncedRevision ?: 0L,
                        lastSyncedAt = previous?.lastSyncedAt,
                        lastAttemptAt = System.currentTimeMillis(),
                        lastError = "Health Connect write permissions are missing for these data types.",
                        syncedRecords = previous?.syncedRecords ?: 0,
                        payloadHash = payloadHash,
                        clientRecordVersion = recordVersion
                    )
                )
                processed++
                updateProgress(processed, measurements.size)
                continue
            }

            syncDao.save(
                HealthConnectSyncState(
                    measurementId = measurement.id,
                    status = HealthConnectSyncState.STATUS_PENDING,
                    lastSyncedRevision = previous?.lastSyncedRevision ?: 0L,
                    lastSyncedAt = previous?.lastSyncedAt,
                    lastAttemptAt = System.currentTimeMillis(),
                    syncedRecords = previous?.syncedRecords ?: 0,
                    payloadHash = payloadHash,
                    clientRecordVersion = recordVersion
                )
            )
            try {
                client.insertRecords(writableRecords)
                deleteStaleRecords(client, granted, item, records)
                val completedAt = System.currentTimeMillis()
                syncDao.save(
                    HealthConnectSyncState(
                        measurementId = measurement.id,
                        status = HealthConnectSyncState.STATUS_SYNCED,
                        lastSyncedRevision = measurement.revision,
                        lastSyncedAt = completedAt,
                        lastAttemptAt = completedAt,
                        lastError = if (missingPermissions) "Certaines autorisations optionnelles de données ne sont pas accordées." else null,
                        syncedRecords = writableRecords.size,
                        payloadHash = payloadHash,
                        clientRecordVersion = recordVersion
                    )
                )
                synced++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                var written = 0
                var errorMessage = error.message?.take(240) ?: error.javaClass.simpleName
                var permissionRevoked = error is SecurityException
                var measurementRetryable = false
                if (!permissionRevoked) {
                    // Retry each record separately so one invalid field cannot block valid values.
                    for (record in writableRecords) {
                        try {
                            client.insertRecords(listOf(record))
                            written++
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (recordError: Exception) {
                            errorMessage = if (recordError is SecurityException) {
                                "Health Connect permission was revoked."
                            } else {
                                recordError.message?.take(240) ?: recordError.javaClass.simpleName
                            }
                            if (recordError is SecurityException) {
                                permissionRevoked = true
                                break
                            }
                            if (recordError !is IllegalArgumentException) {
                                measurementRetryable = true
                                retryable = true
                            }
                        }
                    }
                } else {
                    errorMessage = "Health Connect permission was revoked."
                }
                if (written == writableRecords.size) {
                    val partial = missingPermissions
                    val completedAt = System.currentTimeMillis()
                    syncDao.save(
                        HealthConnectSyncState(
                            measurementId = measurement.id,
                            status = if (partial) HealthConnectSyncState.STATUS_PENDING else HealthConnectSyncState.STATUS_SYNCED,
                            lastSyncedRevision = if (partial) (previous?.lastSyncedRevision ?: 0L) else measurement.revision,
                            lastSyncedAt = if (partial) previous?.lastSyncedAt else completedAt,
                            lastAttemptAt = completedAt,
                            lastError = if (partial) "Health Connect write permissions are missing for some data types." else null,
                            syncedRecords = written,
                            payloadHash = payloadHash,
                            clientRecordVersion = recordVersion
                        )
                    )
                    if (!partial) synced++
                } else {
                    if (permissionRevoked) waitingForPermissions = true
                    val nextStatus = when {
                        measurementRetryable -> HealthConnectSyncState.STATUS_ERROR
                        permissionRevoked || missingPermissions -> HealthConnectSyncState.STATUS_PENDING
                        else -> HealthConnectSyncState.STATUS_INVALID
                    }
                    val attemptedAt = System.currentTimeMillis()
                    syncDao.save(
                        HealthConnectSyncState(
                            measurementId = measurement.id,
                            status = nextStatus,
                            lastSyncedRevision = if (nextStatus == HealthConnectSyncState.STATUS_PENDING) {
                                previous?.lastSyncedRevision ?: 0L
                            } else {
                                measurement.revision
                            },
                            lastSyncedAt = previous?.lastSyncedAt,
                            lastAttemptAt = attemptedAt,
                            lastError = if (nextStatus == HealthConnectSyncState.STATUS_PENDING) {
                                "Health Connect write permissions are missing."
                            } else {
                                errorMessage
                            },
                            syncedRecords = written,
                            payloadHash = payloadHash,
                            clientRecordVersion = recordVersion
                        )
                    )
                    writeMessage(errorMessage)
                    if (nextStatus != HealthConnectSyncState.STATUS_PENDING) failed++
                }
                if (permissionRevoked) {
                    processed++
                    updateProgress(processed, measurements.size)
                    break
                }
            }
            processed++
            updateProgress(processed, measurements.size)
        }

        if (measurements.isNotEmpty() && processed == measurements.size && failed == 0) {
            context.healthConnectDataStore.edit {
                it[lastSyncKey] = System.currentTimeMillis()
                if (!waitingForPermissions) it.remove(messageKey)
            }
        }
        if (waitingForPermissions) {
            writeMessage(
                if (failed > 0) "Some writes failed and some Health Connect permissions are missing."
                else "Some Health Connect write permissions are missing."
            )
        }
        if (attempt >= MAX_RETRIES) retryable = false
        return HealthConnectSyncReport(
            processed = processed,
            synced = synced,
            failed = failed,
            waitingForPermissions = waitingForPermissions,
            retryable = retryable
        )
    }

    private fun hasWritePermission(
        granted: Set<String>,
        record: androidx.health.connect.client.records.Record
    ): Boolean {
        val permission = when (record) {
            is WeightRecord -> HealthPermission.getWritePermission(WeightRecord::class)
            is HeightRecord -> HealthPermission.getWritePermission(HeightRecord::class)
            is BodyFatRecord -> HealthPermission.getWritePermission(BodyFatRecord::class)
            is BodyWaterMassRecord -> HealthPermission.getWritePermission(BodyWaterMassRecord::class)
            is LeanBodyMassRecord -> HealthPermission.getWritePermission(LeanBodyMassRecord::class)
            is BoneMassRecord -> HealthPermission.getWritePermission(BoneMassRecord::class)
            is BasalMetabolicRateRecord -> HealthPermission.getWritePermission(BasalMetabolicRateRecord::class)
            else -> return false
        }
        return permission in granted
    }

    private suspend fun updateProgress(done: Int, total: Int) {
        context.healthConnectDataStore.edit {
            it[progressKey] = done
            it[totalKey] = total
        }
    }

    private suspend fun writeMessage(message: String) {
        context.healthConnectDataStore.edit { it[messageKey] = message }
    }

    private fun payloadHash(item: MeasurementWithValues, user: User?): String =
        HealthConnectRecordMapper.payloadHash(item, user)

    private fun hasCompatibleInput(item: MeasurementWithValues, user: User?): Boolean =
        HealthConnectRecordMapper.hasCompatibleInput(item, user)

    private fun recordsFor(
        item: MeasurementWithValues,
        user: User?,
        recordVersion: Long
    ): List<androidx.health.connect.client.records.Record> =
        HealthConnectRecordMapper.recordsFor(item, user, recordVersion)

    companion object {
        private const val MAX_RETRIES = 8
        private const val DELETE_BATCH_SIZE = 100

        val WRITE_PERMISSIONS = setOf(
            HealthPermission.getWritePermission(WeightRecord::class),
            HealthPermission.getWritePermission(HeightRecord::class),
            HealthPermission.getWritePermission(BodyFatRecord::class),
            HealthPermission.getWritePermission(BodyWaterMassRecord::class),
            HealthPermission.getWritePermission(LeanBodyMassRecord::class),
            HealthPermission.getWritePermission(BoneMassRecord::class),
            HealthPermission.getWritePermission(BasalMetabolicRateRecord::class),
            HealthPermission.getWritePermission(HydrationRecord::class)
        )

        val READ_PERMISSIONS = setOf(
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(BasalBodyTemperatureRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(HydrationRecord::class),
            HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
            HealthPermission.getReadPermission(OxygenSaturationRecord::class)
        )

        val ALL_PERMISSIONS = WRITE_PERMISSIONS + READ_PERMISSIONS

        val REQUIRED_PERMISSIONS = ALL_PERMISSIONS
    }
}
