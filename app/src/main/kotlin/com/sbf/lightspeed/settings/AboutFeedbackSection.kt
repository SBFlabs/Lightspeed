package com.sbf.lightspeed.settings

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.LightspeedLanguageEngine
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.ui.theme.LightspeedStyle

// ---- User-visible strings (kept together so a later language step can extract them) ----
private const val TEXT_SECTION_TITLE = "About & Feedback"
private const val TEXT_BUTTON_BUG = "Report a bug"
private const val TEXT_BUTTON_LANGUAGE = "Report a language issue"
private const val TEXT_HINT = "Opens a pre-filled page in your browser. Nothing is sent until you submit it there."
private const val TEXT_NO_BROWSER = "No browser found; link copied"
private const val TEXT_UNKNOWN = "unknown"

private const val ISSUE_TITLE_BUG = "[Bug] "
private const val ISSUE_TITLE_LANGUAGE = "[Language] "
private const val ISSUE_LABEL_BUG = "bug"
private const val ISSUE_LABEL_LANGUAGE = "language"
private const val BODY_BUG_WHAT_HAPPENED = "What happened:"
private const val BODY_BUG_EXPECTED = "What you expected:"
private const val BODY_BUG_STEPS = "Steps to reproduce:"
private const val BODY_LANG_SCREEN = "Screen or text that is wrong:"
private const val BODY_LANG_SUGGESTION = "Suggested correction:"
// ---------------------------------------------------------------------------------------

private const val ISSUE_HOST = "github.com"
private val ISSUE_PATH_SEGMENTS = listOf("SBFlabs", "Lightspeed", "issues", "new")

/** About page: version line plus two buttons that open a pre-filled public issue form in the browser. */
@Composable
fun AboutFeedbackSection(context: Context) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val lsColors = LightspeedStyle.colors

    CompactAccordionSection(
        title = TEXT_SECTION_TITLE,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        },
        isExpanded = isExpanded,
        onToggle = { isExpanded = !isExpanded }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = versionLine(context),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = lsColors.textPrimary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { openIssuePage(context, buildBugUri(context)) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(TEXT_BUTTON_BUG, fontSize = 12.sp, textAlign = TextAlign.Center)
                }
                OutlinedButton(
                    onClick = { openIssuePage(context, buildLanguageUri(context)) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(TEXT_BUTTON_LANGUAGE, fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            }
            Text(
                text = TEXT_HINT,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )
        }
    }
}

private fun versionName(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: TEXT_UNKNOWN
} catch (e: Exception) {
    TEXT_UNKNOWN
}

private fun versionCode(context: Context): String = try {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode.toString()
    else @Suppress("DEPRECATION") info.versionCode.toString()
} catch (e: Exception) {
    TEXT_UNKNOWN
}

private fun versionLine(context: Context): String =
    "Lightspeed ${versionName(context)} (${versionCode(context)})"

private fun deviceLine(context: Context): String =
    "Lightspeed ${versionName(context)} / Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) / ${Build.MANUFACTURER} ${Build.MODEL}"

private fun buildBugUri(context: Context): Uri {
    val body = listOf(
        BODY_BUG_WHAT_HAPPENED, "", "",
        BODY_BUG_EXPECTED, "", "",
        BODY_BUG_STEPS, "", "",
        deviceLine(context)
    ).joinToString("\n")
    return buildIssueUri(ISSUE_TITLE_BUG, ISSUE_LABEL_BUG, body)
}

private fun buildLanguageUri(context: Context): Uri {
    val currentMode = context.defaultPrefs()
        .getString(LightspeedLanguageEngine.KEY_LANGUAGE_MODE, null) ?: TEXT_UNKNOWN
    val body = listOf(
        "Language mode (current: $currentMode):", "", "",
        BODY_LANG_SCREEN, "", "",
        BODY_LANG_SUGGESTION, "", "",
        deviceLine(context)
    ).joinToString("\n")
    return buildIssueUri(ISSUE_TITLE_LANGUAGE, ISSUE_LABEL_LANGUAGE, body)
}

private fun buildIssueUri(title: String, label: String, body: String): Uri {
    val builder = Uri.Builder().scheme("https").authority(ISSUE_HOST)
    ISSUE_PATH_SEGMENTS.forEach { builder.appendPath(it) }
    return builder
        .appendQueryParameter("title", title)
        .appendQueryParameter("labels", label)
        .appendQueryParameter("body", body)
        .build()
}

/** Opens the URL in the default browser; on failure copies it to the clipboard and shows a toast. */
private fun openIssuePage(context: Context, uri: Uri) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("Lightspeed issue link", uri.toString()))
        Toast.makeText(context, TEXT_NO_BROWSER, Toast.LENGTH_SHORT).show()
    }
}
