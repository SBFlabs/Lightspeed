package com.sbf.lightspeed.system

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Lightspeed Infinity Offline License Manager.
 * Validates cryptographically signed offline activation keys using asymmetric RSA-2048 with SHA256withRSA.
 *
 * Kept in isolated SharedPreferences (lightspeed_infinity_vault) to prevent
 * license leakage during standard backup export operations.
 *
 * ONLY the public key is stored in this open-source client. Unlock codes can only be
 * generated with the private key (held exclusively by SBF Labs).
 */
object LightspeedInfinityManager {

    private const val TAG = "InfinityManager"
    private const val PREFS_VAULT = "lightspeed_infinity_vault"
    private const val KEY_UNLOCKED = "pref_is_infinity_unlocked"
    private const val KEY_ACTIVE_CODE = "pref_active_infinity_code"
    private const val KEY_FAILED_ATTEMPTS = "pref_infinity_failed_attempts"
    private const val KEY_LOCKOUT_TIMESTAMP = "pref_infinity_lockout_timestamp"

    private const val LOCKOUT_DURATION_MS = 30_000L
    private const val MAX_FAILED_ATTEMPTS = 3

    private const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

    /**
     * RSA-2048 Public Key (X.509 DER SubjectPublicKeyInfo encoded in Base64).
     * Publicly verifiable by anyone; mathematically impossible to forge without the private key.
     */
    private const val PUBLIC_KEY_BASE64 =
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAt2TlFH1DRFntELmfCPwNWwyoTqACIuRslCyyEXbYdfEnyF639uh0sOVYXNgohbOPK4PQnKcYZOY4eCIA3gI5snqXSFE4S5P6+WkYyScuU/3CSK3I1agK5KPv7cRXLSUsoCmAaw0+IGqRiVmsPDMcAqVPruRpNr5zrGtTU9v+eYetFk0Nb8aEU8141ZCgHfnHt7fQk47fVUl1+a8k4unZ1SJA1GiCMZKkIeuaWOl+jQ5vbmloB1EiC4jEwL88ly0v444sa9te4YlB6eKmWis/8jxuyW1So9OyUqoE7gGHG2R5cmokN89HC/DL0R3UeUmyXWJq8XTQTVpGjT4ZtzrEbwIDAQAB"

    private val publicKey: PublicKey? by lazy {
        try {
            val keyBytes = Base64.decode(PUBLIC_KEY_BASE64, Base64.DEFAULT)
            val spec = X509EncodedKeySpec(keyBytes)
            KeyFactory.getInstance("RSA").generatePublic(spec)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode Infinity public key", e)
            null
        }
    }

    fun isUnlocked(context: Context): Boolean {
        val vault = getVault(context)
        return vault.getBoolean(KEY_UNLOCKED, false)
    }

    fun getActiveCode(context: Context): String? {
        val vault = getVault(context)
        return vault.getString(KEY_ACTIVE_CODE, null)
    }

    fun isLockedOut(context: Context): Boolean {
        val vault = getVault(context)
        val attempts = vault.getInt(KEY_FAILED_ATTEMPTS, 0)
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            val lockoutTime = vault.getLong(KEY_LOCKOUT_TIMESTAMP, 0L)
            val remaining = (lockoutTime + LOCKOUT_DURATION_MS) - SystemClock.elapsedRealtime()
            if (remaining > 0) return true
            // Lockout expired, reset counter
            vault.edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKOUT_TIMESTAMP, 0L)
                .apply()
        }
        return false
    }

    fun isLockedOut(): Boolean {
        // Fallback for UI if context is not passed directly
        return false
    }

    fun getRemainingLockoutSeconds(context: Context): Int {
        val vault = getVault(context)
        val lockoutTime = vault.getLong(KEY_LOCKOUT_TIMESTAMP, 0L)
        val remaining = (lockoutTime + LOCKOUT_DURATION_MS) - SystemClock.elapsedRealtime()
        return (remaining / 1000L).coerceAtLeast(0L).toInt()
    }

    fun getRemainingLockoutSeconds(): Int = 0

    /**
     * Validates and activates an entered code.
     * Code format: LSINF-<SERIAL>-<BASE64URL_SIGNATURE>
     * Returns true if activation succeeded, false otherwise.
     */
    fun validateAndUnlock(context: Context, rawCode: String): Boolean {
        if (isLockedOut(context)) return false

        val clean = rawCode.trim().replace(" ", "")
        // Split with limit 3 to preserve signature which may contain dashes
        val parts = clean.split("-", limit = 3)
        if (parts.size != 3 || !parts[0].equals("LSINF", ignoreCase = true)) {
            registerFailure(context)
            return false
        }

        val serial = parts[1].uppercase()
        val signatureB64 = parts[2]

        if (serial.length != 8) {
            registerFailure(context)
            return false
        }

        for (c in serial) {
            if (!ALPHABET.contains(c)) {
                registerFailure(context)
                return false
            }
        }

        val isValid = verifySignature(serial, signatureB64)
        if (isValid) {
            // Success! Save to vault and reset failed attempts
            getVault(context).edit()
                .putBoolean(KEY_UNLOCKED, true)
                .putString(KEY_ACTIVE_CODE, clean)
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKOUT_TIMESTAMP, 0L)
                .apply()
            LightspeedHapticEngine.triggerConfirmation(context)
            return true
        } else {
            registerFailure(context)
            return false
        }
    }

    private fun registerFailure(context: Context) {
        val vault = getVault(context)
        val current = vault.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        val editor = vault.edit().putInt(KEY_FAILED_ATTEMPTS, current)
        if (current >= MAX_FAILED_ATTEMPTS) {
            editor.putLong(KEY_LOCKOUT_TIMESTAMP, SystemClock.elapsedRealtime())
        }
        editor.apply()
    }

    private fun verifySignature(serial: String, signatureB64: String): Boolean {
        val key = publicKey ?: return false
        return try {
            // Normalize URL-safe base64 padding
            var safeB64 = signatureB64
            val rem = safeB64.length % 4
            if (rem != 0) {
                safeB64 += "=".repeat(4 - rem)
            }
            val signatureBytes = Base64.decode(safeB64, Base64.URL_SAFE or Base64.NO_WRAP)
            val verifier = Signature.getInstance("SHA256withRSA")
            verifier.initVerify(key)
            verifier.update(serial.toByteArray(StandardCharsets.UTF_8))
            verifier.verify(signatureBytes)
        } catch (e: Exception) {
            Log.w(TAG, "Signature verification failed", e)
            false
        }
    }

    private fun getVault(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_VAULT, Context.MODE_PRIVATE)
    }
}
