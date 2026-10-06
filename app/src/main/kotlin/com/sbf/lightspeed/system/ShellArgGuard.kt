package com.sbf.lightspeed.system

import android.util.Log

internal object ShellArgGuard {
    private const val TAG = "ShellArgGuard"

    private val PACKAGE_REGEX = Regex("""^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$""")
    private val CLASS_NAME_REGEX = Regex("""^[A-Za-z0-9_.$]+$""")
    private val ACTION_REGEX = Regex("""^[A-Za-z0-9_.:\-]+$""")
    private val FLAGS_REGEX = Regex("""^0x[0-9a-fA-F]{1,8}$""")
    private val PHONE_REGEX = Regex("""^\+?[0-9*#]{1,32}$""")
    private val EXTRA_KEY_REGEX = Regex("""^[A-Za-z0-9_.:\-]{1,100}$""")

    fun isPackage(value: String): Boolean {
        return PACKAGE_REGEX.matches(value).also { valid ->
            if (!valid) Log.w(TAG, "ShellArgGuard rejected package: $value")
        }
    }

    fun isClassName(value: String): Boolean {
        return CLASS_NAME_REGEX.matches(value).also { valid ->
            if (!valid) Log.w(TAG, "ShellArgGuard rejected className: $value")
        }
    }

    fun isAction(value: String): Boolean {
        return ACTION_REGEX.matches(value).also { valid ->
            if (!valid) Log.w(TAG, "ShellArgGuard rejected action: $value")
        }
    }

    fun isFlags(value: String): Boolean {
        return FLAGS_REGEX.matches(value).also { valid ->
            if (!valid) Log.w(TAG, "ShellArgGuard rejected flags: $value")
        }
    }

    fun isPhone(value: String): Boolean {
        return PHONE_REGEX.matches(value).also { valid ->
            if (!valid) Log.w(TAG, "ShellArgGuard rejected phone: $value")
        }
    }

    fun isExtraKey(value: String): Boolean {
        return EXTRA_KEY_REGEX.matches(value).also { valid ->
            if (!valid) Log.w(TAG, "ShellArgGuard rejected extraKey: $value")
        }
    }

    fun isComponent(s: String): Boolean {
        val parts = s.split("/")
        if (parts.size != 2) return false
        return isPackage(parts[0]) && isClassName(parts[1])
    }
}
