package com.v2ray.ang.core

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.MessageUtil
import java.util.concurrent.TimeUnit

object MobileTinaSessionLimiter {
    private const val UNIQUE_WORK_NAME = "mobiletina_vpn_24h_limit"

    fun schedule(context: Context) {
        val request = OneTimeWorkRequestBuilder<SessionLimitWorker>()
            .setInitialDelay(24L, TimeUnit.HOURS)
            .addTag(UNIQUE_WORK_NAME)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    class SessionLimitWorker(appContext: Context, params: WorkerParameters) :
        CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            MessageUtil.sendMsg2Service(applicationContext, AppConfig.MSG_STATE_STOP, "mobiletina_24h_limit")
            return Result.success()
        }
    }
}
