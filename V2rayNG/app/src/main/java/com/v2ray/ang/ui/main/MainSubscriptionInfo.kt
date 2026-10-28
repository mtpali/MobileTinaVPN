package com.v2ray.ang.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionUsageInfo
import com.v2ray.ang.extension.toTrafficString
import com.v2ray.ang.handler.SubscriptionInfoParser
import com.v2ray.ang.util.Utils

private const val MILLIS_PER_SECOND = 1000L
private const val EXPIRY_DATE_PATTERN = "yyyy-MM-dd"
private const val CARD_ALPHA = 0.97f
private const val MILLIS_PER_DAY = 86_400_000L

internal enum class SubscriptionValidityLabel { EXPIRED, TRAFFIC_EXHAUSTED, REMAINING, HIDDEN }

/** Locale-neutral values the subscription information box renders. */
internal data class SubscriptionInfoUiModel(
    val title: String?,
    val usedBytes: Long,
    /** Traffic quota in bytes; 0 means unlimited. */
    val totalBytes: Long,
    /** Used share of the quota in 0..1; null when the quota is unlimited. */
    val trafficFraction: Float?,
    val trafficExhausted: Boolean,
    /** Expiry in epoch milliseconds; null when the subscription never expires. */
    val expireMillis: Long?,
    val expired: Boolean,
    val remainingDays: Long?,
    /** One UI wake-up at the next displayed-day boundary, or none after expiry. */
    val nextRefreshDelayMillis: Long?,
    val supportUrl: String?,
    val webPageUrl: String?,
) {
    val validityLabel: SubscriptionValidityLabel get() = when {
        expired -> SubscriptionValidityLabel.EXPIRED
        trafficExhausted -> SubscriptionValidityLabel.TRAFFIC_EXHAUSTED
        expireMillis != null -> SubscriptionValidityLabel.REMAINING
        else -> SubscriptionValidityLabel.HIDDEN
    }

    val trafficLow: Boolean get() = trafficFraction != null && trafficFraction >= TRAFFIC_WARNING_FRACTION

    companion object {
        const val TRAFFIC_WARNING_FRACTION = 0.9f
    }
}

internal fun buildSubscriptionInfoUiModel(
    info: SubscriptionUsageInfo,
    nowMillis: Long,
): SubscriptionInfoUiModel {
    val unlimited = info.total <= 0L
    val expireMillis = info.expire.takeIf { it > 0L }
        ?.coerceAtMost(Long.MAX_VALUE / MILLIS_PER_SECOND)?.let { it * MILLIS_PER_SECOND }
    val remainingMillis = expireMillis?.let { (it - nowMillis).coerceAtLeast(0L) }
    return SubscriptionInfoUiModel(
        title = info.title,
        usedBytes = info.used,
        totalBytes = if (unlimited) 0L else info.total,
        trafficFraction = if (unlimited) null else (info.used.toDouble() / info.total).toFloat().coerceIn(0f, 1f),
        trafficExhausted = info.trafficExhausted,
        expireMillis = expireMillis,
        expired = remainingMillis == 0L,
        remainingDays = remainingMillis?.let { if (it == 0L) 0L else 1L + (it - 1L) / MILLIS_PER_DAY },
        nextRefreshDelayMillis = remainingMillis?.takeIf { it > 0L }?.let {
            if (info.trafficExhausted) it else 1L + (it - 1L) % MILLIS_PER_DAY
        },
        supportUrl = info.supportUrl,
        webPageUrl = info.webPageUrl,
    )
}

/**
 * Fixed box on top of a subscription group showing the account information published by the
 * subscription panel: traffic, expiry date and shortcuts (support link, panel page, update).
 */
@Composable
internal fun SubscriptionInfoCard(
    info: SubscriptionUsageInfo,
    fallbackTitle: String,
    onOpenLink: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val model by produceState(
        initialValue = buildSubscriptionInfoUiModel(info, System.currentTimeMillis()),
        key1 = info,
        key2 = lifecycle,
    ) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val current = buildSubscriptionInfoUiModel(info, System.currentTimeMillis())
                value = current
                val next = current.nextRefreshDelayMillis ?: break
                delay(next)
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        // Almost opaque: the list scrolls underneath without showing through readably.
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = CARD_ALPHA),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 2.dp, bottom = 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = model.title ?: fallbackTitle,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                model.supportUrl?.let { url ->
                    CardIconButton(
                        iconRes = if (SubscriptionInfoParser.isTelegramLink(url)) {
                            R.drawable.ic_telegram_24dp
                        } else {
                            R.drawable.ic_feedback_24dp
                        },
                        contentDescription = stringResource(R.string.sub_info_open_support),
                        onClick = { onOpenLink(url) },
                    )
                }
                model.webPageUrl?.let { url ->
                    CardIconButton(
                        iconRes = R.drawable.ic_about_24dp,
                        contentDescription = stringResource(R.string.sub_info_open_page),
                        onClick = { onOpenLink(url) },
                    )
                }
                CardIconButton(
                    iconRes = R.drawable.ic_refresh_24dp,
                    contentDescription = stringResource(R.string.title_sub_update),
                    onClick = onRefresh,
                )
            }

            // One semantics node for the read-only figures so TalkBack reads them in one go.
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp)
                    .semantics(mergeDescendants = true) {}
            ) {
                StatsRow(model)
                model.trafficFraction?.let { fraction ->
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .clearAndSetSemantics {},
                        color = if (model.trafficLow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                }
            }
        }
    }
}

@Composable
private fun CardIconButton(iconRes: Int, contentDescription: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        Icon(painterResource(iconRes), contentDescription, Modifier.size(20.dp))
    }
}

@Composable
private fun StatsRow(model: SubscriptionInfoUiModel) {
    val totalText = if (model.totalBytes > 0L) {
        model.totalBytes.toTrafficString()
    } else {
        stringResource(R.string.sub_info_unlimited)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = stringResource(R.string.sub_info_traffic, model.usedBytes.toTrafficString(), totalText),
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.bodySmall,
            color = if (model.trafficLow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (model.trafficExhausted || model.expireMillis != null) {
            Spacer(Modifier.size(8.dp))
            Text(
                text = when (model.validityLabel) {
                    SubscriptionValidityLabel.EXPIRED -> stringResource(R.string.mobiletina_subscription_expired)
                    SubscriptionValidityLabel.TRAFFIC_EXHAUSTED -> stringResource(R.string.mobiletina_subscription_traffic_exhausted)
                    else -> Utils.formatTimestamp(model.expireMillis!!, EXPIRY_DATE_PATTERN)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (model.trafficExhausted || model.expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
