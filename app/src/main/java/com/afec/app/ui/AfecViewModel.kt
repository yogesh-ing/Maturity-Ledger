package com.afec.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.afec.app.data.AfecRepository
import com.afec.app.data.CardMandate
import com.afec.app.data.MandateStatus
import com.afec.app.data.NotificationPreference
import com.afec.app.data.SmsMessage
import com.afec.app.data.TrackedInstrument
import com.afec.app.sms.HistoricalSmsScanner
import com.afec.app.sms.SmsScanRange
import com.afec.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** MVVM ViewModel exposing StateFlow to Compose (PRD §07). */
class AfecViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = AfecRepository(application)
    private val uiPrefs = application.getSharedPreferences("afec_ui", 0)

    val instruments: StateFlow<List<TrackedInstrument>> = repo.observeInstruments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val mandates: StateFlow<List<CardMandate>> = repo.observeMandates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val queue: StateFlow<List<SmsMessage>> = repo.observeQueue()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val preferences: StateFlow<NotificationPreference> = repo.observePreferences()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationPreference())

    private val _onboardingDone = MutableStateFlow(uiPrefs.getBoolean("onboarding_done", false))
    val onboardingDone: StateFlow<Boolean> = _onboardingDone.asStateFlow()

    private val _themeMode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(uiPrefs.getString("theme_mode", "SYSTEM")!!) }
            .getOrDefault(ThemeMode.SYSTEM)
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    /** FR-14 Material You is opt-in; the PRD indigo/teal system is the default. */
    private val _dynamicColor = MutableStateFlow(uiPrefs.getBoolean("dynamic_color", false))
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun clearMessage() { _message.value = null }

    private val _selectedSms = MutableStateFlow<SmsMessage?>(null)
    val selectedSms: StateFlow<SmsMessage?> = _selectedSms.asStateFlow()

    fun finishOnboarding() {
        uiPrefs.edit().putBoolean("onboarding_done", true).apply()
        _onboardingDone.value = true
    }

    fun setThemeMode(mode: ThemeMode) {
        uiPrefs.edit().putString("theme_mode", mode.name).apply()
        _themeMode.value = mode
    }

    fun setDynamicColor(enabled: Boolean) {
        uiPrefs.edit().putBoolean("dynamic_color", enabled).apply()
        _dynamicColor.value = enabled
    }

    fun saveInstrument(item: TrackedInstrument) = viewModelScope.launch {
        repo.saveInstrument(item)
        _message.value = "Saved ${item.title}"
    }

    fun saveMandate(mandate: CardMandate) = viewModelScope.launch {
        repo.saveMandate(mandate)
        _message.value = "Saved ${mandate.merchantName}"
    }

    fun setMandateStatus(mandate: CardMandate, status: MandateStatus) = viewModelScope.launch {
        repo.setMandateStatus(mandate, status)
        _message.value = when (status) {
            MandateStatus.PAUSED -> "${mandate.merchantName} paused"
            MandateStatus.ACTIVE -> "${mandate.merchantName} resumed"
            MandateStatus.REVOKED -> "${mandate.merchantName} marked cancelled"
        }
    }

    fun deleteMandate(mandate: CardMandate) = viewModelScope.launch {
        repo.deleteMandate(mandate); _message.value = "Mandate removed"
    }

    fun markCompleted(item: TrackedInstrument) = viewModelScope.launch {
        repo.markCompleted(item); _message.value = "Marked completed"
    }

    fun renew(item: TrackedInstrument) = viewModelScope.launch {
        repo.renewYearly(item); _message.value = "Rolled over to next year"
    }

    fun archive(item: TrackedInstrument) = viewModelScope.launch {
        repo.archive(item); _message.value = "Archived (recoverable for 30 days)"
    }

    fun restore(item: TrackedInstrument) = viewModelScope.launch {
        repo.restore(item); _message.value = "Restored"
    }

    fun deleteForever(item: TrackedInstrument) = viewModelScope.launch {
        repo.deleteForever(item); _message.value = "Deleted permanently"
    }

    fun approve(msg: SmsMessage, draft: TrackedInstrument) = viewModelScope.launch {
        repo.approveSms(msg, draft); _message.value = "Added to Dashboard"
    }

    fun dismiss(msg: SmsMessage) = viewModelScope.launch { repo.dismissSms(msg) }

    fun savePreferences(p: NotificationPreference) = viewModelScope.launch {
        repo.savePreferences(p); _message.value = "Alert settings saved"
    }

    fun loadSourceSms(item: TrackedInstrument) = viewModelScope.launch {
        _selectedSms.value = item.sourceSmsId?.let { repo.getSms(it) }
    }
    fun clearSourceSms() { _selectedSms.value = null }

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    /**
     * Explicit inbox scan. The default (onboarding / permission grant) keeps the
     * 90-day window; Settings can scan everything, the last month, or one month.
     */
    fun rescanSms(range: SmsScanRange = SmsScanRange.LastDays(90)) = performScan(range)

    /**
     * One inbox pass the first time the app opens with SMS access, so a user who
     * granted the permission outside onboarding still gets their history read.
     */
    fun autoScanOnce() {
        if (uiPrefs.getBoolean(KEY_AUTO_SCANNED, false)) return
        performScan(SmsScanRange.All) {
            uiPrefs.edit().putBoolean(KEY_AUTO_SCANNED, true).apply()
        }
    }

    private fun performScan(range: SmsScanRange, onDone: (() -> Unit)? = null) =
        viewModelScope.launch {
            if (_scanning.value) return@launch
            _scanning.value = true
            val result = HistoricalSmsScanner.scan(getApplication(), range)
            _scanning.value = false
            _message.value = when {
                result.error != null -> "Scan failed — ${result.error}"
                result.matched == 0 ->
                    "Read ${result.scanned} SMS — none looked financial"
                else ->
                    "Read ${result.scanned} SMS · ${result.matched} matched · ${result.added} new"
            }
            if (result.error == null) onDone?.invoke()
        }

    fun resetAllData() = viewModelScope.launch {
        repo.resetAllData(); _message.value = "All local data erased"
    }

    private companion object {
        const val KEY_AUTO_SCANNED = "sms_auto_scanned"
    }
}
