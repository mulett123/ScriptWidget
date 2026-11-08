package com.scriptwidget.app

/** Konverter Markdown mini: judul, daftar, **tebal**, *miring*, `kode`. */
object Markdown {

    private fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun inline(s0: String): String {
        var s = esc(s0)
        s = Regex("`([^`]+)`").replace(s) { "<code>" + it.groupValues[1] + "</code>" }
        s = Regex("\\*\\*(.+?)\\*\\*").replace(s) { "<b>" + it.groupValues[1] + "</b>" }
        s = Regex("\\*([^*]+)\\*").replace(s) { "<i>" + it.groupValues[1] + "</i>" }
        return s
    }

    fun toHtml(md: String): String {
        val sb = StringBuilder()
        var inList = false
        for (raw in md.lines()) {
            val line = raw.trimEnd()
            val isItem = line.startsWith("- ") || line.startsWith("* ")
            if (!isItem && inList) {
                sb.append("</ul>")
                inList = false
            }
            if (line.isBlank()) {
                sb.append("<div style=\"height:6px\"></div>")
            } else if (isItem) {
                if (!inList) {
                    sb.append("<ul style=\"margin:4px 0;padding-left:20px\">")
                    inList = true
                }
                sb.append("<li>").append(inline(line.substring(2))).append("</li>")
            } else if (line.startsWith("### ")) {
                sb.append("<h3 style=\"margin:6px 0\">").append(inline(line.substring(4))).append("</h3>")
            } else if (line.startsWith("## ")) {
                sb.append("<h2 style=\"margin:6px 0\">").append(inline(line.substring(3))).append("</h2>")
            } else if (line.startsWith("# ")) {
                sb.append("<h1 style=\"margin:6px 0\">").append(inline(line.substring(2))).append("</h1>")
            } else {
                sb.append("<div>").append(inline(line)).append("</div>")
            }
        }
        if (inList) sb.append("</ul>")
        return sb.toString()
    }
}
