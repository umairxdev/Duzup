package com.duzup.app.navigation

import android.content.res.Resources
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.duzup.app.R
import com.duzup.app.core.backup.BackupException
import com.duzup.app.core.backup.RestoreMode
import com.duzup.app.core.designsystem.DuzupBackground
import com.duzup.app.core.designsystem.DuzupTheme
import com.duzup.app.core.designsystem.LocalBottomBarPadding
import com.duzup.app.core.designsystem.LocalReduceMotion
import com.duzup.app.core.notifications.rememberNotificationPermissionRequester
import com.duzup.app.core.notifications.rememberNotificationStatus
import com.duzup.app.feature.addedit.AddEditCallbacks
import com.duzup.app.feature.addedit.AddEditEvent
import com.duzup.app.feature.addedit.AddEditScreen
import com.duzup.app.feature.addedit.AddEditViewModel
import com.duzup.app.feature.addedit.AddPickerSheet
import com.duzup.app.feature.bills.DetailCallbacks
import com.duzup.app.feature.bills.DetailEvent
import com.duzup.app.feature.bills.DetailScreen
import com.duzup.app.feature.bills.DetailViewModel
import com.duzup.app.feature.calendar.CalendarScreen
import com.duzup.app.feature.calendar.CalendarViewModel
import com.duzup.app.feature.history.HistoryScreen
import com.duzup.app.feature.history.HistoryViewModel
import com.duzup.app.feature.home.HomeCallbacks
import com.duzup.app.feature.home.HomeScreen
import com.duzup.app.feature.home.HomeViewModel
import com.duzup.app.feature.notes.NotesCallbacks
import com.duzup.app.feature.notes.NotesScreen
import com.duzup.app.feature.notes.NotesViewModel
import com.duzup.app.feature.onboarding.OnboardingScreen
import com.duzup.app.feature.onboarding.OnboardingViewModel
import com.duzup.app.feature.reminders.RemindersScreen
import com.duzup.app.feature.reminders.RemindersViewModel
import com.duzup.app.feature.settings.SettingsCallbacks
import com.duzup.app.feature.settings.SettingsMessage
import com.duzup.app.feature.settings.SettingsScreen
import com.duzup.app.feature.settings.SettingsViewModel
import com.duzup.app.feature.stats.StatsScreen
import com.duzup.app.feature.stats.StatsViewModel
import java.time.LocalDate

@Composable
fun DuzupNavHost(
    startOnboarding: Boolean,
    pendingItemId: String?,
    onPendingItemConsumed: () -> Unit,
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val current = TopLevel.entries.firstOrNull { top -> destination?.hasRoute(top.routeClass) == true }
    val snackbar = remember { SnackbarHostState() }
    val reduceMotion = LocalReduceMotion.current
    val colors = DuzupTheme.colors
    val barSpace = if (current != null) bottomBarOccupiedHeight() else 0.dp
    var showPicker by remember { mutableStateOf(false) }

    fun navigateTopLevel(item: TopLevel) {
        navController.navigate(item.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Deep link from a notification: open that item's details once the host exists.
    LaunchedEffect(pendingItemId) {
        if (pendingItemId != null && !startOnboarding) {
            navController.navigate(DetailRoute(pendingItemId)) { launchSingleTop = true }
            onPendingItemConsumed()
        }
    }

    CompositionLocalProvider(LocalBottomBarPadding provides barSpace) {
        DuzupBackground {
            NavHost(
                navController = navController,
                startDestination = if (startOnboarding) OnboardingRoute else HomeRoute,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { if (reduceMotion) EnterTransition.None else fadeIn(tween(220)) },
                exitTransition = { if (reduceMotion) ExitTransition.None else fadeOut(tween(160)) },
                popEnterTransition = { if (reduceMotion) EnterTransition.None else fadeIn(tween(220)) },
                popExitTransition = { if (reduceMotion) ExitTransition.None else fadeOut(tween(160)) },
            ) {
                composable<OnboardingRoute> {
                    val vm: OnboardingViewModel = hiltViewModel()
                    val prefs by vm.prefs.collectAsStateWithLifecycle()
                    OnboardingScreen(
                        prefs = prefs,
                        onCurrency = { vm.setCurrency(it) },
                        onReminderTime = { vm.setReminderTime(it) },
                        onToggleOffset = vm::toggleOffset,
                        onAddOffset = vm::addOffset,
                        onPermissionAsked = { vm.markPermissionAsked() },
                        onFinish = {
                            vm.complete {
                                navController.navigate(HomeRoute) {
                                    popUpTo<OnboardingRoute> { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        },
                    )
                }

                composable<HomeRoute> {
                    val vm: HomeViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    HomeScreen(
                        state = state,
                        callbacks = HomeCallbacks(
                            onSelectTab = vm::selectTab,
                            onSelectKind = vm::selectKind,
                            onShiftMonth = vm::shiftMonth,
                            onResetMonth = vm::resetMonth,
                            onOpenItem = { navController.navigate(DetailRoute(it)) },
                            onAdd = { showPicker = true },
                            onOpenCalendar = { navigateTopLevel(TopLevel.CALENDAR) },
                            onOpenStats = { navController.navigate(StatsRoute) },
                            onOpenReminders = { navController.navigate(RemindersRoute) },
                            onOpenAllItems = { navController.navigate(AllItemsRoute) },
                        ),
                    )
                }

                composable<CalendarRoute> {
                    val vm: CalendarViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    CalendarScreen(
                        state = state,
                        onShiftMonth = vm::shiftMonth,
                        onToday = vm::goToToday,
                        onSelectDate = vm::selectDate,
                        onOpenBill = { navController.navigate(DetailRoute(it)) },
                    )
                }

                composable<NotesRoute> {
                    val vm: NotesViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    NotesScreen(
                        state = state,
                        callbacks = NotesCallbacks(
                            onAddQuickNote = vm::addQuickNote,
                            onOpen = { navController.navigate(DetailRoute(it)) },
                            onSetPinned = vm::setPinned,
                        ),
                    )
                }

                composable<StatsRoute> {
                    val vm: StatsViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    StatsScreen(
                        state = state,
                        onShiftMonth = vm::shiftMonth,
                        onResetMonth = vm::resetMonth,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable<SettingsRoute> {
                    SettingsDestination(
                        snackbar = snackbar,
                        onOpenReminders = { navController.navigate(RemindersRoute) },
                        onOpenStats = { navController.navigate(StatsRoute) },
                    )
                }

                composable<AddEditRoute> {
                    val vm: AddEditViewModel = hiltViewModel()
                    val state by vm.state.collectAsStateWithLifecycle()
                    val status by rememberNotificationStatus()
                    var askPermission by remember { mutableStateOf(false) }
                    val requestPermission = rememberNotificationPermissionRequester {
                        vm.markPermissionAsked()
                        navController.popBackStack()
                    }
                    LaunchedEffect(Unit) {
                        vm.events.collect { event ->
                            when (event) {
                                is AddEditEvent.Saved ->
                                    if (event.askNotificationPermission && status.permissionRequestable) askPermission = true
                                    else navController.popBackStack()
                                AddEditEvent.Missing -> navController.popBackStack()
                            }
                        }
                    }
                    AddEditScreen(
                        state = state,
                        callbacks = AddEditCallbacks(
                            onBack = { navController.popBackStack() },
                            onTitle = vm::setTitle,
                            onAmount = vm::setAmount,
                            onCurrency = vm::setCurrency,
                            onCategory = vm::setCategory,
                            onDueDate = vm::setDueDate,
                            onTime = vm::setTime,
                            onEndTime = vm::setEndTime,
                            onAllDay = vm::setAllDay,
                            onLocation = vm::setLocation,
                            onPriority = vm::setPriority,
                            onPinned = vm::setPinned,
                            onIncludeAge = vm::setIncludeAge,
                            onRemind = vm::setRemind,
                            onRecurrence = vm::setRecurrence,
                            onEndDate = vm::setEndDate,
                            onToggleWeekday = vm::toggleWeekday,
                            onAddRoutineTime = vm::addRoutineTime,
                            onRemoveRoutineTime = vm::removeRoutineTime,
                            onToggleOffset = vm::toggleOffset,
                            onAddOffset = vm::addCustomOffset,
                            onNotes = vm::setNotes,
                            onSave = vm::save,
                            onSaveCategory = vm::saveCategory,
                            onDeleteCategory = vm::deleteCategory,
                        ),
                    )
                    if (askPermission) {
                        PermissionRationaleDialog(
                            onAllow = { askPermission = false; requestPermission() },
                            onLater = {
                                askPermission = false
                                vm.markPermissionAsked()
                                navController.popBackStack()
                            },
                        )
                    }
                }

                composable<DetailRoute> {
                    val vm: DetailViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    val resources = LocalResources.current
                    LaunchedEffect(Unit) {
                        vm.events.collect { event ->
                            when (event) {
                                DetailEvent.Deleted -> navController.popBackStack()
                                is DetailEvent.Snoozed -> snackbar.showSnackbar(
                                    resources.getString(if (event.ok) R.string.snackbar_snoozed else R.string.snackbar_snooze_failed),
                                )
                            }
                        }
                    }
                    DetailScreen(
                        state = state,
                        callbacks = DetailCallbacks(
                            onBack = { navController.popBackStack() },
                            onEdit = { navController.navigate(AddEditRoute(itemId = vm.billId)) },
                            onMarkPaid = vm::markPaid,
                            onMarkDone = vm::markDone,
                            onEditPayment = vm::editPayment,
                            onUndo = { vm.undoPaid(it) },
                            onSkip = { vm.skip(it) },
                            onRestoreSkipped = { vm.restoreSkipped(it) },
                            onSnooze = vm::snooze,
                            onSetPinned = { vm.setPinned(it) },
                            onSetArchived = { vm.setArchived(it) },
                            onDelete = vm::delete,
                            paidInstant = vm::paidInstant,
                        ),
                    )
                }

                composable<AllItemsRoute> {
                    val vm: HistoryViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    HistoryScreen(
                        state = state,
                        onBack = { navController.popBackStack() },
                        onUpdate = vm::update,
                        onOpenBill = { navController.navigate(DetailRoute(it)) },
                        onSetArchived = vm::setArchived,
                    )
                }

                composable<RemindersRoute> {
                    val vm: RemindersViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    RemindersScreen(
                        state = state,
                        onBack = { navController.popBackStack() },
                        onOpenBill = { navController.navigate(DetailRoute(it)) },
                    )
                }
            }

            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = barSpace + 8.dp, start = 16.dp, end = 16.dp),
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = colors.buttonPrimary,
                    contentColor = colors.onButtonPrimary,
                    actionColor = colors.accent,
                    shape = RoundedCornerShape(16.dp),
                )
            }

            if (current != null) {
                DuzupBottomBar(
                    selected = current,
                    onSelect = ::navigateTopLevel,
                    onAdd = { showPicker = true },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }

    if (showPicker) {
        AddPickerSheet(
            onPickKind = { kind ->
                showPicker = false
                navController.navigate(AddEditRoute(kind = kind.name))
            },
            onQuickAdd = { kind, text ->
                showPicker = false
                navController.navigate(AddEditRoute(kind = kind.name, quick = text))
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun PermissionRationaleDialog(onAllow: () -> Unit, onLater: () -> Unit) {
    val colors = DuzupTheme.colors
    AlertDialog(
        onDismissRequest = onLater,
        containerColor = colors.surface,
        shape = RoundedCornerShape(24.dp),
        title = { Text(stringResource(R.string.permission_title), color = colors.textPrimary) },
        text = { Text(stringResource(R.string.permission_body), color = colors.textSecondary) },
        confirmButton = { TextButton(onClick = onAllow) { Text(stringResource(R.string.permission_allow), color = colors.accentText) } },
        dismissButton = { TextButton(onClick = onLater) { Text(stringResource(R.string.permission_later), color = colors.textSecondary) } },
    )
}

@Composable
private fun SettingsDestination(snackbar: SnackbarHostState, onOpenReminders: () -> Unit, onOpenStats: () -> Unit) {
    val vm: SettingsViewModel = hiltViewModel()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val pending by vm.pendingImport.collectAsStateWithLifecycle()
    val resources = LocalResources.current

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportTo(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.prepareImport(uri)
    }
    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(settingsMessageText(resources, it)) }
    }

    SettingsScreen(
        prefs = prefs,
        busy = busy,
        pendingImport = pending,
        callbacks = SettingsCallbacks(
            onTheme = vm::setTheme,
            onCurrency = vm::setCurrency,
            onReminderTime = vm::setReminderTime,
            onToggleOffset = vm::toggleDefaultOffset,
            onAddOffset = vm::addDefaultOffset,
            onOverdue = vm::setOverdueReminders,
            onSnooze = vm::setSnooze,
            onQuietEnabled = vm::setQuietHoursEnabled,
            onQuietStart = vm::setQuietStart,
            onQuietEnd = vm::setQuietEnd,
            onFirstDay = vm::setFirstDay,
            onHaptics = vm::setHaptics,
            onReduceMotion = vm::setReduceMotion,
            onHideAmounts = vm::setHideAmounts,
            onExport = { exportLauncher.launch("duzup-backup-${LocalDate.now()}.json") },
            onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            onConfirmImport = vm::confirmImport,
            onCancelImport = vm::cancelImport,
            onReset = vm::resetAll,
            onOpenReminders = onOpenReminders,
            onOpenStats = onOpenStats,
        ),
    )
}

private fun settingsMessageText(resources: Resources, message: SettingsMessage): String = when (message) {
    SettingsMessage.ExportDone -> resources.getString(R.string.snackbar_export_done)
    SettingsMessage.ExportFailed -> resources.getString(R.string.snackbar_export_failed)
    is SettingsMessage.ImportDone -> resources.getString(
        if (message.mode == RestoreMode.REPLACE) R.string.snackbar_import_replaced else R.string.snackbar_import_merged,
        message.result.bills, message.result.occurrences,
    )
    is SettingsMessage.ImportRejected -> resources.getString(
        when (message.reason) {
            BackupException.Reason.NOT_JSON -> R.string.import_error_not_backup
            BackupException.Reason.WRONG_APP -> R.string.import_error_not_backup
            BackupException.Reason.UNSUPPORTED_VERSION -> R.string.import_error_version
            BackupException.Reason.INVALID_DATA -> R.string.import_error_invalid
            BackupException.Reason.TOO_LARGE -> R.string.import_error_too_large
        },
    )
    SettingsMessage.ImportFailed -> resources.getString(R.string.snackbar_import_failed)
    SettingsMessage.ResetDone -> resources.getString(R.string.snackbar_reset_done)
}
