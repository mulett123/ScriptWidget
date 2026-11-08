package com.scriptwidget.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import java.io.File
import java.io.FileOutputStream

class ScriptWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            pushViews(context, appWidgetManager, id, loadCached(context, id))
        }
        UpdateWorker.enqueue(context, appWidgetIds)
        UpdateWorker.ensurePeriodic(context)
    }

    override fun onEnabled(context: Context) {
        UpdateWorker.ensurePeriodic(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            WidgetPrefs.remove(context, id)
            cacheFile(context, id).delete()
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        // Widget di-resize -> render ulang sesuai ukuran baru.
        UpdateWorker.enqueue(context, intArrayOf(appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val id = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                UpdateWorker.enqueue(context, intArrayOf(id))
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.scriptwidget.app.REFRESH"
        private const val PI_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        fun cacheFile(ctx: Context, id: Int): File = File(ctx.cacheDir, "widget_$id.png")

        fun loadCached(ctx: Context, id: Int): Bitmap? {
            val f = cacheFile(ctx, id)
            return if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
        }

        private fun saveCache(ctx: Context, id: Int, bmp: Bitmap) {
            try {
                FileOutputStream(cacheFile(ctx, id)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } catch (e: Exception) {
                // cache hanya optimasi
            }
        }

        fun buildViews(ctx: Context, id: Int, bmp: Bitmap?): RemoteViews {
            val rv = RemoteViews(ctx.packageName, R.layout.widget_layout)
            val cfg = WidgetPrefs.load(ctx, id)

            if (bmp != null) {
                rv.setImageViewBitmap(R.id.widget_image, bmp)
                rv.setViewVisibility(R.id.widget_image, View.VISIBLE)
                rv.setViewVisibility(R.id.widget_placeholder, View.GONE)
            } else {
                rv.setViewVisibility(R.id.widget_image, View.GONE)
                rv.setViewVisibility(R.id.widget_placeholder, View.VISIBLE)
                rv.setTextViewText(
                    R.id.widget_placeholder,
                    if (cfg == null) "Ketuk untuk memilih skrip" else "Memuat..."
                )
            }

            val pi: PendingIntent = if (cfg == null) {
                val i = Intent(ctx, WidgetConfigActivity::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                PendingIntent.getActivity(ctx, id, i, PI_FLAGS)
            } else if (cfg.tap == 1) {
                val i = Intent(ctx, EditorActivity::class.java).putExtra("name", cfg.script)
                PendingIntent.getActivity(ctx, id, i, PI_FLAGS)
            } else {
                val i = Intent(ctx, ScriptWidgetProvider::class.java)
                    .setAction(ACTION_REFRESH)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                PendingIntent.getBroadcast(ctx, id, i, PI_FLAGS)
            }
            rv.setOnClickPendingIntent(R.id.widget_root, pi)
            return rv
        }

        fun pushViews(ctx: Context, mgr: AppWidgetManager, id: Int, bmp: Bitmap?) {
            try {
                mgr.updateAppWidget(id, buildViews(ctx, id, bmp))
            } catch (e: Exception) {
                // Biasanya gambar terlalu besar -> coba lagi dengan ukuran setengah.
                if (bmp != null) {
                    try {
                        val small = Bitmap.createScaledBitmap(
                            bmp, (bmp.width / 2).coerceAtLeast(1), (bmp.height / 2).coerceAtLeast(1), true
                        )
                        mgr.updateAppWidget(id, buildViews(ctx, id, small))
                    } catch (e2: Exception) {
                        // menyerah
                    }
                }
            }
        }

        /** Ukuran widget dalam piksel, dari opsi yang diberikan launcher. */
        fun sizePx(ctx: Context, mgr: AppWidgetManager, id: Int): Pair<Int, Int> {
            val o = mgr.getAppWidgetOptions(id)
            val dm = ctx.resources.displayMetrics
            val land = ctx.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            var wDp = if (land) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
            else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            var hDp = if (land) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
            if (wDp <= 0) wDp = 250
            if (hDp <= 0) hDp = 110
            return Pair((wDp * dm.density).toInt(), (hDp * dm.density).toInt())
        }

        /** Jalankan skrip, gambar hasilnya, dan perbarui widget. Jangan dari main thread. */
        fun renderAndUpdate(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val cfg = WidgetPrefs.load(ctx, id)
            if (cfg == null) {
                pushViews(ctx, mgr, id, null)
                return
            }
            val size = sizePx(ctx, mgr, id)
            val bmp = Renderer.render(ctx, cfg, size.first, size.second)
            saveCache(ctx, id, bmp)
            cfg.lastRun = System.currentTimeMillis()
            WidgetPrefs.save(ctx, id, cfg)
            pushViews(ctx, mgr, id, bmp)
        }
    }
}
