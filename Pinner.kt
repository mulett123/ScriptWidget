package com.scriptwidget.app

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

/** Menambahkan widget ke beranda langsung dari dalam aplikasi. */
object Pinner {
    fun pin(a: Activity, scriptName: String) {
        WidgetPrefs.setPending(a, scriptName)
        val mgr = AppWidgetManager.getInstance(a)
        if (mgr.isRequestPinAppWidgetSupported) {
            val ok = mgr.requestPinAppWidget(
                ComponentName(a, ScriptWidgetProvider::class.java), null, null
            )
            if (!ok) {
                Toast.makeText(a, "Launcher menolak permintaan. Tambahkan manual dari menu Widget.", Toast.LENGTH_LONG).show()
            }
        } else {
            AlertDialog.Builder(a)
                .setTitle("Tambah manual")
                .setMessage(
                    "Launcher Anda tidak mendukung penambahan otomatis.\n\n" +
                        "Tekan lama di layar beranda -> Widget -> ScriptWidget, lalu seret ke beranda. " +
                        "Skrip \"$scriptName\" sudah dipilih sebagai default di layar pengaturan."
                )
                .setPositiveButton("OK", null)
                .show()
        }
    }
}
