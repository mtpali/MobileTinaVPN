package com.v2ray.ang.util

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.ui.q
import com.v2ray.ang.ui.r
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
            -83, 73, 63, 57, -16, 104, 27, 38,
            -8, -42, 15, 40, -25, 80, -111, 86,
            9, -56, 30, 53, -13, -32, -56, -105,
            -67, 67, 39, -108, -102, -114, 26, 26
        )
        requireDigest(sha256(context.packageName.toByteArray(Charsets.UTF_8)), expected)
    }

    private fun verifyArtwork(context: Context) {
        val resources = context.resources
        val protectedArtwork = arrayOf(
            R.drawable.white to byteArrayOf(
                -115, 64, -1, -49, -115, -69, -36, 42, -82, 59, -18, -107, 118, 25, -109, 117,
                -12, -90, 65, 109, -23, -12, 4, -4, 117, -25, 12, 15, -9, 99, 42, 127
            ),
            R.drawable.yellow to byteArrayOf(
                75, -25, -74, -77, -26, -39, 91, -8, 85, 88, -100, 63, -77, -89, 6, 76,
                -25, -118, -118, 14, 55, -9, 96, -29, -99, 84, 29, -17, -4, 27, 8, -64
            ),
            R.drawable.blue to byteArrayOf(
                -28, 39, -72, -53, -90, -97, -72, 31, 39, -111, 78, -9, -3, 75, 60, 32,
                16, 8, -73, -48, -43, -27, -9, 73, 95, 109, -24, 18, 45, -91, -98, -106
            ),
            R.drawable.red to byteArrayOf(
                -55, 97, 22, -71, 87, -52, -5, -8, -33, -52, 89, 33, 125, 127, -17, -70,
                -79, -124, -96, 119, 30, 100, 74, 20, 3, 79, 71, 109, 70, 72, -70, -33
            ),
            R.drawable.stop to byteArrayOf(
                117, -69, 33, 6, -7, -107, -18, 84, -73, 30, -12, -86, 72, 3, 82, 71,
                97, -45, 17, 28, 17, -10, -31, -71, -117, -93, 68, 125, 78, 116, 54, 97
            ),
            R.drawable.fab to byteArrayOf(
                53, -51, 12, 79, -52, -56, 22, -93, -127, -45, -124, 68, -72, -34, 125, -74,
                -87, 70, -56, 116, -107, -120, 119, -63, -98, -32, -68, -68, -67, -18, -110, 70
            ),
            R.drawable.auto to byteArrayOf(
                5, 86, -105, -113, -42, -21, 122, 32, -79, -30, 61, 53, 90, -41, 12, -18,
                -75, 1, 22, 0, -38, -73, -18, 19, -54, -48, 90, 82, 4, -94, -19, 126
            ),
            R.drawable.nav to byteArrayOf(
                58, 100, -122, 81, -103, -123, -34, 93, -69, -44, -45, 110, 48, -46, 13, 66,
                -52, 15, -91, 70, 20, 118, 67, 109, 28, -56, 74, -106, -51, 38, 38, -12
            ),
            R.mipmap.ic_launcher_foreground to byteArrayOf(
                93, 28, 63, -95, 120, -59, 12, 73, 37, -83, -82, 104, -101, -8, 117, -81,
                -57, -4, 105, -60, -109, 107, 62, 89, 18, 45, 113, -102, -14, 98, -58, 100
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
        val a = intArrayOf(
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
            R.string.mobiletina_store_page_title
        )
        val b = intArrayOf(
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
        fun u(value: String) {
            digest.update(value.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
        }
        a.forEach { resourceId ->
            u(
                when (resourceId) {
                    R.string.mobiletina_subscription_days_remaining -> r.a(7)
                    R.string.mobiletina_subscription_secret_title -> r.a(4)
                    R.string.mobiletina_copy_subscription_link -> r.a(5)
                    R.string.mobiletina_close -> r.a(6)
                    else -> context.getString(resourceId)
                }
            )
        }
        intArrayOf(8, 4, 5, 6, 7).forEach { u(q.a(it)) }
        b.forEach { u(context.getString(it)) }

        val expected = byteArrayOf(
            118, -44, 127, 101, -2, 20, -25, -5,
            10, -68, 57, -118, -53, -2, 105, -41,
            -124, 60, -11, -51, 4, 28, 25, 10,
            3, -58, 119, 45, 68, -29, -55, -118
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
