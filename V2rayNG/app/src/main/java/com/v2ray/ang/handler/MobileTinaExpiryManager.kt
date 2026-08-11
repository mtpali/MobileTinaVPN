package com.v2ray.ang.handler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.gson.JsonParser
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.SubscriptionItem
import com.v2ray.ang.receiver.MobileTinaExpiryReceiver
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

object MobileTinaExpiryManager {
    const val ACTION_EXPIRE = "com.v2ray.mobiletina.action.CONFIG_EXPIRE"
    const val ACTION_DATA_CHANGED = "com.v2ray.mobiletina.action.DATA_CHANGED"

    private const val PREFS_NAME = "mobiletina_config_expiry"
    private const val KEY_TRIGGER = "trigger_at_millis"
    private const val UNIQUE_WORK = "mobiletina_config_expiry_fallback"
    private const val REQUEST_CODE = 96323
    private const val EXPIRED_SUBSCRIPTION_ID = "mobiletina_expired_subscription"
    private const val EXPIRED_REMARKS = "اشتراک منقضی شد"
    private const val EXPIRED_CONFIG = "socks://Og@1:1#%D8%A7%D8%B4%D8%AA%D8%B1%D8%A7%DA%A9%20%D9%85%D9%86%D9%82%D8%B6%DB%8C%20%D8%B4%D8%AF"

    fun scheduleFromImportedText(context: Context, configText: String?) {
        val trigger = extractTriggerAtMillis(configText) ?: return
        schedule(context.applicationContext, trigger)
    }

    fun recoverPending(context: Context) {
        val app = context.applicationContext
        val trigger = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getLong(KEY_TRIGGER, 0L)
        if (trigger <= 0L) return
        if (System.currentTimeMillis() >= trigger) executeIfDue(app) else scheduleInternal(app, trigger, false)
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().remove(KEY_TRIGGER).apply()
        cancelScheduled(app)
    }

    @Synchronized
    fun executeIfDue(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val trigger = prefs.getLong(KEY_TRIGGER, 0L)
        if (trigger <= 0L) return
        if (System.currentTimeMillis() < trigger) {
            scheduleInternal(app, trigger, false)
            return
        }

        prefs.edit().remove(KEY_TRIGGER).commit()
        cancelScheduled(app)
        MobileTinaResetManager.reset(app)
        MmkvManager.encodeSubscription(
            EXPIRED_SUBSCRIPTION_ID,
            SubscriptionItem(remarks = EXPIRED_REMARKS, url = "", enabled = false)
        )
        AngConfigManager.importBatchConfig(EXPIRED_CONFIG, EXPIRED_SUBSCRIPTION_ID, true)
        MmkvManager.encodeSettings(AppConfig.CACHE_SUBSCRIPTION_ID, EXPIRED_SUBSCRIPTION_ID)
        app.sendBroadcast(Intent(ACTION_DATA_CHANGED).setPackage(app.packageName))
    }

    private fun schedule(context: Context, trigger: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putLong(KEY_TRIGGER, trigger).apply()
        scheduleInternal(context, trigger, false)
    }

    private fun scheduleInternal(context: Context, trigger: Long, persist: Boolean) {
        if (persist) context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putLong(KEY_TRIGGER, trigger).apply()
        cancelScheduled(context)
        val delay = (trigger - System.currentTimeMillis()).coerceAtLeast(0L)
        if (delay == 0L) {
            executeIfDue(context)
            return
        }

        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = expiryPendingIntent(context)
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarm.canScheduleExactAlarms()) {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            } else {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            }
        } catch (_: SecurityException) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        }

        val fallback = OneTimeWorkRequestBuilder<ExpiryFallbackWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.REPLACE, fallback)
    }

    private fun cancelScheduled(context: Context) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(expiryPendingIntent(context))
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
    }

    private fun expiryPendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, MobileTinaExpiryReceiver::class.java).setAction(ACTION_EXPIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun extractTriggerAtMillis(configText: String?): Long? {
        val text = configText?.trim().orEmpty()
        if (!text.startsWith('{')) return null
        val raw = try {
            val root = JsonParser.parseString(text)
            if (!root.isJsonObject) return null
            val comment = root.asJsonObject.get("_comment") ?: return null
            if (!comment.isJsonPrimitive || !comment.asJsonPrimitive.isString) return null
            comment.asString.trim()
        } catch (_: Exception) {
            return null
        }
        return parseTimestamp(raw)
    }

    private fun parseTimestamp(value: String): Long? {
        runCatching { return Instant.parse(value).toEpochMilli() }
        runCatching { return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli() }
        runCatching { return ZonedDateTime.parse(value, DateTimeFormatter.ISO_ZONED_DATE_TIME).toInstant().toEpochMilli() }
        return runCatching {
            LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull()
    }

    class ExpiryFallbackWorker(appContext: Context, params: WorkerParameters) :
        CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            executeIfDue(applicationContext)
            return Result.success()
        }
    }
}
