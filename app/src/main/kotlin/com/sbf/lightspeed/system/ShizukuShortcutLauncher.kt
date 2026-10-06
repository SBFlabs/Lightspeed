package com.sbf.lightspeed.system

import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Handles elevated shortcut extraction and launching via Shizuku shell commands.
 */
internal object ShizukuShortcutLauncher {

    private const val TAG = "ShizukuShortcutLauncher"

    fun launchElevatedHomeShortcut(context: Context, parsed: ParsedShortcut): Boolean {
        Log.i(TAG, "launchElevatedHomeShortcut: pkg=${parsed.packageName}, id='${parsed.id}', label='${parsed.label}', shizuku=${ElevatedTaskCloser.isShizukuActive}")
        if (parsed.packageName.isBlank()) return false

        if (!ShellArgGuard.isPackage(parsed.packageName)) {
            Log.w(TAG, "launchElevatedHomeShortcut aborting: invalid package ${parsed.packageName}")
            return false
        }

        try {
            val proc = ElevatedTaskCloser.execShizukuArgv("/system/bin/cmd", "shortcut", "get-shortcuts", parsed.packageName)
            val output = proc.readTextOrKill() ?: return false

            val rawLabel = if (parsed.label.contains("(") && parsed.label.endsWith(")")) {
                parsed.label.substringAfter("(").substringBeforeLast(")").trim()
            } else parsed.label

            val blocks = output.split("ShortcutInfo {").drop(1)
            val block = blocks.firstOrNull { b ->
                (parsed.id.isNotBlank() && (b.contains("id=${parsed.id},") || b.contains("id=${parsed.id}\n") || b.contains("id=${parsed.id}\r\n") || b.contains("id=${parsed.id} "))) ||
                (rawLabel.isNotBlank() && (b.contains("shortLabel=$rawLabel,") || b.contains("shortLabel=$rawLabel\n") || b.contains("shortLabel=$rawLabel\r\n") || b.contains("shortLabel=\"$rawLabel\""))) ||
                (parsed.label.isNotBlank() && (b.contains("shortLabel=${parsed.label},") || b.contains("shortLabel=${parsed.label}\n") || b.contains("shortLabel=${parsed.label}\r\n") || b.contains("shortLabel=\"${parsed.label}\"")))
            } ?: return false

            Log.i(TAG, "launchElevatedHomeShortcut: matched shortcut block successfully")

            // Case A: OEM Direct Dial / Contact Shortcut
            val contactId = if (block.contains("contactId=")) {
                block.substringAfter("contactId=").substringBefore(",").substringBefore("}").substringBefore("\n").substringBefore("\r").trim()
            } else ""

            if (contactId.isNotBlank() && contactId.all { it.isDigit() }) {
                Log.i(TAG, "launchElevatedHomeShortcut: contactId=$contactId")
                val phoneProc = ElevatedTaskCloser.execShizukuArgv(
                    "/system/bin/content", "query", "--uri", "content://com.android.contacts/data/phones", "--projection", "data1", "--where", "contact_id=$contactId"
                )
                val phoneOutput = phoneProc.readTextOrKill().orEmpty()

                val rawNumber = if (phoneOutput.contains("data1=")) {
                    phoneOutput.substringAfter("data1=").substringBefore("\n").substringBefore("\r").substringBefore(",").trim()
                } else ""

                if (rawNumber.isNotBlank()) {
                    val cleanNumber = rawNumber.replace(" ", "")
                    if (!ShellArgGuard.isPhone(cleanNumber)) {
                        Log.w(TAG, "launchElevatedHomeShortcut aborting: invalid phone number $cleanNumber")
                        return false
                    }
                    Log.i(TAG, "launchElevatedHomeShortcut: dialing $cleanNumber")
                    try {
                        val callIntent = Intent(Intent.ACTION_CALL, android.net.Uri.parse("tel:$cleanNumber")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(callIntent)
                        return true
                    } catch (e: Exception) {
                        val amProc = ElevatedTaskCloser.execShizukuArgv("/system/bin/am", "start", "-a", "android.intent.action.CALL", "-d", "tel:$cleanNumber")
                        return amProc.waitForOrKill() == 0
                    }
                }
            }

            // Case B: General App Shortcuts (WhatsApp, MacroDroid, Amazon, etc.)
            if (block.contains("Intent {")) {
                val intentBody = block.substringAfter("Intent {").substringBefore("}")
                val bundleBody = if (block.contains("PersistableBundle[{")) {
                    block.substringAfter("PersistableBundle[{").substringBefore("}]")
                } else ""

                fun extractField(key: String): String {
                    if (!intentBody.contains("$key=")) return ""
                    return intentBody.substringAfter("$key=").substringBefore(" ").trim()
                }

                val act = extractField("act")
                val cmp = extractField("cmp")
                val dat = extractField("dat")
                val flg = extractField("flg")

                val args = mutableListOf("/system/bin/am", "start")

                if (act.isNotBlank()) {
                    if (ShellArgGuard.isAction(act)) {
                        args.add("-a")
                        args.add(act)
                    } else {
                        Log.w(TAG, "launchElevatedHomeShortcut skipping invalid action: $act")
                    }
                }

                if (cmp.isNotBlank()) {
                    val parts = cmp.split("/")
                    if (parts.size == 2 && ShellArgGuard.isPackage(parts[0]) && ShellArgGuard.isClassName(parts[1])) {
                        args.add("-n")
                        args.add(cmp)
                    } else {
                        Log.w(TAG, "launchElevatedHomeShortcut aborting: invalid component: $cmp")
                        return false
                    }
                }

                if (dat.isNotBlank() && dat != "null" && !dat.endsWith("/...")) {
                    args.add("-d")
                    args.add(dat)
                }

                if (flg.isNotBlank()) {
                    if (ShellArgGuard.isFlags(flg)) {
                        args.add("-f")
                        args.add(flg)
                    } else {
                        Log.w(TAG, "launchElevatedHomeShortcut skipping invalid flags: $flg")
                    }
                }

                if (bundleBody.isNotBlank()) {
                    val entries = bundleBody.split(", ")
                    for (entry in entries) {
                        val key = entry.substringBefore("=").trim()
                        val value = entry.substringAfter("=").trim()
                        if (key.isNotEmpty() && value.isNotEmpty() && value != "null") {
                            if (!ShellArgGuard.isExtraKey(key)) {
                                Log.w(TAG, "launchElevatedHomeShortcut skipping extra with invalid key: $key")
                                continue
                            }
                            if (value.toLongOrNull() != null) {
                                args.add("--el")
                                args.add(key)
                                args.add(value)
                            } else if (value.equals("true", ignoreCase = true) || value.equals("false", ignoreCase = true)) {
                                args.add("--ez")
                                args.add(key)
                                args.add(value)
                            } else {
                                args.add("--es")
                                args.add(key)
                                args.add(value)
                            }
                        }
                    }
                }

                Log.i(TAG, "launchElevatedHomeShortcut: running argv: $args")
                val startProc = ElevatedTaskCloser.execShizukuArgv(*args.toTypedArray())
                val exitCode = startProc.waitForOrKill()
                Log.i(TAG, "launchElevatedHomeShortcut: am start exited with $exitCode")
                return exitCode == 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "launchElevatedHomeShortcut error", e)
        }
        return false
    }

    @Suppress("DEPRECATION")
    fun intentToAmStartArgs(intent: Intent): List<String> {
        val args = mutableListOf("/system/bin/am", "start")
        intent.action?.let { action ->
            if (ShellArgGuard.isAction(action)) {
                args.add("-a")
                args.add(action)
            } else {
                Log.w(TAG, "intentToAmStartArgs skipping invalid action: $action")
            }
        }
        intent.dataString?.let { data ->
            args.add("-d")
            args.add(data)
        }
        intent.type?.let { type ->
            args.add("-t")
            args.add(type)
        }
        intent.component?.let { comp ->
            val pkg = comp.packageName
            val cls = comp.className
            if (ShellArgGuard.isPackage(pkg) && ShellArgGuard.isClassName(cls)) {
                args.add("-n")
                args.add(comp.flattenToShortString())
            } else {
                Log.w(TAG, "intentToAmStartArgs invalid component package/class: $pkg / $cls")
            }
        }
        intent.extras?.keySet()?.forEach { key ->
            if (!ShellArgGuard.isExtraKey(key)) {
                Log.w(TAG, "intentToAmStartArgs skipping extra with invalid key: $key")
                return@forEach
            }
            when (val value = intent.extras?.get(key)) {
                is String -> {
                    args.add("--es")
                    args.add(key)
                    args.add(value)
                }
                is Boolean -> {
                    args.add("--ez")
                    args.add(key)
                    args.add(value.toString())
                }
                is Int -> {
                    args.add("--ei")
                    args.add(key)
                    args.add(value.toString())
                }
                is Long -> {
                    args.add("--el")
                    args.add(key)
                    args.add(value.toString())
                }
                is Float -> {
                    args.add("--ef")
                    args.add(key)
                    args.add(value.toString())
                }
            }
        }
        return args
    }
}
