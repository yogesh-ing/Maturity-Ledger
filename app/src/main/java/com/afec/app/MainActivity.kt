package com.afec.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.afec.app.notifications.NotificationHelper
import com.afec.app.ui.AfecApp
import com.afec.app.ui.AfecViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Deep-link extras from milestone notifications (PRD §10 tap → Instrument Detail).
        val instrumentId = intent?.getStringExtra(NotificationHelper.EXTRA_INSTRUMENT_ID)
        val openQueue = intent?.getBooleanExtra("extra_open_queue", false) ?: false
        setContent {
            val vm: AfecViewModel = viewModel()
            AfecApp(vm, initialInstrumentId = instrumentId, openQueueInitially = openQueue)
        }
    }
}
