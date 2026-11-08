// Contoh JavaScript asinkron (fetch).
// widgetReady=false memberi tahu widget untuk menunggu sampai kita set true.
window.widgetReady = false;

fetch("https://api.open-meteo.com/v1/forecast?latitude=-6.2&longitude=106.82&current=temperature_2m")
  .then(function (r) { return r.json(); })
  .then(function (d) {
    html("<div style='font-size:34px;font-weight:700'>" + d.current.temperature_2m + " &deg;C</div>");
    print("Jakarta, sekarang");
  })
  .catch(function (e) { print("Error: " + e); })
  .finally(function () { window.widgetReady = true; });
