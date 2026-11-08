package com.scriptwidget.app

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class WidgetConfigActivity : AppCompatActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private val intervals = intArrayOf(0, 15, 30, 60, 180, 360, 720, 1440)
    private val intervalLabels = listOf(
        "Manual (ketuk widget untuk refresh)",
        "Tiap 15 menit",
        "Tiap 30 menit",
        "Tiap 1 jam",
        "Tiap 3 jam",
        "Tiap 6 jam",
        "Tiap 12 jam",
        "Tiap 24 jam"
    )

    // nama, latar (#AARRGGBB), teks
    private val themes = listOf(
        Triple("Kustom (isi kolom di bawah)", "", ""),
        Triple("Gelap transparan", "#CC1E1E2E", "#FFFFFFFF"),
        Triple("Hitam solid", "#FF000000", "#FFFFFFFF"),
        Triple("Putih solid", "#FFFFFFFF", "#FF000000"),
        Triple("Biru tua", "#FF0D1B2A", "#FFE0F2FF"),
        Triple("Hijau tua", "#FF0B3D2E", "#FFD8FFE9"),
        Triple("Ungu", "#FF5B4BDB", "#FFFFFFFF"),
        Triple("Tanpa latar (transparan)", "#00000000", "#FFFFFFFF")
    )

    private fun hex(c: Int): String = String.format("#%08X", c)

    private fun parse(s: String, fallback: Int): Int =
        try {
            Color.parseColor(s.trim())
        } catch (e: Exception) {
            fallback
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val scripts = ScriptStore.list(this)
        if (scripts.isEmpty()) {
            Toast.makeText(this, "Belum ada skrip. Buat dulu di aplikasi ScriptWidget.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContentView(R.layout.activity_config)
        title = "Pengaturan widget"

        val spScript = findViewById<Spinner>(R.id.sp_script)
        val spInterval = findViewById<Spinner>(R.id.sp_interval)
        val spTheme = findViewById<Spinner>(R.id.sp_theme)
        val spTap = findViewById<Spinner>(R.id.sp_tap)
        val etBg = findViewById<EditText>(R.id.et_bg)
        val etFg = findViewById<EditText>(R.id.et_fg)
        val etSize = findViewById<EditText>(R.id.et_size)

        spScript.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, scripts)
        spInterval.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, intervalLabels)
        spTheme.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, themes.map { it.first })
        spTap.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("Refresh widget", "Buka editor skrip")
        )

        // Nilai awal: pengaturan yang sudah ada, atau skrip yang dipilih dari aplikasi.
        val old = WidgetPrefs.load(this, widgetId)
        val defaults = WidgetCfg()
        val startScript = old?.script ?: WidgetPrefs.getPending(this) ?: scripts[0]
        val idx = scripts.indexOf(startScript)
        spScript.setSelection(if (idx >= 0) idx else 0)
        val intervalIdx = intervals.indexOf(old?.intervalMin ?: defaults.intervalMin)
        spInterval.setSelection(if (intervalIdx >= 0) intervalIdx else 2)
        spTap.setSelection(old?.tap ?: 0)
        etBg.setText(hex(old?.bg ?: defaults.bg))
        etFg.setText(hex(old?.fg ?: defaults.fg))
        etSize.setText((old?.textSp ?: defaults.textSp).toString())

        var themeReady = false
        spTheme.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!themeReady) {
                    themeReady = true
                    return
                }
                val t = themes[position]
                if (t.second.isNotEmpty()) {
                    etBg.setText(t.second)
                    etFg.setText(t.third)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        findViewById<Button>(R.id.btn_save).setOnClickListener {
            val size = (etSize.text.toString().trim().toIntOrNull() ?: defaults.textSp).coerceIn(8, 48)
            val cfg = WidgetCfg(
                script = scripts[spScript.selectedItemPosition],
                intervalMin = intervals[spInterval.selectedItemPosition],
                bg = parse(etBg.text.toString(), old?.bg ?: defaults.bg),
                fg = parse(etFg.text.toString(), old?.fg ?: defaults.fg),
                textSp = size,
                tap = spTap.selectedItemPosition,
                lastRun = 0L
            )
            WidgetPrefs.save(this, widgetId, cfg)
            WidgetPrefs.clearPending(this)

            val mgr = AppWidgetManager.getInstance(this)
            ScriptWidgetProvider.pushViews(this, mgr, widgetId, ScriptWidgetProvider.loadCached(this, widgetId))
            UpdateWorker.enqueue(this, intArrayOf(widgetId))
            UpdateWorker.ensurePeriodic(this)

            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
            finish()
        }
    }
}
