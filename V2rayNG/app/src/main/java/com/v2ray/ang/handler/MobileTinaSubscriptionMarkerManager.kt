package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.SubscriptionUpdateResult
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.fmt.SocksFmt
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils

/**
 * MobileTina subscription retirement marker.
 *
 * A SOCKS profile that round-trips to a URI beginning with `socks://Og@1:` is treated
 * as a server-side marker. When it exists in a real remote subscription, the remote
 * subscription and all of its other profiles are removed. Matching marker profiles are
 * preserved in a disabled local group so they remain visible in the manual list but are
 * never fetched as subscriptions again.
 */
object MobileTinaSubscriptionMarkerManager {
    private const val REMOVAL_MARKER_PREFIX = "socks://Og@1:"
    private const val FIXED_SUBSCRIPTION_NAME = "instagram : mobile.tina"
    private const val PREF_SUBSCRIPTION_EXPIRED = "MOBILETINA_SUBSCRIPTION_EXPIRED"
    private const val PREF_EXPIRED_TOAST_PENDING = "MOBILETINA_SUBSCRIPTION_EXPIRED_TOAST_PENDING"
    private var processingExistingMarkers = false

    private data class StoredMarker(
        val guid: String,
        val profile: ProfileItem
    )

    /**
     * Returns the real marker state instead of trusting the cached preference forever.
     * This makes the Auto FAB leave its expired state as soon as the last marker is
     * edited or removed.
     */
    @Synchronized
    fun isSubscriptionExpired(): Boolean = syncExpiredState()

    @Synchronized
    fun consumeExpiredToastPending(): Boolean {
        if (!syncExpiredState()) return false
        if (!MmkvManager.decodeSettingsBool(PREF_EXPIRED_TOAST_PENDING, false)) return false
        MmkvManager.encodeSettings(PREF_EXPIRED_TOAST_PENDING, false)
        return true
    }

    /**
     * Retires subscriptions whose marker is already present without performing a
     * network refresh. This is called after every config save/import and on resume.
     */
    @Synchronized
    fun processExistingMarkers(): Boolean {
        if (processingExistingMarkers) return false

        var retiredAny = false
        processingExistingMarkers = true
        try {
            MmkvManager.decodeSubscriptions().toList().forEach { cache ->
                // A blank URL identifies the local display-only group that preserves the marker.
                if (cache.subscription.url.isBlank()) return@forEach
                val markers = findRemovalMarkers(cache.guid)
                if (markers.isEmpty()) return@forEach

                retireRemoteSubscription(
                    cache = cache,
                    markers = markers,
                    result = SubscriptionUpdateResult(configCount = markers.size, successCount = 1)
                )
                retiredAny = true
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to process existing MobileTina markers", e)
        } finally {
            processingExistingMarkers = false
            syncExpiredState()
        }
        return retiredAny
    }

    @Synchronized
    fun updateAll(): SubscriptionUpdateResult {
        if (processingExistingMarkers) return SubscriptionUpdateResult()

        processingExistingMarkers = true
        return try {
            MmkvManager.decodeSubscriptions().toList()
                .fold(SubscriptionUpdateResult()) { acc, cache ->
                    acc + updateOne(cache)
                }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to process MobileTina subscription markers", e)
            SubscriptionUpdateResult()
        } finally {
            processingExistingMarkers = false
            syncExpiredState()
        }
    }

    private fun updateOne(cache: SubscriptionCache): SubscriptionUpdateResult {
        // A local group is display-only and must never be retired again or fetched.
        if (cache.subscription.url.isBlank()) {
            return AngConfigManager.updateConfigViaSub(cache)
        }

        // Handle a marker that may already be stored from a previous refresh, even if the
        // network is currently unavailable.
        findRemovalMarkers(cache.guid).takeIf { it.isNotEmpty() }?.let { markers ->
            return retireRemoteSubscription(
                cache = cache,
                markers = markers,
                result = SubscriptionUpdateResult(configCount = markers.size, successCount = 1)
            )
        }

        // A disabled remote subscription is not fetched, but its already-stored marker
        // was still handled above.
        if (!cache.subscription.enabled) {
            return AngConfigManager.updateConfigViaSub(cache)
        }

        val result = AngConfigManager.updateConfigViaSub(cache)
        if (result.successCount <= 0) return result

        val markers = findRemovalMarkers(cache.guid)
        if (markers.isEmpty()) return result

        return retireRemoteSubscription(
            cache = cache,
            markers = markers,
            result = result.copy(configCount = markers.size)
        )
    }

    private fun findRemovalMarkers(subscriptionId: String): List<StoredMarker> {
        return MmkvManager.decodeServerList(subscriptionId)
            .mapNotNull { guid ->
                MmkvManager.decodeServerConfig(guid)
                    ?.copy()
                    ?.takeIf(::isRemovalMarker)
                    ?.let { profile -> StoredMarker(guid, profile) }
            }
    }

    private fun isRemovalMarker(profile: ProfileItem): Boolean {
        if (profile.configType != EConfigType.SOCKS) return false
        val uri = runCatching {
            profile.configType.protocolScheme + SocksFmt.toUri(profile)
        }.getOrNull() ?: return false
        return uri.startsWith(REMOVAL_MARKER_PREFIX, ignoreCase = false)
    }

    private fun syncExpiredState(): Boolean {
        val expired = MmkvManager.decodeAllServerList()
            .asSequence()
            .mapNotNull(MmkvManager::decodeServerConfig)
            .any(::isRemovalMarker)

        if (MmkvManager.decodeSettingsBool(PREF_SUBSCRIPTION_EXPIRED, false) != expired) {
            MmkvManager.encodeSettings(PREF_SUBSCRIPTION_EXPIRED, expired)
        }
        if (!expired) {
            if (MmkvManager.decodeSettingsBool(PREF_EXPIRED_TOAST_PENDING, false)) {
                MmkvManager.encodeSettings(PREF_EXPIRED_TOAST_PENDING, false)
            }
        }
        return expired
    }

    @Synchronized
    fun clearExpiredState() {
        MmkvManager.encodeSettings(PREF_SUBSCRIPTION_EXPIRED, false)
        MmkvManager.encodeSettings(PREF_EXPIRED_TOAST_PENDING, false)
    }

    private fun retireRemoteSubscription(
        cache: SubscriptionCache,
        markers: List<StoredMarker>,
        result: SubscriptionUpdateResult
    ): SubscriptionUpdateResult {
        if (markers.isEmpty()) return result

        val selectedWasInRemovedSubscription = MmkvManager.getSelectServer()
            ?.let(MmkvManager::decodeServerConfig)
            ?.subscriptionId == cache.guid

        // Persist the expired state before removing the remote subscription. The Auto page
        // uses this flag to keep its FAB red while the preserved marker remains in the list.
        MmkvManager.encodeSettings(PREF_SUBSCRIPTION_EXPIRED, true)
        MmkvManager.encodeSettings(PREF_EXPIRED_TOAST_PENDING, true)

        // Remove the real subscription first. This also removes every profile that belonged
        // to it, including the stored copies of the markers. We keep in-memory copies above.
        MmkvManager.removeSubscription(cache.guid)

        val localGroupId = Utils.getUuid()
        MmkvManager.encodeSubscription(
            localGroupId,
            SubscriptionItem(
                remarks = FIXED_SUBSCRIPTION_NAME,
                enabled = false,
                autoUpdate = false
            )
        )

        val preservedKeys = markers.map { marker ->
            val localMarker = marker.profile.copy(
                subscriptionId = localGroupId,
                addedTime = System.currentTimeMillis()
            )
            // Reuse the original GUID so callers that just saved the marker never receive
            // a key that was deleted while the subscription was retired.
            MmkvManager.encodeServerConfig(marker.guid, localMarker)
        }

        if ((selectedWasInRemovedSubscription || MmkvManager.getSelectServer().isNullOrBlank()) && preservedKeys.isNotEmpty()) {
            MmkvManager.setSelectServer(preservedKeys.first())
        }

        LogUtil.i(
            AppConfig.TAG,
            "MobileTina marker retired subscription ${cache.guid}; preserved ${preservedKeys.size} marker config(s)"
        )
        return result
    }
}
