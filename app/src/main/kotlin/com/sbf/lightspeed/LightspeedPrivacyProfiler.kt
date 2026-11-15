package com.sbf.lightspeed

import android.Manifest
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.SystemClock
import com.sbf.lightspeed.system.ElevatedTaskCloser
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Factual Android permission analyzer backing Category Cruise and Sticky Pin Privacy Scores.
 *
 * Reads actual [PackageInfo.requestedPermissionsFlags] grant bits (so denied/revoked runtime
 * permissions incur 0 penalty), honors active Lightspeed Shizuku firewall blocks, covers modern
 * Android permissions (Background Location, Media, SMS, Call Logs, App Inventory, Ad ID), and
 * halves local sensor/storage deductions when an app has no active network transmission path.
 */
internal object LightspeedPrivacyProfiler {

    private const val CACHE_TTL_MS = 8_000L
    private const val SCORE_UNKNOWN = -1

    private data class CachedEntry(
        val capturedAtMs: Long,
        val profile: LightspeedCruiseHudPods.AppPrivacyProfile
    )

    private data class PermissionDomain(
        val tag: String,
        val weight: Int,
        val permissions: List<String>
    )

    private val sensitiveDomains = listOf(
        PermissionDomain("MIC", 18, listOf(Manifest.permission.RECORD_AUDIO)),
        PermissionDomain("SMS", 18, listOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)),
        PermissionDomain("CALL", 15, listOf(Manifest.permission.READ_CALL_LOG)),
        PermissionDomain("CNT", 14, listOf(Manifest.permission.READ_CONTACTS)),
        PermissionDomain("CAM", 12, listOf(Manifest.permission.CAMERA)),
        PermissionDomain(
            "CAL",
            8,
            listOf(Manifest.permission.READ_CALENDAR)
        ),
        PermissionDomain(
            "BODY",
            8,
            listOf(Manifest.permission.BODY_SENSORS, "android.permission.health.READ_HEART_RATE")
        ),
        PermissionDomain("APPS", 10, listOf("android.permission.QUERY_ALL_PACKAGES"))
    )

    private val storageFullPermissions = listOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        "android.permission.READ_MEDIA_IMAGES",
        "android.permission.READ_MEDIA_VIDEO",
        "android.permission.READ_MEDIA_AUDIO"
    )
    private const val PERM_MEDIA_LIMITED = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
    private const val PERM_BG_LOCATION = "android.permission.ACCESS_BACKGROUND_LOCATION"
    private const val PERM_AD_ID = "com.google.android.gms.permission.AD_ID"

    private val profileCache = ConcurrentHashMap<String, CachedEntry>()

    fun invalidate(packageName: String) {
        if (packageName.isNotBlank()) {
            profileCache.remove(packageName)
        }
    }

    fun formatScore(score: Int): String = if (score < 0) "--" else "$score/100"

    fun scoreColor(score: Int, alpha: Int = 255): Int = when {
        score < 0 -> Color.argb(alpha, 165, 180, 200)
        score >= 80 -> Color.argb(alpha, 34, 197, 94)
        score >= 55 -> Color.argb(alpha, 245, 158, 11)
        else -> Color.argb(alpha, 239, 68, 68)
    }

    fun getProfile(context: Context, packageName: String): LightspeedCruiseHudPods.AppPrivacyProfile {
        if (packageName.isBlank()) {
            return LightspeedCruiseHudPods.AppPrivacyProfile(100, false, emptyList())
        }
        val now = SystemClock.elapsedRealtime()
        profileCache[packageName]?.let { cached ->
            if (now - cached.capturedAtMs <= CACHE_TTL_MS) {
                return cached.profile
            }
        }

        val computed = computeProfile(context, packageName)
        profileCache[packageName] = CachedEntry(now, computed)
        return computed
    }

    private fun computeProfile(context: Context, packageName: String): LightspeedCruiseHudPods.AppPrivacyProfile {
        return try {
            @Suppress("DEPRECATION")
            val pkgInfo = context.packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            val perms = pkgInfo.requestedPermissions ?: emptyArray()
            val grantFlags = pkgInfo.requestedPermissionsFlags ?: IntArray(0)

            val requested = HashSet<String>(perms.size)
            val granted = HashSet<String>(perms.size)
            for (i in perms.indices) {
                val perm = perms[i] ?: continue
                requested.add(perm)
                if (i < grantFlags.size && (grantFlags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) {
                    granted.add(perm)
                }
            }

            val hasInternet = Manifest.permission.INTERNET in requested
            val fwBlocked = LightspeedCruiseHudPods.isAppFirewallBlocked(context, packageName)
            val fwEnforced = fwBlocked && ElevatedTaskCloser.isShizukuActive
            val netPenalty = when {
                !hasInternet -> 0
                fwEnforced -> 4
                fwBlocked -> 10
                else -> 15
            }
            val onlinePath = hasInternet && !fwEnforced
            val localScale = if (onlinePath) 1.0f else 0.5f

            val activeFlags = mutableListOf<String>()
            if (hasInternet) activeFlags.add("NET")

            var requestedSensitive = 0
            var grantedSensitive = 0
            var rawSensitivePenalty = 0

            // 1. Location domain (evaluated once so Background > Fine > Coarse without double-counting)
            val locRequested = PERM_BG_LOCATION in requested ||
                Manifest.permission.ACCESS_FINE_LOCATION in requested ||
                Manifest.permission.ACCESS_COARSE_LOCATION in requested
            if (locRequested) {
                requestedSensitive++
                when {
                    PERM_BG_LOCATION in granted -> {
                        grantedSensitive++
                        rawSensitivePenalty += 25
                        activeFlags.add("LOC+")
                    }
                    Manifest.permission.ACCESS_FINE_LOCATION in granted -> {
                        grantedSensitive++
                        rawSensitivePenalty += 15
                        activeFlags.add("LOC")
                    }
                    Manifest.permission.ACCESS_COARSE_LOCATION in granted -> {
                        grantedSensitive++
                        rawSensitivePenalty += 10
                        activeFlags.add("LOC")
                    }
                }
            }

            // 2. Remaining sensitive capability domains
            for (domain in sensitiveDomains) {
                if (domain.permissions.any { it in requested }) {
                    requestedSensitive++
                    if (domain.permissions.any { it in granted }) {
                        grantedSensitive++
                        rawSensitivePenalty += domain.weight
                        activeFlags.add(domain.tag)
                    }
                }
            }

            // 3. Storage / Media domain (recognizes Android 14+ limited visual selection as lower risk)
            val storageRequested = storageFullPermissions.any { it in requested } || PERM_MEDIA_LIMITED in requested
            if (storageRequested) {
                requestedSensitive++
                when {
                    storageFullPermissions.any { it in granted } -> {
                        grantedSensitive++
                        rawSensitivePenalty += 10
                        activeFlags.add("STRG")
                    }
                    PERM_MEDIA_LIMITED in granted -> {
                        grantedSensitive++
                        rawSensitivePenalty += 4
                        activeFlags.add("STRG")
                    }
                }
            }

            // 4. Advertising ID tracking (counted when network transmission is active)
            if (onlinePath && PERM_AD_ID in granted) {
                requestedSensitive++
                grantedSensitive++
                rawSensitivePenalty += 6
                activeFlags.add("AD")
            }

            val totalDeduction = netPenalty + (rawSensitivePenalty * localScale).roundToInt()
            LightspeedCruiseHudPods.AppPrivacyProfile(
                score = (100 - totalDeduction).coerceIn(5, 100),
                hasInternet = hasInternet,
                flags = activeFlags,
                grantedCount = grantedSensitive,
                requestedSensitiveCount = requestedSensitive,
                isFirewallEnforced = fwEnforced
            )
        } catch (_: Exception) {
            LightspeedCruiseHudPods.AppPrivacyProfile(
                score = SCORE_UNKNOWN,
                hasInternet = false,
                flags = emptyList()
            )
        }
    }
}
