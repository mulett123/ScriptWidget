// JavaScript berjalan di WebView.
// Tersedia: print(...) untuk teks, html("<b>..</b>") untuk HTML.
print("Halo dari JavaScript!");
print(new Date().toLocaleString("id-ID"));
html("<div style='font-size:28px;font-weight:700'>" +
     new Date().toLocaleTimeString("id-ID", {hour: "2-digit", minute: "2-digit"}) +
     "</div>");
