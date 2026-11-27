package com.sbf.lightspeed.settings

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.CockpitDialogActivity
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.R
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.bot.LightspeedBotContract
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.system.lsString
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LightspeedTokens

internal const val INFINITY_SHOP_URL = "https://buymeacoffee.com/sbflabs/e/579114"
internal const val INFINITY_CREATOR_URL = "https://buymeacoffee.com/sbflabs"

internal const val EXTRA_OPEN_FLIGHT_DECK = "open_flight_deck"
internal const val EXTRA_OPEN_TRACKPAD_STYLES = "open_trackpad_styles"

val LocalOpenFlightControlDeck = staticCompositionLocalOf<((previewStyleId: String?) -> Unit)?> { null }
val LocalNavigateToTrackpadStyles = staticCompositionLocalOf<(() -> Unit)?> { null }

internal fun openFlightControlDeck(
    context: Context,
    localCallback: ((String?) -> Unit)? = null,
    previewStyleId: String? = null
) {
    if (localCallback != null) {
        localCallback(previewStyleId)
        return
    }
    try {
        val intent = Intent(context, CentralCommandActivity::class.java).apply {
            putExtra(EXTRA_OPEN_FLIGHT_DECK, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        context.startActivity(intent)
    } catch (_: Exception) { }
}

internal fun openCockpitHangarForShowcase(context: Context) {
    val prefs = context.defaultPrefs()
    prefs.edit().putInt("pref_hangar_open_accordion", 2).apply()

    val activity = context as? CockpitDialogActivity
    if (activity != null && activity.intent.getIntExtra(CockpitDialogActivity.EXTRA_SET_INDEX, -1) >= 0) {
        activity.finish()
        return
    }

    val service = LightspeedAccessibilityService.instance
    if (service != null) {
        CentralCommandActivity.activeInstance?.finish()
        service.reopenCockpitHangar(0)
    } else {
        Toast.makeText(context, lsString(R.string.inf_toast_arm_core_first), Toast.LENGTH_SHORT).show()
    }
}

internal fun openTrackpadStylesShowcase(
    context: Context,
    localCallback: (() -> Unit)? = null
) {
    val prefs = context.defaultPrefs()
    val leftEnabled = prefs.getBoolean(LightspeedBotContract.KEY_BOT_TRACKPAD_LEFT_ENABLED, false)
    val rightEnabled = prefs.getBoolean(LightspeedBotContract.KEY_BOT_TRACKPAD_RIGHT_ENABLED, false)
    if (leftEnabled || rightEnabled) {
        val flankKey = if (rightEnabled || !leftEnabled) "right" else "left"
        prefs.edit()
            .putBoolean("pref_accordion_trackpad_$flankKey", true)
            .putBoolean("pref_sub_trackpad_geo_$flankKey", true)
            .apply()
    }
    if (localCallback != null) {
        localCallback()
        return
    }
    try {
        val intent = Intent(context, CentralCommandActivity::class.java).apply {
            putExtra(EXTRA_OPEN_TRACKPAD_STYLES, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        context.startActivity(intent)
    } catch (_: Exception) { }
}

/**
 * Custom Kotlin-drawn Can of Rocket Fuel icon (no asset dependency, 100% vector & theme-tinted).
 */
@Composable
fun RocketFuelCanIcon(
    modifier: Modifier = Modifier.size(15.dp),
    tint: Color = LightspeedTokens.StatusGoldAlt
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeW = (w * 0.10f).coerceAtLeast(1.3f)

        // Angled pour spout on top-left
        val spoutPath = Path().apply {
            moveTo(w * 0.14f, h * 0.24f)
            lineTo(w * 0.28f, h * 0.08f)
            lineTo(w * 0.42f, h * 0.20f)
            lineTo(w * 0.28f, h * 0.34f)
            close()
        }
        drawPath(path = spoutPath, color = tint.copy(alpha = 0.26f))
        drawPath(
            path = spoutPath,
            color = tint,
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Top carry handle
        val handlePath = Path().apply {
            moveTo(w * 0.52f, h * 0.26f)
            lineTo(w * 0.52f, h * 0.15f)
            lineTo(w * 0.80f, h * 0.15f)
            lineTo(w * 0.80f, h * 0.26f)
        }
        drawPath(
            path = handlePath,
            color = tint,
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Chamfered fuel canister body
        val bodyPath = Path().apply {
            moveTo(w * 0.34f, h * 0.26f)
            lineTo(w * 0.82f, h * 0.26f)
            lineTo(w * 0.88f, h * 0.34f)
            lineTo(w * 0.88f, h * 0.88f)
            lineTo(w * 0.82f, h * 0.94f)
            lineTo(w * 0.20f, h * 0.94f)
            lineTo(w * 0.14f, h * 0.88f)
            lineTo(w * 0.14f, h * 0.42f)
            close()
        }
        drawPath(path = bodyPath, color = tint.copy(alpha = 0.22f))
        drawPath(
            path = bodyPath,
            color = tint,
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Structural canister rib
        drawRoundRect(
            color = tint.copy(alpha = 0.45f),
            topLeft = Offset(w * 0.23f, h * 0.35f),
            size = Size(w * 0.56f, h * 0.08f),
            cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
        )

        // Rocket thrust bolt emblem in center
        val boltPath = Path().apply {
            moveTo(w * 0.55f, h * 0.46f)
            lineTo(w * 0.40f, h * 0.67f)
            lineTo(w * 0.52f, h * 0.67f)
            lineTo(w * 0.47f, h * 0.85f)
            lineTo(w * 0.64f, h * 0.62f)
            lineTo(w * 0.52f, h * 0.62f)
            close()
        }
        drawPath(path = boltPath, color = tint)
    }
}

/**
 * Gold live "Try-Before-You-Buy" preview banner docked inside Flight Control Deck
 * whenever a user taps a locked optical theme to test it live.
 */
@Composable
internal fun InfinityLivePreviewBar(
    previewName: String,
    onRevert: () -> Unit,
    onGetKey: () -> Unit,
    onEnterKey: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lsColors = LightspeedStyle.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(LightspeedTokens.StatusGoldAlt.copy(alpha = 0.14f))
            .border(1.dp, LightspeedTokens.StatusGoldAlt.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = lsString(R.string.inf_preview_title, previewName),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = lsColors.accentGold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = lsString(R.string.inf_preview_sub),
                    fontSize = 10.sp,
                    lineHeight = 13.5.sp,
                    color = lsColors.textPrimary.copy(alpha = 0.86f)
                )
            }
            Text(
                text = lsString(R.string.inf_preview_revert),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = lsColors.textPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(lsColors.textPrimary.copy(alpha = 0.10f))
                    .border(0.8.dp, lsColors.textPrimary.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                    .clickable { onRevert() }
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onGetKey,
                modifier = Modifier.weight(1.35f),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LightspeedTokens.StatusGoldAlt)
            ) {
                RocketFuelCanIcon(modifier = Modifier.size(14.dp), tint = Color.Black)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = lsString(R.string.inf_btn_get_key_short),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.Black,
                    maxLines = 1
                )
            }

            OutlinedButton(
                onClick = onEnterKey,
                modifier = Modifier.weight(0.95f),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = lsColors.accentGold.copy(alpha = 0.12f),
                    contentColor = lsColors.accentGold
                ),
                border = BorderStroke(0.9.dp, lsColors.accentGold.copy(alpha = 0.55f))
            ) {
                Text(
                    text = lsString(R.string.inf_btn_enter_key_short),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}
