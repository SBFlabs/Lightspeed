package com.sbf.lightspeed.system

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import com.sbf.lightspeed.BuildConfig
import java.security.MessageDigest

enum class SignatureStatus {
    OFFICIAL_RELEASE,
    NIGHTLY_DEV_BUILD,
    CUSTOM_OR_UNVERIFIED
}

enum class InstallOrigin(val displayName: String, val isOfficialClient: Boolean) {
    OBTAINIUM("Obtainium", true),
    SHIZUSTORE("ShizuStore", true),
    ORION_STORE("Orion Store", true),
    DIRECT_SIDELOAD("Manual or Browser Sideload", false),
    THIRD_PARTY_STORE("Third-Party App Store", false)
}

data class BuildVerificationState(
    val signatureStatus: SignatureStatus,
    val actualCertSha256: String,
    val installOrigin: InstallOrigin
)

/**
 * Performs offline PackageManager checks to verify Lightspeed's signing certificate SHA-256,
 * detect its install source, and open official update channels with clean fallbacks.
 */
object DistributionVerifier {

    const val GITHUB_REPO_URL = "https://github.com/SBFlabs/Lightspeed"
    const val GITHUB_RELEASES_URL = "https://github.com/SBFlabs/Lightspeed/releases/latest"
    const val OBTAINIUM_DEEP_LINK =
        "obtainium://app/%7B%22id%22%3A%22com.sbf.lightspeed%22%2C%22url%22%3A%22https%3A//github.com/SBFlabs/Lightspeed%22%2C%22author%22%3A%22SBF%20Labs%22%2C%22name%22%3A%22Lightspeed%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3A%20false%2C%20%5C%22fallbackToOlderReleases%5C%22%3A%20true%7D%22%7D"
    const val OBTAINIUM_WEB_URL = "https://apps.obtainium.imranr.dev/redirect?r=$OBTAINIUM_DEEP_LINK"
    const val SHIZUSTORE_DEEP_LINK = "shizustore://apps/lightspeed"
    const val SHIZUSTORE_WEB_URL = "https://shizustore.com/apps/lightspeed"
    const val ORION_STORE_URL = "https://rookieenough.github.io/Orion-Data/redirect.html?id=lightspeed"

    // Public X.509 signing certificate SHA-256 for official SBF Labs releases (zero private credentials).
    private const val OFFICIAL_RELEASE_SHA256 =
        "DB:17:99:88:EC:04:1A:51:36:C5:60:D7:91:70:EE:2D:B9:95:02:2C:6D:F4:2E:61:4A:CF:8C:5A:25:04:5F:C4"

    private val OBTAINIUM_PACKAGES = setOf(
        "dev.imranr.obtainium",
        "dev.imranr.obtainium.fdroid"
    )

    private val SHIZUSTORE_PACKAGES = setOf(
        "me.timschneeberger.shizustore",
        "me.timschneeberger.shizustore.nightly"
    )

    private val DIRECT_INSTALLER_PACKAGES = setOf(
        "com.android.shell",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "moe.shizuku.privileged.api"
    )

    @Volatile
    private var cachedVerification: BuildVerificationState? = null

    fun getCachedVerification(): BuildVerificationState? = cachedVerification

    fun inspectBuild(context: Context): BuildVerificationState {
        cachedVerification?.let { return it }
        val signerDigests = readSigningCertSha256List(context)
        val primaryDigest = signerDigests.firstOrNull() ?: "Unavailable"
        val normalizedExpected = normalizeHex(OFFICIAL_RELEASE_SHA256)
        val matchesOfficialRelease = signerDigests.any {
            normalizeHex(it).equals(normalizedExpected, ignoreCase = true)
        }

        val signatureStatus = when {
            matchesOfficialRelease -> SignatureStatus.OFFICIAL_RELEASE
            BuildConfig.DEBUG -> SignatureStatus.NIGHTLY_DEV_BUILD
            else -> SignatureStatus.CUSTOM_OR_UNVERIFIED
        }

        return BuildVerificationState(
            signatureStatus = signatureStatus,
            actualCertSha256 = primaryDigest,
            installOrigin = detectInstallOrigin(context)
        ).also { cachedVerification = it }
    }

    private fun detectInstallOrigin(context: Context): InstallOrigin {
        val candidates: List<String> = try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val info = pm.getInstallSourceInfo(context.packageName)
                listOfNotNull(
                    info.installingPackageName,
                    info.initiatingPackageName,
                    info.originatingPackageName
                )
            } else {
                @Suppress("DEPRECATION")
                listOfNotNull(pm.getInstallerPackageName(context.packageName))
            }
        } catch (_: Exception) {
            emptyList()
        }

        for (pkg in candidates) {
            when {
                pkg in OBTAINIUM_PACKAGES -> return InstallOrigin.OBTAINIUM
                pkg in SHIZUSTORE_PACKAGES -> return InstallOrigin.SHIZUSTORE
                pkg == "com.orion.store" -> return InstallOrigin.ORION_STORE
            }
        }

        val isDirectOrSystemInstaller = candidates.isEmpty() || candidates.all { pkg ->
            pkg in DIRECT_INSTALLER_PACKAGES || pkg.endsWith(".packageinstaller")
        }
        return if (isDirectOrSystemInstaller) {
            InstallOrigin.DIRECT_SIDELOAD
        } else {
            InstallOrigin.THIRD_PARTY_STORE
        }
    }

    private fun readSigningCertSha256List(context: Context): List<String> {
        return try {
            val pm = context.packageManager
            val certByteArrays: List<ByteArray> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
                val signingInfo = info.signingInfo
                val rawSigners = buildList {
                    signingInfo?.apkContentsSigners?.forEach { add(it.toByteArray()) }
                    signingInfo?.signingCertificateHistory?.forEach { add(it.toByteArray()) }
                }
                rawSigners
            } else {
                @Suppress("DEPRECATION")
                val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                info.signatures?.map { it.toByteArray() }.orEmpty()
            }

            val digest = MessageDigest.getInstance("SHA-256")
            certByteArrays.map { bytes ->
                digest.digest(bytes).joinToString(":") { "%02X".format(it) }
            }.distinct()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun normalizeHex(value: String): String =
        value.replace(":", "").trim()

    fun openUriWithFallback(
        context: Context,
        primaryUri: String,
        fallbackUri: String? = null,
        clipboardFallbackText: String? = null
    ) {
        if (tryLaunchUri(context, primaryUri)) return
        if (fallbackUri != null && tryLaunchUri(context, fallbackUri)) return

        copyToClipboard(context, "Lightspeed Official Link", clipboardFallbackText ?: fallbackUri ?: primaryUri)
        Toast.makeText(context, "No browser or store client found; link copied", Toast.LENGTH_SHORT).show()
    }

    private fun tryLaunchUri(context: Context, uriString: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}
