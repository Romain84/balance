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

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.compose.runtime.collectAsState
import com.health.openscale.R
import com.health.openscale.core.healthconnect.HealthConnectSyncManager
import com.health.openscale.ui.shared.SharedViewModel
import android.content.Intent
import java.text.DateFormat
import java.util.Date

@Composable
fun HealthConnectSettingsScreen(
    sharedViewModel: SharedViewModel,
    viewModel: HealthConnectSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var fullHistory by rememberSaveable { mutableStateOf(state.settings.lastSyncAt == null) }
    val context = LocalContext.current
    val permissionsLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (!state.settings.enabled) {
            viewModel.onPermissionResult(granted, fullHistoryOnEnable = fullHistory)
        } else {
            viewModel.requestPermissionsAgain(granted)
        }
    }
    val manageAccessLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refresh()
    }

    val title = stringResource(R.string.health_connect_title)
    LaunchedEffect(Unit) {
        sharedViewModel.setTopBarTitle(title)
        sharedViewModel.setTopBarActions(emptyList())
        viewModel.refresh()
    }
    LaunchedEffect(state.settings.lastSyncAt) {
        if (state.settings.lastSyncAt != null) fullHistory = false
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.health_connect_enable), style = MaterialTheme.typography.titleMedium)
                    Text(
                        connectionStatus(state.sdkStatus, state.hasPermissions, state.hasAnyPermission),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Switch(
                    checked = state.settings.enabled,
                    enabled = state.sdkStatus == HealthConnectClient.SDK_AVAILABLE,
                    onCheckedChange = { enabled ->
                        if (enabled) permissionsLauncher.launch(HealthConnectSyncManager.REQUIRED_PERMISSIONS)
                        else viewModel.disable()
                    }
                )
            }
        }

        if (state.sdkStatus == HealthConnectClient.SDK_AVAILABLE) {
            if (!state.hasPermissions) {
                Button(onClick = { permissionsLauncher.launch(HealthConnectSyncManager.REQUIRED_PERMISSIONS) }) {
                    Text(stringResource(R.string.health_connect_grant_permissions))
                }
            }
            Button(
                onClick = {
                    manageAccessLauncher.launch(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
                }
            ) {
                Text(stringResource(R.string.health_connect_manage_access))
            }
        }

        Card {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.health_connect_sync_status), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.health_connect_synced_count, state.counts.synced))
                Text(stringResource(R.string.health_connect_pending_count, state.counts.pending))
                Text(stringResource(R.string.health_connect_failed_count, state.counts.failed))
                Text(stringResource(R.string.health_connect_unsupported_count, state.counts.unsupported))
                if (state.counts.skipped > 0) {
                    Text(stringResource(R.string.health_connect_skipped_count, state.counts.skipped))
                }
                val lastSyncText = state.settings.lastSyncAt?.let {
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))
                } ?: stringResource(R.string.health_connect_never_synced)
                Text(stringResource(R.string.health_connect_last_sync, lastSyncText))
                if (state.settings.total > 0 && state.settings.progress < state.settings.total) {
                    LinearProgressIndicator(
                        progress = { state.settings.progress.toFloat() / state.settings.total.toFloat() },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(stringResource(R.string.health_connect_progress, state.settings.progress, state.settings.total))
                }
                state.settings.lastMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = fullHistory, onCheckedChange = { fullHistory = it })
            Text(stringResource(R.string.health_connect_full_history))
        }
        Button(
            onClick = { viewModel.syncNow(fullHistory) },
            enabled = state.settings.enabled && state.hasAnyPermission
        ) {
            Text(stringResource(R.string.health_connect_sync_now))
        }

        Text(stringResource(R.string.health_connect_supported_types), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.health_connect_privacy_rationale), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun connectionStatus(
    sdkStatus: Int,
    hasPermissions: Boolean,
    hasAnyPermission: Boolean
): String = when {
    sdkStatus == HealthConnectClient.SDK_AVAILABLE && hasPermissions -> stringResource(R.string.health_connect_status_connected)
    sdkStatus == HealthConnectClient.SDK_AVAILABLE && hasAnyPermission -> stringResource(R.string.health_connect_status_partial)
    sdkStatus == HealthConnectClient.SDK_AVAILABLE -> stringResource(R.string.health_connect_status_permissions)
    sdkStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> stringResource(R.string.health_connect_status_update)
    else -> stringResource(R.string.health_connect_status_unavailable)
}
