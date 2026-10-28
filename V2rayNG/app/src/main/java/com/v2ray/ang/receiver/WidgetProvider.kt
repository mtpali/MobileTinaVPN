package com.v2ray.ang.receiver

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.widget.RemoteViews
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.V2RayServiceManager
import com.v2ray.ang.service.SmartConnectServiceState
import com.v2ray.ang.ui.WidgetPermissionActivity

class WidgetProvider : AppWidgetProvider() {
    /**
     * This method is called every time the widget is updated.
     * It updates the widget background based on the V2Ray service running state.
     *
     * @param context The Context in which the receiver is running.
     * @param appWidgetManager The AppWidgetManager instance.
     * @param appWidgetIds The appWidgetIds for which an update is needed.
     */
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        updateWidgetBackground(context, appWidgetManager, appWidgetIds, CoreServiceManager.isRunning())
    }

    /**
     * Updates the widget background based on whether the V2Ray service is running.
     *
     * @param context The Context in which the receiver is running.
     * @param appWidgetManager The AppWidgetManager instance.
     * @param appWidgetIds The appWidgetIds for which an update is needed.
     * @param isRunning Boolean indicating if the V2Ray service is running.
     */
    private fun updateWidgetBackground(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray, isRunning: Boolean) {
        val remoteViews = RemoteViews(context.packageName, R.layout.widget_switch)
        remoteViews.setOnClickPendingIntent(R.id.layout_switch, createTogglePendingIntent(context))
        if (isRunning || SmartConnectServiceState.isPending(context)) {
            remoteViews.setInt(R.id.image_switch, "setImageResource", R.drawable.ic_stop_24dp)
            remoteViews.setInt(R.id.layout_background, "setBackgroundResource", R.drawable.ic_rounded_corner_active)
        } else {
            remoteViews.setInt(R.id.image_switch, "setImageResource", R.drawable.ic_play_24dp)
            remoteViews.setInt(R.id.layout_background, "setBackgroundResource", R.drawable.ic_rounded_corner_inactive)
        }

        for (appWidgetId in appWidgetIds) {
            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
        }
    }

    /**
     * This method is called when the BroadcastReceiver is receiving an Intent broadcast.
     * It handles widget click actions and updates the widget background based on the V2Ray service state.
     *
     * @param context The Context in which the receiver is running.
     * @param intent The Intent being received.
     */
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (AppConfig.BROADCAST_ACTION_WIDGET_CLICK == intent.action) {
            toggle(context)
        } else if (Intent.ACTION_MY_PACKAGE_REPLACED == intent.action || SmartConnectServiceState.ACTION_CHANGED == intent.action) {
            val manager = AppWidgetManager.getInstance(context)
            onUpdate(context, manager, manager.getAppWidgetIds(ComponentName(context, WidgetProvider::class.java)))
        } else if (AppConfig.BROADCAST_ACTION_ACTIVITY == intent.action) {
            AppWidgetManager.getInstance(context)?.let { manager ->
                when (intent.getIntExtra("key", 0)) {
                    AppConfig.MSG_STATE_RUNNING, AppConfig.MSG_STATE_START_SUCCESS -> {
                        updateWidgetBackground(
                            context, manager, manager.getAppWidgetIds(ComponentName(context, WidgetProvider::class.java)),
                            true
                        )
                    }

                    AppConfig.MSG_STATE_NOT_RUNNING, AppConfig.MSG_STATE_START_FAILURE, AppConfig.MSG_STATE_STOP_SUCCESS -> {
                        updateWidgetBackground(
                            context, manager, manager.getAppWidgetIds(ComponentName(context, WidgetProvider::class.java)),
                            false
                        )
                    }
                }
            }
        }
    }

    companion object {
        internal fun toggle(context: Context, control: WidgetConnectionControl = AndroidWidgetConnectionControl(context)) {
            if (control.isRunningOrPending()) {
                control.stop()
            } else if (control.needsConsent()) {
                // Only the system VPN-consent dialog may appear. This activity has no app UI.
                context.startActivity(Intent(context, WidgetPermissionActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } else {
                control.startInBackground()
            }
        }

        internal fun createTogglePendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, R.id.layout_switch, Intent(context, WidgetProvider::class.java).apply {
                action = AppConfig.BROADCAST_ACTION_WIDGET_CLICK
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}

internal interface WidgetConnectionControl {
    fun isRunningOrPending(): Boolean
    fun needsConsent(): Boolean
    fun startInBackground()
    fun stop()
}

private class AndroidWidgetConnectionControl(private val context: Context) : WidgetConnectionControl {
    override fun isRunningOrPending() = V2RayServiceManager.reconcileRunningState(context) || SmartConnectServiceState.isPending(context)
    override fun needsConsent() = SettingsManager.isVpnMode() && VpnService.prepare(context) != null
    override fun startInBackground() { LauncherManager.startSmartService(context, toggle = true) }
    override fun stop() = V2RayServiceManager.stopVService(context)
}
