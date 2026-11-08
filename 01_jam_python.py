# Contoh Python -> output HTML (otomatis dirender karena diawali tag HTML)
import datetime
import widgetlib as w

now = datetime.datetime.now()
hari = ["Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu"][now.weekday()]
baterai = w.battery()
baterai_txt = "?" if baterai is None else str(baterai)

print(f"""
<div style="font-size:44px;font-weight:700;line-height:1">{now:%H:%M}</div>
<div style="opacity:.85;margin-top:2px">{hari}, {now:%d-%m-%Y}</div>
<div style="margin-top:8px;font-size:13px;opacity:.8">Baterai: {baterai_txt}%</div>
""")
