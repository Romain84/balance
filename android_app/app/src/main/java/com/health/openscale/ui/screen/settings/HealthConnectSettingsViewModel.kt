/*
 * openScale
 * Copyright (C) 2026 openScale contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.health.openscale.ui.screen.settings

import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.health.openscale.core.database.HealthConnectSyncCounts
import com.health.openscale.core.healthconnect.HealthConnectSettings
import com.health.openscale.core.healthconnect.HealthConnectSyncManager
import com.health.openscale.core.healthconnect.HealthConnectSyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HealthConnectSettingsState(
    val settings: HealthConnectSettings = HealthConnectSettings(),
    val counts: HealthConnectSyncCounts = HealthConnectSyncCounts(0, 0, 0, 0, 0),
    val sdkStatus: Int = HealthConnectClient.SDK_UNAVAILABLE,
    val hasPermissions: Boolean = false,
    val hasAnyPermission: Boolean = false
)

@HiltViewModel
class HealthConnectSettingsViewModel @Inject constructor(
    private val syncManager: HealthConnectSyncManager,
    private val scheduler: HealthConnectSyncScheduler
) : ViewModel() {
    private val sdkStatus = MutableStateFlow(HealthConnectClient.SDK_UNAVAILABLE)
    private val permissions = MutableStateFlow(false)
    private val anyPermission = MutableStateFlow(false)

    val state: StateFlow<HealthConnectSettingsState> = combine(
        syncManager.settings,
        syncManager.counts,
        sdkStatus,
        permissions,
        anyPermission
    ) { settings, counts, sdk, granted, anyGranted ->
        HealthConnectSettingsState(settings, counts, sdk, granted, anyGranted)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HealthConnectSettingsState())

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val sdk = syncManager.availability()
            val granted = syncManager.grantedPermissions()
            val hasAll = granted.containsAll(HealthConnectSyncManager.REQUIRED_PERMISSIONS)
            val hasAny = granted.any { it in HealthConnectSyncManager.REQUIRED_PERMISSIONS }
            val permissionsChanged = permissions.value != hasAll || anyPermission.value != hasAny
            sdkStatus.value = sdk
            permissions.value = hasAll
            anyPermission.value = hasAny
            syncManager.refreshPermissions(granted)

            val settings = syncManager.settings.first()
            if (settings.enabled) {
                if (hasAny) {
                    scheduler.scheduleRecoverySync()
                    if (permissionsChanged) {
                        // The history scope chosen on enable decides what is sent; a permission
                        // change must not silently turn into a forced full re-upload.
                        scheduler.enqueue(fullHistory = false)
                    }
                } else if (sdk == HealthConnectClient.SDK_AVAILABLE) {
                    scheduler.cancelAll()
                }
            }
        }
    }

    fun disable() {
        viewModelScope.launch {
            syncManager.setEnabled(false)
            scheduler.cancelAll()
        }
    }

    fun onPermissionResult(granted: Set<String>, fullHistoryOnEnable: Boolean = true) {
        viewModelScope.launch {
            val enabled = granted.containsAll(HealthConnectSyncManager.REQUIRED_PERMISSIONS)
            val anyGranted = granted.any { it in HealthConnectSyncManager.REQUIRED_PERMISSIONS }
            syncManager.setEnabled(anyGranted)
            permissions.value = enabled
            anyPermission.value = anyGranted
            if (anyGranted) {
                syncManager.setHistoryScope(includeHistory = fullHistoryOnEnable)
                scheduler.scheduleRecoverySync()
                scheduler.enqueue(fullHistory = fullHistoryOnEnable)
            } else {
                scheduler.cancelAll()
            }
        }
    }

    fun requestPermissionsAgain(granted: Set<String>) {
        viewModelScope.launch {
            permissions.value = granted.containsAll(HealthConnectSyncManager.REQUIRED_PERMISSIONS)
            anyPermission.value = granted.any { it in HealthConnectSyncManager.REQUIRED_PERMISSIONS }
            if (anyPermission.value) {
                syncManager.refreshPermissions(granted)
                if (state.value.settings.enabled) {
                    scheduler.scheduleRecoverySync()
                    scheduler.enqueue(fullHistory = false)
                }
            }
        }
    }

    fun syncNow(fullHistory: Boolean) {
        viewModelScope.launch { scheduler.enqueue(fullHistory = fullHistory) }
    }
}
