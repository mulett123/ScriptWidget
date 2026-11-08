"""Penjalan skrip Python untuk ScriptWidget.

Dipanggil dari Kotlin: sw_runner.run(kode, nama_file, folder_skrip, timeout_detik)
Mengembalikan dict {"ok": bool, "out": str}.
"""
import ctypes
import io
import os
import sys
import threading
import traceback

_lock = threading.Lock()


def _async_raise(tid, exc_type):
    ctypes.pythonapi.PyThreadState_SetAsyncExc(
        ctypes.c_ulong(tid), ctypes.py_object(exc_type)
    )


def run(code, filename="script.py", script_dir=None, timeout=25):
    if not _lock.acquire(timeout=timeout + 5):
        return {"ok": False, "out": "Penjalan Python masih sibuk, coba lagi."}
    try:
        if script_dir:
            # Skrip bisa saling `import` (mis. import modul_saya dari modul_saya.py).
            if script_dir not in sys.path:
                sys.path.insert(0, script_dir)
            try:
                os.chdir(script_dir)
            except OSError:
                pass

        buf = io.StringIO()
        state = {"ok": True}

        def target():
            g = {"__name__": "__main__", "__file__": filename}
            try:
                exec(compile(code, filename, "exec"), g)
            except SystemExit:
                pass
            except BaseException:
                state["ok"] = False
                buf.write(traceback.format_exc())

        old_out, old_err = sys.stdout, sys.stderr
        sys.stdout = sys.stderr = buf
        try:
            t = threading.Thread(target=target, daemon=True)
            t.start()
            t.join(timeout)
            if t.is_alive():
                _async_raise(t.ident, TimeoutError)
                t.join(2)
                state["ok"] = False
                buf.write("\n[Timeout: skrip melebihi %s detik]" % timeout)
        finally:
            sys.stdout, sys.stderr = old_out, old_err
        return {"ok": state["ok"], "out": buf.getvalue()}
    finally:
        _lock.release()
