package com.afec.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.currentValueAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.surface.Surface
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.snapshotIn
import com.afec.app.data.ExpiryItem
import com.afec.app.data.AFECDatabase
import com.afec.app.data.ExpiryItemDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flowState

@Composable
fun Dashboard() {
    val context = LocalContext.current
    val database = AFECDatabase.getInstance(context)
    val dao = database.expiryItemDao()

    // Load items from database as state
    val items by currentValueAsState { dao.getAllActive() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = androidx.compose.foundation.layout.HorizontalAlignment.Center
    ) {
        // Header
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Text(
                text = "AFEC Countdown Dashboard",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .align(Alignment.CenterHorizontally)
            )
        }

        // Items list
        if (items.isEmpty()) {
            Text(
                text = "No active expiry items. Add items via SMS or manual entry.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        } else {
            items.reverse().forEach { item ->
                CountdownCard(item = item)
            }
        }

        // FAB for manual entry
        androidx.compose.material3.FloatingActionButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            onClick = {
                // TODO: Open manual entry dialog
            }
        ) {
            androidx.compose.material3.Icon(
                imageVector = androidx.compose.material3.Icons.Default.Add,
                contentDescription = "Add manual entry"
            )
        }
    }
}