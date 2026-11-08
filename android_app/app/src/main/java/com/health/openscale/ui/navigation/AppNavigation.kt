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
package com.health.openscale.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailDefaults
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.window.core.layout.WindowSizeClass
import com.health.openscale.BuildConfig
import com.health.openscale.R
import com.health.openscale.core.data.IconResource
import com.health.openscale.core.data.User
import com.health.openscale.ui.navigation.Routes.getIconForRoute
import com.health.openscale.ui.shared.SharedViewModel
import com.health.openscale.ui.screen.dialog.AssistedWeighingDialog
import com.health.openscale.ui.screen.dialog.BluetoothUserInteractionDialog
import com.health.openscale.ui.screen.settings.BluetoothViewModel
import com.health.openscale.ui.screen.settings.SettingsViewModel
import com.health.openscale.ui.theme.AppBrandBlue
import com.health.openscale.core.stoma.StomaTrackingRepository
import com.health.openscale.core.stoma.StomaNotificationManager
import com.health.openscale.core.medication.MedicationRepository
import com.health.openscale.ui.screen.dialog.UniversalAddSheet
import com.health.openscale.ui.screen.medication.AddMedicationIntakeDialog
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Main composable function that sets up the application's navigation structure.
 * This includes a modal navigation drawer, a top app bar, a snackbar host for displaying
 * messages, and a [NavHost] for handling screen transitions based on defined routes.
 *
 * It observes [SharedViewModel] for shared UI state like the top bar title, actions,
 * user information, and snackbar messages.
 *
 * @param sharedViewModel The [SharedViewModel] instance shared across multiple screens,
 *                        providing access to shared data and UI event channels.
 */
@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun AppNavigation(sharedViewModel: SharedViewModel) {
    val resources = LocalResources.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Initialize ViewModels that might be needed by screens within this navigation structure
    val settingsViewModel: SettingsViewModel = hiltViewModel()

    val bluetoothViewModel: BluetoothViewModel = hiltViewModel()

    val context = LocalContext.current
    val stomaRepo = remember { StomaTrackingRepository(context) }
    val medRepo = remember { MedicationRepository(context) }
    var showUniversalAddSheet by remember { mutableStateOf(false) }
    var showMedicationAddDialog by remember { mutableStateOf(false) }

    // Observe the current navigation route
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    // Define the main navigation routes that appear in the navigation drawer
    val mainRoutes = listOf(
        Routes.OVERVIEW,
        Routes.GRAPH,
        Routes.TABLE,
        Routes.INSIGHTS,
        Routes.DRINKS,
        Routes.SETTINGS
    )

    // The primary routes displayed in the bottom navigation bar for quick 1-tap thumb access
    val bottomBarRoutes = listOf(
        Routes.OVERVIEW,
        Routes.GRAPH,
        Routes.TABLE,
        Routes.INSIGHTS,
        Routes.DRINKS
    )

    // Collect UI states from SharedViewModel
    val topBarTitleFromVM by sharedViewModel.topBarTitle.collectAsState()
    val topBarActions by sharedViewModel.topBarActions.collectAsState()
    val isInContextualSelectionMode by sharedViewModel.isInContextualSelectionMode.collectAsState()
    val allUsers by sharedViewModel.allUsers.collectAsState()
    val selectedUser by sharedViewModel.selectedUser.collectAsState()

    // Resolve the title for the TopAppBar.
    // The title can be provided as a direct String or as a @StringRes Int.
    val topBarTitle = when (val titleData = topBarTitleFromVM) {
        is String -> titleData
        is Int -> if (titleData != Routes.NO_TITLE_RESOURCE_ID) stringResource(id = titleData) else ""
        else -> "" // Default to empty string if title data is null or unexpected type
    }

    // From medium width on (tablets, unfolded foldables, phones in landscape) a navigation rail
    // replaces the modal drawer.
    val useNavigationRail = currentWindowAdaptiveInfoV2().windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    // The main destination whose back stack is showing, also on its sub-pages (e.g. Settings
    // while editing a user), so the drawer/rail can mark it and a second tap can reset it.
    // Only the start destination and at most one other main destination are on the back stack.
    val activeMainRoute = remember(currentBackStackEntry) {
        mainRoutes.lastOrNull { route ->
            runCatching { navController.getBackStackEntry(route) }.isSuccess
        }
    }

    val navigateToMainRoute: (String) -> Unit = { route ->
        // Tapping the destination that is already active returns to its start page.
        if (route == activeMainRoute) {
            navController.popBackStack(route, inclusive = false)
        } else {
            navController.navigate(route) {
                // Pop up to the start destination of the graph to avoid building up a large back stack.
                popUpTo(navController.graph.startDestinationId) {
                    saveState = true // Save the state of popped destinations.
                }
                // Avoid multiple copies of the same destination when reselecting the same item.
                launchSingleTop = true
                // Restore state when reselecting a previously visited item.
                restoreState = true
            }
        }
    }

    LaunchedEffect(snackbarHostState, sharedViewModel, settingsViewModel, bluetoothViewModel) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            kotlinx.coroutines.flow.merge(
                sharedViewModel.snackbarEvents,
                settingsViewModel.snackbarEvents,
                bluetoothViewModel.snackbarEvents
            )
            .distinctUntilChanged { a, b ->
                    a.messageResId == b.messageResId && a.message == b.message && a.messageFormatArgs == b.messageFormatArgs
            }
            .debounce(150.milliseconds)
            .collect { evt ->
                val msg = evt.message ?: resources.getString(
                    requireNotNull(evt.messageResId),
                    *evt.messageFormatArgs.toTypedArray()
                )
                val action = evt.actionLabel ?: evt.actionLabelResId?.let { resources.getString(it) }
                val res = snackbarHostState.run {
                    currentSnackbarData?.dismiss()
                    showSnackbar(
                        message = msg,
                        actionLabel = action,
                        duration = evt.duration
                    )
                }
                if (res == SnackbarResult.ActionPerformed) evt.onAction?.invoke()
            }
        }
    }

    // --- Global dialogs shown above the navigation host ---
    AssistedWeighingDialog(
        sharedViewModel = sharedViewModel,
        bluetoothViewModel = bluetoothViewModel,
        allUsers = allUsers
    )

    BluetoothUserInteractionDialog(
        bluetoothViewModel = bluetoothViewModel,
        sharedViewModel = sharedViewModel
    )

    Row {
        if (useNavigationRail) {
            AppNavigationRail(
                mainRoutes = mainRoutes,
                activeMainRoute = activeMainRoute,
                onNavigate = navigateToMainRoute
            )
        }
        Scaffold(
            modifier = if (useNavigationRail) {
                // Not the top: the top bar still needs the status bar inset. The bottom one is
                // covered by the filler below the NavHost.
                Modifier.consumeWindowInsets(
                    NavigationRailDefaults.windowInsets.only(WindowInsetsSides.Start + WindowInsetsSides.Bottom)
                )
            } else {
                Modifier
            },
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) { snackbarData ->
                    // Custom Snackbar appearance defined here.
                    Snackbar(
                        modifier = Modifier.padding(8.dp), // Padding around the snackbar.
                        shape = RoundedCornerShape(8.dp), // Rounded corners for the snackbar.
                        containerColor = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = stringResource(R.string.app_logo_content_description), // Accessibility.
                                tint = LocalContentColor.current // Uses the contentColor from Snackbar.
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(snackbarData.visuals.message)
                        }
                    }
                }
            },
            topBar = {
                TopAppBar(
                    title = {
                        if (currentRoute == Routes.OVERVIEW) {
                            Column {
                                Text(
                                    text = "openScale Santé",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (selectedUser != null) {
                                    Text(
                                        text = selectedUser?.name ?: "Patiente",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = topBarTitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    navigationIcon = {
                        if (currentRoute !in mainRoutes) {
                            val backDispatcher =
                                LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
                            IconButton(onClick = {
                                if (backDispatcher != null) backDispatcher.onBackPressed()
                                else navController.popBackStack()
                            }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.content_desc_back)
                                )
                            }
                        }
                    },
                        actions = {
                            // Display actions defined in SharedViewModel.
                            topBarActions.forEach { action ->
                                val contentDesc = action.contentDescriptionResId?.let { stringResource(id = it) }
                                    ?: action.contentDescription
                                IconButton(onClick = action.onClick) {
                                    Icon(
                                        imageVector = action.icon,
                                        contentDescription = contentDesc,
                                        tint = action.tint ?: LocalContentColor.current
                                    )
                                }
                                // If the action has associated dropdown content, invoke it here.
                                // This allows TopAppBar actions to also host DropdownMenus.
                                action.dropdownContent?.invoke()
                            }

                            // Show user switcher dropdown if on a main route and users exist.
                            if (!isInContextualSelectionMode && currentRoute in mainRoutes && allUsers.isNotEmpty() && currentRoute != Routes.SETTINGS) {
                                UserDropdownAsAction(
                                    users = allUsers,
                                    selectedUser = selectedUser,
                                    onUserSelected = { userId ->
                                        sharedViewModel.selectUser(userId)
                                        // Consider closing the drawer if open, or other UI updates.
                                    },
                                    onManageUsersClicked = {
                                        navController.navigate(Routes.USER_SETTINGS)
                                        // Consider closing the drawer if open.
                                    }
                                )
                            }

                            // Settings icon in top right
                            if (!isInContextualSelectionMode && currentRoute != Routes.SETTINGS) {
                                IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = stringResource(R.string.route_title_settings)
                                    )
                                }
                            }
                        }
                    )
                },
                bottomBar = {
                    if (!useNavigationRail && currentRoute in mainRoutes) {
                        AppBottomNavigationBar(
                            bottomRoutes = bottomBarRoutes,
                            activeMainRoute = activeMainRoute,
                            onNavigate = navigateToMainRoute
                        )
                    }
                },
                floatingActionButton = {
                    if (!isInContextualSelectionMode && currentRoute in bottomBarRoutes) {
                        FloatingActionButton(
                            onClick = { showUniversalAddSheet = true },
                            containerColor = Color(0xFF00365B),
                            contentColor = Color.White,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Ajouter un soin, boisson ou traitement"
                            )
                        }
                    }
                }
            ) { innerPadding ->
                AppNavHost(
                    navController = navController,
                    innerPadding = innerPadding,
                    sharedViewModel = sharedViewModel,
                    settingsViewModel = settingsViewModel,
                    bluetoothViewModel = bluetoothViewModel
                )
            }
        }

    // Modal Sheet: Universal Action (+)
    if (showUniversalAddSheet) {
        UniversalAddSheet(
            onDismiss = { showUniversalAddSheet = false },
            onLogPouchChange = {
                stomaRepo.logPouchChange()
                if (stomaRepo.isNotificationEnabled()) {
                    StomaNotificationManager.showOrUpdateNotification(context, stomaRepo.getTodaySummary())
                }
                scope.launch {
                    snackbarHostState.showSnackbar("Changement de poche enregistré ✓")
                }
            },
            onLogSupportChange = {
                stomaRepo.logSupportChange()
                if (stomaRepo.isNotificationEnabled()) {
                    StomaNotificationManager.showOrUpdateNotification(context, stomaRepo.getTodaySummary())
                }
                scope.launch {
                    snackbarHostState.showSnackbar("Changement complet du support enregistré ✓")
                }
            },
            onAddWater250 = {
                stomaRepo.addWater(250)
                if (stomaRepo.isNotificationEnabled()) {
                    StomaNotificationManager.showOrUpdateNotification(context, stomaRepo.getTodaySummary())
                }
                scope.launch {
                    snackbarHostState.showSnackbar("+ 250 ml d'eau enregistré 💧")
                }
            },
            onAddWater500 = {
                stomaRepo.addWater(500)
                if (stomaRepo.isNotificationEnabled()) {
                    StomaNotificationManager.showOrUpdateNotification(context, stomaRepo.getTodaySummary())
                }
                scope.launch {
                    snackbarHostState.showSnackbar("+ 500 ml d'eau enregistré 🥤")
                }
            },
            onOpenMedicationDialog = {
                showMedicationAddDialog = true
            },
            onNewMeasurement = {
                if (selectedUser != null && selectedUser?.id != 0) {
                    navController.navigate(
                        Routes.measurementDetail(
                            measurementId = null,
                            userId = selectedUser?.id
                        )
                    )
                } else {
                    scope.launch {
                        snackbarHostState.showSnackbar("Veuillez sélectionner un profil d'abord")
                    }
                }
            }
        )
    }

    // Dialog: Add Medication Intake
    if (showMedicationAddDialog) {
        AddMedicationIntakeDialog(
            onDismiss = { showMedicationAddDialog = false },
            onConfirm = { name, dosage, category, pain, postPain, notes ->
                medRepo.logIntake(
                    name = name,
                    dosage = dosage,
                    category = category,
                    initialPainScore = pain,
                    postPainScore = postPain,
                    notes = notes
                )
                scope.launch {
                    snackbarHostState.showSnackbar("Prise de $name enregistrée 💊")
                }
                showMedicationAddDialog = false
            }
        )
    }
}

@Composable
private fun mainRouteTitle(route: String): String {
    val titleResId = Routes.getTitleResourceId(route)
    return if (titleResId != Routes.NO_TITLE_RESOURCE_ID) {
        stringResource(id = titleResId)
    } else {
        route // Fallback to the raw route string if no title resource ID is defined.
    }
}

/**
 * Navigation rail with the same destinations as the modal drawer, used from medium window
 * width on. Scrolls vertically so all items stay reachable on short windows (phone landscape).
 */
@Composable
private fun AppNavigationRail(
    mainRoutes: List<String>,
    activeMainRoute: String?,
    onNavigate: (String) -> Unit
) {
    NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            mainRoutes.forEach { route ->
                if (route == Routes.SETTINGS) {
                    HorizontalDivider(modifier = Modifier.width(48.dp).padding(vertical = 4.dp))
                }
                val titleText = mainRouteTitle(route)
                NavigationRailItem(
                    selected = activeMainRoute == route,
                    onClick = { onNavigate(route) },
                    icon = { Icon(imageVector = getIconForRoute(route), contentDescription = null) },
                    label = {
                        Text(
                            text = titleText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }
    }
}

/**
 * Modern Material 3 navigation bar for mobile portrait viewports.
 * Gives immediate 1-tap thumb access to the primary screens.
 */
@Composable
private fun AppBottomNavigationBar(
    bottomRoutes: List<String>,
    activeMainRoute: String?,
    onNavigate: (String) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp
    ) {
        bottomRoutes.forEach { route ->
            val isSelected = activeMainRoute == route
            val titleText = mainRouteTitle(route)
            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigate(route) },
                icon = {
                    Icon(
                        imageVector = getIconForRoute(route),
                        contentDescription = titleText
                    )
                },
                label = {
                    Text(
                        text = titleText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

/**
 * Composable function for an interactive profile pill in the TopAppBar to display
 * the active user and provide a quick dropdown menu to switch users or manage profiles.
 *
 * @param users List of available [User]s to display in the dropdown.
 * @param selectedUser The currently selected [User], or null if no user is selected.
 * @param onUserSelected Callback invoked with the user's ID when a user is selected.
 * @param onManageUsersClicked Callback invoked when "Manage Users" is clicked.
 * @param modifier Optional [Modifier] for this composable.
 */
@Composable
fun UserDropdownAsAction(
    users: List<User>,
    selectedUser: User?,
    onUserSelected: (Int) -> Unit,
    onManageUsersClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    if (users.isEmpty()) {
        return
    }

    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.padding(end = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 6.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val iconResource = selectedUser?.icon?.resource
                if (iconResource is IconResource.PainterResource) {
                    Image(
                        painter = painterResource(id = iconResource.id),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp).clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = selectedUser?.name?.take(1)?.uppercase() ?: "U",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Text(
                    text = selectedUser?.name ?: "",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            users.forEach { user ->
                DropdownMenuItem(
                    text = {
                        Text(
                            user.name,
                            fontWeight = if (user.id == selectedUser?.id) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onUserSelected(user.id)
                        expanded = false
                    },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(
                                    if (user.id == selectedUser?.id) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerHigh
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = user.name.take(1).uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (user.id == selectedUser?.id) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    trailingIcon = {
                        if (user.id == selectedUser?.id) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = stringResource(R.string.content_desc_selected_user_indicator),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            DropdownMenuItem(
                text = { Text(stringResource(R.string.manage_users)) },
                onClick = {
                    onManageUsersClicked()
                    expanded = false
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = null
                    )
                }
            )
        }
    }
}
