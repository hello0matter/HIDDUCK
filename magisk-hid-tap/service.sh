#!/system/bin/sh
MODDIR=${0%/*}
until [ "$(getprop sys.boot_completed)" = "1" ]; do sleep 2; done
sleep 8
[ "$(getprop persist.nethunter.hid)" = "1" ] || exit 0
sh "$MODDIR/hid-ctl.sh" on
