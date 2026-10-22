package com.afec.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.afec.app.data.TrackedInstrument
import com.afec.app.export.CsvExporter
import com.afec.app.export.PdfExporter
import com.afec.app.ui.theme.AfecTheme

private enum class Tab { DASHBOARD, QUEUE, SETTINGS }
private sealed interface SheetState {
    data object New : SheetState
    data class Edit(val item: TrackedInstrument) : SheetState
}

/**
 * Root: onboarding gate → bottom navigation (3 destinations max, PRD §06) → detail.
 *
 * A single Scaffold owns the snackbar host, the bottom bar and the FAB so that:
 *  - messages raised from Detail / onboarding are actually visible;
 *  - there is no nested Scaffold (and therefore no doubled content padding);
 *  - the FAB sits above the navigation bar on the Dashboard tab only.
 *
 * Everything, including the bottom sheet, is composed inside [AfecTheme] and a
 * background Surface — previously the Add/Edit sheet fell back to the default
 * Material palette and dark-mode screens drew light-on-light.
 */
@Composable
fun AfecApp(
    viewModel: AfecViewModel,
    initialInstrumentId: String? = null,
    openQueueInitially: Boolean = false
) {
    val context = LocalContext.current
    val themeMode by viewModel.themeMode.collectAsState()
    val dynamicColor by viewModel.dynamicColor.collectAsState()
    val onboardingDone by viewModel.onboardingDone.collectAsState()
    val instruments by viewModel.instruments.collectAsState()
    val queue by viewModel.queue.collectAsState()
    val preferences by viewModel.preferences.collectAsState()
    val message by viewModel.message.collectAsState()

    var tab by remember { mutableStateOf(if (openQueueInitially) Tab.QUEUE else Tab.DASHBOARD) }
    var selectedId by remember { mutableStateOf(initialInstrumentId) }
    var sheet by remember { mutableStateOf<SheetState?>(null) }
    var firstLinkHandled by remember { mutableStateOf(false) }

    fun smsPermissionGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) ==
            PackageManager.PERMISSION_GRANTED

    var hasSmsPermission by remember { mutableStateOf(smsPermissionGranted()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasSmsPermission = grants[Manifest.permission.READ_SMS] == true ||
            grants[Manifest.permission.RECEIVE_SMS] == true
        if (hasSmsPermission) viewModel.rescanSms() // 90-day historical scan (§04)
        viewModel.finishOnboarding()
    }

    fun requestPermissions() {
        val perms = mutableListOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.POST_NOTIFICATIONS
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    // Permission can be granted or revoked in system Settings while we are paused;
    // re-read it on every resume instead of trusting the first composition.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = smsPermissionGranted()
                if (granted && !hasSmsPermission) viewModel.rescanSms()
                hasSmsPermission = granted
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }
    LaunchedEffect(initialInstrumentId) {
        if (!firstLinkHandled && initialInstrumentId != null) {
            selectedId = initialInstrumentId
            firstLinkHandled = true
        }
    }

    val selected = instruments.firstOrNull { it.id == selectedId }
    BackHandler(enabled = selected != null) {
        selectedId = null
        viewModel.clearSourceSms()
    }

    AfecTheme(mode = themeMode, dynamicColor = dynamicColor) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            // Manual Entry sheet (add / edit) — Screen 03. Inside the theme.
            sheet?.let { state ->
                ManualEntrySheet(
                    initial = (state as? SheetState.Edit)?.item,
                    onDismiss = { sheet = null },
                    onSave = { viewModel.saveInstrument(it); sheet = null }
                )
            }

            Scaffold(
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    if (onboardingDone && selected == null) {
                        NavigationBar {
                            NavigationBarItem(
                                selected = tab == Tab.DASHBOARD,
                                onClick = { tab = Tab.DASHBOARD },
                                icon = { Icon(Icons.Default.Home, contentDescription = null) },
                                label = { Text("Dashboard") }
                            )
                            NavigationBarItem(
                                selected = tab == Tab.QUEUE,
                                onClick = { tab = Tab.QUEUE },
                                icon = {
                                    BadgedBox(badge = {
                                        if (queue.isNotEmpty()) {
                                            Badge {
                                                Text(
                                                    if (queue.size > 99) "99+"
                                                    else "${queue.size}"
                                                )
                                            }
                                        }
                                    }) { Icon(Icons.Default.Inbox, contentDescription = null) }
                                },
                                label = { Text("Inbox") }
                            )
                            NavigationBarItem(
                                selected = tab == Tab.SETTINGS,
                                onClick = { tab = Tab.SETTINGS },
                                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                label = { Text("Settings") }
                            )
                        }
                    }
                },
                floatingActionButton = {
                    if (onboardingDone && selected == null && tab == Tab.DASHBOARD) {
                        ExtendedFloatingActionButton(
                            onClick = { sheet = SheetState.New },
                            icon = { Icon(Icons.Default.Add, contentDescription = null) },
                            text = { Text("Add") }
                        )
                    }
                }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    when {
                        !onboardingDone -> OnboardingScreen(
                            onRequestPermissions = { requestPermissions() },
                            onSkipPermissions = { viewModel.finishOnboarding() }
                        )
                        selected != null -> DetailScreen(
                            item = selected,
                            viewModel = viewModel,
                            onBack = { selectedId = null; viewModel.clearSourceSms() },
                            onEdit = { sheet = SheetState.Edit(it) }
                        )
                        tab == Tab.DASHBOARD -> DashboardScreen(
                            instruments = instruments,
                            manualOnlyMode = !hasSmsPermission,
                            onAdd = { sheet = SheetState.New },
                            onOpenSettings = { tab = Tab.SETTINGS },
                            onItemClick = { selectedId = it.id },
                            onRestore = { viewModel.restore(it) },
                            onDeleteForever = { viewModel.deleteForever(it) }
                        )
                        tab == Tab.QUEUE -> QueueScreen(
                            queue = queue,
                            onApprove = { msg, draft -> viewModel.approve(msg, draft) },
                            onDismiss = { viewModel.dismiss(it) }
                        )
                        else -> SettingsScreen(
                            preferences = preferences,
                            themeMode = themeMode,
                            dynamicColor = dynamicColor,
                            hasSmsPermission = hasSmsPermission,
                            onSavePreferences = { viewModel.savePreferences(it) },
                            onThemeMode = { viewModel.setThemeMode(it) },
                            onDynamicColor = { viewModel.setDynamicColor(it) },
                            onRescanSms = { viewModel.rescanSms() },
                            onExportCsv = { CsvExporter.shareCsv(context, instruments) },
                            onExportPdf = { PdfExporter.sharePdf(context, instruments) },
                            onResetAllData = { viewModel.resetAllData() }
                        )
                    }
                }
            }
        }
    }
}
