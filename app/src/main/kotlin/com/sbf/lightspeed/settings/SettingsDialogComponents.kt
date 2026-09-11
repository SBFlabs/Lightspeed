package com.sbf.lightspeed.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.view.WindowManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.LightspeedPreferences
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.sbf.lightspeed.system.LightspeedHapticEngine
import kotlin.math.roundToInt
import androidx.compose.foundation.Canvas



@Composable
fun CentralCommandDeckDialog(
    context: Context,
    prefs: SharedPreferences,
    onDismiss: () -> Unit,
    onRefreshNeeded: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val thresholdPx = with(density) { 90.dp.toPx() }
    val dragOffsetY = remember { Animatable(0f) }
    var hasCrossedThreshold by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val glassVisuals = rememberDeckGlassVisuals(context)
        val backdropVisuals = rememberDeckBackdropVisuals(context)
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dialogWindow?.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                val lp = dialogWindow?.attributes
                if (lp != null) {
                    lp.blurBehindRadius = backdropVisuals.blurBehindRadius
                    dialogWindow.attributes = lp
                }
            }
            dialogWindow?.setDimAmount(backdropVisuals.dimAmount)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.98f)
                    .fillMaxHeight(0.88f)
                    .offset { IntOffset(0, dragOffsetY.value.roundToInt()) }
                    .clip(RoundedCornerShape(glassVisuals.shapeCornerRadius))
                    .background(glassVisuals.backgroundBrush)
                    .border(glassVisuals.borderWidth, glassVisuals.borderBrush, RoundedCornerShape(glassVisuals.shapeCornerRadius)),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(glassVisuals.shapeCornerRadius)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (glassVisuals.showTopGlare) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = glassVisuals.topGlareAlpha),
                                            Color.White.copy(alpha = glassVisuals.topGlareAlpha * 0.45f),
                                            Color(0xFF80D8FF).copy(alpha = glassVisuals.topGlareAlpha * 0.15f),
                                            Color.Transparent
                                        ),
                                        center = Offset(x = 350f, y = 0f),
                                        radius = 650f
                                    )
                                )
                        )
                    }
                    if (glassVisuals.showBottomCaustic) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(70.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color(0xFF00E5FF).copy(alpha = 0.08f),
                                            Color.White.copy(alpha = 0.14f)
                                        )
                                    )
                                )
                        )
                    }
                    if (glassVisuals.showRefractiveRim) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .padding(2.5.dp)
                                .border(
                                    1.dp,
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF00E5FF).copy(alpha = 0.50f),
                                            Color.White.copy(alpha = 0.65f),
                                            Color(0xFFE040FB).copy(alpha = 0.45f),
                                            Color.Transparent
                                        ),
                                        start = Offset(0f, 0f),
                                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                                    ),
                                    RoundedCornerShape(glassVisuals.shapeCornerRadius - 2.5.dp)
                                )
                        )
                    }
                    if (glassVisuals.innerChamferAlpha > 0f) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .padding(1.dp)
                                .border(
                                    0.8.dp,
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = glassVisuals.innerChamferAlpha),
                                            Color.Transparent
                                        )
                                    ),
                                    RoundedCornerShape(glassVisuals.shapeCornerRadius - 1.dp)
                                )
                        )
                    }
                    if (glassVisuals.showNoiseGrain) {
                        Canvas(modifier = Modifier.matchParentSize()) {
                            drawRect(
                                brush = GlassNoiseTexture.getBrush(),
                                alpha = glassVisuals.noiseAlpha
                            )
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                    // Header Drag Region (drag gesture scoped to drag handle & header row)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragStart = { hasCrossedThreshold = false },
                                    onDragEnd = {
                                        if (dragOffsetY.value >= thresholdPx) {
                                            onDismiss()
                                        } else {
                                            coroutineScope.launch {
                                                dragOffsetY.animateTo(
                                                    0f,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessMediumLow
                                                    )
                                                )
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        coroutineScope.launch {
                                            dragOffsetY.animateTo(0f)
                                        }
                                    },
                                    onVerticalDrag = { change, dragAmount ->
                                        change.consume()
                                        val current = dragOffsetY.value + dragAmount
                                        val newOffset = if (current <= 0f) 0f
                                        else if (current <= thresholdPx) current
                                        else thresholdPx + (current - thresholdPx) * 0.35f

                                        if (!hasCrossedThreshold && newOffset >= thresholdPx) {
                                            hasCrossedThreshold = true
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            LightspeedHapticEngine.tick(context)
                                        } else if (hasCrossedThreshold && newOffset < thresholdPx) {
                                            hasCrossedThreshold = false
                                        }

                                        coroutineScope.launch {
                                            dragOffsetY.snapTo(newOffset)
                                        }
                                    }
                                )
                            }
                    ) {
                        // Sleek Drag Handle Pill
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp, bottom = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 38.dp, height = 4.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.35f))
                            )
                        }

                        // Header Row: Title + Close Icon
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Bolt,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        "FLIGHT CONTROL DECK",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        "Master avionics & universal shortcuts",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                    )
                                }
                            }

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(32.dp),
                                colors = IconButtonDefaults.iconButtonColors(containerColor = Color.White.copy(alpha = 0.08f))
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                // Scrollable Flight Core Controls & Shortcut Mapping
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Card 0: Communication Protocol (Language Engine)
                    LanguageEngineCard(context = context)

                    // Card 1: Master Flight Controls
                    FlightControlDeckCard(
                        context = context,
                        prefs = prefs,
                        onStateChanged = onRefreshNeeded
                    )

                    // Card 2: System Watchdogs & Perimeter Telemetry
                    WatchdogQuickTelemetryCard(
                        context = context,
                        prefs = prefs,
                        onStateChanged = onRefreshNeeded
                    )

                    // Card 3: Central Command Header Long-Press Quick Action Selector
                    var selectedAction by remember {
                        mutableStateOf(LightspeedPreferences.getCentralCommandLongPressAction(context))
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.TouchApp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "TITLE LONG-PRESS QUICK ACTION",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 0.8.sp
                                )
                            }
                            Text(
                                "Holding down 'Central Command ▾' executes this instant shortcut with heavy tactile haptics:",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                lineHeight = 16.sp
                            )

                            val actionOptions = listOf(
                                "toggle_master_flight" to "Toggle Whole Ship (Flight Mode)",
                                "toggle_all_deflectors" to "Toggle All Deflectors",
                                "toggle_left_deflector" to "Toggle Left Deflector",
                                "toggle_right_deflector" to "Toggle Right Deflector"
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                actionOptions.forEach { (actionKey, label) ->
                                    val isSelected = selectedAction == actionKey
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                selectedAction = actionKey
                                                LightspeedPreferences.setCentralCommandLongPressAction(context, actionKey)
                                                LightspeedHapticEngine.tick(context)
                                            },
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                            else Color.White.copy(alpha = 0.04f)
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                            else Color.White.copy(alpha = 0.06f)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = isSelected,
                                                onClick = {
                                                    selectedAction = actionKey
                                                    LightspeedPreferences.setCentralCommandLongPressAction(context, actionKey)
                                                    LightspeedHapticEngine.tick(context)
                                                },
                                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = label,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}
}

@Composable
fun ProfileNamingDialog(
    initialName: String,
    valueStr: String,
    dialogTitle: String = "Name Slider Profile",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF161B26),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = dialogTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Calibrated Value: $valueStr",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile Label / Context", fontSize = 12.sp) },
                    placeholder = { Text("e.g. Rainy days, Gaming, Desk mode...", fontSize = 12.sp, color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = Color.LightGray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.LightGray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(name.trim()) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Save Profile", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SliderCalibrationFlyoutDialog(
    title: String,
    sliderKey: String,
    currentValueStr: String,
    defaultValueStr: String,
    onValueTyped: (String) -> Unit,
    onResetToDefault: () -> Unit,
    onSelectProfile: (String) -> Unit,
    onDismiss: () -> Unit,
    prefs: SharedPreferences,
    context: Context,
    isTapToJumpEnabled: Boolean,
    onTapToJumpChanged: (Boolean) -> Unit
) {
    var presets by remember { mutableStateOf(SliderPresetManager.getPresets(prefs, sliderKey)) }
    var isProfilesExpanded by remember { mutableStateOf(true) }
    var itemToRename by remember { mutableStateOf<SliderPresetManager.PresetItem?>(null) }
    var isAddingNewProfile by remember { mutableStateOf(false) }
    val isCurrentValueAlreadySaved = remember(presets, currentValueStr) {
        presets.any { it.valueStr.trim() == currentValueStr.trim() }
    }

    if (isAddingNewProfile) {
        ProfileNamingDialog(
            initialName = "",
            valueStr = currentValueStr,
            dialogTitle = "Save New Profile",
            onConfirm = { customName ->
                SliderPresetManager.addPreset(prefs, sliderKey, customName, currentValueStr)
                presets = SliderPresetManager.getPresets(prefs, sliderKey)
                isAddingNewProfile = false
            },
            onDismiss = { isAddingNewProfile = false }
        )
    }

    itemToRename?.let { targetItem ->
        ProfileNamingDialog(
            initialName = targetItem.label ?: "",
            valueStr = targetItem.valueStr,
            dialogTitle = "Rename Profile",
            onConfirm = { newName ->
                SliderPresetManager.updatePresetLabel(prefs, sliderKey, targetItem, newName)
                presets = SliderPresetManager.getPresets(prefs, sliderKey)
                itemToRename = null
            },
            onDismiss = { itemToRename = null }
        )
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight()
                .clip(RoundedCornerShape(22.dp))
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(22.dp)),
            shape = RoundedCornerShape(22.dp),
            color = Color(0xF0141822)
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .fillMaxWidth()
            ) {
                // 1. Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = title.ifEmpty { "Slider Calibration" },
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Precision Control & Presets",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.LightGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = Color.White.copy(alpha = 0.1f)
                )

                // 2. Current & Default Value Chips (with direct numeric editing)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var directValueText by remember(currentValueStr) { mutableStateOf(currentValueStr.filter { it.isDigit() || it == '.' || it == '-' }) }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Type Value", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Type value",
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            BasicTextField(
                                value = directValueText,
                                onValueChange = { newTxt ->
                                    directValueText = newTxt
                                    onValueTyped(newTxt)
                                },
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("Factory Default", fontSize = 10.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(defaultValueStr, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.LightGray)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 3. Toggles Section (Tap to Jump)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTapToJumpChanged(!isTapToJumpEnabled) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Tap-to-Jump on Track",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = "Instantly snap thumb to tap position on rail",
                                fontSize = 10.5.sp,
                                color = Color.Gray
                            )
                        }
                        Switch(
                            checked = isTapToJumpEnabled,
                            onCheckedChange = onTapToJumpChanged,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.scale(0.8f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 4. Reset to Default Button
                Button(
                    onClick = {
                        LightspeedHapticEngine.heavyClick(context)
                        onResetToDefault()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Reset to Default ($defaultValueStr)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 5. User Saved Profiles Dropdown Menu Section
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isProfilesExpanded = !isProfilesExpanded },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bookmarks,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Saved Profiles (${presets.size})",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Icon(
                                imageVector = if (isProfilesExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Color.LightGray,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        if (isProfilesExpanded) {
                            Spacer(modifier = Modifier.height(8.dp))

                            // Add Current Profile Row ("Add +")
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isCurrentValueAlreadySaved) Color.White.copy(alpha = 0.06f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isCurrentValueAlreadySaved) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (!isCurrentValueAlreadySaved) {
                                            LightspeedHapticEngine.click(context)
                                            isAddingNewProfile = true
                                        } else {
                                            LightspeedHapticEngine.tick(context)
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = if (isCurrentValueAlreadySaved) Icons.Default.Check else Icons.Default.Add,
                                        contentDescription = "Add",
                                        tint = if (isCurrentValueAlreadySaved) Color.LightGray else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isCurrentValueAlreadySaved) "Current Value '$currentValueStr' Already Saved" else "Add + (Save '$currentValueStr' as Profile)",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrentValueAlreadySaved) Color.LightGray else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // Profiles List
                            if (presets.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No saved profiles for this slider yet.",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.height(6.dp))
                                presets.forEach { profileItem ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0x22FFFFFF),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp)
                                            .clickable {
                                                LightspeedHapticEngine.click(context)
                                                onSelectProfile(profileItem.valueStr)
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Bookmark,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    // Value first in bold primary color
                                                    Text(
                                                        text = profileItem.valueStr,
                                                        fontSize = 12.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    // Followed by Name in distinct tertiary color on the same line
                                                    if (!profileItem.label.isNullOrBlank()) {
                                                        Text(
                                                            text = "• ${profileItem.label}",
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.95f),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                // Pencil Edit Icon for Renaming
                                                IconButton(
                                                    onClick = {
                                                        LightspeedHapticEngine.tick(context)
                                                        itemToRename = profileItem
                                                    },
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = "Rename Profile",
                                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }

                                                // Delete Icon at most right edge
                                                IconButton(
                                                    onClick = {
                                                        LightspeedHapticEngine.tick(context)
                                                        SliderPresetManager.removePreset(prefs, sliderKey, profileItem)
                                                        presets = SliderPresetManager.getPresets(prefs, sliderKey)
                                                    },
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Delete Profile",
                                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

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

/**
 * Power Button gesture mapping row with 7-tap safety interlock for single-press
 * and inline runtime installed tools dropdown.
 */
