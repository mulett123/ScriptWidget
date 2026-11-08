# Contoh Python -> output teks biasa, mengambil data dari internet (tanpa API key).
# Ganti latitude/longitude sesuai kota Anda.
import json
import urllib.request

LAT, LON = -6.2, 106.82  # Jakarta
URL = (
    "https://api.open-meteo.com/v1/forecast"
    f"?latitude={LAT}&longitude={LON}"
    "&current=temperature_2m,relative_humidity_2m,wind_speed_10m"
)

try:
    with urllib.request.urlopen(URL, timeout=10) as r:
        d = json.load(r)["current"]
    print("Cuaca Jakarta")
    print(f"Suhu    : {d['temperature_2m']} C")
    print(f"Lembab  : {d['relative_humidity_2m']} %")
    print(f"Angin   : {d['wind_speed_10m']} km/j")
except Exception as e:
    print("Gagal mengambil data:", e)
