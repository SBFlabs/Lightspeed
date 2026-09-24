package com.sbf.lightspeed.system

import android.content.Context
import android.view.KeyEvent
import com.sbf.lightspeed.settings.resolveDynamicTokenLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Manages Hardware HUD navigation states and volume key event processing
 * when navigating through gear set items.
 */
object LightspeedKeyHudNav {
    const val LONG_PRESS_TIMEOUT_MS = 400L
    const val HUD_NAV_INACTIVITY_TIMEOUT_MS = 5000L

    private var hudScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    var isHudNavActive = false
        private set
    var currentNavState: HudNavState? = null
        private set
    var onNavStateListener: ((HudNavState?) -> Unit)? = null

    private var navItems: List<String> = emptyList()
    private var navIndex = 0
    private var navSetName = ""
    private var isVolUpNavHoldFired = false
    private var isVolDownNavHoldFired = false

    private var volUpHoldJob: Job? = null
    private var volDownHoldJob: Job? = null
    private var hudNavInactivityJob: Job? = null

    fun startHudNav(context: Context) {
        val prefs = context.defaultPrefs()
        val activeSetIndex = prefs.getInt("last_active_set_index", 0)
        val setsOrderStr = prefs.getString("gear_sets_order", "0,1,2,3") ?: "0,1,2,3"
        val setIds = setsOrderStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val currentSetId = setIds.getOrNull(activeSetIndex) ?: setIds.firstOrNull() ?: "0"

        val rawName = prefs.getString("gear_set_${currentSetId}_name", "") ?: ""
        navSetName = if (rawName.isEmpty() || rawName in listOf("SET A", "SET B", "SET C", "SET D", "SET")) {
            when (currentSetId) {
                "0" -> "POWER USER ANDROID"
                "1" -> "MY APP STORES"
                "2" -> "UTILITIES SECTOR"
                "3" -> "ENTERTAINMENT DECK"
                else -> "GEAR SET ${activeSetIndex + 1}"
            }
        } else rawName

        val r0 = prefs.getString("gear_set_${currentSetId}_ring_0_packages", "") ?: ""
        val r1 = prefs.getString("gear_set_${currentSetId}_ring_1_packages", "") ?: ""
        val combined = (r0.split(",") + r1.split(","))
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "none" }
            .distinct()

        navItems = if (combined.isNotEmpty()) combined else listOf("system:recents", "system:previous_app", "system:screenshot", "system:flashlight")
        navIndex = 0
        isHudNavActive = true

        publishHudNavState(context)
        resetNavInactivityTimer()
        LightspeedHapticEngine.heavyClick(context)
    }

    fun exitHudNav() {
        isHudNavActive = false
        hudNavInactivityJob?.cancel()
        hudNavInactivityJob = null
        currentNavState = null
        onNavStateListener?.invoke(null)
    }

    fun resetNavInactivityTimer() {
        hudNavInactivityJob?.cancel()
        hudNavInactivityJob = hudScope.launch {
            delay(HUD_NAV_INACTIVITY_TIMEOUT_MS)
            exitHudNav()
        }
    }

    fun navigatePrevious(context: Context) {
        if (navItems.isEmpty()) return
        navIndex = (navIndex - 1 + navItems.size) % navItems.size
        publishHudNavState(context)
        LightspeedHapticEngine.click(context)
    }

    fun navigateNext(context: Context) {
        if (navItems.isEmpty()) return
        navIndex = (navIndex + 1) % navItems.size
        publishHudNavState(context)
        LightspeedHapticEngine.click(context)
    }

    fun navLaunch(context: Context) {
        val token = navItems.getOrNull(navIndex)
        exitHudNav()
        if (!token.isNullOrBlank()) {
            ActionDispatcher.dispatch(token, context)
        }
    }

    fun publishHudNavState(context: Context) {
        val token = navItems.getOrNull(navIndex) ?: "none"
        val label = if (token.startsWith("shortcut:")) {
            LightspeedShortcutManager.resolveLabel(context, token)
        } else {
            resolveDynamicTokenLabel(context, token)
        }

        val state = HudNavState(
            isActive = true,
            setName = navSetName,
            currentToken = token,
            currentLabel = label,
            currentIndex = navIndex,
            totalCount = navItems.size
        )
        currentNavState = state
        onNavStateListener?.invoke(state)
    }

    fun handleHudNavKeyEvent(context: Context, event: KeyEvent): Boolean {
        if (!isHudNavActive) return false
        resetNavInactivityTimer()
        val keyCode = event.keyCode
        val action = event.action

        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (action == KeyEvent.ACTION_DOWN) {
                    if (event.repeatCount > 0) return true
                    isVolUpNavHoldFired = false
                    volUpHoldJob?.cancel()
                    volUpHoldJob = hudScope.launch {
                        delay(LONG_PRESS_TIMEOUT_MS)
                        isVolUpNavHoldFired = true
                        LightspeedHapticEngine.heavyClick(context)
                        navLaunch(context)
                    }
                    return true
                } else if (action == KeyEvent.ACTION_UP) {
                    volUpHoldJob?.cancel()
                    volUpHoldJob = null
                    if (!isVolUpNavHoldFired) {
                        navigatePrevious(context)
                    }
                    isVolUpNavHoldFired = false
                    return true
                }
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (action == KeyEvent.ACTION_DOWN) {
                    if (event.repeatCount > 0) return true
                    isVolDownNavHoldFired = false
                    volDownHoldJob?.cancel()
                    volDownHoldJob = hudScope.launch {
                        delay(LONG_PRESS_TIMEOUT_MS)
                        isVolDownNavHoldFired = true
                        LightspeedHapticEngine.heavyClick(context)
                        exitHudNav()
                    }
                    return true
                } else if (action == KeyEvent.ACTION_UP) {
                    volDownHoldJob?.cancel()
                    volDownHoldJob = null
                    if (!isVolDownNavHoldFired) {
                        navigateNext(context)
                    }
                    isVolDownNavHoldFired = false
                    return true
                }
            }
        }
        return true
    }

    fun reset() {
        volUpHoldJob?.cancel()
        volUpHoldJob = null
        volDownHoldJob?.cancel()
        volDownHoldJob = null
        hudNavInactivityJob?.cancel()
        hudNavInactivityJob = null
        isHudNavActive = false
        currentNavState = null
    }
}
