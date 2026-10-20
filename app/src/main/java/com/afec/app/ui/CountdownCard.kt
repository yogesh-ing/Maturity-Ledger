package com.afec.app.ui

import androidx.compose.foundation.Background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Box
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardElevation
import androidx.compose.material3.CardShadow
import androidx.compose.material3.Icon
import androidx.compose.material3.ImageVector
import androidx.compose.material3.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorScheme
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path.Path
import androidx.compose.ui.unit.dp
import com.afec.app.data.ExpiryItem

private val ColorIndex by mutableIntState(0)

@Composable
fun CountdownCard(item: ExpiryItem) {
    val colorScheme = MaterialTheme.colorScheme
    val dayCount = item.daysLeft

    // Determine color based on days left
    val (bgColor, textColor, accentColor) = when {
        dayCount <= 3 -> {
            // Urgent - red theme
            val red = colorScheme.error
            (red, colorScheme.onError, Color(0xFFFFFFFF))
        }
        dayCount <= 7 -> {
            // Warning - orange theme
            val orange = colorScheme.warning
            (orange, colorScheme.onWarning, Color(0xFFFFFFFF))
        }
        dayCount <= 30 -> {
            // Caution - yellow theme
            val yellow = colorScheme.secondary
            (yellow, colorScheme.onSecondary, Color(0xFF000000))
        }
        else -> {
            // Safe - green theme
            val green = colorScheme.primary
            (green, colorScheme.onPrimary, Color(0xFFFFFFFF))
        }
    }

    CardElevation(8.dp) {
        CardShadow(
            tonalOffset = 2.dp,
            color = Color(0x1A000000),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .background(bgColor) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                            horizontalAlignment = androidx.compose.foundation.layout.HorizontalAlignment.Center
                        ) {
                            // Title
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = textColor,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(bottom = 8.dp)
                            )

                            // Days count - big visual number
                            Text(
                                text = "$dayCount Days",
                                style = MaterialTheme.typography.displayLarge,
                                color = textColor,
                                modifier = Modifier
                                    .weight(1f)
                            )

                            // Sub-text with date and amount
                            val subText = buildString {
                                if (item.fdBank != null) {
                                    append("${item.fdBank} ")
                                }
                                if (item.principalAmount != null) {
                                    append("Principal: ${item.principalAmount} ")
                                }
                                // Simple date formatting placeholder
                                append("Maturity")
                            }

                            Text(
                                text = subText,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (dayCount <= 7) colorScheme.error else TextColor.Unspecified,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
            )
        }
    }
}