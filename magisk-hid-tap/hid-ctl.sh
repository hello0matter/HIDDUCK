#!/system/bin/sh
# Phone-side HID control. Usage: hid-ctl.sh on|off|status|test
MODDIR=$(dirname "$0")
[ -f "$MODDIR/hid-ctl.sh" ] || MODDIR=/data/adb/modules/hid-tap
[ -f "$MODDIR/hid-ctl.sh" ] || MODDIR=/data/local/tmp/hid-tap
CMD=${1:-status}
MODEL=$(getprop ro.product.model)
LOG=/data/local/tmp/hid-tap.log
KEY_DELAY_MS=${HID_KEY_DELAY_MS:-30}
KEY_DELAY_US=$((KEY_DELAY_MS * 1000))
log() { echo "$(date '+%F %T') $MODEL $CMD: $*" >> "$LOG"; }

kb_report() {
  printf "\x${1}\x00\x${2}\x00\x00\x00\x00\x00" > /dev/hidg0 || return 1
  usleep "$KEY_DELAY_US"
  printf '\x00\x00\x00\x00\x00\x00\x00\x00' > /dev/hidg0 || return 1
  usleep "$KEY_DELAY_US"
}

key_code() {
  n=$(printf '%s' "$1" | tr 'A-Z' 'a-z')
  case "$n" in
    a) echo 04 ;; b) echo 05 ;; c) echo 06 ;; d) echo 07 ;; e) echo 08 ;;
    f) echo 09 ;; g) echo 0a ;; h) echo 0b ;; i) echo 0c ;; j) echo 0d ;;
    k) echo 0e ;; l) echo 0f ;; m) echo 10 ;; n) echo 11 ;; o) echo 12 ;;
    p) echo 13 ;; q) echo 14 ;; r) echo 15 ;; s) echo 16 ;; t) echo 17 ;;
    u) echo 18 ;; v) echo 19 ;; w) echo 1a ;; x) echo 1b ;; y) echo 1c ;;
    z) echo 1d ;; 1) echo 1e ;; 2) echo 1f ;; 3) echo 20 ;; 4) echo 21 ;;
    5) echo 22 ;; 6) echo 23 ;; 7) echo 24 ;; 8) echo 25 ;; 9) echo 26 ;;
    0) echo 27 ;;
    enter|return) echo 28 ;;
    esc|escape) echo 29 ;;
    backspace|bckspc) echo 2a ;;
    tab) echo 2b ;;
    space|spacebar) echo 2c ;;
    minus|dash) echo 2d ;;
    equal|equals) echo 2e ;;
    lbracket) echo 2f ;;
    rbracket) echo 30 ;;
    backslash) echo 31 ;;
    semicolon) echo 33 ;;
    quote) echo 34 ;;
    grave|backquote|tilde) echo 35 ;;
    comma) echo 36 ;;
    dot|period|stop) echo 37 ;;
    slash) echo 38 ;;
    *) return 1 ;;
  esac
}

mod_code() {
  case "$1" in
    left-ctrl|ctrl|control) echo 01 ;;
    left-shift|shift) echo 02 ;;
    left-alt|alt) echo 04 ;;
    left-meta|gui|windows|meta) echo 08 ;;
    *) echo 00 ;;
  esac
}

kb() {
  set -- $1
  mod=00
  while [ $# -gt 1 ]; do
    m=$(mod_code "$1")
    mod=$(printf '%02x' $((0x$mod | 0x$m)))
    shift
  done
  k=$(key_code "$1") || return 1
  kb_report "$mod" "$k"
}

pixel_on() {
  G=/config/usb_gadget/g1
  C=$G/configs/b.1
  UDC=$(getprop sys.usb.controller)
  [ -n "$UDC" ] || UDC=11110000.dwc3
  mkdir -p "$G/functions/hid.0" "$G/functions/hid.1"
  echo 1 > "$G/functions/hid.0/protocol"
  echo 1 > "$G/functions/hid.0/subclass"
  echo 8 > "$G/functions/hid.0/report_length"
  printf '\x05\x01\x09\x06\xa1\x01\x05\x07\x19\xe0\x29\xe7\x15\x00\x25\x01\x75\x01\x95\x08\x81\x02\x95\x01\x75\x08\x81\x03\x95\x05\x75\x01\x05\x08\x19\x01\x29\x05\x91\x02\x95\x01\x75\x03\x91\x03\x95\x06\x75\x08\x15\x00\x25\x65\x05\x07\x19\x00\x29\x65\x81\x00\xc0' > "$G/functions/hid.0/report_desc"
  echo 1 > "$G/functions/hid.1/protocol"
  echo 2 > "$G/functions/hid.1/subclass"
  echo 4 > "$G/functions/hid.1/report_length"
  printf '\x05\x01\x09\x02\xa1\x01\x09\x01\xa1\x00\x05\x09\x19\x01\x29\x05\x15\x00\x25\x01\x95\x05\x75\x01\x81\x02\x95\x01\x75\x03\x81\x01\x05\x01\x09\x30\x09\x31\x09\x38\x15\x81\x25\x7f\x75\x08\x95\x03\x81\x06\xc0\xc0' > "$G/functions/hid.1/report_desc"
  echo none > "$G/UDC"
  rm -f "$C"/function0 "$C"/function1 "$C"/function2 "$C"/function3
  ln -s "$G/functions/hid.0" "$C/function0"
  ln -s "$G/functions/hid.1" "$C/function1"
  ln -s "$G/functions/ffs.adb" "$C/function2"
  setprop sys.usb.ffs.ready 1
  echo "$UDC" > "$G/UDC"
  usleep 400000
  chmod 666 /dev/hidg0 /dev/hidg1 2>/dev/null || true
}

pixel_off() {
  G=/config/usb_gadget/g1
  C=$G/configs/b.1
  UDC=$(getprop sys.usb.controller)
  [ -n "$UDC" ] || UDC=11110000.dwc3
  echo none > "$G/UDC" 2>/dev/null || true
  rm -f "$C"/function0 "$C"/function1 "$C"/function2 "$C"/function3
  ln -s "$G/functions/ffs.adb" "$C/function0" 2>/dev/null || true
  setprop sys.usb.ffs.ready 1
  echo "$UDC" > "$G/UDC" 2>/dev/null || true
  setprop sys.usb.config adb
}

samsung_on() {
  G=/config/usb_gadget/g1
  C=$G/configs/b.1
  H=$G/functions/hid.usb0
  UDC=$(getprop sys.usb.controller)
  [ -n "$UDC" ] || UDC=a600000.dwc3
  MODE=$(cat /sys/bus/platform/devices/a600000.ssusb/mode 2>/dev/null)
  if [ "$MODE" != peripheral ] && [ "$MODE" != device ]; then
    echo peripheral > /sys/bus/platform/devices/a600000.ssusb/mode 2>/dev/null || true
    usleep 300000
  fi
  setprop sys.usb.configfs 0
  [ -d "$H" ] || mkdir "$H"
  echo 1 > "$H/protocol" 2>/dev/null || true
  echo 1 > "$H/subclass" 2>/dev/null || true
  echo 8 > "$H/report_length" 2>/dev/null || true
  printf '\x05\x01\x09\x06\xa1\x01\x05\x07\x19\xe0\x29\xe7\x15\x00\x25\x01\x75\x01\x95\x08\x81\x02\x95\x01\x75\x08\x81\x03\x95\x05\x75\x01\x05\x08\x19\x01\x29\x05\x91\x02\x95\x01\x75\x03\x91\x03\x95\x06\x75\x08\x15\x00\x25\x65\x05\x07\x19\x00\x29\x65\x81\x00\xc0' > "$H/report_desc" 2>/dev/null || true
  echo none > "$G/UDC" 2>/dev/null || true
  usleep 200000
  i=1; while [ $i -le 16 ]; do rm -f "$C/f$i"; i=$((i+1)); done
  echo 0x04e8 > "$G/idVendor"
  echo 0x6860 > "$G/idProduct"
  ln -s "$G/functions/ffs.mtp" "$C/f1"
  ln -s "$G/functions/ss_acm.0" "$C/f2"
  ln -s "$G/functions/ffs.adb" "$C/f3"
  ln -s "$G/functions/ss_mon.mtp" "$C/f4"
  ln -s "$H" "$C/f5"
  echo "$UDC" > "$G/UDC"
  usleep 300000
  DEV=$(cat "$H/dev" 2>/dev/null)
  MAJ=${DEV%%:*}; MIN=${DEV##*:}
  rm -f /dev/hidg0
  [ -n "$DEV" ] && mknod /dev/hidg0 c "$MAJ" "$MIN"
  chmod 666 /dev/hidg0 2>/dev/null || true
}

samsung_off() {
  G=/config/usb_gadget/g1
  C=$G/configs/b.1
  UDC=$(getprop sys.usb.controller)
  [ -n "$UDC" ] || UDC=a600000.dwc3
  echo none > "$G/UDC" 2>/dev/null || true
  usleep 200000
  i=1; while [ $i -le 16 ]; do rm -f "$C/f$i"; i=$((i+1)); done
  echo 0x04e8 > "$G/idVendor"
  echo 0x6860 > "$G/idProduct"
  ln -s "$G/functions/ffs.mtp" "$C/f1" 2>/dev/null || true
  ln -s "$G/functions/ss_acm.0" "$C/f2" 2>/dev/null || true
  ln -s "$G/functions/ffs.adb" "$C/f3" 2>/dev/null || true
  ln -s "$G/functions/ss_mon.mtp" "$C/f4" 2>/dev/null || true
  echo "$UDC" > "$G/UDC" 2>/dev/null || true
  setprop sys.usb.configfs 1
  setprop sys.usb.config mtp,adb
  rm -f /dev/hidg0
}

do_on() {
  case "$MODEL" in
    "Pixel 6")
      if [ -c /dev/hidg0 ]; then
        echo PIXEL_HID_READY
        ls -l /dev/hidg0
        return 0
      fi
      pixel_on
      ;;
    "SM-F731N") samsung_on ;;
    *) echo "unsupported model: $MODEL"; exit 2 ;;
  esac
  setprop persist.nethunter.hid 1
  if [ -c /dev/hidg0 ]; then echo HID_ON_OK; ls -l /dev/hidg0; else echo HID_ON_FAIL; exit 1; fi
}

do_off() {
  case "$MODEL" in
    "Pixel 6") pixel_off ;;
    "SM-F731N") samsung_off ;;
    *) echo "unsupported model: $MODEL"; exit 2 ;;
  esac
  setprop persist.nethunter.hid 0
  echo HID_OFF_OK
}

do_status() {
  echo "model=$MODEL"
  echo "persist=$(getprop persist.nethunter.hid)"
  echo "usb=$(getprop sys.usb.config)"
  ls -l /dev/hidg0 /dev/hidg1 2>/dev/null || echo 'no hidg'
}

do_test() {
  if [ ! -c /dev/hidg0 ]; then
    echo "HID is off. Run on first."
    exit 3
  fi
  chmod 666 /dev/hidg0 2>/dev/null || true
  usleep 500000
  kb 'left-shift n'
  kb 'left-shift h'
  kb space
  kb 'left-shift h'
  kb 'left-shift i'
  kb 'left-shift d'
  kb space
  kb 'left-shift s'
  kb 'left-shift a'
  kb 'left-shift f'
  kb 'left-shift e'
  kb space
  kb 'left-shift t'
  kb 'left-shift e'
  kb 'left-shift s'
  kb 'left-shift t'
  kb enter
  echo TYPED_NH_HID_SAFE_TEST
}


map_char() {
  c=$1
  case "$c" in
    ' ') echo space ;;
    [a-z0-9]) echo "$c" ;;
    [A-Z]) echo "left-shift $(echo $c | tr A-Z a-z)" ;;
    '!') echo 'left-shift 1' ;;
    '@') echo 'left-shift 2' ;;
    '#') echo 'left-shift 3' ;;
    '$') echo 'left-shift 4' ;;
    '%') echo 'left-shift 5' ;;
    '^') echo 'left-shift 6' ;;
    '&') echo 'left-shift 7' ;;
    '*') echo 'left-shift 8' ;;
    '(') echo 'left-shift 9' ;;
    ')') echo 'left-shift 0' ;;
    '-') echo dash ;;
    '_') echo 'left-shift dash' ;;
    '=') echo equal ;;
    '+') echo 'left-shift equal' ;;
    '[') echo lbracket ;;
    '{') echo 'left-shift lbracket' ;;
    ']') echo rbracket ;;
    '}') echo 'left-shift rbracket' ;;
    "\") echo backslash ;;
    '|') echo 'left-shift backslash' ;;
    ';') echo semicolon ;;
    ':') echo 'left-shift semicolon' ;;
    "'") echo quote ;;
    '"') echo 'left-shift quote' ;;
    ',') echo comma ;;
    '<') echo 'left-shift comma' ;;
    '.') echo period ;;
    '>') echo 'left-shift period' ;;
    '/') echo slash ;;
    '?') echo 'left-shift slash' ;;
    '`') echo backquote ;;
    '~') echo 'left-shift backquote' ;;
    $'\t') echo tab ;;
    *) return 1 ;;
  esac
}

do_exec() {
  IN=${2:-/data/local/tmp/hid-input.duck}
  if [ ! -f "$IN" ]; then
    echo CONVERT_FAIL
    echo missing $IN
    exit 1
  fi
  if [ ! -c /dev/hidg0 ]; then
    echo RUN_FAIL
    echo missing /dev/hidg0
    exit 3
  fi
  chmod 666 /dev/hidg0 2>/dev/null || true
  echo CONVERT_OK
  usleep 300000
  # strip CR
  while IFS= read -r line || [ -n "$line" ]; do
    line=$(printf '%s' "$line" | tr -d '\r')
    [ -z "$line" ] && continue
    case "$line" in
      REM*|//*) continue ;;
      DELAY\ *)
        ms=${line#DELAY }
        usleep $((ms * 1000)) 2>/dev/null || sleep 1
        ;;
      DEFAULT_DELAY*|DEFAULTDELAY*)
        ms=${line#DEFAULT_DELAY }
        [ "$ms" = "$line" ] && ms=${line#DEFAULTDELAY }
        KEY_DELAY_MS=$ms
        KEY_DELAY_US=$((KEY_DELAY_MS * 1000))
        ;;
      STRING\ *|TEXT\ *)
        s=${line#STRING }
        [ "$s" = "$line" ] && s=${line#TEXT }
        i=0
        len=${#s}
        while [ $i -lt $len ]; do
          # mksh $(dd) drops trailing spaces; keep a sentinel.
          raw=$( { printf '%s' "$s" | dd bs=1 skip=$i count=1 2>/dev/null; printf .; } )
          ch=${raw%.}
          i=$((i+1))
          [ -n "$ch" ] || continue
          if [ "$ch" = " " ]; then
            key=space
          else
            key=$(map_char "$ch") || continue
          fi
          kb "$key" || { echo RUN_FAIL; echo kb_fail; exit 4; }
        done
        ;;
      ENTER|RETURN) kb enter ;;
      TAB) kb tab ;;
      ESC|ESCAPE) kb escape ;;
      SPACE) kb space ;;
      GUI\ *|WINDOWS\ *|META\ *)
        rest=${line#* }
        kb "left-meta $rest"
        ;;
      CTRL\ *|CONTROL\ *)
        rest=${line#* }
        kb "left-ctrl $rest"
        ;;
      ALT\ *)
        rest=${line#ALT }
        kb "left-alt $rest"
        ;;
      SHIFT\ *)
        rest=${line#SHIFT }
        kb "left-shift $rest"
        ;;
      *)
        echo CONVERT_FAIL
        echo "unsupported: $line"
        exit 1
        ;;
    esac
  done < "$IN"
  echo RUN_OK
}

case "$CMD" in
  on) do_on ;;
  off) do_off ;;
  test) do_test ;;
  exec) do_exec "$@" ;;
  status|*) do_status ;;
esac
