package com.v2ray.ang.handler

import android.content.Context
import android.system.Os
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import java.io.File
import java.io.FileOutputStream

/**
 * Protects the bundled geo databases from incomplete first-run copies and allows a
 * one-shot self-repair when Xray explicitly reports that a bundled geo database is bad.
 *
 * User supplied/updated geo databases are intentionally preserved. They are overwritten
 * only when the core itself reports an error that names that database.
 */
object GeoAssetManager {
    private val lock = Any()

    @Volatile
    private var checkedThisProcess = false

    private val bundledGeoFiles = listOf(
        AppConfig.GEOSITE_DAT,
        AppConfig.GEOIP_DAT,
        AppConfig.GEOIP_ONLY_CN_PRIVATE_DAT,
    )

    /** Ensures missing or obviously incomplete bundled files are restored atomically. */
    fun ensure(context: Context) {
        if (checkedThisProcess) return
        synchronized(lock) {
            if (checkedThisProcess) return

            val directory = File(Utils.userAssetPath(context))
            if (!directory.exists() && !directory.mkdirs()) {
                LogUtil.w(AppConfig.TAG, "GeoAssetManager: cannot create ${directory.absolutePath}")
                return
            }

            bundledGeoFiles.forEach { name ->
                val target = File(directory, name)
                if (!target.isFile || target.length() < MIN_VALID_GEO_BYTES) {
                    copyBundledAssetAtomically(context, name, target)
                }
            }
            checkedThisProcess = true
        }
    }

    /**
     * Repairs only the geo database explicitly mentioned by a core/config error.
     * Returns true when a bundled file was replaced and a single retry is worthwhile.
     */
    fun repairFromCoreError(context: Context, error: Throwable): Boolean {
        val text = buildString {
            append(error.message.orEmpty())
            append(' ')
            append(error.cause?.message.orEmpty())
        }
        val assetName = bundledGeoFiles.firstOrNull { name ->
            text.contains(name, ignoreCase = true) ||
                text.contains(name.substringBeforeLast('.'), ignoreCase = true)
        } ?: return false

        return synchronized(lock) {
            val directory = File(Utils.userAssetPath(context))
            if (!directory.exists() && !directory.mkdirs()) return@synchronized false
            val repaired = copyBundledAssetAtomically(
                context = context,
                assetName = assetName,
                target = File(directory, assetName),
            )
            if (repaired) {
                checkedThisProcess = true
                LogUtil.w(AppConfig.TAG, "GeoAssetManager: repaired $assetName after core load failure")
            }
            repaired
        }
    }

    private fun copyBundledAssetAtomically(
        context: Context,
        assetName: String,
        target: File,
    ): Boolean {
        val temp = File(target.parentFile, ".${target.name}.${android.os.Process.myPid()}.tmp")
        return try {
            if (temp.exists()) temp.delete()
            context.assets.open(assetName).use { input ->
                FileOutputStream(temp).use { output ->
                    input.copyTo(output, DEFAULT_BUFFER_SIZE)
                    output.flush()
                    output.fd.sync()
                }
            }

            if (temp.length() < MIN_VALID_GEO_BYTES) {
                throw IllegalStateException("Bundled $assetName is unexpectedly small (${temp.length()} bytes)")
            }

            // POSIX rename on Android replaces the target atomically on the same filesystem.
            Os.rename(temp.absolutePath, target.absolutePath)
            LogUtil.i(AppConfig.TAG, "GeoAssetManager: installed ${target.absolutePath}")
            true
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "GeoAssetManager: failed to install $assetName", e)
            temp.delete()
            false
        }
    }

    private const val MIN_VALID_GEO_BYTES = 4L * 1024L
}
