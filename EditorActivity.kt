package com.scriptwidget.app

import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

class EditorActivity : AppCompatActivity() {

    private lateinit var name: String
    private lateinit var edit: EditText
    private lateinit var previewBox: View
    private lateinit var webView: WebView
    private lateinit var outScroll: View
    private lateinit var outText: TextView
    private var saved = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val n = intent.getStringExtra("name")
        if (n == null) {
            finish()
            return
        }
        name = n
        setContentView(R.layout.activity_editor)
        title = name

        edit = findViewById(R.id.editor)
        previewBox = findViewById(R.id.preview_box)
        webView = findViewById(R.id.web)
        outScroll = findViewById(R.id.out_scroll)
        outText = findViewById(R.id.out_text)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.setBackgroundColor(Color.parseColor("#1E1E2E"))

        saved = ScriptStore.read(this, name) ?: ""
        edit.setText(saved)

        findViewById<Button>(R.id.btn_save).setOnClickListener {
            save(true)
        }
        findViewById<Button>(R.id.btn_run).setOnClickListener { runPreview() }
        findViewById<Button>(R.id.btn_widget).setOnClickListener {
            save(false)
            Pinner.pin(this, name)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (previewBox.visibility == View.VISIBLE) {
                    previewBox.visibility = View.GONE
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onPause() {
        super.onPause()
        if (::edit.isInitialized) save(false)
    }

    private fun save(showToast: Boolean) {
        val text = edit.text.toString()
        if (text != saved) {
            ScriptStore.write(this, name, text)
            saved = text
            // Perbarui widget yang memakai skrip ini.
            val ids = WidgetPrefs.idsUsing(this, name)
            if (ids.isNotEmpty()) UpdateWorker.enqueue(this, ids.toIntArray())
        }
        if (showToast) Toast.makeText(this, "Tersimpan", Toast.LENGTH_SHORT).show()
    }

    private fun runPreview() {
        save(false)
        previewBox.visibility = View.VISIBLE
        webView.visibility = View.GONE
        outScroll.visibility = View.VISIBLE
        outText.setTextColor(Color.parseColor("#E0E0E0"))
        outText.text = "Menjalankan..."
        val code = edit.text.toString()
        val app = applicationContext
        Thread {
            val r = ScriptRunner.run(app, name, code)
            runOnUiThread {
                if (!isFinishing && !isDestroyed) show(r)
            }
        }.start()
    }

    private fun show(r: RunResult) {
        if (r.isHtml) {
            outScroll.visibility = View.GONE
            webView.visibility = View.VISIBLE
            webView.loadDataWithBaseURL(
                "https://widget.local/",
                Renderer.wrapHtml(r.content, Color.WHITE, 14),
                "text/html",
                "UTF-8",
                null
            )
        } else {
            webView.visibility = View.GONE
            outScroll.visibility = View.VISIBLE
            outText.setTextColor(if (r.isError) Color.parseColor("#FF6B6B") else Color.parseColor("#E0E0E0"))
            outText.text = if (r.content.isEmpty()) "(tidak ada output)" else r.content
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(Menu.NONE, 1, Menu.NONE, "Perbarui widget yang memakai skrip ini")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 1) {
            save(false)
            val ids = WidgetPrefs.idsUsing(this, name)
            if (ids.isEmpty()) {
                Toast.makeText(this, "Belum ada widget yang memakai skrip ini", Toast.LENGTH_SHORT).show()
            } else {
                UpdateWorker.enqueue(this, ids.toIntArray())
                Toast.makeText(this, "Memperbarui ${ids.size} widget...", Toast.LENGTH_SHORT).show()
            }
        }
        return super.onOptionsItemSelected(item)
    }
}
