package com.sbf.lightspeed.system

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import java.util.Locale

object OemNotchDetector {

    fun getDetectedFeatureName(): String {
        val brand = (Build.BRAND ?: "").lowercase(Locale.US)
        val manufacturer = (Build.MANUFACTURER ?: "").lowercase(Locale.US)
        val combined = "$brand $manufacturer"

        return when {
            combined.contains("infinix") || combined.contains("tecno") || combined.contains("transsion") || combined.contains("itel") -> "Dynamic Bar"
            combined.contains("realme") -> "Mini Capsule"
            combined.contains("honor") -> "Magic Capsule"
            combined.contains("oneplus") || combined.contains("oppo") -> "Fluid Cloud"
            combined.contains("xiaomi") || combined.contains("poco") || combined.contains("redmi") -> "Dynamic Notch"
            combined.contains("huawei") -> "Live View"
            else -> "Live Notification"
        }
    }

    fun openSearch(context: Context) {
        val featureName = getDetectedFeatureName()
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("OEM Feature Name", featureName))
        } catch (_: Exception) {}

        val searchIntent = Intent("android.settings.APP_SEARCH_SETTINGS").apply {
            putExtra("query", featureName)
            putExtra("android.provider.extra.SEARCH_QUERY", featureName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            context.startActivity(searchIntent)
        } catch (_: Exception) {
            try {
                context.startActivity(fallbackIntent)
            } catch (_: Exception) {}
        }
    }
}
