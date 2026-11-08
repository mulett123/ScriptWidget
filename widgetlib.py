"""Pustaka bantu untuk skrip widget.  Pakai:  import widgetlib as w"""
import html as _html
import json
import urllib.request


def context():
    """Android Context aplikasi (untuk memanggil API Android lewat Chaquopy)."""
    try:
        from com.chaquo.python import Python
        return Python.getPlatform().getApplication()
    except Exception:
        return None


def battery():
    """Persentase baterai (0-100), atau None jika gagal."""
    try:
        from android.content import Intent, IntentFilter
        ctx = context()
        i = ctx.registerReceiver(None, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        level = i.getIntExtra("level", -1)
        scale = i.getIntExtra("scale", 100)
        return int(level * 100 / scale)
    except Exception:
        return None


def get(url, timeout=10, headers=None):
    """GET sederhana, mengembalikan teks."""
    req = urllib.request.Request(
        url, headers=headers or {"User-Agent": "ScriptWidget/1.0"}
    )
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read().decode("utf-8", "replace")


def get_json(url, timeout=10, headers=None):
    return json.loads(get(url, timeout, headers))


def esc(text):
    """Escape teks supaya aman ditaruh di HTML."""
    return _html.escape(str(text))


def card(title, body, color="#5B4BDB"):
    """Kartu HTML siap pakai: print(w.card('Judul', 'Isi'))"""
    return (
        '<div style="background:%s;border-radius:14px;padding:12px;color:#fff">'
        '<div style="font-size:12px;opacity:.8">%s</div>'
        '<div style="font-size:22px;font-weight:700;margin-top:2px">%s</div>'
        "</div>" % (color, esc(title), esc(body))
    )
