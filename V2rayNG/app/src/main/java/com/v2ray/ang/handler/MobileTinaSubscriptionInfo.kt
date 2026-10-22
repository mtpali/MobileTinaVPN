package com.v2ray.ang.handler

import android.os.SystemClock
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.util.JsonUtil
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Reads subscription-userinfo and exposes the same successful response body to the
 * MobileTina subscription optimizer so unchanged subscriptions do not need a second GET.
 */
object MobileTinaSubscriptionInfo {
    data class Snapshot(
        val body: String,
        val userInfo: String?,
        val profileTitle: String? = null,
        val supportUrl: String? = null,
        val webPageUrl: String? = null,
    )

    private const val RECENT_SNAPSHOT_WINDOW_MS = 5_000L
    private val recentSnapshotAt = ConcurrentHashMap<String, Long>()

    fun refreshAll() {
        val now = SystemClock.elapsedRealtime()
        MmkvManager.decodeSubscriptions()
            .filter { it.subscription.enabled && it.subscription.url.isNotBlank() }
            .forEach { cache ->
                val capturedAt = recentSnapshotAt[cache.guid] ?: 0L
                if (capturedAt > 0L && now - capturedAt <= RECENT_SNAPSHOT_WINDOW_MS) {
                    return@forEach
                }
                runCatching { refreshOne(cache.guid, cache.subscription) }
            }
    }

    fun refresh(subscriptionId: String) {
        val item = MmkvManager.decodeSubscription(subscriptionId) ?: return
        if (!item.enabled || item.url.isBlank()) return
        runCatching { refreshOne(subscriptionId, item) }
    }

    /**
     * Fetches the subscription once, preserving the existing local-proxy -> direct fallback.
     * The response body is used for change detection and subscription-userinfo is applied
     * immediately so MainActivity.refreshAll() can skip its otherwise duplicate request.
     */
    fun captureSnapshot(guid: String, item: SubscriptionItem): Snapshot? {
        val proxyPort = SettingsManager.getHttpPort()
        val snapshot = if (proxyPort > 0) {
            fetchSnapshot(item, proxyPort) ?: fetchSnapshot(item, 0)
        } else {
            fetchSnapshot(item, 0)
        } ?: return null

        // A transient empty body must not erase metadata from the last usable response.
        if (snapshot.body.isBlank()) return snapshot
        val usageInfo = SubscriptionInfoParser.parse(
            snapshot.userInfo, snapshot.profileTitle, snapshot.supportUrl, snapshot.webPageUrl
        )
        if (MobileTinaSubscriptionMetadata.apply(item, usageInfo)) {
            MmkvManager.encodeSubscription(guid, item)
        }
        MobileTinaExpiryManager.updateDisplayExpiryFromPayload(guid, snapshot.body)
        recentSnapshotAt[guid] = SystemClock.elapsedRealtime()
        return snapshot
    }

    private fun refreshOne(guid: String, item: SubscriptionItem) {
        captureSnapshot(guid, item)
    }

    private fun fetchSnapshot(item: SubscriptionItem, httpPort: Int): Snapshot? {
        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
        if (httpPort > 0) {
            builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(AppConfig.LOOPBACK, httpPort)))
        }
        val requestBuilder = Request.Builder()
            .url(item.url)
            .get()
            .header("User-Agent", item.userAgent?.takeIf { it.isNotBlank() } ?: "v2rayNG/${BuildConfig.VERSION_NAME}")
            .header("Connection", "close")
        if (!item.requestHeaders.isNullOrBlank()) {
            val headers = JsonUtil.parseString(item.requestHeaders.orEmpty())?.takeIf { it.isJsonObject }?.asJsonObject
            headers?.entrySet()?.forEach { (name, value) ->
                if (value.isJsonPrimitive) requestBuilder.header(name, value.asString)
            }
        }
        val request = requestBuilder.build()

        return runCatching {
            builder.build().newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    null
                } else {
                    Snapshot(
                        body = response.body?.string().orEmpty(),
                        userInfo = response.header("subscription-userinfo"),
                        profileTitle = response.header("Profile-Title"),
                        supportUrl = response.header("Support-Url"),
                        webPageUrl = response.header("Profile-Web-Page-Url"),
                    )
                }
            }
        }.getOrNull()
    }
}
