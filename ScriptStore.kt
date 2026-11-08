package com.scriptwidget.app

import android.content.Context
import java.io.File

enum class Lang(val label: String, val ext: String) {
    PYTHON("Python", "py"),
    HTML("HTML", "html"),
    JS("JavaScript", "js"),
    SHELL("Shell", "sh"),
    MARKDOWN("Markdown", "md"),
    TEXT("Teks", "txt");

    companion object {
        fun fromName(name: String): Lang = when (name.substringAfterLast('.', "").lowercase()) {
            "py" -> PYTHON
            "html", "htm" -> HTML
            "js" -> JS
            "sh" -> SHELL
            "md", "markdown" -> MARKDOWN
            else -> TEXT
        }
    }
}

/** Skrip disimpan sebagai file biasa di folder privat aplikasi: files/scripts/ */
object ScriptStore {

    fun dir(ctx: Context): File {
        val d = File(ctx.filesDir, "scripts")
        if (!d.exists()) d.mkdirs()
        return d
    }

    fun list(ctx: Context): List<String> =
        dir(ctx).listFiles()
            ?.filter { it.isFile }
            ?.map { it.name }
            ?.sortedBy { it.lowercase() }
            ?: emptyList()

    fun read(ctx: Context, name: String): String? {
        val f = File(dir(ctx), name)
        return if (f.isFile) f.readText() else null
    }

    fun write(ctx: Context, name: String, text: String) {
        File(dir(ctx), safeName(name)).writeText(text)
    }

    fun delete(ctx: Context, name: String): Boolean = File(dir(ctx), name).delete()

    /** Mengembalikan nama baru, atau null jika gagal / nama sudah dipakai. */
    fun rename(ctx: Context, old: String, new: String): String? {
        val target = safeName(new)
        val dst = File(dir(ctx), target)
        if (dst.exists()) return null
        return if (File(dir(ctx), old).renameTo(dst)) target else null
    }

    fun safeName(raw: String): String {
        var n = raw.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
        n = n.trimStart('.')
        if (n.isEmpty()) n = "skrip.txt"
        return n
    }

    fun uniqueName(ctx: Context, wanted: String): String {
        val base = safeName(wanted)
        if (!File(dir(ctx), base).exists()) return base
        val stem = base.substringBeforeLast('.', base)
        val ext = base.substringAfterLast('.', "")
        var i = 2
        while (true) {
            val cand = if (ext.isEmpty()) "$stem-$i" else "$stem-$i.$ext"
            if (!File(dir(ctx), cand).exists()) return cand
            i++
        }
    }

    /** Menyalin contoh skrip dari assets/samples. Mengembalikan jumlah file yang disalin. */
    fun installSamples(ctx: Context, force: Boolean = false): Int {
        val sp = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)
        if (!force && sp.getBoolean("samples_v1", false)) return 0
        var n = 0
        val names = ctx.assets.list("samples") ?: emptyArray()
        for (name in names) {
            val dst = File(dir(ctx), name)
            if (dst.exists()) continue
            ctx.assets.open("samples/$name").use { input ->
                dst.outputStream().use { output -> input.copyTo(output) }
            }
            n++
        }
        sp.edit().putBoolean("samples_v1", true).apply()
        return n
    }
}

object ScriptTemplates {
    fun of(l: Lang): String = when (l) {
        Lang.PYTHON ->
            "# Python: hasil print() tampil di widget.\n" +
                "# Jika output diawali tag HTML (<div>...), widget dirender sebagai HTML.\n" +
                "import datetime\n" +
                "print(\"Halo!\", datetime.datetime.now().strftime(\"%H:%M\"))\n"
        Lang.HTML ->
            "<div style=\"font-size:28px;font-weight:700\">Halo Widget</div>\n" +
                "<div style=\"opacity:.8\">Edit HTML ini sesuka hati.</div>\n"
        Lang.JS ->
            "// JavaScript berjalan di WebView. Pakai print(...) atau html(\"<b>...</b>\")\n" +
                "print(\"Halo dari JavaScript!\");\n" +
                "print(new Date().toLocaleString(\"id-ID\"));\n"
        Lang.SHELL ->
            "date '+%H:%M:%S'\n" +
                "uptime\n"
        Lang.MARKDOWN ->
            "# Judul\n\nTeks **tebal**, *miring*, dan `kode`.\n\n- Butir satu\n- Butir dua\n"
        Lang.TEXT ->
            "Tulis teks apa saja di sini.\n"
    }
}
