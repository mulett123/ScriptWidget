package com.scriptwidget.app

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.util.concurrent.TimeUnit

/**
 * Hasil menjalankan skrip.
 * isHtml = true  -> content adalah HTML (dirender lewat WebView)
 * isHtml = false -> content adalah teks biasa
 */
class RunResult(val isHtml: Boolean, val content: String, val isError: Boolean = false)

object ScriptRunner {

    /** code = null berarti baca dari file. Jangan panggil dari main thread. */
    fun run(ctx: Context, name: String, code: String? = null): RunResult {
        val src = code ?: ScriptStore.read(ctx, name)
            ?: return RunResult(false, "Skrip '$name' tidak ditemukan.", true)
        return try {
            when (Lang.fromName(name)) {
                Lang.PYTHON -> runPython(ctx, name, src)
                Lang.HTML -> RunResult(true, src)
                Lang.JS -> RunResult(true, jsToHtml(src))
                Lang.SHELL -> runShell(ctx, src)
                Lang.MARKDOWN -> RunResult(true, Markdown.toHtml(src))
                Lang.TEXT -> RunResult(false, src)
            }
        } catch (t: Throwable) {
            RunResult(false, "Error: " + t.javaClass.simpleName + ": " + t.message, true)
        }
    }

    private fun runPython(ctx: Context, name: String, src: String): RunResult {
        synchronized(this) {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(ctx.applicationContext))
            }
        }
        val mod = Python.getInstance().getModule("sw_runner")
        val r = mod.callAttr("run", src, name, ScriptStore.dir(ctx).absolutePath, 25)
        val ok = r.callAttr("get", "ok").toBoolean()
        val out = r.callAttr("get", "out").toString()
        val trimmed = out.trim()
        val html = ok && trimmed.startsWith("<") && trimmed.endsWith(">")
        return RunResult(html, if (html) trimmed else out.trimEnd(), !ok)
    }

    private fun jsToHtml(js: String): String =
        "<div id=\"out\" style=\"white-space:pre-wrap;word-break:break-word\"></div>" +
            "<script>var __o=document.getElementById('out');" +
            "function print(){var s=Array.prototype.slice.call(arguments).join(' ');" +
            "__o.appendChild(document.createTextNode(s+'\\n'));}" +
            "function html(h){__o.insertAdjacentHTML('beforeend',h);}" +
            "window.onerror=function(m){print('Error: '+m);};</script>" +
            "<script>\n" + js + "\n</script>"

    private fun runShell(ctx: Context, src: String): RunResult {
        val p = ProcessBuilder("/system/bin/sh", "-c", src)
            .redirectErrorStream(true)
            .directory(ctx.filesDir)
            .start()
        val sb = StringBuffer()
        val reader = Thread {
            try {
                p.inputStream.bufferedReader().forEachLine { sb.append(it).append('\n') }
            } catch (e: Exception) {
                // proses dihentikan
            }
        }
        reader.start()
        var timedOut = false
        if (!p.waitFor(20, TimeUnit.SECONDS)) {
            p.destroyForcibly()
            timedOut = true
        }
        reader.join(1500)
        var out = sb.toString().trimEnd()
        if (timedOut) out += "\n[timeout 20 detik]"
        return RunResult(false, out, false)
    }
}
