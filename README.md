# ScriptWidget

Aplikasi Android untuk membuat **widget layar beranda dari skrip**: Python, HTML (+CSS/JS), JavaScript, Shell, Markdown, atau teks. Skrip bisa dibuat/ditempel di dalam aplikasi atau **diimpor dari file**.

## Fitur

- **Python 3.11 asli** di dalam aplikasi (Chaquopy), termasuk `requests`, `urllib`, `json`, dll.
- Skrip bisa saling `import` (impor `modul.py`, lalu `import modul` dari skrip lain).
- Pustaka bantu `import widgetlib` (baterai, HTTP, kartu HTML, akses Android Context).
- Editor bawaan + tombol **Jalankan** untuk pratinjau.
- **Impor banyak file sekaligus** (`.py .html .js .sh .md .txt`).
- Tombol **Jadikan widget di beranda** langsung dari aplikasi (atau Widget -> ScriptWidget di menu widget launcher).
- Per widget: pilih skrip, interval refresh (manual, 15 menit sampai 24 jam), warna latar/teks, ukuran teks, aksi saat diketuk. Widget bisa di-resize.
- Banyak widget sekaligus, masing-masing dengan skrip berbeda.

## Cara membuat file APK

Proyek ini harus di-build dulu (butuh Android SDK + internet). Dua cara:

### Cara A - GitHub Actions (tanpa PC, gratis)

1. Buat akun & repositori baru di github.com (boleh dari HP, pilih "Desktop site" di browser).
2. Unggah **seluruh isi** folder proyek ini ke repositori (termasuk folder `.github`). Di PC cukup `git push`; di HP bisa lewat Termux (`pkg install git`, `git init`, `git add .`, `git commit`, `git push`).
3. Buka tab **Actions** -> workflow **Build APK** berjalan otomatis (sekitar 5-10 menit). Kalau tidak jalan, klik **Run workflow**.
4. Setelah hijau, ambil `ScriptWidget.apk` dari tab **Releases** (bisa diunduh langsung di HP) atau dari *Artifacts* run tersebut.
5. Buka APK di HP -> izinkan "Install dari sumber ini" -> install.

### Cara B - Android Studio

1. Buka folder proyek di Android Studio (Koala/2024.1 atau lebih baru), pastikan **Python 3** terpasang di komputer (dipakai Chaquopy saat build).
2. Tunggu Gradle sync, lalu **Build -> Build APK(s)**, atau jalankan `./gradlew assembleDebug`.
3. APK ada di `app/build/outputs/apk/debug/app-debug.apk`.

## Cara memakai

1. Buka **ScriptWidget**. Sudah ada contoh skrip (jam, cuaca, kartu HTML, JS, shell, catatan).
2. **+ Baru** untuk membuat skrip, atau **Impor file** untuk mengambil dari penyimpanan.
3. Ketuk skrip -> edit -> **Jalankan** untuk melihat hasil.
4. Tekan lama skrip -> **Jadikan widget di beranda** (atau tombol *Jadikan widget* di editor). Atur interval & warna, lalu **Simpan widget**.
5. Mengedit skrip otomatis memperbarui widget yang memakainya.

## Aturan output skrip

| Jenis | Cara kerja |
|---|---|
| **Python** (`.py`) | Hasil `print()` jadi isi widget. Jika output **diawali tag HTML** (mis. `<div>...`), widget dirender sebagai HTML. Error tampil sebagai teks merah. Batas waktu 25 detik. |
| **HTML** (`.html`) | Fragmen HTML atau dokumen utuh; CSS & JavaScript didukung. Fragmen otomatis dibungkus dengan teks berwarna sesuai pengaturan widget. |
| **JavaScript** (`.js`) | Berjalan di WebView. `print(...)` menulis teks, `html("<b>..</b>")` menulis HTML. Untuk `fetch`/async: tulis `window.widgetReady = false` di awal dan `window.widgetReady = true` saat selesai (maks. ~10 detik menunggu). |
| **Shell** (`.sh`) | Dijalankan dengan `/system/bin/sh` (perintah dasar Android: `date`, `uptime`, `getprop`, `cat /proc/...`). Batas 20 detik. |
| **Markdown** (`.md`) | Judul `#`, daftar `- `, **tebal**, *miring*, `kode`. |
| **Teks** (`.txt`) | Ditampilkan apa adanya. |

### Contoh Python

```python
import widgetlib as w
print(w.card("Baterai", f"{w.battery()}%"))          # kartu HTML
data = w.get_json("https://api.open-meteo.com/v1/forecast?latitude=-6.2&longitude=106.8&current=temperature_2m")
print("Suhu:", data["current"]["temperature_2m"])
```

Memanggil API Android dari Python:

```python
from android.content import Context
ctx = w.context()
```

## Batasan yang perlu diketahui

- Widget Android **tidak bisa menampilkan halaman web hidup**. ScriptWidget menjalankan skrip, memotret hasilnya jadi gambar, lalu menempelkannya ke widget. Jadi **tidak ada animasi/interaksi di dalam widget**; yang diperbarui adalah gambarnya.
- Refresh otomatis minimal **15 menit** (aturan Android/WorkManager) dan bisa tertunda oleh hemat baterai. Matikan optimasi baterai untuk ScriptWidget kalau widget sering telat. Ketuk widget untuk refresh manual.
- Render HTML memakai WebView tanpa layar; pada sebagian perangkat hasilnya bisa kosong. Jika itu terjadi, aplikasi otomatis jatuh ke render teks sederhana (tag dasar saja, tanpa CSS).
- Gambar widget dibatasi sekitar 700 ribu piksel (widget sangat besar akan sedikit diperkecil).
- Versi plugin (Chaquopy 16.0.0, AGP 8.7.3) bisa Anda naikkan di `build.gradle.kts` jika build meminta.

## Struktur

```
app/src/main/java/com/scriptwidget/app/
  MainActivity.kt          daftar skrip, impor, tambah widget
  EditorActivity.kt        editor + pratinjau
  WidgetConfigActivity.kt  pengaturan per widget
  ScriptWidgetProvider.kt  widget (AppWidgetProvider)
  UpdateWorker.kt          refresh terjadwal (WorkManager)
  ScriptRunner.kt          menjalankan tiap jenis skrip
  Renderer.kt              skrip -> gambar widget
app/src/main/python/       sw_runner.py, widgetlib.py
app/src/main/assets/samples/  contoh skrip
```
