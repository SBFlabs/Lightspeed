package com.sbf.lightspeed.system

enum class VolumeTriggerSlot(
    val prefKey: String,
    val title: String,
    val description: String
) {
    VOL_UP_LONG_PRESS(
        LightspeedPreferences.KEY_VOL_UP_LONG_PRESS,
        "Volume Up Long Press",
        "Hold Volume Up for ~400ms"
    ),
    VOL_DOWN_LONG_PRESS(
        LightspeedPreferences.KEY_VOL_DOWN_LONG_PRESS,
        "Volume Down Long Press",
        "Hold Volume Down for ~400ms"
    ),
    CHORD_DOWN_HOLD_UP_TAP(
        LightspeedPreferences.KEY_CHORD_DOWN_HOLD_UP_TAP,
        "Hold Vol Down + Tap Vol Up",
        "Hold Volume Down, tap Volume Up"
    ),
    CHORD_UP_HOLD_DOWN_TAP(
        LightspeedPreferences.KEY_CHORD_UP_HOLD_DOWN_TAP,
        "Hold Vol Up + Tap Vol Down",
        "Hold Volume Up, tap Volume Down"
    ),
    SEQ_UP_THEN_DOWN(
        LightspeedPreferences.KEY_SEQ_UP_THEN_DOWN,
        "Sequence: Vol Up → Vol Down",
        "Tap Volume Up, then tap Volume Down within 300ms"
    ),
    SEQ_DOWN_THEN_UP(
        LightspeedPreferences.KEY_SEQ_DOWN_THEN_UP,
        "Sequence: Vol Down → Vol Up",
        "Tap Volume Down, then tap Volume Up within 300ms"
    ),
    SEQ_DOWN_TAP_THEN_UP_HOLD(
        LightspeedPreferences.KEY_SEQ_DOWN_TAP_THEN_UP_HOLD,
        "Tap Vol Down → Hold Vol Up",
        "Tap Vol Down, then press & hold Vol Up within 300ms for ~400ms"
    ),
    SEQ_UP_TAP_THEN_DOWN_HOLD(
        LightspeedPreferences.KEY_SEQ_UP_TAP_THEN_DOWN_HOLD,
        "Tap Vol Up → Hold Vol Down",
        "Tap Vol Up, then press & hold Vol Down within 300ms for ~400ms"
    )
}

enum class PowerTriggerSlot(
    val prefKey: String,
    val title: String,
    val description: String
) {
    POWER_SINGLE_PRESS(
        LightspeedPreferences.KEY_POWER_SINGLE_PRESS,
        "Power Single Press",
        "Tap Power button once"
    ),
    POWER_DOUBLE_PRESS(
        LightspeedPreferences.KEY_POWER_DOUBLE_PRESS,
        "Power Double Press",
        "Double-tap Power button within 300ms"
    ),
    POWER_HOLD(
        LightspeedPreferences.KEY_POWER_HOLD,
        "Power Button Hold (Long Press)",
        "Hold Power button for ~400ms"
    ),
    POWER_PRESS_THEN_HOLD(
        LightspeedPreferences.KEY_POWER_PRESS_THEN_HOLD,
        "Power Tap-then-Hold",
        "Tap Power button, then immediately press & hold for ~400ms"
    )
}

data class HudNavState(
    val isActive: Boolean,
    val setName: String,
    val currentToken: String,
    val currentLabel: String,
    val currentIndex: Int,
    val totalCount: Int
)
