package com.sbf.lightspeed.system

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
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
    private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val UNICODE_DASH_REGEX = Regex("[\u2010-\u2015\u2212]")
    private val BASE32_PATTERN = Regex("^[A-Za-z2-7]+=*$")
    private val ISSUED_SHORT_CODE_SHA256 = setOf(
        "7c0eccabe6e9a117c8433b5f3409b9a1270437801861a665b8e1b876a590903f"
    )

    @Volatile
    private var cachedUnlocked: Boolean? = null

    private val _unlockEpoch = MutableStateFlow(0)
    val unlockEpoch: StateFlow<Int> = _unlockEpoch.asStateFlow()

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
        cachedUnlocked?.let { if (it) return true }
        val unlocked = getVault(context).getBoolean(KEY_UNLOCKED, false)
        cachedUnlocked = unlocked
        return unlocked
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
     * Supports:
     * - RSA-2048 Base32 keys: LSINF-<8-CHAR-SERIAL>-<BASE32_SIGNATURE>
     * - RSA-2048 Base64URL keys: LSINF-<8-CHAR-SERIAL>-<BASE64URL_SIGNATURE>
     * - Issued v1.0.0 supporter keys verified by SHA-256 hash: LSINF-<4-CHAR>-<4-CHAR>
     * Returns true if activation succeeded, false otherwise.
     */
    fun validateAndUnlock(context: Context, rawCode: String): Boolean {
        if (isLockedOut(context)) return false

        val clean = rawCode.trim()
            .trim('"', '\'', '`')
            .replace(WHITESPACE_REGEX, "")
            .replace(UNICODE_DASH_REGEX, "-")
        // Split with limit 3 to preserve signature which may contain dashes
        val parts = clean.split("-", limit = 3)
        if (parts.size != 3 || !parts[0].equals("LSINF", ignoreCase = true)) {
            registerFailure(context)
            return false
        }

        // Handle issued v1.0.0 4+4 supporter batch codes safely without exposing legacy signing secrets
        if (parts[1].length == 4 && parts[2].length == 4) {
            val canonicalShort = "LSINF-${parts[1].uppercase()}-${parts[2].uppercase()}"
            val digest = sha256Hex("LIGHTSPEED_INF_V2:$canonicalShort")
            return if (digest in ISSUED_SHORT_CODE_SHA256) {
                unlockSuccess(context, canonicalShort)
                true
            } else {
                registerFailure(context)
                false
            }
        }

        val serial = parts[1].uppercase()
        val signaturePart = parts[2]

        if (serial.length != 8 || serial.any { !ALPHABET.contains(it) }) {
            registerFailure(context)
            return false
        }

        val isValid = if (signaturePart.length in 400..420 && BASE32_PATTERN.matches(signaturePart)) {
            verifyBase32Signature(serial, signaturePart)
        } else {
            verifySignature(serial, signaturePart)
        }
        return if (isValid) {
            unlockSuccess(context, "LSINF-$serial-$signaturePart")
            true
        } else {
            registerFailure(context)
            false
        }
    }

    private fun unlockSuccess(context: Context, storedCode: String) {
        cachedUnlocked = true
        getVault(context).edit()
            .putBoolean(KEY_UNLOCKED, true)
            .putString(KEY_ACTIVE_CODE, storedCode)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_TIMESTAMP, 0L)
            .apply()
        _unlockEpoch.value = _unlockEpoch.value + 1
        LightspeedHapticEngine.triggerConfirmation(context)
        try {
            com.sbf.lightspeed.LightspeedAccessibilityService.instance?.reloadPreferences()
        } catch (_: Exception) { }
        try {
            com.sbf.lightspeed.system.bot.LightspeedBotTrackpadOverlay.reload(context)
        } catch (_: Exception) { }
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

    private fun verifyBase32Signature(serial: String, signatureB32: String): Boolean {
        val signatureBytes = decodeBase32(signatureB32) ?: return false
        return verifySignatureBytes(serial, signatureBytes)
    }

    private fun verifySignature(serial: String, signatureB64: String): Boolean {
        return try {
            // Normalize URL-safe base64 padding
            var safeB64 = signatureB64
            val rem = safeB64.length % 4
            if (rem != 0) {
                safeB64 += "=".repeat(4 - rem)
            }
            val signatureBytes = Base64.decode(safeB64, Base64.URL_SAFE or Base64.NO_WRAP)
            verifySignatureBytes(serial, signatureBytes)
        } catch (e: Exception) {
            Log.w(TAG, "Signature verification failed", e)
            false
        }
    }

    private fun verifySignatureBytes(serial: String, signatureBytes: ByteArray): Boolean {
        val key = publicKey ?: return false
        return try {
            val verifier = Signature.getInstance("SHA256withRSA")
            verifier.initVerify(key)
            verifier.update(serial.toByteArray(StandardCharsets.UTF_8))
            verifier.verify(signatureBytes)
        } catch (e: Exception) {
            Log.w(TAG, "Signature verification failed", e)
            false
        }
    }

    private fun decodeBase32(input: String): ByteArray? {
        val clean = input.trim().trimEnd('=').uppercase()
        if (clean.isEmpty()) return null
        val out = ByteArray((clean.length * 5) / 8)
        var buffer = 0
        var bitsLeft = 0
        var outIndex = 0
        for (c in clean) {
            val valBits = BASE32_ALPHABET.indexOf(c)
            if (valBits < 0) return null
            buffer = (buffer shl 5) or valBits
            bitsLeft += 5
            if (bitsLeft >= 8) {
                out[outIndex++] = ((buffer shr (bitsLeft - 8)) and 0xFF).toByte()
                bitsLeft -= 8
            }
        }
        return if (outIndex == out.size) out else out.copyOf(outIndex)
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
        val hex = StringBuilder(digest.size * 2)
        for (b in digest) {
            hex.append(String.format("%02x", b.toInt() and 0xFF))
        }
        return hex.toString()
    }

    private fun getVault(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_VAULT, Context.MODE_PRIVATE)
    }
}
