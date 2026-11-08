package com.scriptwidget.app

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import org.json.JSONObject

data class WidgetCfg(
    var script: String = "",
    var intervalMin: Int = 30,
    var bg: Int = Color.parseColor("#CC1E1E2E"),
    var fg: Int = Color.WHITE,
    var textSp: Int = 14,
    /** 0 = ketuk untuk refresh, 1 = ketuk untuk buka editor */
    var tap: Int = 0,
    var lastRun: Long = 0L
)

object WidgetPrefs {

    private fun sp(ctx: Context) = ctx.getSharedPreferences("widget_cfg", Context.MODE_PRIVATE)

    fun load(ctx: Context, id: Int): WidgetCfg? {
        val s = sp(ctx).getString("w$id", null) ?: return null
        return try {
            val j = JSONObject(s)
            WidgetCfg(
                script = j.optString("script", ""),
                intervalMin = j.optInt("interval", 30),
                bg = j.optInt("bg", Color.parseColor("#CC1E1E2E")),
                fg = j.optInt("fg", Color.WHITE),
                textSp = j.optInt("textSp", 14),
                tap = j.optInt("tap", 0),
                lastRun = j.optLong("lastRun", 0L)
            )
        } catch (e: Exception) {
            null
        }
    }

    fun save(ctx: Context, id: Int, c: WidgetCfg) {
        val j = JSONObject()
        j.put("script", c.script)
        j.put("interval", c.intervalMin)
        j.put("bg", c.bg)
        j.put("fg", c.fg)
        j.put("textSp", c.textSp)
        j.put("tap", c.tap)
        j.put("lastRun", c.lastRun)
        sp(ctx).edit().putString("w$id", j.toString()).apply()
    }

    fun remove(ctx: Context, id: Int) {
        sp(ctx).edit().remove("w$id").apply()
    }

    fun allIds(ctx: Context): IntArray =
        AppWidgetManager.getInstance(ctx)
            .getAppWidgetIds(ComponentName(ctx, ScriptWidgetProvider::class.java))

    fun idsUsing(ctx: Context, script: String): List<Int> =
        allIds(ctx).filter { load(ctx, it)?.script == script }

    // Skrip yang dipilih pengguna saat menekan "Jadikan widget di beranda".
    fun setPending(ctx: Context, name: String) {
        sp(ctx).edit().putString("pending_script", name).apply()
    }

    fun getPending(ctx: Context): String? = sp(ctx).getString("pending_script", null)

    fun clearPending(ctx: Context) {
        sp(ctx).edit().remove("pending_script").apply()
    }
}
