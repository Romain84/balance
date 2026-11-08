/*
 * openScale
 * Copyright (C) 2025 olie.xdev <olie.xdeveloper@googlemail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.health.openscale.ui.screen.overview

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Link
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.WaterDrop
import com.health.openscale.ui.screen.dialog.HealthCheckinSheet
import com.health.openscale.ui.screen.dialog.MedicalReportDialog
import com.health.openscale.core.stoma.StomaNotificationManager
import com.health.openscale.core.stoma.StomaTrackingRepository
import com.health.openscale.core.stoma.StomaEventType
import com.health.openscale.core.stoma.DailyStomaSummary
import com.health.openscale.core.medication.MedicationRepository
import com.health.openscale.core.medication.MedicationIntake
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.window.core.layout.WindowSizeClass
import com.health.openscale.R
import com.health.openscale.core.data.AggregationLevel
import com.health.openscale.core.data.EvaluationState
import com.health.openscale.core.data.InputFieldType
import com.health.openscale.core.data.MeasurementType
import com.health.openscale.core.data.Trend
import com.health.openscale.core.data.UnitType
import com.health.openscale.core.data.UserGoals
import com.health.openscale.core.facade.SettingsPreferenceKeys
import com.health.openscale.core.model.AggregatedMeasurement
import com.health.openscale.core.model.MeasurementWithValues
import com.health.openscale.core.data.HealthConnectSyncState
import com.health.openscale.core.model.UserEvaluationContext
import com.health.openscale.core.model.ValueWithDifference
import com.health.openscale.core.usecase.GoalProgress
import com.health.openscale.core.utils.ConverterUtils
import com.health.openscale.core.utils.LocaleUtils
import com.health.openscale.ui.components.FullscreenImageViewer
import com.health.openscale.ui.components.LinearGauge
import com.health.openscale.ui.components.MeasurementImageThumbnail
import com.health.openscale.ui.components.RoundMeasurementIcon
import com.health.openscale.ui.navigation.Routes
import com.health.openscale.ui.screen.components.ChartSplitterHandle
import com.health.openscale.ui.screen.components.MeasurementChart
import com.health.openscale.ui.screen.components.MeasurementChartTypeFilterRow
import com.health.openscale.ui.screen.components.UserGoalChip
import com.health.openscale.ui.screen.components.provideFilterTopBarAction
import com.health.openscale.ui.screen.components.rememberAddMeasurementActionButton
import com.health.openscale.ui.screen.components.rememberBluetoothActionButton
import com.health.openscale.ui.screen.components.rememberResolvedAggregationLevel
import com.health.openscale.ui.screen.dialog.DeleteConfirmationDialog
import com.health.openscale.ui.screen.dialog.UserGoalDialog
import com.health.openscale.ui.screen.settings.BluetoothViewModel
import com.health.openscale.ui.shared.SharedViewModel
import com.health.openscale.ui.shared.TopBarAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs


// ---------------------------------------------------------------------------
// OverviewSection Tabs
// ---------------------------------------------------------------------------

enum class OverviewSection(val label: String, val emoji: String) {
    SYNTHESIS("Synthèse", "🏠"),
    HYDRATION("Hydratation & Stomie", "💧"),
    MEDICATION("Traitements", "💊"),
    MEASUREMENTS("Mesures & Balance", "⚖️")
}

// ---------------------------------------------------------------------------
// OverviewScreen
// ---------------------------------------------------------------------------

@Composable
fun OverviewScreen(
    navController: NavController,
    sharedViewModel: SharedViewModel,
    bluetoothViewModel: BluetoothViewModel,
    // When non-null the screen acts as a drill-down: shows raw measurements for this
    // period only — no chart, no aggregation toggle, no goals section.
    drillDownStartMillis: Long? = null,
    drillDownEndMillis: Long? = null,
) {
    val isDrillDown = drillDownStartMillis != null && drillDownEndMillis != null
    val useSideBySide = currentWindowAdaptiveInfoV2().windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

    val selectedUserId by sharedViewModel.selectedUserId.collectAsState()
    val context = LocalContext.current
    val resources = LocalResources.current
    val listState  = rememberLazyListState()
    val scope      = rememberCoroutineScope()
    var highlightedMeasurementId by rememberSaveable { mutableStateOf<Int?>(null) }

    // ── Splitter ──────────────────────────────────────────────────────────────
    val splitterWeight by remember(SettingsPreferenceKeys.OVERVIEW_SCREEN_CONTEXT, sharedViewModel) {
        sharedViewModel.observeSplitterWeight(SettingsPreferenceKeys.OVERVIEW_SCREEN_CONTEXT, 0.3f)
    }.collectAsState(initial = 0.3f)
    var localSplitterWeight by remember { mutableStateOf(splitterWeight) }
    // Last non-collapsed weight, restored when the user taps the handle to show the chart again.
    var lastExpandedWeight by remember { mutableStateOf(splitterWeight.takeIf { it > 0f } ?: 0.3f) }
    LaunchedEffect(splitterWeight) {
        localSplitterWeight = splitterWeight
        if (splitterWeight > 0f) lastExpandedWeight = splitterWeight
    }

    // ── Aggregation ───────────────────────────────────────────────────────────
    val activeAggregationLevel by rememberResolvedAggregationLevel(
        screenContextName = SettingsPreferenceKeys.OVERVIEW_SCREEN_CONTEXT,
        sharedViewModel   = sharedViewModel,
    )
    val isAggregated = !isDrillDown && activeAggregationLevel != AggregationLevel.NONE
    // Hoisted out of the item scope: the same rule the period bounds behind each row were
    // built from, so label and drill-down range always agree.
    val weekFields   = remember { LocaleUtils.systemWeekFields() }

    val stomaRepo = remember { StomaTrackingRepository(context) }
    val stomaSummary by stomaRepo.todaySummary.collectAsState()
    var isLockscreenNotifEnabled by remember { mutableStateOf(stomaRepo.isNotificationEnabled()) }

    var selectedSection by rememberSaveable { mutableStateOf(OverviewSection.SYNTHESIS) }
    val medRepo = remember { MedicationRepository(context) }
    var medicationIntakes by remember { mutableStateOf(medRepo.loadIntakes()) }

    val onAddWater: (Int) -> Unit = remember(stomaRepo, isLockscreenNotifEnabled) {
        { amount ->
            stomaRepo.addWater(amount)
            if (isLockscreenNotifEnabled) {
                StomaNotificationManager.showOrUpdateNotification(context, stomaRepo.getTodaySummary())
            }
        }
    }
    val onLogPouch: () -> Unit = remember(stomaRepo, isLockscreenNotifEnabled) {
        {
            stomaRepo.logPouchChange()
            if (isLockscreenNotifEnabled) {
                StomaNotificationManager.showOrUpdateNotification(context, stomaRepo.getTodaySummary())
            }
        }
    }
    val onLogSupport: () -> Unit = remember(stomaRepo, isLockscreenNotifEnabled) {
        {
            stomaRepo.logSupportChange()
            if (isLockscreenNotifEnabled) {
                StomaNotificationManager.showOrUpdateNotification(context, stomaRepo.getTodaySummary())
            }
        }
    }
    val onToggleLockscreenNotif: (Boolean) -> Unit = remember(stomaRepo) {
        { enabled ->
            isLockscreenNotifEnabled = enabled
            stomaRepo.setNotificationEnabled(enabled)
            if (enabled) {
                StomaNotificationManager.showOrUpdateNotification(context, stomaRepo.getTodaySummary())
            } else {
                StomaNotificationManager.cancelNotification(context)
            }
        }
    }

    // ── Data ──────────────────────────────────────────────────────────────────
    // Normal mode: screenFlow — cached, reacts to user/time-range/aggregation changes.
    // Drill-down mode: drillDownFlow — uncached, fixed window, always AggregationLevel.NONE.
    val overviewState by if (isDrillDown) {
        sharedViewModel.drillDownFlow(drillDownStartMillis, drillDownEndMillis)
            .collectAsStateWithLifecycle(initialValue = SharedViewModel.UiState.Loading)
    } else {
        sharedViewModel.screenFlow(SettingsPreferenceKeys.OVERVIEW_SCREEN_CONTEXT)
            .collectAsStateWithLifecycle(initialValue = SharedViewModel.UiState.Loading)
    }

    // Flat list of AggregatedMeasurement for the current state
    val aggregatedItems: List<AggregatedMeasurement> = remember(overviewState) {
        when (val s = overviewState) {
            is SharedViewModel.UiState.Success -> s.data
            else -> emptyList()
        }
    }

    // ── Top-bar actions ───────────────────────────────────────────────────────
    val bluetoothAction      = rememberBluetoothActionButton(bluetoothViewModel, sharedViewModel, navController)
    val addMeasurementAction = rememberAddMeasurementActionButton(sharedViewModel, navController)
    val timeFilterAction     = if (isDrillDown) null else provideFilterTopBarAction(
        sharedViewModel   = sharedViewModel,
        screenContextName = SettingsPreferenceKeys.OVERVIEW_SCREEN_CONTEXT,
    )

    // ── Goals + UI helpers ────────────────────────────────────────────────────
    val allMeasurementTypes  by sharedViewModel.measurementTypes.collectAsState()
    val goalDialogContextData by sharedViewModel.userGoalDialogContext.collectAsState()
    val userGoals by if (selectedUserId != null && selectedUserId != 0) {
        sharedViewModel.getAllGoalsForUser(selectedUserId!!).collectAsState(initial = emptyList())
    } else {
        remember { mutableStateOf(emptyList<UserGoals>()) }
    }
    val goalProgressState by sharedViewModel.goalProgressFlow.collectAsStateWithLifecycle()
    val progressByTypeId: Map<Int, GoalProgress> = remember(goalProgressState) {
        (goalProgressState as? SharedViewModel.UiState.Success)?.data
            ?.associateBy { it.type.id } ?: emptyMap()
    }
    val isGoalsSectionExpanded by sharedViewModel.myGoalsExpandedOverview.collectAsState(initial = true)
    val userEvalContext        by sharedViewModel.userEvaluationContext.collectAsState()
    val currentSelectedUser   by sharedViewModel.selectedUser.collectAsState()

    val activeUser = currentSelectedUser
    val activeUserId = activeUser?.id ?: 0

    val isHealthAssistantEnabled by if (activeUserId != 0) {
        sharedViewModel.isHealthAssistantEnabled(activeUserId).collectAsState(initial = false)
    } else {
        remember { mutableStateOf(false) }
    }

    val isStomaEnabled by if (activeUserId != 0) {
        sharedViewModel.isStomaTrackingEnabled(activeUserId).collectAsState(initial = true)
    } else {
        remember { mutableStateOf(true) }
    }

    val isEndoEnabled by if (activeUserId != 0) {
        sharedViewModel.isEndoTrackingEnabled(activeUserId).collectAsState(initial = true)
    } else {
        remember { mutableStateOf(true) }
    }

    val isGlutenEnabled by if (activeUserId != 0) {
        sharedViewModel.isGlutenTrackingEnabled(activeUserId).collectAsState(initial = true)
    } else {
        remember { mutableStateOf(true) }
    }

    val userCheckins by if (activeUserId != 0 && isHealthAssistantEnabled) {
        sharedViewModel.getHealthCheckinsForUser(activeUserId).collectAsState(initial = emptyList())
    } else {
        remember { mutableStateOf(emptyList<com.health.openscale.core.data.HealthCheckin>()) }
    }

    val latestCheckin = userCheckins.firstOrNull()

    var currentSelectedMeasurementId  by rememberSaveable { mutableStateOf<Int?>(null) }
    var currentSelectedAggregatedTs   by rememberSaveable { mutableStateOf<Long?>(null) }

    // O(1) lookup maps — rebuilt only when aggregatedItems changes, not on every recomposition.
    val aggregatedItemById: Map<Int, AggregatedMeasurement> = remember(aggregatedItems) {
        aggregatedItems.associateBy { it.enriched.measurementWithValues.measurement.id }
    }
    val aggregatedItemByTs: Map<Long, AggregatedMeasurement> = remember(aggregatedItems) {
        aggregatedItems.associateBy { it.enriched.measurementWithValues.measurement.timestamp }
    }
    // MeasurementType lookup map — avoids O(t) linear scan per goal chip.
    val typeById: Map<Int, MeasurementType> = remember(allMeasurementTypes) {
        allMeasurementTypes.associateBy { it.id }
    }

    // Goal reference: the MeasurementWithValues the goal chips compare against.
    // In aggregated mode: the selected period's averaged entry.
    // In raw mode: the selected (or topmost) raw entry.
    val goalReferenceMeasurement: MeasurementWithValues? = remember(
        currentSelectedMeasurementId,
        currentSelectedAggregatedTs,
        aggregatedItemById,
        aggregatedItemByTs,
        isAggregated,
    ) {
        val fallback = aggregatedItems.firstOrNull()?.enriched?.measurementWithValues
        if (isAggregated) {
            val ts = currentSelectedAggregatedTs
            if (ts != null)
                aggregatedItemByTs[ts]?.enriched?.measurementWithValues ?: fallback
            else
                fallback
        } else {
            if (currentSelectedMeasurementId != null)
                aggregatedItemById[currentSelectedMeasurementId]?.enriched?.measurementWithValues ?: fallback
            else
                fallback
        }
    }

    // ── Delete dialog ─────────────────────────────────────────────────────────
    var measurementToDelete by remember { mutableStateOf<AggregatedMeasurement?>(null) }
    measurementToDelete?.let { aggItem ->
        val enrichedItem  = aggItem.enriched
        val weightValue   = enrichedItem.valuesWithTrend.find { it.currentValue.type.key == MeasurementType.WEIGHT }
        val weightString  = weightValue?.currentValue?.let {
            LocaleUtils.formatValueForDisplay(it.value.floatValue.toString(), it.type.unit)
        } ?: ""
        val formattedDate = remember(enrichedItem.measurementWithValues.measurement.timestamp) {
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault())
                .format(Date(enrichedItem.measurementWithValues.measurement.timestamp))
        }
        DeleteConfirmationDialog(
            onDismissRequest = { measurementToDelete = null },
            onConfirm        = {
                // VM owns the coroutine → completes even if the screen changes.
                sharedViewModel.deleteMeasurement(enrichedItem.measurementWithValues.measurement)
                measurementToDelete = null
            },
            title = stringResource(R.string.dialog_title_delete_item),
            text  = stringResource(R.string.dialog_message_delete_item, formattedDate, weightString),
        )
    }

    // ── Top bar ───────────────────────────────────────────────────────────────
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, selectedUserId, bluetoothAction, timeFilterAction, isDrillDown, aggregatedItems) {
        fun updateTopBar() {
            if (isDrillDown) {
                val fmt = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
                val title = "${fmt.format(Date(drillDownStartMillis))} – ${fmt.format(Date(drillDownEndMillis - 1))}"
                sharedViewModel.setTopBarTitle(title)
                sharedViewModel.setTopBarActions(emptyList())
            } else {
                sharedViewModel.setTopBarTitle(resources.getString(R.string.route_title_overview))
                val actions = mutableListOf<TopBarAction>()
                actions.add(bluetoothAction)
                sharedViewModel.setTopBarActions(actions)
            }
        }
        updateTopBar()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                updateTopBar()
                val target = sharedViewModel.lastDrillDownPeriodStart.value ?: return@LifecycleEventObserver
                val idx = aggregatedItems.indexOfFirst { it.periodStartMillis == target }
                if (idx >= 0) {
                    scope.launch {
                        listState.scrollToItem(idx)
                        sharedViewModel.setLastDrillDownPeriodStart(null)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ── Main UI ───────────────────────────────────────────────────────────────
    when {
        overviewState is SharedViewModel.UiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        selectedUserId == null && overviewState !is SharedViewModel.UiState.Loading -> {
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                NoUserSelectedCard(navController = navController)
            }
        }

        selectedUserId != null && overviewState !is SharedViewModel.UiState.Loading -> {
            Column(modifier = Modifier.fillMaxSize()) {
                when (val state = overviewState) {
                    is SharedViewModel.UiState.Success -> {

                        // Scroll to top when the newest item changes and prompt check-in
                        val topId = aggregatedItems.firstOrNull()
                            ?.enriched?.measurementWithValues?.measurement?.id
                        var lastSeenTopMeasurementId by rememberSaveable { mutableStateOf<Int?>(null) }

                        LaunchedEffect(topId, aggregatedItems.size) {
                            if (topId != null && !listState.isScrollInProgress) {
                                delay(60.milliseconds)
                                listState.smartScrollTo(0)
                            }
                            if (topId != null) {
                                if (lastSeenTopMeasurementId != null && topId != lastSeenTopMeasurementId) {
                                    // New weigh-in detected! Prompt clinical check-in
                                    sharedViewModel.openHealthCheckinSheet(
                                        userId = selectedUserId,
                                        measurementId = topId,
                                        existingCheckin = null
                                    )
                                }
                                lastSeenTopMeasurementId = topId
                            }
                        }

                            // ── Chart + divider + goals (hidden in drill-down) ────────
                            val chartContent: @Composable (Modifier) -> Unit = { chartModifier ->
                                MeasurementChart(
                                    sharedViewModel   = sharedViewModel,
                                    screenContextName = SettingsPreferenceKeys.OVERVIEW_SCREEN_CONTEXT,
                                    showFilterControls = true,
                                    // Side by side, the filter row spans the full width above chart and list.
                                    showTypeFilterRow = !useSideBySide,
                                    modifier          = chartModifier,
                                    showYAxis         = false,
                                    onPointSelected   = { selectedTs ->
                                        if (isAggregated) {
                                            // AggregatedMeasurement carries pre-computed period bounds —
                                            // no need to call periodBoundsFor().
                                            val idx = aggregatedItems.indexOfFirst { item ->
                                                selectedTs in item.periodStartMillis until item.periodEndMillis
                                            }
                                            if (idx >= 0) {
                                                val itemTs = aggregatedItems[idx]
                                                    .enriched.measurementWithValues.measurement.timestamp
                                                scope.launch {
                                                    listState.smartScrollTo(idx)
                                                    highlightedMeasurementId =
                                                        aggregatedItems[idx].enriched.measurementWithValues.measurement.id
                                                    currentSelectedAggregatedTs = itemTs
                                                    delay(600.milliseconds)
                                                    highlightedMeasurementId = null
                                                }
                                            }
                                        } else {
                                            val listForFind = aggregatedItems.map {
                                                it.enriched.measurementWithValues
                                            }
                                            sharedViewModel
                                                .findClosestMeasurement(selectedTs, listForFind)
                                                ?.let { (targetIndex, mwv) ->
                                                    val targetId = mwv.measurement.id
                                                    scope.launch {
                                                        listState.smartScrollTo(targetIndex)
                                                        highlightedMeasurementId          = targetId
                                                        currentSelectedMeasurementId      = targetId
                                                        delay(600.milliseconds)
                                                        if (highlightedMeasurementId == targetId)
                                                            highlightedMeasurementId = null
                                                    }
                                                }
                                        }
                                    },
                                )
                            }
                            val goalsSection: @Composable () -> Unit = {
                                if (userGoals.isNotEmpty()) {
                                    Column {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    scope.launch {
                                                        sharedViewModel.setMyGoalsExpandedOverview(
                                                            !isGoalsSectionExpanded
                                                        )
                                                    }
                                                }
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Box(
                                                modifier         = Modifier.weight(1f),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text  = stringResource(R.string.my_goals_label),
                                                        style = MaterialTheme.typography.titleMedium,
                                                    )
                                                    if (!isGoalsSectionExpanded && userGoals.isNotEmpty()) {
                                                        Text(
                                                            text  = " (${userGoals.size})",
                                                            style = MaterialTheme.typography.titleMedium,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                }
                                            }
                                            Icon(
                                                imageVector        = if (isGoalsSectionExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = if (isGoalsSectionExpanded)
                                                    stringResource(R.string.action_show_less_desc)
                                                else
                                                    stringResource(R.string.action_show_more_desc),
                                            )
                                        }

                                        AnimatedVisibility(visible = isGoalsSectionExpanded) {
                                            Column {
                                                LazyRow(
                                                    modifier            = Modifier.fillMaxWidth(),
                                                    contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                ) {
                                                    items(
                                                        userGoals,
                                                        key = { goal -> "${goal.userId}_${goal.measurementTypeId}" },
                                                    ) { goal ->
                                                        // Local copy: currentSelectedUser comes from a separate
                                                        // flow than userGoals and may still be null while the user
                                                        // is being switched — avoid the !! NPE.
                                                        val selectedUser = currentSelectedUser
                                                        if (selectedUser != null && goal.userId == selectedUser.id) {
                                                            val measurementType = typeById[goal.measurementTypeId]
                                                            if (measurementType != null) {
                                                                UserGoalChip(
                                                                    userGoal             = goal,
                                                                    measurementType      = measurementType,
                                                                    progress             = progressByTypeId[goal.measurementTypeId],
                                                                    onClick              = {
                                                                        val user = currentSelectedUser
                                                                        if (user != null && user.id != 0 &&
                                                                            goal.userId == user.id
                                                                        ) {
                                                                            sharedViewModel.showUserGoalDialogWithContext(
                                                                                type         = measurementType,
                                                                                existingGoal = goal,
                                                                            )
                                                                        }
                                                                    },
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                                // Only meaningful while the chips still depend on
                                                // the selected entry, i.e. without progress.
                                                if (progressByTypeId.isEmpty() &&
                                                    goalReferenceMeasurement?.measurement?.timestamp != null
                                                ) {
                                                    Row(
                                                        modifier              = Modifier
                                                            .fillMaxWidth()
                                                            .padding(top = 4.dp, bottom = 4.dp, end = 16.dp),
                                                        horizontalArrangement = Arrangement.End,
                                                        verticalAlignment     = Alignment.CenterVertically,
                                                    ) {
                                                        val shortDateTimeFormatter = remember {
                                                            DateFormat.getDateTimeInstance(
                                                                DateFormat.MEDIUM,
                                                                DateFormat.SHORT,
                                                                Locale.getDefault(),
                                                            )
                                                        }
                                                        Icon(
                                                            imageVector        = Icons.Outlined.Link,
                                                            contentDescription = stringResource(R.string.my_goals_label),
                                                            modifier           = Modifier.size(14.dp),
                                                            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                        Spacer(Modifier.width(4.dp))
                                                        Text(
                                                            text  = shortDateTimeFormatter.format(
                                                                Date(goalReferenceMeasurement.measurement.timestamp)
                                                            ),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        HorizontalDivider()
                                    }
                                }
                            }
                            val measurementList: @Composable (Modifier) -> Unit = { listModifier ->
                                // ── Main list ─────────────────────────────────────────────
                                LazyColumn(
                                    state                 = listState,
                                    modifier              = listModifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    verticalArrangement   = Arrangement.spacedBy(12.dp),
                                ) {
                                    itemsIndexed(
                                        items = aggregatedItems,
                                        // Must not depend on isAggregated: that flag comes from the
                                        // settings flow while the items come from the data flow, so
                                        // the two disagree for a frame after an aggregation change.
                                        // Aggregated entries are synthetic and all carry id = -1,
                                        // which then yields duplicate keys and crashes the list.
                                        // id + timestamp is unique in both modes.
                                        key   = { _, item ->
                                            val m = item.enriched.measurementWithValues.measurement
                                            "${m.id}_${m.timestamp}"
                                        },
                                    ) { _, aggItem ->
                                        val enrichedItem = aggItem.enriched
                                        val ts           = enrichedItem.measurementWithValues.measurement.timestamp

                                        if (isAggregated) {
                                            // aggregatedFromCount and period bounds come directly from
                                            // AggregatedMeasurement — no recomputation needed.
                                            val periodStart = aggItem.periodStartMillis
                                            val periodEnd   = aggItem.periodEndMillis
                                            MeasurementCard(
                                                sharedViewModel            = sharedViewModel,
                                                measurementWithValues      = enrichedItem.measurementWithValues,
                                                processedValuesForDisplay  = enrichedItem.valuesWithTrend,
                                                userEvaluationContext      = userEvalContext,
                                                onClick = {
                                                    currentSelectedAggregatedTs = ts
                                                    sharedViewModel.setLastDrillDownPeriodStart(periodStart)
                                                    navController.navigate(Routes.overviewDrillDown(periodStart, periodEnd))
                                                },
                                                onEdit                     = {
                                                    currentSelectedAggregatedTs = ts
                                                    navController.navigate(
                                                        Routes.overviewDrillDown(periodStart, periodEnd)
                                                    )
                                                },
                                                onDelete                   = null,
                                                isAggregated               = true,
                                                rawCount                   = aggItem.aggregatedFromCount,
                                                aggregatedPeriodLabel      = activeAggregationLevel.periodLabel(
                                                    timestamp            = ts,
                                                    calendarWeekAbbrev  = stringResource(R.string.calendar_week_abbrev),
                                                    weekFields          = weekFields,
                                                ),
                                            )
                                        } else {
                                            MeasurementCard(
                                                sharedViewModel           = sharedViewModel,
                                                measurementWithValues     = enrichedItem.measurementWithValues,
                                                processedValuesForDisplay = enrichedItem.valuesWithTrend,
                                                userEvaluationContext     = userEvalContext,
                                                onClick                   = {
                                                    currentSelectedMeasurementId =
                                                        enrichedItem.measurementWithValues.measurement.id
                                                },
                                                onEdit                    = {
                                                    selectedUserId?.let { userId ->
                                                        navController.navigate(
                                                            Routes.measurementDetail(
                                                                enrichedItem.measurementWithValues.measurement.id,
                                                                userId,
                                                            )
                                                        )
                                                    }
                                                },
                                                onDelete                  = { measurementToDelete = aggItem },
                                                isHighlighted             = (highlightedMeasurementId ==
                                                        enrichedItem.measurementWithValues.measurement.id),
                                            )
                                        }
                                    }
                                }
                            }

                        if (isDrillDown) {
                            if (aggregatedItems.isEmpty()) {
                                Box(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    NoMeasurementsCard(
                                        navController = navController,
                                        selectedUserId = selectedUserId,
                                    )
                                }
                            } else {
                                measurementList(Modifier.weight(1f))
                            }
                        } else {
                            // ── Compartmentalized Tabs ──────────────────────────────────────────
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 1.dp
                            ) {
                                ScrollableTabRow(
                                    selectedTabIndex = selectedSection.ordinal,
                                    edgePadding = 12.dp,
                                    divider = { HorizontalDivider() }
                                ) {
                                    OverviewSection.values().forEach { section ->
                                        Tab(
                                            selected = selectedSection == section,
                                            onClick = { selectedSection = section },
                                            text = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    modifier = Modifier.padding(vertical = 4.dp)
                                                ) {
                                                    Text(section.emoji, fontSize = 15.sp)
                                                    Text(
                                                        text = section.label,
                                                        fontWeight = if (selectedSection == section) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (selectedSection == section) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            when (selectedSection) {
                                OverviewSection.SYNTHESIS -> {
                                    SynthesisSection(
                                        user = activeUser,
                                        latestCheckin = latestCheckin,
                                        stomaSummary = stomaSummary,
                                        medicationIntakes = medicationIntakes,
                                        latestMeasurement = aggregatedItems.firstOrNull(),
                                        onNavigateToSection = { selectedSection = it },
                                        onCheckinClick = {
                                            sharedViewModel.openHealthCheckinSheet(
                                                userId = activeUserId,
                                                measurementId = null,
                                                existingCheckin = latestCheckin
                                            )
                                        },
                                        onReportClick = { sharedViewModel.openMedicalReportDialog() },
                                        onOrderClick = { navController.navigate(Routes.STOMA_ORDER) },
                                        onConsultationClick = { navController.navigate(Routes.DOCTOR_CONSULTATION) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                OverviewSection.HYDRATION -> {
                                    HydrationSection(
                                        stomaRepo = stomaRepo,
                                        summary = stomaSummary,
                                        isNotificationEnabled = isLockscreenNotifEnabled,
                                        onAddWater = onAddWater,
                                        onLogPouch = onLogPouch,
                                        onLogSupport = onLogSupport,
                                        onToggleNotification = onToggleLockscreenNotif,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                OverviewSection.MEDICATION -> {
                                    MedicationTrackingSection(
                                        context = context,
                                        medRepo = medRepo,
                                        intakes = medicationIntakes,
                                        onIntakesUpdated = { medicationIntakes = medRepo.loadIntakes() },
                                        onNavigateToFullLog = { navController.navigate(Routes.MEDICATION_LOG) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                OverviewSection.MEASUREMENTS -> {
                                    if (aggregatedItems.isEmpty()) {
                                        Box(
                                            modifier = Modifier.weight(1f).fillMaxWidth(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            NoMeasurementsCard(
                                                navController = navController,
                                                selectedUserId = selectedUserId,
                                            )
                                        }
                                    } else {
                                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                            if (useSideBySide) {
                                                Column(Modifier.fillMaxSize()) {
                                                    MeasurementChartTypeFilterRow(
                                                        sharedViewModel = sharedViewModel,
                                                        screenContextName = SettingsPreferenceKeys.OVERVIEW_SCREEN_CONTEXT,
                                                        showFilterControls = true,
                                                    )
                                                    Row(Modifier.weight(1f).fillMaxWidth()) {
                                                        chartContent(Modifier.weight(0.6f).fillMaxHeight())
                                                        VerticalDivider()
                                                        Column(Modifier.weight(0.4f).fillMaxHeight()) {
                                                            goalsSection()
                                                            measurementList(Modifier.weight(1f))
                                                        }
                                                    }
                                                }
                                            } else {
                                                Column(Modifier.fillMaxSize()) {
                                                    if (localSplitterWeight > 0f) {
                                                        Box(modifier = Modifier.weight(localSplitterWeight)) {
                                                            chartContent(Modifier.fillMaxWidth())
                                                        }
                                                    }
                                                    ChartSplitterHandle(
                                                        modifier = Modifier.padding(top = 8.dp),
                                                        collapsed = localSplitterWeight <= 0f,
                                                        onDrag = { dy ->
                                                            localSplitterWeight =
                                                                (localSplitterWeight + dy / 2000f).coerceIn(0f, 0.8f)
                                                        },
                                                        onDragEnd = {
                                                            scope.launch {
                                                                sharedViewModel.setSplitterWeight(
                                                                    SettingsPreferenceKeys.OVERVIEW_SCREEN_CONTEXT,
                                                                    localSplitterWeight,
                                                                )
                                                            }
                                                        },
                                                        onToggleCollapse = {
                                                            localSplitterWeight = if (localSplitterWeight > 0f) {
                                                                lastExpandedWeight = localSplitterWeight
                                                                0f
                                                            } else {
                                                                lastExpandedWeight.takeIf { it > 0f } ?: 0.3f
                                                            }
                                                            scope.launch {
                                                                sharedViewModel.setSplitterWeight(
                                                                    SettingsPreferenceKeys.OVERVIEW_SCREEN_CONTEXT,
                                                                    localSplitterWeight,
                                                                )
                                                            }
                                                        },
                                                    )
                                                    goalsSection()
                                                    measurementList(Modifier.weight(1f - localSplitterWeight))
                                                }
                                            }

                                            FloatingActionButton(
                                                onClick = {
                                                    if (selectedUserId != null && selectedUserId != 0) {
                                                        navController.navigate(
                                                            Routes.measurementDetail(
                                                                measurementId = null,
                                                                userId = selectedUserId!!
                                                            )
                                                        )
                                                    }
                                                },
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(16.dp),
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = stringResource(R.string.action_add_measurement_desc)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    is SharedViewModel.UiState.Error -> {
                        Box(
                            modifier         = Modifier.weight(1f).fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(state.message ?: stringResource(R.string.error_loading_data))
                        }
                    }

                    SharedViewModel.UiState.Loading -> {
                        Box(
                            modifier         = Modifier.weight(1f).fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                    }
                }
            }
        }

        else -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Unexpected state")
            }
        }
    }

    // ── Goal dialog ───────────────────────────────────────────────────────────
    if (goalDialogContextData.showDialog) {
        val dialogContext        = goalDialogContextData
        val userIdForDialog      = currentSelectedUser?.id
        if (dialogContext.typeForDialog == null || userIdForDialog == null || userIdForDialog == 0) {
            LaunchedEffect(goalDialogContextData.showDialog) {
                sharedViewModel.dismissUserGoalDialogWithContext()
            }
        } else {
            UserGoalDialog(
                navController         = navController,
                existingUserGoal      = dialogContext.existingGoalForDialog,
                allMeasurementTypes   = allMeasurementTypes,
                allGoalsOfCurrentUser = userGoals,
                onDismiss             = { sharedViewModel.dismissUserGoalDialogWithContext() },
                // Parsing and validation live in the dialog — it hands back ready values.
                onConfirm             = { measurementTypeId, goalValue, goalTargetDate, startDate ->
                    val goalToProcess = UserGoals(
                        userId            = userIdForDialog,
                        measurementTypeId = measurementTypeId,
                        goalValue         = goalValue,
                        goalTargetDate    = goalTargetDate,
                        startDate         = startDate,
                    )
                    if (dialogContext.existingGoalForDialog != null)
                        sharedViewModel.updateUserGoal(goalToProcess)
                    else
                        sharedViewModel.insertUserGoal(goalToProcess)
                },
                onDelete = { _, measurementTypeIdToDelete ->
                    sharedViewModel.deleteUserGoal(userIdForDialog, measurementTypeIdToDelete)
                    Toast.makeText(context, R.string.toast_goal_deleted, Toast.LENGTH_SHORT).show()
                    sharedViewModel.dismissUserGoalDialogWithContext()
                },
            )
        }
    }

    // ── Health Check-in Sheet & Medical Report Dialog ────────────────────────
    val checkinSheetData by sharedViewModel.healthCheckinSheetData.collectAsState()
    val showMedicalReport by sharedViewModel.showMedicalReportDialog.collectAsState()

    if (checkinSheetData != null) {
        val data = checkinSheetData!!
        HealthCheckinSheet(
            userId = data.userId,
            measurementId = data.measurementId,
            existingCheckin = data.existingCheckin ?: latestCheckin,
            isStomaEnabled = isStomaEnabled,
            isEndoEnabled = isEndoEnabled,
            isGlutenEnabled = isGlutenEnabled,
            onDismiss = { sharedViewModel.dismissHealthCheckinSheet() },
            onSave = { checkin -> sharedViewModel.saveHealthCheckin(checkin) }
        )
    }

    if (showMedicalReport && activeUser != null && activeUser.id != 0) {
        val allMeasurementsForUser by sharedViewModel.getMeasurementsForUser(activeUserId).collectAsState(initial = emptyList())
        MedicalReportDialog(
            user = activeUser,
            measurements = allMeasurementsForUser,
            checkins = userCheckins,
            onDismiss = { sharedViewModel.dismissMedicalReportDialog() }
        )
    }
}

// ---------------------------------------------------------------------------
// SynthesisSection (Cockpit / Executive Summary)
// ---------------------------------------------------------------------------

@Composable
fun SynthesisSection(
    user: com.health.openscale.core.data.User?,
    latestCheckin: com.health.openscale.core.data.HealthCheckin?,
    stomaSummary: DailyStomaSummary,
    medicationIntakes: List<MedicationIntake>,
    latestMeasurement: AggregatedMeasurement?,
    onNavigateToSection: (OverviewSection) -> Unit,
    onCheckinClick: () -> Unit,
    onReportClick: () -> Unit,
    onOrderClick: () -> Unit,
    onConsultationClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val todayFmt = remember { SimpleDateFormat("EEEE d MMMM yyyy", Locale.FRANCE) }
    val todayDateStr = remember { todayFmt.format(Date()).replaceFirstChar { it.uppercase() } }

    val todayIntakes = remember(medicationIntakes) {
        medicationIntakes.filter { android.text.format.DateUtils.isToday(it.timestamp) }
    }
    val pendingEvaluations = remember(medicationIntakes) {
        val now = System.currentTimeMillis()
        val sixHoursAgo = now - 6 * 3600 * 1000L
        medicationIntakes.filter {
            it.postPainScore == null && it.timestamp >= sixHoursAgo && it.initialPainScore != null
        }
    }

    val latestWeightValue = latestMeasurement?.enriched?.valuesWithTrend?.find { it.currentValue.type.key == MeasurementType.WEIGHT }
    val latestWeightStr = latestWeightValue?.currentValue?.let {
        LocaleUtils.formatValueForDisplay(it.value.floatValue.toString(), it.type.unit)
    }
    val latestWaterValue = latestMeasurement?.enriched?.valuesWithTrend?.find { it.currentValue.type.key == MeasurementType.WATER }
    val latestWaterStr = latestWaterValue?.currentValue?.let {
        LocaleUtils.formatValueForDisplay(it.value.floatValue.toString(), it.type.unit)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. CARTOUCHE PATIENTE & CHECK-IN QUOTIDIEN ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            )
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalHospital,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Bilan Clinique Quotidien",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = todayDateStr,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    FilledTonalButton(
                        onClick = onReportClick,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Bilan PDF", style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (latestCheckin != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // EVA Badge
                        val pain = latestCheckin.painScore
                        val painColor = when {
                            pain == null -> Color(0xFF757575)
                            pain <= 3 -> Color(0xFF2E7D32)
                            pain <= 6 -> Color(0xFFE65100)
                            else -> Color(0xFFC62828)
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = painColor.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, painColor.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = if (pain != null) "EVA $pain/10" else "EVA non renseignée",
                                color = painColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        // Stoma Transit Badge
                        val stomaLabel = when (latestCheckin.stomaOutput) {
                            "HIGH_LIQUID" -> "Stomie liquide ⚠️"
                            "SLOW" -> "Stomie ralentie"
                            else -> "Stomie normale 🟢"
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF00365B).copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = stomaLabel,
                                color = Color(0xFF00365B),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        // Hormonal Sensation (Post-hysterectomy)
                        if (latestCheckin.cycleSensation == true) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFAD1457).copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = "Cycle ressenti 🌸",
                                    color = Color(0xFFAD1457),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Button(
                        onClick = onCheckinClick,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Modifier le Check-in Santé 🩺")
                    }
                } else {
                    Text(
                        text = "Aucun bilan de santé enregistré pour aujourd'hui.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onCheckinClick,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("🩺 Remplir mon Check-in Santé du Jour", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // --- 2. VOS PILIERS DE SANTÉ (CLOISONNEMENT) ---
        Text(
            text = "Vos Piliers de Suivi Clinique",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Pilier 1: Hydratation & Stomie
        val waterTarget = stomaSummary.targetWaterMl.coerceAtLeast(1)
        val waterCurrent = stomaSummary.totalWaterMl
        val waterPct = ((waterCurrent.toFloat() / waterTarget) * 100).toInt().coerceIn(0, 100)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToSection(OverviewSection.HYDRATION) },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F7F9)),
            border = BorderStroke(1.dp, Color(0xFFB2DFDB))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("💧", fontSize = 20.sp)
                        Text(
                            text = "Hydratation & Stomie",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00365B)
                        )
                    }
                    Text(
                        text = "Gérer →",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0077A8)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Eau bue auj. : ${String.format(Locale.FRANCE, "%.2f", waterCurrent / 1000.0)} L / ${String.format(Locale.FRANCE, "%.2f", waterTarget / 1000.0)} L",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF37474F)
                    )
                    Text(
                        text = "$waterPct%",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0077A8)
                    )
                }

                LinearProgressIndicator(
                    progress = { (waterCurrent.toFloat() / waterTarget).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFF009DA8),
                    trackColor = Color(0xFFE0F2F1)
                )

                val supportStatus = when (stomaSummary.daysSinceLastSupportChange) {
                    0 -> "Support changé aujourd'hui"
                    1 -> "Support changé hier (1j)"
                    else -> "Support : il y a ${stomaSummary.daysSinceLastSupportChange} j"
                }
                Text(
                    text = "🚽 ${stomaSummary.pouchChangeCount} poche(s) changée(s) auj. • 🛡️ $supportStatus",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF455A64)
                )
            }
        }

        // Pilier 2: Traitements & Douleur
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToSection(OverviewSection.MEDICATION) },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF9F7F5)),
            border = BorderStroke(1.dp, Color(0xFFFFCCBC))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("💊", fontSize = 20.sp)
                        Text(
                            text = "Traitements & Soulagement",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4E342E)
                        )
                    }
                    Text(
                        text = "Consigner →",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD84315)
                    )
                }

                Text(
                    text = "${todayIntakes.size} prise(s) enregistrée(s) aujourd'hui",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF3E2723)
                )

                if (pendingEvaluations.isNotEmpty()) {
                    val p = pendingEvaluations.first()
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFFF3E0),
                        border = BorderStroke(1.dp, Color(0xFFFFB74D))
                    ) {
                        Text(
                            text = "⚠️ Évaluation H+2 requise pour ${p.name} (EVA initial: ${p.initialPainScore}/10)",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                } else if (todayIntakes.isNotEmpty()) {
                    val last = todayIntakes.first()
                    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(last.timestamp))
                    Text(
                        text = "Dernière prise : ${last.name} (${last.dosage}) à $timeStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF5D4037)
                    )
                } else {
                    Text(
                        text = "Aucune prise antalgique aujourd'hui. Appuyez pour ajouter Lamaline, Acupan...",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF795548)
                    )
                }
            }
        }

        // Pilier 3: Mesures & Balance
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToSection(OverviewSection.MEASUREMENTS) },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F6F8)),
            border = BorderStroke(1.dp, Color(0xFFCFD8DC))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("⚖️", fontSize = 20.sp)
                        Text(
                            text = "Mesures & Impédancemétrie",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF263238)
                        )
                    }
                    Text(
                        text = "Courbes →",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF455A64)
                    )
                }

                if (latestMeasurement != null) {
                    val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(
                        Date(latestMeasurement.enriched.measurementWithValues.measurement.timestamp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Dernière pesée : ${latestWeightStr ?: "--"}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF263238)
                        )
                        if (latestWaterStr != null) {
                            Text(
                                text = "Eau : $latestWaterStr",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0077A8)
                            )
                        }
                    }
                    Text(
                        text = "Mesuré le $dateStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF78909C)
                    )
                } else {
                    Text(
                        text = "Aucune pesée enregistrée. Connectez la balance Bluetooth pour synchroniser vos données.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF78909C)
                    )
                }
            }
        }

        // --- 3. ACCÈS RAPIDES MÉDICAUX & MATÉRIEL ---
        Text(
            text = "Outils Médicaux & Prise en Charge",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onOrderClick,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Commande 📦", maxLines = 1, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onConsultationClick,
                modifier = Modifier.weight(1.2f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF00365B)
                )
            ) {
                Text("🩺 Consultation", maxLines = 1, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// HydrationSection & TodayStomaEventsCard
// ---------------------------------------------------------------------------

@Composable
fun HydrationSection(
    stomaRepo: StomaTrackingRepository,
    summary: DailyStomaSummary,
    isNotificationEnabled: Boolean,
    onAddWater: (Int) -> Unit,
    onLogPouch: () -> Unit,
    onLogSupport: () -> Unit,
    onToggleNotification: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Live hydration & stoma care card
        StomaHydrationLiveCard(
            summary = summary,
            isNotificationEnabled = isNotificationEnabled,
            onAddWater = onAddWater,
            onLogPouch = onLogPouch,
            onLogSupport = onLogSupport,
            onToggleNotification = onToggleNotification
        )

        // Today's events card
        TodayStomaEventsCard(
            stomaRepo = stomaRepo,
            summary = summary
        )

        // Clinical advice card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
            border = BorderStroke(1.dp, Color(0xFFA5D6A7))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "💡 Recommandation Clinique Stomie & Hydratation",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B5E20)
                )
                Text(
                    text = "En colostomie, une hydratation minimale de 2,0 à 2,5 L/jour est indispensable pour assurer la fluidité du transit et compenser les pertes. Lors des épisodes de selles liquides ou d'Endo-Belly, privilégiez les bouillons salés et l'eau riche en électrolytes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF2E7D32)
                )
            }
        }
    }
}

@Composable
fun TodayStomaEventsCard(
    stomaRepo: StomaTrackingRepository,
    summary: DailyStomaSummary
) {
    val context = LocalContext.current
    val todayEvents = remember(summary) {
        stomaRepo.getAllEvents()
            .filter { android.text.format.DateUtils.isToday(it.timestamp) }
            .reversed()
    }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Journal des Saisies du Jour",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF263238)
                )

                if (todayEvents.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            if (stomaRepo.undoLastEvent()) {
                                Toast.makeText(context, "Dernière action annulée", Toast.LENGTH_SHORT).show()
                                if (stomaRepo.isNotificationEnabled()) {
                                    StomaNotificationManager.showOrUpdateNotification(context, stomaRepo.getTodaySummary())
                                }
                            }
                        }
                    ) {
                        Text("Annuler dernière saisie ↩️", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (todayEvents.isEmpty()) {
                Text(
                    text = "Aucune action enregistrée aujourd'hui. Utilisez les boutons ci-dessus pour ajouter vos verres d'eau et changements de poche.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF78909C)
                )
            } else {
                todayEvents.forEach { ev ->
                    val (icon, label) = when (ev.type) {
                        StomaEventType.WATER -> "💧" to "+${ev.amountMl} ml d'eau bue"
                        StomaEventType.POUCH_CHANGE -> "🚽" to "Changement de poche SenSura Mio"
                        StomaEventType.SUPPORT_CHANGE -> "🛡️" to "Changement support complet (semi-convexe)"
                    }
                    val timeStr = timeFmt.format(Date(ev.timestamp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(icon, fontSize = 16.sp)
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF37474F)
                            )
                        }
                        Text(
                            text = timeStr,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF78909C)
                        )
                    }
                    HorizontalDivider(color = Color(0xFFECEFF1))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// MedicationTrackingSection
// ---------------------------------------------------------------------------

@Composable
fun MedicationTrackingSection(
    context: android.content.Context,
    medRepo: MedicationRepository,
    intakes: List<MedicationIntake>,
    onIntakesUpdated: () -> Unit,
    onNavigateToFullLog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val todayFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    var selectedMolecule by remember { mutableStateOf("Lamaline") }
    var selectedDosage by remember { mutableStateOf("1 gélule") }
    var painScore by remember { mutableStateOf(5) }
    var notes by remember { mutableStateOf("") }
    var customMolecule by remember { mutableStateOf("") }
    var customDosage by remember { mutableStateOf("") }

    val pendingEvaluations = remember(intakes) {
        val now = System.currentTimeMillis()
        val sixHoursAgo = now - 6 * 3600 * 1000L
        intakes.filter {
            it.postPainScore == null && it.timestamp >= sixHoursAgo && it.initialPainScore != null
        }
    }

    val todayIntakes = remember(intakes) {
        intakes.filter { android.text.format.DateUtils.isToday(it.timestamp) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Pending Evaluation Card (if any intake is pending post evaluation)
        if (pendingEvaluations.isNotEmpty()) {
            val pending = pendingEvaluations.first()
            var evalScore by remember(pending.id) { mutableStateOf(2) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                border = BorderStroke(1.dp, Color(0xFFFFB74D))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("⏱️", fontSize = 20.sp)
                        Column {
                            Text(
                                text = "Évaluation d'Efficacité (H+1 / H+2)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE65100)
                            )
                            val timeStr = todayFmt.format(Date(pending.timestamp))
                            Text(
                                text = "Prise de ${pending.name} (${pending.dosage}) à $timeStr (EVA initial : ${pending.initialPainScore}/10)",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF5D4037)
                            )
                        }
                    }

                    Text(
                        text = "Douleur actuelle (EVA après traitement) : $evalScore / 10",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF3E2723)
                    )

                    Slider(
                        value = evalScore.toFloat(),
                        onValueChange = { evalScore = it.toInt() },
                        valueRange = 0f..10f,
                        steps = 9,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFE65100),
                            activeTrackColor = Color(0xFFE65100)
                        )
                    )

                    Button(
                        onClick = {
                            medRepo.evaluateIntake(pending.id, evalScore)
                            onIntakesUpdated()
                            Toast.makeText(context, "Évaluation enregistrée avec succès", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
                    ) {
                        Text("Valider le soulagement", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Quick Intake Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("💊", fontSize = 22.sp)
                    Text(
                        text = "Consigner une Prise de Traitement",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00365B)
                    )
                }

                // Pain level before intake
                val painColor = when {
                    painScore <= 3 -> Color(0xFF2E7D32)
                    painScore <= 6 -> Color(0xFFE65100)
                    else -> Color(0xFFC62828)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Douleur ressentie (EVA au moment de la prise) :",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = painColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, painColor)
                    ) {
                        Text(
                            text = "EVA $painScore/10",
                            color = painColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Slider(
                    value = painScore.toFloat(),
                    onValueChange = { painScore = it.toInt() },
                    valueRange = 0f..10f,
                    steps = 9,
                    colors = SliderDefaults.colors(
                        thumbColor = painColor,
                        activeTrackColor = painColor
                    )
                )

                Text(
                    text = "Molécule :",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )

                // Quick molecule selection chips
                val molecules = listOf(
                    Triple("Lamaline", "1 gélule", "Antalgique morphinique / paracétamol"),
                    Triple("Acupan", "20mg (1 ampoule)", "Antalgique central (Néfopam)"),
                    Triple("Spasfon", "80mg (1 lyoc)", "Antispasmodique"),
                    Triple("Doliprane", "1000mg", "Paracétamol"),
                    Triple("Autre...", "", "Autre")
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(molecules) { (name, defaultDosage, category) ->
                        val isSelected = selectedMolecule == name
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedMolecule = name
                                if (name != "Autre...") {
                                    selectedDosage = defaultDosage
                                }
                            },
                            label = { Text(name, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }
                }

                if (selectedMolecule == "Autre...") {
                    OutlinedTextField(
                        value = customMolecule,
                        onValueChange = { customMolecule = it },
                        label = { Text("Nom du médicament") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = customDosage,
                        onValueChange = { customDosage = it },
                        label = { Text("Posologie / Dosage (ex: 50mg)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = selectedDosage,
                        onValueChange = { selectedDosage = it },
                        label = { Text("Posologie") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Remarque / Contexte (optionnel)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        val name = if (selectedMolecule == "Autre...") customMolecule.trim() else selectedMolecule
                        val dosage = if (selectedMolecule == "Autre...") customDosage.trim() else selectedDosage
                        if (name.isNotBlank()) {
                            val cat = molecules.find { it.first == selectedMolecule }?.third ?: "Antalgique"
                            medRepo.logIntake(
                                name = name,
                                dosage = dosage,
                                category = cat,
                                initialPainScore = painScore,
                                notes = notes.takeIf { it.isNotBlank() }
                            )
                            onIntakesUpdated()
                            Toast.makeText(context, "Prise enregistrée : $name à $painScore/10", Toast.LENGTH_SHORT).show()
                            notes = ""
                            if (selectedMolecule == "Autre...") {
                                customMolecule = ""
                                customDosage = ""
                            }
                        } else {
                            Toast.makeText(context, "Veuillez renseigner le nom du médicament", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Enregistrer la prise 💊", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Today's Intakes
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Prises du Jour (${todayIntakes.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF263238)
                )

                if (todayIntakes.isEmpty()) {
                    Text(
                        text = "Aucun traitement consigné pour aujourd'hui.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF78909C)
                    )
                } else {
                    todayIntakes.forEach { intake ->
                        val timeStr = todayFmt.format(Date(intake.timestamp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${intake.name} (${intake.dosage})",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF00365B)
                                )
                                Text(
                                    text = timeStr,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF78909C)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val initial = intake.initialPainScore
                                val post = intake.postPainScore
                                val relief = intake.reliefPercent

                                if (post != null && relief != null) {
                                    Text(
                                        text = "EVA initial $initial/10 → EVA post $post/10",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF37474F)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFFE8F5E9)
                                    ) {
                                        Text(
                                            text = "Soulagement : $relief%",
                                            color = Color(0xFF2E7D32),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "EVA au moment de la prise : $initial/10",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF37474F)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFFFFF3E0)
                                    ) {
                                        Text(
                                            text = "⏳ En attente H+2",
                                            color = Color(0xFFE65100),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(color = Color(0xFFECEFF1))
                        }
                    }
                }
            }
        }

        // Full history button
        OutlinedButton(
            onClick = onNavigateToFullLog,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("📊 Voir l'historique complet & statistiques d'efficacité", fontWeight = FontWeight.SemiBold)
        }
    }
}

// ---------------------------------------------------------------------------
// Utility
// ---------------------------------------------------------------------------

suspend fun LazyListState.smartScrollTo(index: Int) {
    val dist = abs(firstVisibleItemIndex - index)
    if (dist > 20) scrollToItem(index) else animateScrollToItem(index)
}

// ---------------------------------------------------------------------------
// MeasurementCard
// ---------------------------------------------------------------------------

@Composable
fun MeasurementCard(
    sharedViewModel: SharedViewModel,
    measurementWithValues: MeasurementWithValues,
    processedValuesForDisplay: List<ValueWithDifference>,
    userEvaluationContext: UserEvaluationContext?,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?,
    isHighlighted: Boolean = false,
    isAggregated: Boolean = false,
    rawCount: Int = 1,
    aggregatedPeriodLabel: String = "",
) {
    val surfaceAtElevation = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
    val isLightSurface     = surfaceAtElevation.luminance() > 0.5f
    val tintAlpha          = if (isLightSurface) 0.16f else 0.12f
    val highlightColor     = MaterialTheme.colorScheme.primary.copy(alpha = tintAlpha)
        .compositeOver(surfaceAtElevation)
    val highlightBorder    = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    val measuredAtMillis   = measurementWithValues.measurement.timestamp
    val expandedTypeIds    = remember { mutableStateMapOf<Int, Boolean>() }

    val headerLabel = if (isAggregated) {
        "$aggregatedPeriodLabel ($rawCount)"
    } else {
        // Read the locale observably so the label recomposes on locale changes.
        val locale = ComposeLocale.current.platformLocale
        remember(measurementWithValues.measurement.timestamp, locale) {
            val date = Date(measurementWithValues.measurement.timestamp)
            "${DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(date)} " +
                    "(${SimpleDateFormat("EE", locale).format(date)}) " +
                    DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(date)
        }
    }

    var isExpanded by rememberSaveable { mutableStateOf(false) }

    val pinnedValues = remember(processedValuesForDisplay) {
        processedValuesForDisplay.filter { it.currentValue.type.isPinned && it.currentValue.type.isEnabled }
    }
    val nonPinnedValues = remember(processedValuesForDisplay) {
        processedValuesForDisplay.filter { !it.currentValue.type.isPinned && it.currentValue.type.isEnabled }
    }
    val allActiveProcessedValues = remember(processedValuesForDisplay) {
        processedValuesForDisplay.filter { it.currentValue.type.isEnabled }
    }

    Card(
        modifier  = Modifier.fillMaxWidth().clickable(onClick = onClick),
        border    = if (isHighlighted) highlightBorder else null,
        colors    = if (isHighlighted)
            CardDefaults.cardColors(containerColor = highlightColor)
        else
            CardDefaults.cardColors(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            ) {
                Text(
                    text     = headerLabel,
                    style    = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                val syncState = measurementWithValues.healthConnectSyncStates.firstOrNull()
                val syncLabel = when {
                    syncState?.status == HealthConnectSyncState.STATUS_ERROR || syncState?.status == HealthConnectSyncState.STATUS_INVALID -> stringResource(R.string.health_connect_measurement_error)
                    syncState?.status == HealthConnectSyncState.STATUS_UNSUPPORTED -> stringResource(R.string.health_connect_measurement_unsupported)
                    syncState?.status == HealthConnectSyncState.STATUS_SKIPPED -> stringResource(R.string.health_connect_measurement_skipped)
                    syncState?.status == HealthConnectSyncState.STATUS_SYNCED && syncState.lastSyncedRevision >= measurementWithValues.measurement.revision -> stringResource(R.string.health_connect_measurement_synced)
                    syncState != null -> stringResource(R.string.health_connect_measurement_pending)
                    else -> null
                }
                if (syncLabel != null && !isAggregated) {
                    Text(
                        text = syncLabel,
                        color = if (syncState?.status == HealthConnectSyncState.STATUS_ERROR || syncState?.status == HealthConnectSyncState.STATUS_INVALID) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
                val iconButtonSize  = 36.dp
                val actionIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)

                if (isAggregated) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(iconButtonSize)) {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint               = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    if (onDelete != null) {
                        IconButton(onClick = onDelete, modifier = Modifier.size(iconButtonSize)) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.action_delete_measurement_desc, headerLabel),
                                tint               = actionIconColor,
                            )
                        }
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(iconButtonSize)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.action_edit_measurement_desc, headerLabel),
                            tint               = actionIconColor,
                        )
                    }
                    if (nonPinnedValues.isNotEmpty() && pinnedValues.isEmpty()) {
                        IconButton(
                            onClick  = { isExpanded = !isExpanded },
                            modifier = Modifier.size(iconButtonSize),
                        ) {
                            Icon(
                                imageVector        = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = stringResource(
                                    if (isExpanded) R.string.action_show_less_desc else R.string.action_show_more_desc
                                ),
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.padding(
                    start   = 16.dp,
                    end     = 16.dp,
                    top     = if (pinnedValues.isNotEmpty()) 4.dp else 0.dp,
                    bottom  = 0.dp,
                ),
            ) {
                if (pinnedValues.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        pinnedValues.forEach { valueWithTrend ->
                            MeasurementRowExpandable(
                                sharedViewModel       = sharedViewModel,
                                valueWithTrend        = valueWithTrend,
                                userEvaluationContext = userEvaluationContext,
                                measuredAtMillis      = measuredAtMillis,
                                expandedTypeIds       = expandedTypeIds,
                                valuePrefix           = if (isAggregated && rawCount > 1) "⌀ " else "",
                            )
                        }
                    }
                }
            }

            if (nonPinnedValues.isNotEmpty()) {
                AnimatedVisibility(visible = isExpanded || pinnedValues.isEmpty()) {
                    Column(
                        modifier            = Modifier.padding(start = 16.dp, end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        nonPinnedValues.forEach { valueWithTrend ->
                            MeasurementRowExpandable(
                                sharedViewModel       = sharedViewModel,
                                valueWithTrend        = valueWithTrend,
                                userEvaluationContext = userEvaluationContext,
                                measuredAtMillis      = measuredAtMillis,
                                expandedTypeIds       = expandedTypeIds,
                                valuePrefix           = if (isAggregated && rawCount > 1) "⌀ " else "",
                            )
                        }
                    }
                }
            }

            if (nonPinnedValues.isNotEmpty() && (pinnedValues.isNotEmpty() || !isExpanded)) {
                if (isExpanded || pinnedValues.isNotEmpty()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(
                            // nonPinnedValues is non-empty here; when collapsed, the outer
                            // conditions guarantee pinnedValues is non-empty.
                            top    = if (isExpanded) 4.dp else 8.dp,
                            bottom = 0.dp,
                        ),
                    )
                }
                TextButton(
                    onClick   = { isExpanded = !isExpanded },
                    modifier  = Modifier.fillMaxWidth().height(48.dp),
                    shape     = MaterialTheme.shapes.extraSmall,
                ) {
                    Icon(
                        imageVector        = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        tint               = MaterialTheme.colorScheme.secondary,
                        contentDescription = stringResource(
                            if (isExpanded) R.string.action_show_less_desc else R.string.action_show_more_desc
                        ),
                    )
                }
            }

            if (allActiveProcessedValues.isEmpty()) {
                Text(
                    text      = stringResource(R.string.no_active_values_for_measurement),
                    style     = MaterialTheme.typography.bodyMedium,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier  = Modifier.padding(16.dp).fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
            if (pinnedValues.isNotEmpty() && nonPinnedValues.isEmpty() && allActiveProcessedValues.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// MeasurementValueRow + MeasurementRowExpandable (unchanged logic)
// ---------------------------------------------------------------------------

@Composable
fun MeasurementValueRow(
    sharedViewModel: SharedViewModel,
    valueWithTrend: ValueWithDifference,
    userEvaluationContext: UserEvaluationContext?,
    measuredAtMillis: Long,
    valuePrefix: String = "",
) {
    val type          = valueWithTrend.currentValue.type
    val originalValue = valueWithTrend.currentValue.value
    val difference    = valueWithTrend.difference
    val trend         = valueWithTrend.trend
    val unitName      = type.unit.displayName
    val context       = LocalContext.current

    val displayValue = when (type.inputType) {
        InputFieldType.FLOAT -> originalValue.floatValue?.let { LocaleUtils.formatValueForDisplay(it.toString(), type.unit) }
        InputFieldType.INT   -> originalValue.intValue?.let { LocaleUtils.formatValueForDisplay(it.toString(), type.unit) }
        InputFieldType.TEXT  -> originalValue.textValue
        InputFieldType.DATE  -> originalValue.dateValue?.let { DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault()).format(Date(it)) }
        InputFieldType.TIME  -> originalValue.dateValue?.let { DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(Date(it)) }
        InputFieldType.USER, InputFieldType.IMAGE -> null
    } ?: "-"
    val imageFile = if (type.inputType == InputFieldType.IMAGE) sharedViewModel.imageFile(originalValue.textValue) else null
    var showImageViewer by remember { mutableStateOf(false) }

    val iconMeasurementType = remember(type.icon) { type.icon }
    val numeric: Float? = when (type.inputType) {
        InputFieldType.FLOAT -> originalValue.floatValue
        InputFieldType.INT   -> originalValue.intValue?.toFloat()
        else -> null
    }

    val evalResult = remember(valueWithTrend, userEvaluationContext, measuredAtMillis) {
        if (userEvaluationContext != null && numeric != null)
            sharedViewModel.evaluateMeasurement(
                type                  = type,
                value                 = numeric,
                userEvaluationContext = userEvaluationContext,
                measuredAtMillis      = measuredAtMillis,
            )
        else null
    }

    val noAgeBand: Boolean = evalResult?.let { it.lowLimit < 0f || it.highLimit < 0f } ?: false
    val plausible          = sharedViewModel.getPlausiblePercentRange(type.key)
    val outOfPlausibleRange = if (numeric == null) false
    else plausible?.let { numeric < it.start || numeric > it.endInclusive }
        ?: (unitName == "%" && (numeric < 0f || numeric > 100f))

    val flagged    = noAgeBand || outOfPlausibleRange
    val evalState  = evalResult?.state ?: EvaluationState.UNDEFINED
    val evalIcon: ImageVector? = if (flagged) Icons.Filled.Warning
    else when (evalState) {
        EvaluationState.LOW       -> Icons.Filled.Circle
        EvaluationState.NORMAL    -> Icons.Filled.Circle
        EvaluationState.HIGH      -> Icons.Filled.Circle
        EvaluationState.UNDEFINED -> null
    }
    val iconSize = if (flagged) 14.dp
    else when (evalState)  {
        EvaluationState.LOW       -> 10.dp
        EvaluationState.NORMAL    -> 10.dp
        EvaluationState.HIGH      -> 10.dp
        EvaluationState.UNDEFINED -> 10.dp
    }

    val evalColor = if (flagged) MaterialTheme.colorScheme.error else evalState.toColor()

    Row(
        modifier          = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            RoundMeasurementIcon(icon = iconMeasurementType.resource, backgroundTint = Color(type.color))
            Spacer(modifier = Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text     = type.getDisplayName(context),
                    style    = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                )
                if (difference != null && trend != Trend.NOT_APPLICABLE) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val trendIconVector = when (trend) {
                            Trend.UP   -> Icons.Filled.ArrowUpward
                            Trend.DOWN -> Icons.Filled.ArrowDownward
                            else       -> null
                        }
                        val subtle = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        if (trendIconVector != null) {
                            Icon(
                                imageVector        = trendIconVector,
                                contentDescription = trend.name,
                                tint               = subtle,
                                modifier           = Modifier.size(12.dp),
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                        }
                        Text(
                            text  = when (type.inputType) {
                                InputFieldType.FLOAT, InputFieldType.INT ->
                                    LocaleUtils.formatValueForDisplay(
                                        value       = difference.toString(),
                                        unit        = type.unit,
                                        includeSign = (trend != Trend.NONE),
                                    )
                                else -> ""
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = subtle,
                        )
                    }
                } else if (type.inputType == InputFieldType.FLOAT || type.inputType == InputFieldType.INT) {
                    Spacer(modifier = Modifier.height((MaterialTheme.typography.bodySmall.fontSize.value + 2).dp))
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.padding(start = 8.dp),
        ) {
            if (valuePrefix.isNotEmpty()) {
                Text(
                    text       = valuePrefix,
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            if (imageFile != null) {
                MeasurementImageThumbnail(
                    file               = imageFile,
                    contentDescription = stringResource(R.string.content_desc_photo, type.getDisplayName(context)),
                    onClick            = { showImageViewer = true },
                )
            } else {
                Text(
                    text       = displayValue,
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (valuePrefix.isNotEmpty()) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign  = TextAlign.End,
                )
            }
            Spacer(Modifier.width(6.dp))
            if (evalIcon != null) {
                Icon(
                    imageVector        = evalIcon,
                    contentDescription = null,
                    tint               = evalColor,
                    modifier           = Modifier.size(iconSize),
                )
            } else {
                Spacer(modifier = Modifier.size(iconSize))
            }
        }
    }

    if (showImageViewer) {
        val title = remember(measuredAtMillis) {
            "${type.getDisplayName(context)} · " +
                DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(Date(measuredAtMillis))
        }
        FullscreenImageViewer(file = imageFile, title = title, type = type, onDismiss = { showImageViewer = false })
    }
}

@Composable
private fun EvaluationErrorBanner(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector        = Icons.Filled.Error,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text  = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
fun MeasurementRowExpandable(
    sharedViewModel: SharedViewModel,
    valueWithTrend: ValueWithDifference,
    userEvaluationContext: UserEvaluationContext?,
    measuredAtMillis: Long,
    expandedTypeIds: MutableMap<Int, Boolean>,
    modifier: Modifier = Modifier,
    gaugeHeightDp: Dp = 80.dp,
    valuePrefix: String = "",
) {
    val type    = valueWithTrend.currentValue.type
    val numeric: Float? = when (type.inputType) {
        InputFieldType.FLOAT -> valueWithTrend.currentValue.value.floatValue
        InputFieldType.INT   -> valueWithTrend.currentValue.value.intValue?.toFloat()
        else -> null
    }

    val evalResult = remember(valueWithTrend, userEvaluationContext, measuredAtMillis) {
        if (userEvaluationContext == null || numeric == null) null
        else sharedViewModel.evaluateMeasurement(
            type                  = type,
            value                 = numeric,
            userEvaluationContext = userEvaluationContext,
            measuredAtMillis      = measuredAtMillis,
        )
    }

    val noAgeBand           = evalResult?.let { it.lowLimit < 0f || it.highLimit < 0f } ?: false
    val unitName            = type.unit.displayName
    val plausible           = sharedViewModel.getPlausiblePercentRange(type.key)
    val outOfPlausibleRange = if (numeric == null) false
    else plausible?.let { numeric < it.start || numeric > it.endInclusive }
        ?: (unitName == "%" && (numeric < 0f || numeric > 100f))

    val canExpand = (evalResult != null && !noAgeBand) || noAgeBand || outOfPlausibleRange

    Column(modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = canExpand) {
                    val cur = expandedTypeIds[type.id] ?: false
                    expandedTypeIds[type.id] = !cur
                },
        ) {
            MeasurementValueRow(
                sharedViewModel       = sharedViewModel,
                valueWithTrend        = valueWithTrend,
                userEvaluationContext = userEvaluationContext,
                measuredAtMillis      = measuredAtMillis,
                valuePrefix           = valuePrefix,
            )
        }

        AnimatedVisibility(visible = canExpand && (expandedTypeIds[type.id] == true)) {
            when {
                noAgeBand -> EvaluationErrorBanner(
                    message = stringResource(R.string.eval_no_age_band)
                )
                outOfPlausibleRange -> {
                    val p = sharedViewModel.getPlausiblePercentRange(type.key) ?: (0f..100f)
                    EvaluationErrorBanner(
                        message = stringResource(
                            R.string.eval_out_of_plausible_range_percent,
                            p.start,
                            p.endInclusive,
                        )
                    )
                }
                evalResult != null -> {
                    val (displayValue, displayLow, displayHigh) = remember(evalResult, type.unit) {
                        val targetUnit = type.unit
                        val baseUnit   = when (type.key) {
                            MeasurementType.WEIGHT, MeasurementType.LBM -> UnitType.KG
                            MeasurementType.WAIST                          -> UnitType.CM
                            else                                               -> UnitType.PERCENT
                        }
                        // The noAgeBand branch above already handled negative limits,
                        // so lowLimit/highLimit are guaranteed to be >= 0 here.
                        if (baseUnit != targetUnit) {
                            Triple(
                                ConverterUtils.convertFloatValueUnit(evalResult.value, baseUnit, targetUnit),
                                ConverterUtils.convertFloatValueUnit(evalResult.lowLimit, baseUnit, targetUnit),
                                ConverterUtils.convertFloatValueUnit(evalResult.highLimit, baseUnit, targetUnit),
                            )
                        } else {
                            Triple(
                                evalResult.value,
                                evalResult.lowLimit,
                                evalResult.highLimit,
                            )
                        }
                    }
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)) {
                        LinearGauge(
                            value         = displayValue,
                            lowLimit      = displayLow,
                            highLimit     = displayHigh,
                            modifier      = Modifier.fillMaxWidth().height(gaugeHeightDp),
                            labelProvider = { value ->
                                LocaleUtils.formatValueForDisplay(value.toString(), type.unit)
                            },
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Empty state cards
// ---------------------------------------------------------------------------

@Composable
fun NoUserSelectedCard(navController: NavController) {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Card(
            modifier  = Modifier.fillMaxWidth(0.9f),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Column(
                modifier              = Modifier.padding(horizontal = 24.dp, vertical = 32.dp).fillMaxWidth(),
                horizontalAlignment   = Alignment.CenterHorizontally,
                verticalArrangement   = Arrangement.spacedBy(16.dp),
            ) {
                Icon(imageVector = Icons.Filled.PersonSearch, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Text(text = stringResource(R.string.no_user_selected_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(text = stringResource(R.string.no_user_selected_message), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { navController.navigate(Routes.userDetail(-1)) }, modifier = Modifier.fillMaxWidth(0.8f)) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.action_add_user))
                }
            }
        }
    }
}

@Composable
fun NoMeasurementsCard(navController: NavController, selectedUserId: Int?) {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Card(
            modifier  = Modifier.fillMaxWidth(0.9f),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Column(
                modifier            = Modifier.padding(horizontal = 24.dp, vertical = 32.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(imageVector = Icons.Filled.Assessment, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.secondary)
                Text(text = stringResource(R.string.no_measurements_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(text = stringResource(R.string.no_measurements_message), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton(
                    onClick  = { if (selectedUserId != null) navController.navigate(Routes.measurementDetail(measurementId = null, userId = selectedUserId)) },
                    modifier = Modifier.fillMaxWidth(0.8f),
                    enabled  = selectedUserId != null,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.action_add_measurement))
                }
            }
        }
    }
}
