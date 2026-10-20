package com.afec.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.afec.app.data.TrackedInstrument
import com.afec.app.export.CsvExporter
import com.afec.app.ui.theme.AfecTheme

private enum class Tab { DASHBOARD, QUEUE, SETTINGS }
private sealed interface SheetState {
    data object New : SheetState
    data class Edit(val item: TrackedInstrument) : SheetState
}

/** Root: onboarding gate → bottom navigation (3 destinations max, PRD §06) → detail. */
@Composable
fun AfecApp(
    viewModel: AfecViewModel,
    initialInstrumentId: String? = null,
    openQueueInitially: Boolean = false
) {
    val context = LocalContext.current
    val themeMode by viewModel.themeMode.collectAsState()
    val onboardingDone by viewModel.onboardingDone.collectAsState()
    val instruments by viewModel.instruments.collectAsState()
    val queue by viewModel.queue.collectAsState()
    val preferences by viewModel.preferences.collectAsState()
    val message by viewModel.message.collectAsState()

    var tab by remember { mutableStateOf(if (openQueueInitially) Tab.QUEUE else Tab.DASHBOARD) }
    var selectedId by remember { mutableStateOf(initialInstrumentId) }
    var sheet by remember { mutableStateOf<SheetState?>(null) }
    var firstLinkHandled by remember { mutableStateOf(false) }

    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

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

    // Manual Entry sheet (add / edit) — Screen 03
    sheet?.let { state ->
        ManualEntrySheet(
            initial = (state as? SheetState.Edit)?.item,
            onDismiss = { sheet = null },
            onSave = { viewModel.saveInstrument(it); sheet = null }
        )
    }

    AfecTheme(mode = themeMode) {
        if (!onboardingDone) {
            OnboardingScreen(
                onRequestPermissions = { requestPermissions() },
                onSkipPermissions = { viewModel.finishOnboarding() }
            )
            return@AfecTheme
        }

        val selected = instruments.firstOrNull { it.id == selectedId }
        if (selected != null) {
            DetailScreen(
                item = selected,
                viewModel = viewModel,
                onBack = { selectedId = null; viewModel.clearSourceSms() },
                onEdit = { sheet = SheetState.Edit(it) }
            )
            return@AfecTheme
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == Tab.DASHBOARD,
                        onClick = { tab = Tab.DASHBOARD },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
                        label = { Text("Dashboard") }
                    )
                    NavigationBarItem(
                        selected = tab == Tab.QUEUE,
                        onClick = { tab = Tab.QUEUE },
                        icon = {
                            BadgedBox(badge = {
                                if (queue.isNotEmpty()) Badge { Text("${queue.size}") }
                            }) { Icon(Icons.Default.Inbox, contentDescription = "Inbox") }
                        },
                        label = { Text("Inbox") }
                    )
                    NavigationBarItem(
                        selected = tab == Tab.SETTINGS,
                        onClick = { tab = Tab.SETTINGS },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") }
                    )
                }
            }
        ) { padding ->
            androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
                when (tab) {
                    Tab.DASHBOARD -> DashboardScreen(
                        instruments = instruments,
                        manualOnlyMode = !hasSmsPermission,
                        onAdd = { sheet = SheetState.New },
                        onOpenSettings = { tab = Tab.SETTINGS },
                        onItemClick = { selectedId = it.id },
                        onRestore = { viewModel.restore(it) },
                        onDeleteForever = { viewModel.deleteForever(it) }
                    )
                    Tab.QUEUE -> QueueScreen(
                        queue = queue,
                        onApprove = { msg, draft -> viewModel.approve(msg, draft) },
                        onDismiss = { viewModel.dismiss(it) }
                    )
                    Tab.SETTINGS -> SettingsScreen(
                        preferences = preferences,
                        themeMode = themeMode,
                        hasSmsPermission = hasSmsPermission,
                        onSavePreferences = { viewModel.savePreferences(it) },
                        onThemeMode = { viewModel.setThemeMode(it) },
                        onRescanSms = { viewModel.rescanSms() },
                        onExportCsv = { CsvExporter.shareCsv(context, instruments) },
                        onResetAllData = { viewModel.resetAllData() }
                    )
                }
            }
        }
    }
}
