package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.ui.r

/** Every user export/editor/deletion entry rechecks persisted ownership. */
internal object ProfileActionPolicy {
    fun restricted(value: String): Boolean {
        val source = value.trim().trimStart('\uFEFF').trimStart()
        return source.isNotEmpty() && (!r.b(source) || source.first().code == 35)
    }

    fun seal(item: SubscriptionItem) {
        item.actionRestricted = item.actionRestricted || restricted(item.url) || restricted(item.remarks)
    }

    fun allows(item: SubscriptionItem): Boolean =
        !item.actionRestricted && !restricted(item.url) && !restricted(item.remarks)

    fun allowsSubscription(id: String): Boolean = when {
        id.isBlank() || id == AppConfig.DEFAULT_SUBSCRIPTION_ID -> true
        else -> MmkvManager.decodeSubscription(id)?.let(::allows) == true
    }

    fun allowsProfile(guid: String): Boolean {
        val profile = MmkvManager.decodeServerConfig(guid) ?: return false
        return !restricted(profile.remarks) && allowsSubscription(profile.subscriptionId)
    }
}
