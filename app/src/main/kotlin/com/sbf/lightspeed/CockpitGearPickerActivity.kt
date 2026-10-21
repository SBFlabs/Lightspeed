package com.sbf.lightspeed

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.Color
import com.sbf.lightspeed.settings.BackdropBlurMode
import com.sbf.lightspeed.settings.BackdropBlurModeManager
import com.sbf.lightspeed.settings.CentralCommandActivity
import com.sbf.lightspeed.settings.CentralCommandBackdropBlur
import com.sbf.lightspeed.settings.DeckThemeManager
import com.sbf.lightspeed.settings.LightspeedActionRegistry
import com.sbf.lightspeed.system.LightspeedIconManager
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedShortcutManager
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.system.logSwallowed
import com.sbf.lightspeed.ui.theme.LightspeedTheme
import androidx.compose.runtime.CompositionLocalProvider
import com.sbf.lightspeed.ui.theme.CockpitPalette
import com.sbf.lightspeed.ui.theme.LocalDomainPalette
import java.util.function.Consumer

class CockpitGearPickerActivity : ComponentActivity() {
    private var crossWindowBlurListener: Consumer<Boolean>? = null

    internal fun applyWindowBlur(): Int {
        val snapshot = DeckThemeManager.getSnapshot(this)
        val hasFallbackBackdrop = CentralCommandBackdropBlur.backdropBitmap != null
        val blurMode = BackdropBlurModeManager.getMode(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val nativeSupported = BackdropBlurModeManager.isNativeBlurSupported(windowManager)
            val useNative = BackdropBlurModeManager.shouldUseNativeBlur(windowManager, blurMode)
            val useScreenshot = BackdropBlurModeManager.shouldUseScreenshotBlur(windowManager, blurMode)

            CentralCommandActivity.updateCrossWindowBlurState(nativeSupported && blurMode != BackdropBlurMode.SCREENSHOT)
            val targetRadius = if (useNative) {
                snapshot.windowBlurRadiusPx.coerceIn(0, 150)
            } else {
                0
            }
            if (useNative) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                val lp = window.attributes
                lp.blurBehindRadius = targetRadius
                window.attributes = lp
                window.setDimAmount(snapshot.dimAmount)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                val lp = window.attributes
                lp.blurBehindRadius = 0
                window.attributes = lp
                window.setDimAmount(if (useScreenshot && hasFallbackBackdrop) 0f else snapshot.dimAmount)
            }
            return targetRadius
        } else {
            CentralCommandActivity.updateCrossWindowBlurState(false)
            window.setDimAmount(snapshot.dimAmount)
            return 0
        }
    }

    override fun onResume() {
        super.onResume()
        if (CentralCommandBackdropBlur.backdropBitmap == null) {
            CentralCommandBackdropBlur.prepareForCentralCommand(windowManager)
        }
        applyWindowBlur()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyWindowBlur()
        }
    }

    override fun finish() {
        if (isTaskRoot) {
            finishAndRemoveTask()
        }
        super.finish()
        overrideZeroTransition()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            crossWindowBlurListener?.let { listener ->
                try {
                    windowManager.removeCrossWindowBlurEnabledListener(listener)
                } catch (e: Exception) {
                    logSwallowed("CockpitGearPickerActivity", "removeCrossWindowBlurEnabledListener", e)
                }
            }
            crossWindowBlurListener = null
        }
        LightspeedIconManager.temporaryPickerCache.clear()
        LightspeedShortcutManager.temporaryPickerCache.clear()
        val setId = intent.getStringExtra("SET_ID")
        val isSingleSelect = !intent.getStringExtra("SINGLE_SELECT_PREF_KEY").isNullOrBlank()
        if (!isSingleSelect && !setId.isNullOrBlank()) {
            val setIndex = setId.toIntOrNull() ?: -1
            if (setIndex >= 0) {
                try {
                    LightspeedAccessibilityService.instance?.reopenCockpitHangar(setIndex)
                } catch (e: Exception) { logSwallowed("CockpitGearPickerActivity", "onDestroy:81", e) }
            }
        }
        if (isTaskRoot) {
            CentralCommandBackdropBlur.onWindowHidden(isChangingConfigurations)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        if (CentralCommandBackdropBlur.backdropBitmap == null) {
            CentralCommandBackdropBlur.prepareForCentralCommand(windowManager)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val listener = Consumer<Boolean> { enabled ->
                CentralCommandActivity.updateCrossWindowBlurState(enabled)
                applyWindowBlur()
            }
            crossWindowBlurListener = listener
            try {
                windowManager.addCrossWindowBlurEnabledListener(listener)
            } catch (e: Exception) {
                logSwallowed("CockpitGearPickerActivity", "addCrossWindowBlurEnabledListener", e)
            }
        }
        applyWindowBlur()

        val singleSelectPrefKey = intent.getStringExtra("SINGLE_SELECT_PREF_KEY")
        val singleSelectTitle = intent.getStringExtra("SINGLE_SELECT_TITLE") ?: "Select Action"
        val isSingleSelect = !singleSelectPrefKey.isNullOrBlank()
        val isSplitMode = intent.getBooleanExtra("SPLIT_MODE", false)
        val isHoldGesture = intent.getBooleanExtra("IS_HOLD_GESTURE", false) ||
                singleSelectPrefKey?.contains("_HOLD", ignoreCase = true) == true ||
                singleSelectPrefKey?.contains("SCRUB", ignoreCase = true) == true

        val setId = intent.getStringExtra("SET_ID") ?: "0"
        val ringIndex = intent.getIntExtra("RING_INDEX", 0)

        val primaryAccent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getColor(android.R.color.system_accent1_600)
        } else {
            0xFF6750A4.toInt()
        }
        val secondaryAccent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getColor(android.R.color.system_accent1_300)
        } else {
            0xFFD0BCFF.toInt()
        }

        val dynamicPrimary = Color(primaryAccent)
        val dynamicSecondary = Color(secondaryAccent)

        LightspeedIconManager.clearCache()
        LightspeedActionRegistry.iconBitmapCache.clear()
        LightspeedActionRegistry.initializeSync(this)

        val prefs = defaultPrefs()
        if (!prefs.contains(LightspeedPreferences.KEY_PICKER_PINNED_APPS)) {
            val defaultTools = listOf("com.arlosoft.macrodroid", "net.dinglisch.android.taskerm", "io.github.sds100.keymapper", "rk.android.app.shortcutmaker")
            val pm = packageManager
            val installedDefaults = defaultTools.filter { pkg ->
                try { pm.getPackageInfo(pkg, 0); true } catch (_: Exception) { false }
            }.toSet()
            prefs.edit().putStringSet(LightspeedPreferences.KEY_PICKER_PINNED_APPS, installedDefaults).apply()
        }

        val initialItems = if (isSingleSelect) {
            val currentToken = prefs.getString(singleSelectPrefKey, "none") ?: "none"
            if (currentToken != "none") listOf(currentToken) else emptyList()
        } else {
            val csvString = prefs.getString("gear_set_${setId}_ring_${ringIndex}_packages", "") ?: ""
            csvString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }

        setContent {
            val shortcutConfigLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { /* Handled in ScreenContent */ }

            LightspeedTheme(forceDark = true) {
                CompositionLocalProvider(LocalDomainPalette provides CockpitPalette) {
                    CockpitGearPickerScreenContent(
                        activity = this@CockpitGearPickerActivity,
                        prefs = prefs,
                        setId = setId,
                        ringIndex = ringIndex,
                        singleSelectPrefKey = singleSelectPrefKey,
                        singleSelectTitle = singleSelectTitle,
                        isSingleSelect = isSingleSelect,
                        isSplitMode = isSplitMode,
                        isHoldGesture = isHoldGesture,
                        initialItems = initialItems,
                        dynamicPrimary = dynamicPrimary,
                        dynamicSecondary = dynamicSecondary,
                        shortcutConfigLauncher = shortcutConfigLauncher,
                        onApplyWindowBlur = { applyWindowBlur() },
                        onFinish = { finish() }
                    )
                }
            }
        }
    }
}
