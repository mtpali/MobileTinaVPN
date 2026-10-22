package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.dto.entities.SubscriptionUsageInfo

/** Keeps upstream header metadata and the retained product's usage card in sync. */
internal object MobileTinaSubscriptionMetadata {
    /** Called only for a successful response; a failed fetch must leave saved metadata intact. */
    fun apply(item: SubscriptionItem, info: SubscriptionUsageInfo?): Boolean {
        val upload = info?.upload
        val download = info?.download
        val total = info?.total?.takeIf { it > 0L }
        val expire = info?.expire?.takeIf { it > 0L }
        val changed = item.usageInfo != info ||
                item.trafficUploadBytes != upload || item.trafficDownloadBytes != download ||
                item.trafficTotalBytes != total || item.expireEpochSeconds != expire

        item.usageInfo = info
        item.trafficUploadBytes = upload
        item.trafficDownloadBytes = download
        item.trafficTotalBytes = total
        item.expireEpochSeconds = expire
        // The full-custom JSON's _comment expiry and protected brand title are independent.
        return changed
    }
}
