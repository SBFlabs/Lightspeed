package com.sbf.lightspeed.system

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Lightspeed Infinity Offline License Manager.
 * Validates cryptographically signed offline activation keys.
 * Kept in isolated SharedPreferences (lightspeed_infinity_vault) to prevent
 * license leakage during standard backup export operations.
 */
object LightspeedInfinityManager {

    private const val PREFS_VAULT = "lightspeed_infinity_vault"
    private const val KEY_UNLOCKED = "pref_is_infinity_unlocked"
    private const val KEY_ACTIVE_CODE = "pref_active_infinity_code"

    private const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    private val MASTER_SALT = "LIGHTSPEED_INFINITY_SECRET_SALT_2026_SBF".toByteArray(StandardCharsets.UTF_8)

    private var failedAttempts = 0
    private var lockoutTimestamp = 0L

    fun isUnlocked(context: Context): Boolean {
        val vault = getVault(context)
        return vault.getBoolean(KEY_UNLOCKED, false)
    }

    fun getActiveCode(context: Context): String? {
        val vault = getVault(context)
        return vault.getString(KEY_ACTIVE_CODE, null)
    }

    fun isLockedOut(): Boolean {
        if (failedAttempts >= 3) {
            val remaining = (lockoutTimestamp + 30_000L) - SystemClock.elapsedRealtime()
            if (remaining > 0) return true
            // Lockout expired, reset counter
            failedAttempts = 0
        }
        return false
    }

    fun getRemainingLockoutSeconds(): Int {
        val remaining = (lockoutTimestamp + 30_000L) - SystemClock.elapsedRealtime()
        return (remaining / 1000L).coerceAtLeast(0L).toInt()
    }

    /**
     * Validates and activates an entered code.
     * Returns true if activation succeeded, false otherwise.
     */
    fun validateAndUnlock(context: Context, rawCode: String): Boolean {
        if (isLockedOut()) return false

        val clean = rawCode.trim().uppercase().replace(" ", "")
        val parts = clean.split("-")
        if (parts.size != 3 || parts[0] != "LSINF") {
            registerFailure()
            return false
        }

        val serial = parts[1]
        val providedChecksum = parts[2]

        if (serial.length != 4 || providedChecksum.length != 4) {
            registerFailure()
            return false
        }

        for (c in serial) {
            if (!ALPHABET.contains(c)) {
                registerFailure()
                return false
            }
        }

        val expectedChecksum = computeChecksum(serial)
        if (expectedChecksum == providedChecksum) {
            // Success! Save to vault and reset failed attempts
            failedAttempts = 0
            getVault(context).edit()
                .putBoolean(KEY_UNLOCKED, true)
                .putString(KEY_ACTIVE_CODE, clean)
                .apply()
            LightspeedHapticEngine.triggerConfirmation(context)
            return true
        } else {
            registerFailure()
            return false
        }
    }

    private fun registerFailure() {
        failedAttempts++
        if (failedAttempts >= 3) {
            lockoutTimestamp = SystemClock.elapsedRealtime()
        }
    }

    private fun computeChecksum(serial: String): String {
        return try {
            val mac = Mac.getInstance("HmacSHA256")
            val keySpec = SecretKeySpec(MASTER_SALT, "HmacSHA256")
            mac.init(keySpec)
            val hash = mac.doFinal(serial.toByteArray(StandardCharsets.UTF_8))
            val sb = java.lang.StringBuilder()
            for (i in 0 until 4) {
                val byteVal = hash[i].toInt() and 0xFF
                val idx = byteVal % ALPHABET.length
                sb.append(ALPHABET[idx])
            }
            sb.toString()
        } catch (_: Exception) {
            ""
        }
    }

    private fun getVault(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_VAULT, Context.MODE_PRIVATE)
    }
}
