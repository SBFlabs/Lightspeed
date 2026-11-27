package com.sbf.lightspeed.system

import com.sbf.lightspeed.BuildConfig

/**
 * Strict 'Nightly-Only' Feature Gating & Release Isolation Registry.
 *
 * Mandate:
 * Any experimental, unfinished, or actively iterating feature:
 * - Must carry a visible "[NIGHTLY]" badge in debug builds (BuildConfig.DEBUG == true).
 * - Must be completely hidden, omitted, and disabled from Stable release builds (BuildConfig.DEBUG == false)
 *   so Stable remains 100% rock-solid.
 *
 * Pre-Release Invariant:
 * Before preparing any staged release or promoting to public release, the AI orchestrator
 * must compile and present an inventory of all Nightly-Only features via [getNightlyFeatureInventory].
 */
object NightlyFeatures {

    /** Badge suffix attached to titles, cards, and option pills in debug builds */
    const val BADGE_SUFFIX = " [NIGHTLY]"

    /**
     * Category Cruise Top HUD Widgets (Focused App Inspector, Screen Time, System Vitals, App Widget Slot).
     * Graduated to Public Stable in v1.4.0.
     */
    const val IS_CATEGORY_HUD_TAGGED_NIGHTLY = false

    val isCategoryCruiseHudWidgetsEnabled: Boolean
        get() = !IS_CATEGORY_HUD_TAGGED_NIGHTLY || BuildConfig.DEBUG

    /**
     * Home Screen Widget Stack (interactive swipeable ViewFlipper collection widget).
     * Gated strictly behind BuildConfig.DEBUG for v1.5.0.
     */
    val isWidgetStackEnabled: Boolean
        get() = BuildConfig.DEBUG

    /**
     * Home Screen Folder Widget Nightly Tag.
     * Tagged as Nightly-Only for v1.5.0 and gated behind BuildConfig.DEBUG.
     */
    const val IS_FOLDER_WIDGET_TAGGED_NIGHTLY = true

    val isFolderWidgetEnabled: Boolean
        get() = !IS_FOLDER_WIDGET_TAGGED_NIGHTLY || BuildConfig.DEBUG

    /**
     * Home Screen Space Pod Widget Nightly Tag.
     * Graduated to Public Stable in v1.4.0.
     */
    const val IS_SPACE_POD_WIDGET_TAGGED_NIGHTLY = false

    val isSpacePodWidgetEnabled: Boolean
        get() = !IS_SPACE_POD_WIDGET_TAGGED_NIGHTLY || BuildConfig.DEBUG

    /**
     * Manual trigger inside About to test the full-screen Post-Update What's New -> Engine Warm-Up Gate.
     * Strictly Nightly-Only (BuildConfig.DEBUG) and omitted completely from Stable release builds.
     */
    val isWarmupGateTesterEnabled: Boolean
        get() = BuildConfig.DEBUG

    /**
     * Manual trigger inside About to replay the Arm Lightspeed Core setup gate (Shizuku 1-click vs
     * Accessibility Service Only) in a simulated, side-effect-free preview. Nightly-Only.
     */
    val isCoreSetupGateTesterEnabled: Boolean
        get() = BuildConfig.DEBUG

    /**
     * 20-20-20 Eye Rest Timer inside Astrogation Airlock (Central Command -> HUD Strip).
     * Tagged as Nightly-Only while active screen-time reminder behavior is unfinished;
     * carries a visible [NIGHTLY] badge in debug builds and is completely hidden/disabled in Stable.
     */
    const val IS_EYE_REST_TIMER_TAGGED_NIGHTLY = true

    val isEyeRestTimerEnabled: Boolean
        get() = !IS_EYE_REST_TIMER_TAGGED_NIGHTLY || BuildConfig.DEBUG

    /**
     * Returns an inventory of all experimental features and their active gating status.
     * Used for pre-release audits and compliance verification.
     */
    fun getNightlyFeatureInventory(): List<NightlyFeatureItem> {
        return listOf(
            NightlyFeatureItem(
                name = "Category Cruise HUD Widgets",
                description = "Focused App Inspector, Screen Time, System Vitals, Alerts/Firewall Modals, App Widget Slot",
                isNightlyOnly = IS_CATEGORY_HUD_TAGGED_NIGHTLY,
                isEnabledInCurrentBuild = isCategoryCruiseHudWidgetsEnabled,
                badge = if (IS_CATEGORY_HUD_TAGGED_NIGHTLY) (if (BuildConfig.DEBUG) BADGE_SUFFIX.trim() else "HIDDEN_IN_STABLE") else "STABLE"
            ),
            NightlyFeatureItem(
                name = "Home Screen Widget Stack",
                description = "12-Theme interactive swipeable stack widget (LightspeedStackWidgetProvider)",
                isNightlyOnly = true,
                isEnabledInCurrentBuild = isWidgetStackEnabled,
                badge = if (BuildConfig.DEBUG) BADGE_SUFFIX.trim() else "HIDDEN_IN_STABLE"
            ),
            NightlyFeatureItem(
                name = "Home Screen Folder Widget",
                description = "12-Theme resizable app folder widget (LightspeedFolderWidgetProvider)",
                isNightlyOnly = IS_FOLDER_WIDGET_TAGGED_NIGHTLY,
                isEnabledInCurrentBuild = isFolderWidgetEnabled,
                badge = if (IS_FOLDER_WIDGET_TAGGED_NIGHTLY) (if (BuildConfig.DEBUG) BADGE_SUFFIX.trim() else "HIDDEN_IN_STABLE") else "STABLE"
            ),
            NightlyFeatureItem(
                name = "Home Screen Space Pod Widget",
                description = "12-Theme 1x1 multi-gesture pod widget (SpacePodWidgetProvider)",
                isNightlyOnly = IS_SPACE_POD_WIDGET_TAGGED_NIGHTLY,
                isEnabledInCurrentBuild = isSpacePodWidgetEnabled,
                badge = if (IS_SPACE_POD_WIDGET_TAGGED_NIGHTLY) (if (BuildConfig.DEBUG) BADGE_SUFFIX.trim() else "HIDDEN_IN_STABLE") else "STABLE"
            ),
            NightlyFeatureItem(
                name = "Warm-Up Gate Test Trigger",
                description = "Manual debug launcher inside About to preview Post-Update What's New -> Engine Warm-Up Gate",
                isNightlyOnly = true,
                isEnabledInCurrentBuild = isWarmupGateTesterEnabled,
                badge = if (BuildConfig.DEBUG) BADGE_SUFFIX.trim() else "HIDDEN_IN_STABLE"
            ),
            NightlyFeatureItem(
                name = "Core Setup Gate Test Trigger",
                description = "Manual debug launcher inside About to preview the Arm Lightspeed Core gate with simulated Shizuku states",
                isNightlyOnly = true,
                isEnabledInCurrentBuild = isCoreSetupGateTesterEnabled,
                badge = if (BuildConfig.DEBUG) BADGE_SUFFIX.trim() else "HIDDEN_IN_STABLE"
            ),
            NightlyFeatureItem(
                name = "20-20-20 Eye Rest Timer",
                description = "Periodic 20s eye rest reminder toggle inside Astrogation Airlock (HudAstrogationAirlockSection)",
                isNightlyOnly = IS_EYE_REST_TIMER_TAGGED_NIGHTLY,
                isEnabledInCurrentBuild = isEyeRestTimerEnabled,
                badge = if (IS_EYE_REST_TIMER_TAGGED_NIGHTLY) (if (BuildConfig.DEBUG) BADGE_SUFFIX.trim() else "HIDDEN_IN_STABLE") else "STABLE"
            )
        )
    }
}

/** Feature metadata item for pre-release reporting */
data class NightlyFeatureItem(
    val name: String,
    val description: String,
    val isNightlyOnly: Boolean,
    val isEnabledInCurrentBuild: Boolean,
    val badge: String
)
