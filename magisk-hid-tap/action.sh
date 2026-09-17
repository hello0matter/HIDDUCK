#!/system/bin/sh
MODDIR=${0%/*}
MODEL=$(getprop ro.product.model)
if [ "$MODEL" = "Pixel 6" ]; then
  if [ -c /dev/hidg0 ]; then
    echo PIXEL_HID_READY
    echo Use the execute page to paste Duck script.
    ls -l /dev/hidg0
    exit 0
  fi
fi
if [ "$(getprop persist.nethunter.hid)" = "1" ] && [ -c /dev/hidg0 ]; then
  sh "$MODDIR/hid-ctl.sh" off
else
  sh "$MODDIR/hid-ctl.sh" on
fi
