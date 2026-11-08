package com.scriptwidget.app

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Memperbarui widget di latar belakang.
 *  - OneTime dengan KEY_IDS  : render widget tertentu sekarang
 *  - OneTime tanpa KEY_IDS   : render semua widget sekarang
 *  - Periodic (tiap 15 menit): render widget yang intervalnya sudah jatuh tempo
 */
class UpdateWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        val app = applicationContext
        val mgr = AppWidgetManager.getInstance(app)
        val all = mgr.getAppWidgetIds(ComponentName(app, ScriptWidgetProvider::class.java))
        val given = inputData.getIntArray(KEY_IDS)
        val periodic = inputData.getBoolean(KEY_PERIODIC, false)
        val targets: List<Int> =
            if (given != null && given.isNotEmpty()) given.toList() else all.toList()
        val now = System.currentTimeMillis()

        for (id in targets) {
            if (!all.contains(id)) continue
            val cfg = WidgetPrefs.load(app, id) ?: continue
            if (periodic) {
                if (cfg.intervalMin <= 0) continue
                if (now - cfg.lastRun < cfg.intervalMin * 60_000L - 90_000L) continue
            }
            try {
                ScriptWidgetProvider.renderAndUpdate(app, mgr, id)
            } catch (e: Throwable) {
                // satu widget gagal tidak boleh menghentikan yang lain
            }
        }
        return Result.success()
    }

    companion object {
        const val KEY_IDS = "ids"
        const val KEY_PERIODIC = "periodic"

        fun enqueue(ctx: Context, ids: IntArray) {
            val data = Data.Builder().putIntArray(KEY_IDS, ids).build()
            val req = OneTimeWorkRequestBuilder<UpdateWorker>().setInputData(data).build()
            WorkManager.getInstance(ctx.applicationContext)
                .enqueueUniqueWork("sw_update", ExistingWorkPolicy.APPEND_OR_REPLACE, req)
        }

        fun enqueueAll(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<UpdateWorker>().build()
            WorkManager.getInstance(ctx.applicationContext)
                .enqueueUniqueWork("sw_update", ExistingWorkPolicy.APPEND_OR_REPLACE, req)
        }

        fun ensurePeriodic(ctx: Context) {
            val data = Data.Builder().putBoolean(KEY_PERIODIC, true).build()
            val req = PeriodicWorkRequestBuilder<UpdateWorker>(15, TimeUnit.MINUTES)
                .setInputData(data)
                .build()
            WorkManager.getInstance(ctx.applicationContext)
                .enqueueUniquePeriodicWork("sw_periodic", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
