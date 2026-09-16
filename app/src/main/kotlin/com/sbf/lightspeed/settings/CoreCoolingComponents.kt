package com.sbf.lightspeed.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CoreCoolingRotarySchedulePicker(
    selectedDay: Int,
    selectedHour: Int,
    onScheduleChanged: (day: Int, hour: Int) -> Unit
) {
    val daysList = remember {
        listOf(
            Pair(java.util.Calendar.MONDAY, "Monday"),
            Pair(java.util.Calendar.TUESDAY, "Tuesday"),
            Pair(java.util.Calendar.WEDNESDAY, "Wednesday"),
            Pair(java.util.Calendar.THURSDAY, "Thursday"),
            Pair(java.util.Calendar.FRIDAY, "Friday"),
            Pair(java.util.Calendar.SATURDAY, "Saturday"),
            Pair(java.util.Calendar.SUNDAY, "Sunday")
        )
    }

    val hoursList = remember {
        (0..23).map { h ->
            val hourStr = String.format(java.util.Locale.US, "%02d:00 (%s)", h, if (h < 12) if (h == 0) "12 AM" else "$h AM" else if (h == 12) "12 PM" else "${h - 12} PM")
            Pair(h, hourStr)
        }
    }

    val currentDayIndex = remember(selectedDay) {
        val idx = daysList.indexOfFirst { it.first == selectedDay }
        if (idx >= 0) idx else 6 // Default Sunday
    }

    val currentHourIndex = remember(selectedHour) {
        selectedHour.coerceIn(0, 23)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RotaryWheelColumn(
                items = daysList,
                selectedIndex = currentDayIndex,
                onItemSelected = { _, item ->
                    onScheduleChanged(item.first, selectedHour)
                },
                labelProvider = { it.second },
                modifier = Modifier.weight(1f)
            )

            RotaryWheelColumn(
                items = hoursList,
                selectedIndex = currentHourIndex,
                onItemSelected = { _, item ->
                    onScheduleChanged(selectedDay, item.first)
                },
                labelProvider = { it.second },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun CoreCoolingTripleLockButton(
    onExecuteReboot: () -> Unit
) {
    var lockState by remember { mutableIntStateOf(0) } // 0: Idle, 1: Are you sure?, 2: Are you sure sure?

    // 5-second inactivity auto-reset timer
    LaunchedEffect(lockState) {
        if (lockState > 0) {
            kotlinx.coroutines.delay(5000L)
            lockState = 0
        }
    }

    val buttonColor = when (lockState) {
        1 -> Color(0xFFFFB300) // Amber
        2 -> Color(0xFFFF3D00) // Red
        else -> Color(0xFF00E5FF) // Electric Blue
    }

    val buttonText = when (lockState) {
        0 -> "Initiate Core Cooling (Reboot)"
        1 -> "Are you sure? (Tap again)"
        2 -> "Are you sure? (Confirm Reboot)"
        else -> "Initiate Core Cooling"
    }

    Button(
        onClick = {
            when (lockState) {
                0 -> lockState = 1
                1 -> lockState = 2
                2 -> {
                    lockState = 0
                    onExecuteReboot()
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = buttonColor.copy(alpha = if (lockState == 0) 0.18f else 0.85f),
            contentColor = if (lockState == 0) buttonColor else Color.Black
        ),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, buttonColor)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (lockState == 0) Icons.Default.RestartAlt else Icons.Default.Warning,
                contentDescription = null,
                tint = if (lockState == 0) buttonColor else Color.Black,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = buttonText,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}
