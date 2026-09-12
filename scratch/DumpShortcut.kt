package com.sbf.lightspeed

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.util.Log

fun dumpShortcut(context: Context) {
    val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    val query = LauncherApps.ShortcutQuery().apply {
        setPackage("com.sh.smart.caller")
        setShortcutIds(listOf("android.intent.action.CALL1335r5511-314D434743.3789r20275-314D4347431814359486"))
        setQueryFlags(
            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
            LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or
            LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
        )
    }
    val shortcuts = launcherApps.getShortcuts(query, Process.myUserHandle())
    val shortcut = shortcuts?.firstOrNull()
    if (shortcut != null) {
        Log.e("DumpShortcut", "Intents array size: ${shortcut.intents?.size}")
        shortcut.intents?.forEachIndexed { index, intent ->
            Log.e("DumpShortcut", "Intent $index: ${intent.toUri(0)}")
        }
    } else {
        Log.e("DumpShortcut", "Shortcut not found")
    }
}
