package com.scriptwidget.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Widget Android hanya bisa menampilkan RemoteViews (tidak bisa WebView langsung),
 * jadi hasil skrip dirender menjadi gambar (Bitmap) lalu ditempel ke widget.
 */
object Renderer {

    private const val MAX_PIXELS = 700_000

    /** Bungkus fragmen HTML dengan gaya dasar (dilewati jika sudah dokumen HTML utuh). */
    fun wrapHtml(content: String, fg: Int, textSp: Int): String {
        val head = content.trimStart().take(200).lowercase()
        if (head.startsWith("<!doctype") || head.startsWith("<html")) return content
        val fgHex = String.format("#%06X", fg and 0xFFFFFF)
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<style>html,body{margin:0;padding:0;height:100%;background:transparent;color:" + fgHex +
            ";font-family:sans-serif;font-size:" + textSp + "px;line-height:1.35}" +
            "body{padding:10px;box-sizing:border-box}</style></head><body>" +
            content + "</body></html>"
    }

    /** Jalankan skrip lalu gambar hasilnya. Harus dipanggil dari thread latar belakang. */
    fun render(ctx: Context, cfg: WidgetCfg, wPx: Int, hPx: Int): Bitmap {
        val w = wPx.coerceAtLeast(40)
        val h = hPx.coerceAtLeast(40)
        return try {
            val res = ScriptRunner.run(ctx, cfg.script)
            val bmp = (if (res.isHtml) renderHtml(ctx, res.content, cfg, w, h) else null)
                ?: renderText(ctx, res, cfg, w, h)
            limit(bmp)
        } catch (t: Throwable) {
            limit(renderText(ctx, RunResult(false, "Error: $t", true), cfg, w, h))
        }
    }

    private fun newBitmap(ctx: Context, cfg: WidgetCfg, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = cfg.bg
        val r = 18f * ctx.resources.displayMetrics.density
        c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, p)
        return bmp
    }

    private fun limit(b: Bitmap): Bitmap {
        val px = b.width.toLong() * b.height.toLong()
        if (px <= MAX_PIXELS) return b
        val s = Math.sqrt(MAX_PIXELS.toDouble() / px.toDouble())
        return Bitmap.createScaledBitmap(
            b,
            (b.width * s).toInt().coerceAtLeast(1),
            (b.height * s).toInt().coerceAtLeast(1),
            true
        )
    }

    private fun renderText(ctx: Context, res: RunResult, cfg: WidgetCfg, w: Int, h: Int): Bitmap {
        val dm = ctx.resources.displayMetrics
        val bmp = newBitmap(ctx, cfg, w, h)
        val c = Canvas(bmp)
        val pad = 12f * dm.density
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
        tp.color = if (res.isError) Color.parseColor("#FF6B6B") else cfg.fg
        tp.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, cfg.textSp.toFloat(), dm)

        val text: CharSequence = if (res.isHtml) {
            val cleaned = res.content
                .replace(Regex("(?is)<script.*?</script>"), "")
                .replace(Regex("(?is)<style.*?</style>"), "")
            Html.fromHtml(cleaned, Html.FROM_HTML_MODE_COMPACT)
        } else {
            res.content.ifEmpty { "(tidak ada output)" }
        }

        val width = (w - 2 * pad).toInt().coerceAtLeast(10)
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, tp, width)
            .setLineSpacing(0f, 1.1f)
            .build()
        c.save()
        c.translate(pad, pad)
        c.clipRect(0f, 0f, width.toFloat(), h - 2 * pad)
        layout.draw(c)
        c.restore()
        return bmp
    }

    private fun hasContent(b: Bitmap): Boolean {
        val w = b.width
        val row = IntArray(w)
        var y = 0
        while (y < b.height) {
            b.getPixels(row, 0, w, 0, y, w, 1)
            for (px in row) {
                if ((px ushr 24) != 0) return true
            }
            y += 2
        }
        return false
    }

    /**
     * Render HTML (CSS + JS penuh) lewat WebView tanpa layar, lalu ambil gambarnya.
     * Mengembalikan null jika WebView gagal / hasilnya kosong -> pemanggil memakai render teks.
     *
     * Skrip HTML/JS yang memuat data asinkron (fetch, dll.) boleh menulis
     * `window.widgetReady = false` di awal dan `window.widgetReady = true` setelah selesai;
     * render menunggu sampai maksimal ~10 detik.
     */
    private fun renderHtml(ctx: Context, html: String, cfg: WidgetCfg, w: Int, h: Int): Bitmap? {
        val app = ctx.applicationContext
        val handler = Handler(Looper.getMainLooper())
        val latch = CountDownLatch(1)
        var out: Bitmap? = null
        val page = wrapHtml(html, cfg.fg, cfg.textSp)

        handler.post {
            try {
                val wv = WebView(app)
                var finished = false

                fun finish() {
                    if (finished) return
                    finished = true
                    try {
                        val layer = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        wv.draw(Canvas(layer))
                        if (hasContent(layer)) {
                            val base = newBitmap(app, cfg, w, h)
                            Canvas(base).drawBitmap(layer, 0f, 0f, null)
                            out = base
                        }
                    } catch (e: Throwable) {
                        // biarkan out = null -> fallback ke render teks
                    }
                    try {
                        wv.destroy()
                    } catch (e: Throwable) {
                        // abaikan
                    }
                    latch.countDown()
                }

                fun waitReady(tries: Int) {
                    wv.evaluateJavascript("(function(){return window.widgetReady===false;})()") { v ->
                        if (v == "true" && tries > 0) {
                            handler.postDelayed({ waitReady(tries - 1) }, 300)
                        } else {
                            handler.postDelayed({ finish() }, 350)
                        }
                    }
                }

                wv.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                wv.setBackgroundColor(Color.TRANSPARENT)
                val s = wv.settings
                s.javaScriptEnabled = true
                s.domStorageEnabled = true
                s.allowFileAccess = false
                s.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                wv.measure(
                    View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
                )
                wv.layout(0, 0, w, h)

                wv.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        wv.postVisualStateCallback(0L, object : WebView.VisualStateCallback() {
                            override fun onComplete(requestId: Long) {
                                waitReady(33)
                            }
                        })
                    }
                }
                handler.postDelayed({ finish() }, 22_000)
                wv.loadDataWithBaseURL("https://widget.local/", page, "text/html", "UTF-8", null)
            } catch (t: Throwable) {
                latch.countDown()
            }
        }

        latch.await(30, TimeUnit.SECONDS)
        return out
    }
}
