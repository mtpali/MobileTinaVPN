package com.v2ray.ang.handler

import com.v2ray.ang.BuildConfig
import com.v2ray.ang.dto.entities.SubscriptionItem
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Reads subscription-userinfo without modifying the v2rayNG 2.2.6 updater or Core. */
object MobileTinaSubscriptionInfo {
    fun refreshAll() {
        MmkvManager.decodeSubscriptions()
            .filter { it.subscription.enabled && it.subscription.url.isNotBlank() }
            .forEach { cache -> runCatching { refreshOne(cache.guid, cache.subscription) } }
    }

    fun refresh(subscriptionId: String) {
        val item = MmkvManager.decodeSubscription(subscriptionId) ?: return
        if (!item.enabled || item.url.isBlank()) return
        runCatching { refreshOne(subscriptionId, item) }
    }

    private fun refreshOne(guid: String, item: SubscriptionItem) {
        val raw = fetchHeader(item) ?: return
        val fields = raw.split(';').mapNotNull { segment ->
            val i = segment.indexOf('=')
            if (i <= 0) return@mapNotNull null
            val key = segment.substring(0, i).trim().lowercase()
            val value = segment.substring(i + 1).trim().toLongOrNull() ?: return@mapNotNull null
            key to value
        }.toMap()

        item.trafficUploadBytes = fields["upload"]?.takeIf { it >= 0L }
        item.trafficDownloadBytes = fields["download"]?.takeIf { it >= 0L }
        item.trafficTotalBytes = fields["total"]?.takeIf { it > 0L }
        item.expireEpochSeconds = fields["expire"]?.takeIf { it > 0L }
        MmkvManager.encodeSubscription(guid, item)
    }

    private fun fetchHeader(item: SubscriptionItem): String? {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
        val request = Request.Builder()
            .url(item.url)
            .get()
            .header("User-Agent", item.userAgent?.takeIf { it.isNotBlank() } ?: "v2rayNG/${BuildConfig.VERSION_NAME}")
            .header("Connection", "close")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else response.header("subscription-userinfo")
        }
    }
}
