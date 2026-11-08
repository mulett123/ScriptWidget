# Shell dijalankan dengan /system/bin/sh (perintah dasar Android).
echo "Perangkat : $(getprop ro.product.model)"
echo "Android   : $(getprop ro.build.version.release)"
echo "Waktu     : $(date '+%H:%M:%S')"
uptime
