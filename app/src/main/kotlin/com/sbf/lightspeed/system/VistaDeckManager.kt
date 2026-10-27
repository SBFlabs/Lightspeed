package com.sbf.lightspeed.system

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.settings.ConsciousAirlockAppPickerContent
import com.sbf.lightspeed.settings.DeckGlassCardSurface
import com.sbf.lightspeed.settings.DeckThemeManager
import com.sbf.lightspeed.system.hud.ExpandedBrightnessWellnessSection
import com.sbf.lightspeed.ui.theme.DeckTheme
import com.sbf.lightspeed.ui.theme.LightspeedTheme

/**
 * 12-Theme Optical Glass Vista Command Deck Modal.
 *
 * Exactly mirrors Sonic Deck in architecture, appearance, and gestures,
 * dedicated to Display Wellness, Brightness Takeover, Extra Dim, Eye Care & OLED Smart Pixels.
 */
object VistaDeckManager {

    private var composeView: ComposeView? = null
    private var windowManager: WindowManager? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    fun isShowing(): Boolean = composeView != null

    fun show(service: AccessibilityService) {
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {
            if (composeView != null) return@post

            val wm = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post
            windowManager = wm

            val view = ComposeView(service)
            val owner = OverlayLifecycleOwner()
            owner.attachToView(view)
            owner.onCreate()
            owner.onStart()
            owner.onResume()
            lifecycleOwner = owner

            view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            view.setContent {
                LightspeedTheme(forceDark = true) {
                    DeckTheme {
                        VistaDeckDialog(
                            service = service,
                            isVisible = true,
                            onDismiss = { dismiss() }
                        )
                    }
                }
            }
            composeView = view

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
                dimAmount = 0f
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setFitInsetsTypes(0)
                    setFitInsetsSides(0)
                    setFitInsetsIgnoringVisibility(true)
                }
            }

            try {
                wm.addView(view, params)
            } catch (e: Exception) {
                logSwallowed("VistaDeckManager", "show.addView", e)
                dismiss()
            }
        }
    }

    fun dismiss(context: Context? = null) {
        dismiss()
    }

    fun dismiss() {
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {
            composeView?.let { view ->
                try {
                    lifecycleOwner?.onPause()
                    lifecycleOwner?.onStop()
                    lifecycleOwner?.onDestroy()
                    lifecycleOwner = null
                    view.setContent {}
                    windowManager?.removeView(view)
                } catch (e: Exception) {
                    logSwallowed("VistaDeckManager", "dismiss.removeView", e)
                }
                composeView = null
            }
            windowManager = null
        }
    }

    @Composable
    private fun VistaDeckDialog(
        service: AccessibilityService,
        isVisible: Boolean,
        onDismiss: () -> Unit
    ) {
        val snapshot by DeckThemeManager.snapshotFlow.collectAsState()
        var showAirlockPicker by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(200)) + scaleIn(tween(250), initialScale = 0.94f),
                exit = fadeOut(tween(150)) + scaleOut(tween(200), targetScale = 0.94f)
            ) {
                DeckGlassCardSurface(
                    modifier = Modifier
                        .widthIn(max = 380.dp)
                        .fillMaxWidth(0.92f)
                        .wrapContentHeight()
                        .heightIn(max = 540.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                    snapshotOverride = snapshot,
                    isFlyout = false
                ) {
                    if (showAirlockPicker) {
                        ConsciousAirlockAppPickerContent(
                            context = service,
                            prefs = service.defaultPrefs(),
                            onDismissRequest = { showAirlockPicker = false }
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Brightness4,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "◈ VISTA COMMAND DECK ◈",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.8f))
                                }
                            }

                            HorizontalDivider(color = Color(0x25FFFFFF))

                            // Full Expanded Brightness & Display Wellness Suite
                            ExpandedBrightnessWellnessSection(
                                service = service,
                                onOpenAirlockPicker = { showAirlockPicker = true }
                            )
                        }
                    }
                }
            }
        }
    }
}
