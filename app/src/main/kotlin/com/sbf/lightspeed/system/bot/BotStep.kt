package com.sbf.lightspeed.system.bot

import org.json.JSONObject

/**
 * Data model for a single recorded step in a Bot Macro.
 * Supports screen-reading accessibility node identification and coordinate fallbacks.
 */
data class BotStep(
    val stepType: String = TYPE_TAP, // "tap", "swipe", "delay"
    val xRatio: Float = 0f,
    val yRatio: Float = 0f,
    val endXRatio: Float = 0f,
    val endYRatio: Float = 0f,
    val delayMs: Long = 150L,
    val durationMs: Long = 50L,
    // Screen reading metadata
    val viewIdResourceName: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val className: String? = null,
    val packageName: String? = null
) {
    companion object {
        const val TYPE_TAP = "tap"
        const val TYPE_SWIPE = "swipe"
        const val TYPE_DELAY = "delay"

        fun fromJson(json: JSONObject): BotStep {
            return BotStep(
                stepType = json.optString("stepType", TYPE_TAP),
                xRatio = json.optDouble("xRatio", 0.0).toFloat(),
                yRatio = json.optDouble("yRatio", 0.0).toFloat(),
                endXRatio = json.optDouble("endXRatio", 0.0).toFloat(),
                endYRatio = json.optDouble("endYRatio", 0.0).toFloat(),
                delayMs = json.optLong("delayMs", 150L),
                durationMs = json.optLong("durationMs", 50L),
                viewIdResourceName = json.optString("viewId").takeIf { !it.isNullOrBlank() },
                text = json.optString("text").takeIf { !it.isNullOrBlank() },
                contentDescription = json.optString("desc").takeIf { !it.isNullOrBlank() },
                className = json.optString("class").takeIf { !it.isNullOrBlank() },
                packageName = json.optString("pkg").takeIf { !it.isNullOrBlank() }
            )
        }
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("stepType", stepType)
            put("xRatio", xRatio.toDouble())
            put("yRatio", yRatio.toDouble())
            put("endXRatio", endXRatio.toDouble())
            put("endYRatio", endYRatio.toDouble())
            put("delayMs", delayMs)
            put("durationMs", durationMs)
            viewIdResourceName?.let { put("viewId", it) }
            text?.let { put("text", it) }
            contentDescription?.let { put("desc", it) }
            className?.let { put("class", it) }
            packageName?.let { put("pkg", it) }
        }
    }
}
