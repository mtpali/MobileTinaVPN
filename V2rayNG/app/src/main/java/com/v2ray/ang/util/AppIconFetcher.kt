package com.v2ray.ang.util

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import com.v2ray.ang.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppIconFetcher(
    private val packageName: String,
    private val context: Context,
    private val pixels: Int,
) : Fetcher {
    override suspend fun fetch(): FetchResult? = withContext(iconDispatcher) {
        val drawable = try {
            if (packageName == AppConfig.UNIDENTIFIED_PACKAGE) {
                context.getDrawable(android.R.drawable.sym_def_app_icon)
            } else context.packageManager.getApplicationIcon(packageName)
        } catch (_: Exception) { null } ?: return@withContext null

        // Cache a small immutable raster, rather than retaining full launcher drawables.
        ImageFetchResult(
            image = drawable.toBitmap(pixels, pixels, Bitmap.Config.ARGB_8888).asImage(),
            isSampled = false,
            dataSource = DataSource.DISK,
        )
    }

    class Factory(context: Context, private val pixels: Int = (40 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)) : Fetcher.Factory<Any> {
        private val appContext = context.applicationContext
        override fun create(data: Any, options: Options, imageLoader: ImageLoader): Fetcher? {
            val value = data.toString()
            return if (value.startsWith("appicon:")) AppIconFetcher(value.substringAfter("appicon:"), appContext, pixels) else null
        }
    }

    private companion object {
        val iconDispatcher = Dispatchers.IO.limitedParallelism(4)
    }
}
