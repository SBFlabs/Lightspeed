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

        try {
            val proc = ElevatedTaskCloser.execShizuku("cmd shortcut get-shortcuts ${parsed.packageName}") ?: return false
            val output = proc.inputStream.bufferedReader().readText()
            proc.waitFor()

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
                val phoneProc = ElevatedTaskCloser.execShizuku(
                    "content query --uri content://com.android.contacts/data/phones --projection data1 --where 'contact_id=$contactId'"
                )
                val phoneOutput = phoneProc?.inputStream?.bufferedReader()?.readText().orEmpty()
                phoneProc?.waitFor()

                val rawNumber = if (phoneOutput.contains("data1=")) {
                    phoneOutput.substringAfter("data1=").substringBefore("\n").substringBefore("\r").substringBefore(",").trim()
                } else ""

                if (rawNumber.isNotBlank()) {
                    val cleanNumber = rawNumber.replace(" ", "")
                    Log.i(TAG, "launchElevatedHomeShortcut: dialing $cleanNumber")
                    try {
                        val callIntent = Intent(Intent.ACTION_CALL, android.net.Uri.parse("tel:$cleanNumber")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(callIntent)
                        return true
                    } catch (e: Exception) {
                        val amProc = ElevatedTaskCloser.execShizuku("am start -a android.intent.action.CALL -d 'tel:$cleanNumber'")
                        return amProc?.waitFor() == 0
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

                val cmd = StringBuilder("am start ")
                if (act.isNotBlank()) cmd.append("-a $act ")
                if (cmp.isNotBlank()) cmd.append("-n $cmp ")
                if (dat.isNotBlank() && dat != "null" && !dat.endsWith("/...")) cmd.append("-d '$dat' ")
                if (flg.isNotBlank()) cmd.append("-f $flg ")

                if (bundleBody.isNotBlank()) {
                    val entries = bundleBody.split(", ")
                    for (entry in entries) {
                        val key = entry.substringBefore("=").trim()
                        val value = entry.substringAfter("=").trim()
                        if (key.isNotEmpty() && value.isNotEmpty() && value != "null") {
                            if (value.toLongOrNull() != null) {
                                cmd.append("--el '$key' $value ")
                            } else if (value.equals("true", ignoreCase = true) || value.equals("false", ignoreCase = true)) {
                                cmd.append("--ez '$key' $value ")
                            } else {
                                cmd.append("--es '$key' '${value.replace("'", "'\\''")}' ")
                            }
                        }
                    }
                }

                Log.i(TAG, "launchElevatedHomeShortcut: running: $cmd")
                val startProc = ElevatedTaskCloser.execShizuku(cmd.toString().trim())
                val exitCode = startProc?.waitFor() ?: -1
                Log.i(TAG, "launchElevatedHomeShortcut: am start exited with $exitCode")
                return exitCode == 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "launchElevatedHomeShortcut error", e)
        }
        return false
    }

    @Suppress("DEPRECATION")
    fun intentToAmStartCommand(intent: Intent): String {
        val sb = StringBuilder("am start ")
        intent.action?.let { sb.append("-a $it ") }
        intent.dataString?.let { sb.append("-d '$it' ") }
        intent.type?.let { sb.append("-t '$it' ") }
        intent.component?.let { sb.append("-n ${it.flattenToShortString()} ") }
        intent.extras?.keySet()?.forEach { key ->
            when (val value = intent.extras?.get(key)) {
                is String -> sb.append("--es '$key' '${value.replace("'", "'\\''")}' ")
                is Boolean -> sb.append("--ez '$key' $value ")
                is Int -> sb.append("--ei '$key' $value ")
                is Long -> sb.append("--el '$key' $value ")
                is Float -> sb.append("--ef '$key' $value ")
            }
        }
        return sb.toString().trim()
    }
}
