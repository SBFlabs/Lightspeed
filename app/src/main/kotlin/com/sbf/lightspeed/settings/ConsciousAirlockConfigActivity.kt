package com.sbf.lightspeed.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sbf.lightspeed.R
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.system.lsString
import com.sbf.lightspeed.ui.theme.LightspeedTheme

/**
 * Dedicated Dialog Activity for Conscious Airlock Gated Applications.
 * Cleanly callable from both Settings Activities and Accessibility Overlay Windows (e.g. Vista Deck)
 * with zero WindowManager BadTokenException risks.
 */
class ConsciousAirlockConfigActivity : ComponentActivity() {

    companion object {
        const val EXTRA_MODE = "extra_mode"
        const val MODE_SCREEN_TIME_IGNORE = "screen_time_ignore"
        const val MODE_REQUEST_CALENDAR_PERMISSION = "request_calendar_permission"
        const val EXTRA_RETURN_CATEGORY_ID = "extra_return_category_id"
    }

    private var returnCategoryId: String? = null

    private val calendarPermLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            com.sbf.lightspeed.system.LightspeedCalendarCountdownEngine.invalidateCaches()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra(EXTRA_MODE)
        val isScreenTimeIgnore = mode == MODE_SCREEN_TIME_IGNORE
        returnCategoryId = intent.getStringExtra(EXTRA_RETURN_CATEGORY_ID)

        if (mode == MODE_REQUEST_CALENDAR_PERMISSION) {
            if (com.sbf.lightspeed.system.LightspeedCalendarCountdownEngine.hasCalendarPermission(this)) {
                com.sbf.lightspeed.system.LightspeedCalendarCountdownEngine.invalidateCaches()
                finish()
            } else {
                calendarPermLauncher.launch(android.Manifest.permission.READ_CALENDAR)
            }
            return
        }

        setContent {
            LightspeedTheme(forceDark = true) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.65f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { finish() }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .widthIn(max = 380.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {}
                            )
                    ) {
                        DeckGlassCardSurface(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isScreenTimeIgnore) {
                                ConsciousAirlockAppPickerContent(
                                    context = this@ConsciousAirlockConfigActivity,
                                    prefs = defaultPrefs(),
                                    onDismissRequest = { finish() },
                                    prefKey = com.sbf.lightspeed.system.LightspeedPreferences.KEY_SCREEN_TIME_IGNORED_PACKAGES,
                                    title = lsString(R.string.picker_ignore_st_title),
                                    subtitleTemplate = { count -> lsString(R.string.picker_ignore_st_sub, count) }
                                )
                            } else {
                                ConsciousAirlockAppPickerContent(
                                    context = this@ConsciousAirlockConfigActivity,
                                    prefs = defaultPrefs(),
                                    onDismissRequest = { finish() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val catId = returnCategoryId
        if (!catId.isNullOrBlank()) {
            com.sbf.lightspeed.LightspeedAccessibilityService.instance?.reopenCategoryCruise(catId)
        }
    }
}
