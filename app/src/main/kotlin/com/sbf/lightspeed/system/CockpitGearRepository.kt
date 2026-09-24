package com.sbf.lightspeed.system

import com.sbf.lightspeed.system.defaultPrefs
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

object CockpitGearRepository {

    private val DEFAULT_GEAR_PACKAGES_RING_0 = listOf("com.android.settings", "system:perimeter_watchdog", "system:core_watchdog")
    private val DEFAULT_GEAR_PACKAGES_RING_1 = listOf("com.android.vending", "org.fdroid.fdroid")

    private val gearAppsCache = java.util.concurrent.ConcurrentHashMap<String, List<String>>()
    private val gearSetsOrderCache = java.util.concurrent.ConcurrentHashMap<Boolean, MutableList<String>>()
    private var prefListenerRegistered = false
    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && (key.startsWith("gear_set_") || key.startsWith("gear_sets_order"))) {
            clearCache()
        }
    }

    private fun ensureListener(context: Context) {
        if (!prefListenerRegistered) {
            prefListenerRegistered = true
            val appContext = context.applicationContext ?: context
            appContext.defaultPrefs().registerOnSharedPreferenceChangeListener(prefListener)
        }
    }

    fun clearCache() {
        gearAppsCache.clear()
        gearSetsOrderCache.clear()
    }

    fun persistActiveGearSetIndex(context: Context, setIndex: Int, isLeft: Boolean) {
        val prefs = context.defaultPrefs()
        val editor = prefs.edit().putInt(LightspeedPreferences.KEY_LAST_ACTIVE_SET_INDEX, setIndex)
        if (isLeft) {
            editor.putInt(LightspeedPreferences.KEY_LAST_ACTIVE_SET_INDEX_LEFT, setIndex)
        } else {
            editor.putInt(LightspeedPreferences.KEY_LAST_ACTIVE_SET_INDEX_RIGHT, setIndex)
        }
        editor.apply()
    }

    fun getGearSetsOrder(context: Context, isLeft: Boolean): MutableList<String> {
        ensureListener(context)
        gearSetsOrderCache[isLeft]?.let { return it.toMutableList() }
        val prefs = context.defaultPrefs()
        val key = if (isLeft) "gear_sets_order_left" else "gear_sets_order_right"
        val saved = prefs.getString(key, null)
        val result = if (!saved.isNullOrEmpty()) {
            saved.split(",").filter { it.isNotEmpty() }.toMutableList()
        } else {
            val legacy = prefs.getString("gear_sets_order", null)
            if (!legacy.isNullOrEmpty() && legacy != "0,1,2,3" && legacy != "0") {
                legacy.split(",").filter { it.isNotEmpty() }.toMutableList()
            } else {
                // Default first-time experience:
                // Left flank opens Dev & Power Deck (Set 1) by default!
                // Right flank opens Core Avionics (Set 0) by default!
                if (isLeft) mutableListOf("1", "0") else mutableListOf("0", "1")
            }
        }
        gearSetsOrderCache[isLeft] = result.toMutableList()
        return result
    }

    fun saveGearSetsOrder(context: Context, isLeft: Boolean, list: List<String>) {
        val prefs = context.defaultPrefs()
        val key = if (isLeft) "gear_sets_order_left" else "gear_sets_order_right"
        prefs.edit().putString(key, list.joinToString(",")).apply()
        clearCache()
    }

    fun getFlankLaunchBehavior(context: Context, isLeft: Boolean): String {
        val prefs = context.defaultPrefs()
        val key = if (isLeft) "cockpit_launch_behavior_left" else "cockpit_launch_behavior_right"
        val saved = prefs.getString(key, null)
        if (!saved.isNullOrEmpty()) {
            return saved
        }
        return prefs.getString("cockpit_launch_behavior", "default") ?: "default"
    }

    fun setFlankLaunchBehavior(context: Context, isLeft: Boolean, behavior: String) {
        val prefs = context.defaultPrefs()
        val key = if (isLeft) "cockpit_launch_behavior_left" else "cockpit_launch_behavior_right"
        prefs.edit().putString(key, behavior).apply()
    }

    fun resolveDefaultRingPackages(context: Context, ringIndex: Int): List<String> {
        val pm = context.packageManager
        if (ringIndex == 1) {
            // INNER RING (Ring 02): Daily Essentials (Phone, SMS, Primary Chat, Browser, Files, Camera, Email) - Keep lean (~5-6 apps)!
            val innerList = mutableListOf<String>()

            // 1. Phone / Dialer
            try {
                val dialerIntent = Intent(Intent.ACTION_DIAL)
                val resolve = pm.resolveActivity(dialerIntent, PackageManager.MATCH_DEFAULT_ONLY)
                val pkg = resolve?.activityInfo?.packageName
                if (!pkg.isNullOrBlank() && pkg != "android") {
                    innerList.add(pkg)
                }
            } catch (_: Exception) {}

            // 2. Messages / SMS
            try {
                val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
                val resolve = pm.resolveActivity(smsIntent, PackageManager.MATCH_DEFAULT_ONLY)
                val pkg = resolve?.activityInfo?.packageName
                if (!pkg.isNullOrBlank() && pkg != "android") {
                    innerList.add(pkg)
                }
            } catch (_: Exception) {}

            // 3. Primary Chat Apps (WhatsApp, Telegram, Signal, etc.)
            val chatCandidates = listOf(
                "com.whatsapp",
                "org.telegram.messenger",
                "org.thunderdog.challegram",
                "org.telegram.plus",
                "org.thoughtcrime.securesms"
            )
            for (pkg in chatCandidates) {
                try {
                    if (pm.getLaunchIntentForPackage(pkg) != null) {
                        innerList.add(pkg)
                    }
                } catch (_: Exception) {}
            }

            // 4. Default Browser
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
                val resolve = pm.resolveActivity(browserIntent, PackageManager.MATCH_DEFAULT_ONLY)
                val pkg = resolve?.activityInfo?.packageName
                if (!pkg.isNullOrBlank() && pkg != "android") {
                    innerList.add(pkg)
                }
            } catch (_: Exception) {}

            // 5. File Managers (MiXplorer, Material Files, Total Commander, etc.)
            val fileCandidates = listOf(
                "com.mixplorer.beta",
                "com.mixplorer",
                "com.google.android.documentsui",
                "com.android.documentsui",
                "com.sec.android.app.myfiles",
                "com.google.android.apps.nbu.files",
                "com.transsion.filemanager",
                "com.mi.android.globalFileexplorer",
                "me.zhanghai.android.files",
                "com.alphainventor.filemanager",
                "com.ghisler.android.TotalCommander"
            )
            for (pkg in fileCandidates) {
                try {
                    if (pm.getLaunchIntentForPackage(pkg) != null) {
                        innerList.add(pkg)
                    }
                } catch (_: Exception) {}
            }

            // 6. Camera
            try {
                val camIntent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                val resolve = pm.resolveActivity(camIntent, PackageManager.MATCH_DEFAULT_ONLY)
                val pkg = resolve?.activityInfo?.packageName
                if (!pkg.isNullOrBlank() && pkg != "android") {
                    innerList.add(pkg)
                }
            } catch (_: Exception) {}

            // 7. Email Apps (Gmail, K-9, FairEmail, Outlook)
            val emailCandidates = listOf(
                "com.google.android.gm",
                "com.fsck.k9",
                "eu.faircode.email",
                "com.microsoft.office.outlook"
            )
            for (pkg in emailCandidates) {
                try {
                    if (pm.getLaunchIntentForPackage(pkg) != null) {
                        innerList.add(pkg)
                    }
                } catch (_: Exception) {}
            }

            val distinct = innerList.distinct()
            return if (distinct.isNotEmpty()) distinct else DEFAULT_GEAR_PACKAGES_RING_1
        } else {
            // OUTER RING (Ring 01): App Stores & Ecosystem (F-Droid/Droid-ify, Aurora/Obtainium, Play Store, My Apps, Settings, Central Command)
            val outerList = mutableListOf<String>()

            // 1. F-Droid / Droid-ify / Forks
            val fdroidCandidates = listOf(
                "com.looker.droidify",
                "com.looker.droidify.nightly",
                "org.fdroid.fdroid",
                "org.fdroid.fdroid.privileged"
            )
            for (pkg in fdroidCandidates) {
                try {
                    if (pm.getLaunchIntentForPackage(pkg) != null) {
                        outerList.add(pkg)
                    }
                } catch (_: Exception) {}
            }

            // 2. Aurora Store / Obtainium / ObtainX / Orion
            val altStoreCandidates = listOf(
                "dev.bikram.obtainx",
                "dev.imranr.obtainium",
                "com.orion.store",
                "com.aurora.store"
            )
            for (pkg in altStoreCandidates) {
                try {
                    if (pm.getLaunchIntentForPackage(pkg) != null) {
                        outerList.add(pkg)
                    }
                } catch (_: Exception) {}
            }

            // 3. Google Play Store
            try {
                if (pm.getLaunchIntentForPackage("com.android.vending") != null) {
                    outerList.add("com.android.vending")
                    // 4. Play Store "My apps" shortcut if Play Store is present
                    outerList.add("shortcut:intent:intent:#Intent;action=shortcutmaker.intent.action.LAUNCH_SHORTCUT;extendedLaunchFlags=0x4;B.extra_auth=false;B.extra_file=false;S.extra_name=My%20apps;S.extra_intent=intent%3A%23Intent%3Baction%3Dcom.google.android.finsky.VIEW_MY_DOWNLOADS%3BlaunchFlags%3D0x10000000%3Bpackage%3Dcom.android.vending%3Bend;end;custom_label=My apps;")
                }
            } catch (_: Exception) {}

            // 5. Central Command
            outerList.add(context.packageName)

            // 6. System Settings
            outerList.add("com.android.settings")

            val distinct = outerList.distinct()
            return if (distinct.isNotEmpty()) distinct else DEFAULT_GEAR_PACKAGES_RING_0
        }
    }

    fun resolveDefaultDevDeckPackages(context: Context, ringIndex: Int): List<String> {
        val pm = context.packageManager
        if (ringIndex == 1) {
            // INNER RING: Dev Core & Medical Study (Termux, GitHub, Shizuku/Forks, AnkiDroid, Docs/Reader, Notes)
            val innerList = mutableListOf<String>()

            // 1. Terminal (Termux)
            val termuxCandidates = listOf("com.termux", "com.termux.nix")
            for (pkg in termuxCandidates) {
                try {
                    if (pm.getLaunchIntentForPackage(pkg) != null) {
                        innerList.add(pkg)
                    }
                } catch (_: Exception) {}
            }

            // 2. GitHub / Git Clients
            val githubCandidates = listOf("com.github.android", "org.forkhub", "com.fastaccess.github")
            for (pkg in githubCandidates) {
                try {
                    if (pm.getLaunchIntentForPackage(pkg) != null) {
                        innerList.add(pkg)
                    }
                } catch (_: Exception) {}
            }

            // 3. Shizuku & Privileged API Forks (Shizuku, Sui, Shevery, Stellar, IceBox)
            val shizukuCandidates = listOf(
                "moe.shizuku.privileged.api",
                "io.github.shizuku",
                "rikka.sui",
                "one.jwr.interstellar",
                "com.stellar.shizuku",
                "com.stellar",
                "io.github.shevery",
                "com.shevery",
                "com.chevery.shizuku",
                "com.catchingnow.icebox"
            )
            for (pkg in shizukuCandidates) {
                try {
                    if (pm.getLaunchIntentForPackage(pkg) != null) {
                        innerList.add(pkg)
                    }
                } catch (_: Exception) {}
            }

            // 4. AnkiDroid (Medical Study & Spaced Repetition)
            val ankiCandidates = listOf("com.ichi2.anki", "com.ichi2.anki.debug")
            for (pkg in ankiCandidates) {
                try {
                    if (pm.getLaunchIntentForPackage(pkg) != null) {
                        innerList.add(pkg)
                    }
                } catch (_: Exception) {}
            }

            // 5. Document & Book Readers (WPS, ReadEra, LibreOffice, FaDocx, Moon+, Acrobat)
            val docCandidates = listOf(
                "cn.wps.moffice_eng",
                "cn.wps.moffice",
                "org.readera",
                "org.readera.premium",
                "org.documentfoundation.libreoffice.read-only",
                "com.collabora.libreoffice",
                "com.foobnix.pdf.reader",
                "com.fadocx",
                "com.fastdocx",
                "com.flyersoft.moonreader",
                "com.flyersoft.moonreaderp",
                "com.adobe.reader"
            )
            for (pkg in docCandidates) {
                try {
                    if (pm.getLaunchIntentForPackage(pkg) != null) {
                        innerList.add(pkg)
                    }
                } catch (_: Exception) {}
            }

            // 6. Notes (Obsidian)
            try {
                if (pm.getLaunchIntentForPackage("md.obsidian") != null) {
                    innerList.add("md.obsidian")
                }
            } catch (_: Exception) {}

            val distinct = innerList.distinct()
            return if (distinct.isNotEmpty()) distinct else DEFAULT_GEAR_PACKAGES_RING_1
        } else {
            // OUTER RING: Automation, Deep Modifiers & System Deck (MacroDroid, Key Mapper, Tasker, SD Maid, App Manager, System UI Tuner, Advanced File Explorers, Perimeter Watchdog)
            val outerList = mutableListOf<String>()

            // 1. Automation Tools (MacroDroid, Key Mapper, Tasker)
            val autoCandidates = listOf(
                "com.arlosoft.macrodroid",
                "io.github.sds100.keymapper",
                "net.dinglisch.android.taskerm"
            )
            for (pkg in autoCandidates) {
                try {
                    if (pm.getLaunchIntentForPackage(pkg) != null) {
                        outerList.add(pkg)
                    }
                } catch (_: Exception) {}
            }

            // 2. System Maintenance & Cleanup (SD Maid 2 / SE, SD Maid)
            val cleanCandidates = listOf("eu.darken.sdmse", "eu.darken.sdm")
            for (pkg in cleanCandidates) {
                try {
                    if (pm.getLaunchIntentForPackage(pkg) != null) {
                        outerList.add(pkg)
                    }
                } catch (_: Exception) {}
            }

            // 3. App Inspectors & Kernel Tools (App Manager, SmartPack, System UI Tuner)
            val inspectorCandidates = listOf(
                "io.github.muntashirakon.AppManager",
                "com.zacharee1.systemuituner",
                "com.smartpack.kernelmanager"
            )
            for (pkg in inspectorCandidates) {
                try {
                    if (pm.getLaunchIntentForPackage(pkg) != null) {
                        outerList.add(pkg)
                    }
                } catch (_: Exception) {}
            }

            // 4. Advanced Power-User File Explorers (X-plore, MiXplorer, Total Commander)
            val fileCandidates = listOf(
                "com.lonelycatgames.Xplore",
                "com.mixplorer.beta",
                "com.mixplorer",
                "com.ghisler.android.TotalCommander",
                "pl.solidexplorer2"
            )
            for (pkg in fileCandidates) {
                try {
                    if (pm.getLaunchIntentForPackage(pkg) != null) {
                        outerList.add(pkg)
                    }
                } catch (_: Exception) {}
            }

            // 5. System Security & Controls
            outerList.add("system:perimeter_watchdog")
            outerList.add("system:core_watchdog")
            outerList.add(context.packageName)
            outerList.add("com.android.settings")

            val distinct = outerList.distinct()
            return if (distinct.isNotEmpty()) distinct else DEFAULT_GEAR_PACKAGES_RING_0
        }
    }

    fun getAppsForActiveGear(context: Context, isLeft: Boolean, setIndex: Int, ringIndex: Int): List<String> {
        ensureListener(context)
        val cacheKey = "${isLeft}_${setIndex}_${ringIndex}"
        gearAppsCache[cacheKey]?.let { return it }

        val prefs = context.defaultPrefs()
        val setsList = getGearSetsOrder(context, isLeft)
        val setId = if (setIndex in setsList.indices) setsList[setIndex] else setIndex.toString()
        val csvString = prefs.getString("gear_set_${setId}_ring_${ringIndex}_packages", null)
            ?: prefs.getString("gear_set_${setId}_ring${ringIndex}", null)
        val result = if (!csvString.isNullOrEmpty()) {
            csvString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            when (setId) {
                "0" -> resolveDefaultRingPackages(context, ringIndex)
                "1" -> resolveDefaultDevDeckPackages(context, ringIndex)
                else -> emptyList()
            }
        }
        gearAppsCache[cacheKey] = result
        return result
    }

    fun getGearSetNameById(context: Context, setId: String): String {
        val prefs = context.defaultPrefs()
        val defaultName = when(setId) {
            "0" -> "CORE AVIONICS"
            "1" -> "POWER & DEV DECK"
            "2" -> "UTILITIES SECTOR"
            "3" -> "ENTERTAINMENT DECK"
            else -> "CUSTOM SET"
        }
        val saved = prefs.getString("gear_set_${setId}_name", defaultName) ?: defaultName
        return if (saved == "SET A" || saved == "SET B" || saved == "SET C" || saved == "SET D" || saved == "SET" || saved.isEmpty()) defaultName else saved
    }

    fun getGearSetNameByIndex(context: Context, isLeft: Boolean, index: Int): String {
        val setsList = getGearSetsOrder(context, isLeft)
        if (index in setsList.indices) {
            return getGearSetNameById(context, setsList[index])
        }
        val alphabetLabel = if (index in 0..25) "${'A' + index}" else index.toString()
        return "SET " + alphabetLabel
    }
}
