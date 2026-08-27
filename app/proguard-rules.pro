# ==============================================================================
# Lightspeed ProGuard / R8 Rules
# ==============================================================================

# ── Kotlin ─────────────────────────────────────────────────────────────────────
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings { <fields>; }
-keepclassmembers class kotlin.Lazy { *; }

# ── Jetpack Compose ─────────────────────────────────────────────────────────────
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
-keep class **ComposableSingletons** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# ── Android Core ────────────────────────────────────────────────────────────────
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses,EnclosingMethod

-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.accessibilityservice.AccessibilityService
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# ── Lightspeed Accessibility Service ────────────────────────────────────────────
-keep class com.sbf.lightspeed.LightspeedAccessibilityService { *; }
-keep class com.sbf.lightspeed.LightspeedAccessibilityService$* { *; }

# ── Lightspeed Activities & Services ────────────────────────────────────────────
-keep class com.sbf.lightspeed.** extends android.app.Activity { *; }
-keep class com.sbf.lightspeed.** extends android.app.Service { *; }

# ── Shizuku IPC ─────────────────────────────────────────────────────────────────
-keep class rikka.shizuku.** { *; }
-keep class dev.rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**
-dontwarn dev.rikka.shizuku.**
-keepclassmembers class * implements android.os.IInterface { *; }

# ── JSON (org.json — LightspeedBackupEngine) ────────────────────────────────────
-keep class org.json.** { *; }
-dontwarn org.json.**

# ── Data/value classes used in token parsing ─────────────────────────────────────
-keep class com.sbf.lightspeed.system.ParsedShortcut { *; }
-keep class com.sbf.lightspeed.system.IconPackInfo { *; }

# ── Object singletons ────────────────────────────────────────────────────────────
-keep class com.sbf.lightspeed.system.LightspeedIconManager { *; }
-keep class com.sbf.lightspeed.system.LightspeedShortcutManager { *; }
-keep class com.sbf.lightspeed.system.LightspeedBackupEngine { *; }
-keep class com.sbf.lightspeed.system.ElevatedTaskCloser { *; }

# ── XmlPullParser (icon pack appfilter.xml parsing) ─────────────────────────────
-keep class org.xmlpull.** { *; }
-dontwarn org.xmlpull.**

# ── Coroutines ──────────────────────────────────────────────────────────────────
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ── Strip verbose logs in release ───────────────────────────────────────────────
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

# ── Enums ───────────────────────────────────────────────────────────────────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── Parcelable ──────────────────────────────────────────────────────────────────
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# ── Suppress optional-dependency warnings ───────────────────────────────────────
-dontwarn com.google.**
-dontwarn javax.**
