package com.scriptwidget.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var empty: TextView
    private var items: List<String> = emptyList()

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            importUris(uris)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ScriptStore.installSamples(this)

        listView = findViewById(R.id.list)
        empty = findViewById(R.id.empty)

        findViewById<Button>(R.id.btn_new).setOnClickListener { newScriptDialog() }
        findViewById<Button>(R.id.btn_import).setOnClickListener {
            importLauncher.launch(arrayOf("*/*"))
        }
        findViewById<Button>(R.id.btn_refresh).setOnClickListener {
            UpdateWorker.enqueueAll(this)
            toast("Memperbarui semua widget...")
        }

        listView.setOnItemClickListener { _, _, pos, _ -> openEditor(items[pos]) }
        listView.setOnItemLongClickListener { _, _, pos, _ ->
            showItemMenu(items[pos])
            true
        }

        listView.adapter = object : BaseAdapter() {
            override fun getCount(): Int = items.size
            override fun getItem(position: Int): Any = items[position]
            override fun getItemId(position: Int): Long = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val v = convertView ?: layoutInflater.inflate(R.layout.item_script, parent, false)
                val name = items[position]
                v.findViewById<TextView>(R.id.item_name).text = name
                val used = WidgetPrefs.idsUsing(this@MainActivity, name).size
                val sub = Lang.fromName(name).label + if (used > 0) "  •  dipakai $used widget" else ""
                v.findViewById<TextView>(R.id.item_sub).text = sub
                return v
            }
        }

        UpdateWorker.ensurePeriodic(this)
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        items = ScriptStore.list(this)
        (listView.adapter as BaseAdapter).notifyDataSetChanged()
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun openEditor(name: String) {
        startActivity(Intent(this, EditorActivity::class.java).putExtra("name", name))
    }

    private fun showItemMenu(name: String) {
        val options = arrayOf("Edit / jalankan", "Jadikan widget di beranda", "Ubah nama", "Hapus")
        AlertDialog.Builder(this)
            .setTitle(name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openEditor(name)
                    1 -> Pinner.pin(this, name)
                    2 -> renameDialog(name)
                    3 -> deleteDialog(name)
                }
            }
            .show()
    }

    private fun renameDialog(name: String) {
        val et = EditText(this)
        et.setText(name)
        et.setSingleLine()
        AlertDialog.Builder(this)
            .setTitle("Ubah nama")
            .setView(et)
            .setPositiveButton("Simpan") { _, _ ->
                val newName = ScriptStore.rename(this, name, et.text.toString())
                if (newName == null) {
                    toast("Gagal: nama sudah dipakai")
                } else {
                    // Widget yang memakai skrip ini ikut diarahkan ke nama baru.
                    for (id in WidgetPrefs.idsUsing(this, name)) {
                        val c = WidgetPrefs.load(this, id) ?: continue
                        c.script = newName
                        WidgetPrefs.save(this, id, c)
                    }
                    reload()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun deleteDialog(name: String) {
        AlertDialog.Builder(this)
            .setTitle("Hapus skrip?")
            .setMessage(name)
            .setPositiveButton("Hapus") { _, _ ->
                ScriptStore.delete(this, name)
                reload()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun newScriptDialog() {
        val langs = Lang.values()
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(48, 24, 48, 0)
        val nameEt = EditText(this)
        nameEt.hint = "Nama skrip (tanpa ekstensi)"
        nameEt.setSingleLine()
        val sp = Spinner(this)
        sp.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            langs.map { it.label }
        )
        box.addView(nameEt)
        box.addView(sp)

        AlertDialog.Builder(this)
            .setTitle("Skrip baru")
            .setView(box)
            .setPositiveButton("Buat") { _, _ ->
                val lang = langs[sp.selectedItemPosition]
                var n = nameEt.text.toString().trim()
                if (n.isEmpty()) n = "skrip"
                if (!n.lowercase().endsWith("." + lang.ext)) n += "." + lang.ext
                val finalName = ScriptStore.uniqueName(this, n)
                ScriptStore.write(this, finalName, ScriptTemplates.of(lang))
                openEditor(finalName)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun importUris(uris: List<Uri>) {
        var n = 0
        for (u in uris) {
            try {
                var name = "import.txt"
                contentResolver.query(u, null, null, null, null)?.use { c ->
                    val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (i >= 0 && c.moveToFirst()) name = c.getString(i)
                }
                val text = contentResolver.openInputStream(u)?.use { it.bufferedReader().readText() }
                    ?: continue
                ScriptStore.write(this, ScriptStore.uniqueName(this, name), text)
                n++
            } catch (e: Exception) {
                // lewati file yang gagal dibaca
            }
        }
        toast(if (n > 0) "$n file diimpor" else "Tidak ada file yang diimpor")
        reload()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(Menu.NONE, 1, Menu.NONE, "Cara pakai")
        menu.add(Menu.NONE, 2, Menu.NONE, "Pulihkan contoh skrip")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            1 -> showHelp()
            2 -> {
                val n = ScriptStore.installSamples(this, force = true)
                toast("$n contoh dipulihkan")
                reload()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showHelp() {
        val msg = "1. Buat skrip (+ Baru) atau impor file (.py, .html, .js, .sh, .md, .txt).\n\n" +
            "2. Ketuk skrip untuk mengedit & mencoba (tombol Jalankan).\n\n" +
            "3. Tekan lama skrip -> \"Jadikan widget di beranda\", lalu atur tampilan & interval.\n\n" +
            "ATURAN OUTPUT\n" +
            "- Python: hasil print() tampil di widget. Jika output diawali tag HTML (<div>...), " +
            "widget dirender sebagai HTML.\n" +
            "- HTML: CSS & JavaScript didukung.\n" +
            "- JavaScript: print(...) dan html(\"...\") tersedia. Untuk fetch/async, tulis " +
            "window.widgetReady=false di awal dan =true saat selesai.\n" +
            "- Shell: dijalankan dengan /system/bin/sh.\n\n" +
            "Python bisa import file skrip lain yang Anda impor, dan memakai \"import widgetlib\" " +
            "(baterai, HTTP, kartu HTML)."
        AlertDialog.Builder(this)
            .setTitle("Cara pakai")
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .show()
    }
}
