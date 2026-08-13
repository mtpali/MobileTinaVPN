package com.v2ray.ang.util

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import java.security.MessageDigest

/**
 * Release-only integrity checks for the hardened MobileTina distribution.
 *
 * The checks intentionally use compiled resource IDs rather than resource file names so they
 * continue to work when AAPT/R8 shorten packaged resource paths. The expected artwork digests
 * are the immutable source hashes already enforced by CI. The text digest covers MobileTina's
 * branded/user-facing strings as one ordered payload. The signing-certificate digest is injected
 * by the hardened build workflow before compilation, so a modified/re-signed APK is rejected.
 */
object MobileTinaIntegrityGuard {
    @Volatile
    private var verified = false

    fun verify(context: Context) {
        if (!BuildConfig.MOBILETINA_HARDENED_BUILD || verified) return

        synchronized(this) {
            if (verified) return

            verifyPackageName(context)
            verifyArtwork(context)
            verifyBrandText(context)
            verifySigningCertificate(context)

            verified = true
        }
    }

    private fun verifyPackageName(context: Context) {
        val expected = byteArrayOf(
            68, -122, 19, 123, 79, -105, -105, 60,
            -100, 104, -11, 99, -51, -9, 37, -45,
            -58, -86, 120, -37, -27, 75, 105, -58,
            -84, -65, -15, 23, 58, -47, -82, -7
        )
        requireDigest(sha256(context.packageName.toByteArray(Charsets.UTF_8)), expected)
    }

    private fun verifyArtwork(context: Context) {
        val resources = context.resources
        val protectedArtwork = arrayOf(
            R.drawable.mt_auto_white to byteArrayOf(
                68, -41, 63, -69, -65, 80, -80, 55, -85, 1, -124, 5, -98, 13, -119, 15,
                79, -88, 122, 19, 18, 29, 91, -101, -113, -24, 44, 89, -42, 127, 105, 3
            ),
            R.drawable.mt_auto_yellow to byteArrayOf(
                -17, -88, 69, 4, 103, -20, 126, -66, 32, -115, 113, -87, -72, 74, 85, -121,
                124, 65, 106, 81, -109, -27, 12, 83, -5, -96, -27, 14, -88, -49, -57, 108
            ),
            R.drawable.mt_auto_blue to byteArrayOf(
                20, -93, 77, 36, -123, -63, -112, -112, -14, -98, 116, 84, 122, 18, 106, 89,
                24, 0, 20, 101, -99, -64, 69, -6, 117, -108, -83, 98, 67, 78, -114, -119
            ),
            R.drawable.mt_auto_red to byteArrayOf(
                92, 70, -78, -116, 69, 54, 31, -83, 107, -109, 97, 43, 92, -76, -24, -33,
                -10, 121, 52, 55, -83, -103, 8, -28, 76, -20, 105, 83, 96, 56, 14, -71
            ),
            R.drawable.mt_manual_stop to byteArrayOf(
                -100, 120, 84, 19, -66, -60, -12, -25, 7, -26, -99, -20, -2, -86, 56, 126,
                28, 77, 98, 104, -12, 10, 26, -45, 87, -63, 53, 119, 11, 118, 58, -68
            ),
            R.drawable.mt_manual_fab to byteArrayOf(
                -92, -34, -23, 6, -80, 110, -97, 8, 51, -77, 94, -6, 119, 24, 42, -56,
                -43, 103, -93, -23, -48, -85, 51, 95, -2, 76, 112, -25, -86, 38, 16, 38
            ),
            R.drawable.mt_manual_auto to byteArrayOf(
                34, -79, -121, -120, 83, -18, 55, -109, -72, 29, 84, 93, 1, -33, -27, -72,
                -6, -40, 55, 104, -103, 19, -24, 83, 13, 85, -71, 15, 81, 10, -91, 90
            ),
            R.drawable.mt_nav to byteArrayOf(
                -13, 59, -98, 109, 4, -62, -10, -20, 52, 76, -111, 127, 58, 36, 18, 7,
                92, -113, 98, -81, -124, 27, 68, 79, -6, 122, -122, 64, -103, -29, -107, -9
            )
        )

        for ((resourceId, expected) in protectedArtwork) {
            val digest = MessageDigest.getInstance("SHA-256")
            resources.openRawResource(resourceId).use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            requireDigest(digest.digest(), expected)
        }
    }

    private fun verifyBrandText(context: Context) {
        val protectedStrings = intArrayOf(
            R.string.app_name,
            R.string.app_widget_name,
            R.string.app_tile_name,
            R.string.mobiletina_status_connected,
            R.string.mobiletina_status_disconnected,
            R.string.mobiletina_status_connecting,
            R.string.mobiletina_status_failed,
            R.string.mobiletina_testing,
            R.string.mobiletina_no_working_server,
            R.string.mobiletina_ping_unknown,
            R.string.mobiletina_ping_inactive,
            R.string.mobiletina_ping_format,
            R.string.mobiletina_enable_internet,
            R.string.mobiletina_mode_auto,
            R.string.mobiletina_mode_manual,
            R.string.mobiletina_tap_for_ping,
            R.string.mobiletina_manual_smart_connect,
            R.string.mobiletina_manual_smart_connect_with_icon,
            R.string.mobiletina_locate_selected,
            R.string.mobiletina_countdown_format,
            R.string.mobiletina_smart_countdown_format,
            R.string.mobiletina_smart_countdown_short,
            R.string.mobiletina_subscription_status,
            R.string.mobiletina_subscription_used,
            R.string.mobiletina_subscription_days_remaining,
            R.string.mobiletina_subscription_usage_compact,
            R.string.mobiletina_subscription_secret_title,
            R.string.mobiletina_copy_subscription_link,
            R.string.mobiletina_copy_all_configs,
            R.string.mobiletina_close,
            R.string.mobiletina_confirm,
            R.string.mobiletina_delete_vpn,
            R.string.mobiletina_reset_title,
            R.string.mobiletina_reset_message,
            R.string.mobiletina_reset_confirm,
            R.string.mobiletina_reset_done,
            R.string.mobiletina_store_about_title,
            R.string.mobiletina_store_page_title,
            R.string.mobiletina_store_about_subtitle,
            R.string.mobiletina_store_instagram_branch_1,
            R.string.mobiletina_store_instagram_branch_2,
            R.string.mobiletina_store_instagram_branch_3,
            R.string.mobiletina_store_developer,
            R.string.mobiletina_store_address_section,
            R.string.mobiletina_store_address_branch_1,
            R.string.mobiletina_store_address_branch_2,
            R.string.mobiletina_real_delay,
            R.string.title_file_chooser,
            R.string.notification_action_stop_v2ray,
            R.string.per_app_proxy_settings,
            R.string.title_settings
        )

        val digest = MessageDigest.getInstance("SHA-256")
        for (resourceId in protectedStrings) {
            digest.update(context.getString(resourceId).toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
        }

        val expected = byteArrayOf(
            -18, 9, 107, 2, 34, 103, 119, -63,
            -91, 13, -76, 29, 71, -48, 101, -90,
            126, -47, -52, -65, -114, -96, -97, 100,
            105, -19, -93, -116, -10, -56, 35, 70
        )
        requireDigest(digest.digest(), expected)
    }

    private fun verifySigningCertificate(context: Context) {
        val expectedHex = BuildConfig.MOBILETINA_EXPECTED_CERT_SHA256
        if (expectedHex.length != 64) fail()
        val expected = hexToBytes(expectedHex)

        @Suppress("DEPRECATION")
        val signatures: List<Signature> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
            val signingInfo = info.signingInfo ?: fail()
            val rawSignatures = if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
            rawSignatures?.filterNotNull().orEmpty()
        } else {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES
            ).signatures?.filterNotNull().orEmpty()
        }

        if (signatures.isEmpty() || signatures.none {
                MessageDigest.isEqual(sha256(it.toByteArray()), expected)
            }
        ) {
            fail()
        }
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun requireDigest(actual: ByteArray, expected: ByteArray) {
        if (!MessageDigest.isEqual(actual, expected)) fail()
    }

    private fun hexToBytes(value: String): ByteArray {
        if (value.length % 2 != 0) fail()
        return ByteArray(value.length / 2) { index ->
            val hi = Character.digit(value[index * 2], 16)
            val lo = Character.digit(value[index * 2 + 1], 16)
            if (hi < 0 || lo < 0) fail()
            ((hi shl 4) or lo).toByte()
        }
    }

    private fun fail(): Nothing = throw SecurityException("Application integrity validation failed")
}
