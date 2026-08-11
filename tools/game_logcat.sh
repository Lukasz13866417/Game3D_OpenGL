#!/usr/bin/env bash

set -euo pipefail

readonly APP_ID="com.example.game3d_opengl"
# Keep this synchronized with the dedicated frame-timing tag in GameplayStage.
readonly DT_TAG="GameFrameTiming"
readonly GAME_ADB_BIN="${GAME_ADB_BIN:-adb}"

usage() {
  cat <<'EOF'
Usage:
  tools/game_logcat.sh [--serial SERIAL] [--dump] MODE

Modes:
  dt      Show only frame-timing ("dt=...") logs from GameFrameTiming.
          If the game is already running, logs are also restricted to its PID.
  game    Show all logs from the currently running game process.
  all     Show all logs from every process on the selected device.
  clear   Clear all logcat buffers on the selected device.
  help    Show this help.

Options:
  -s, --serial SERIAL  Select an adb device explicitly. ANDROID_SERIAL is used
                       when this option is omitted.
  -d, --dump           Print buffered matching logs and exit instead of waiting.
  -h, --help           Show this help.

Examples:
  tools/game_logcat.sh dt
  tools/game_logcat.sh --serial 192.168.0.15:37123 dt
  tools/game_logcat.sh --serial emulator-5554 game
  tools/game_logcat.sh --dump dt
  tools/game_logcat.sh clear

Set GAME_ADB_BIN to use a non-default adb executable.
EOF
}

die() {
  printf 'game_logcat: %s\n' "$*" >&2
  exit 1
}

note() {
  printf 'game_logcat: %s\n' "$*" >&2
}

require_value() {
  local option="$1"
  local value="${2:-}"
  [[ -n "$value" ]] || die "${option} requires a value"
}

mode=""
requested_serial="${ANDROID_SERIAL:-}"
dump_logs=false

while (($# > 0)); do
  case "$1" in
    -s|--serial)
      require_value "$1" "${2:-}"
      requested_serial="$2"
      shift 2
      ;;
    -d|--dump)
      dump_logs=true
      shift
      ;;
    -h|--help)
      mode="help"
      shift
      ;;
    dt|game|all|clear|help)
      [[ -z "$mode" ]] || die "only one mode may be specified"
      mode="$1"
      shift
      ;;
    --)
      shift
      (($# == 1)) || die "expected exactly one mode after --"
      [[ -z "$mode" ]] || die "only one mode may be specified"
      mode="$1"
      shift
      ;;
    -*)
      die "unknown option: $1 (try --help)"
      ;;
    *)
      die "unknown mode: $1 (try --help)"
      ;;
  esac
done

[[ -n "$mode" ]] || mode="help"
if [[ "$mode" == "help" ]]; then
  usage
  exit 0
fi

command -v "$GAME_ADB_BIN" >/dev/null 2>&1 \
  || die "adb executable not found: ${GAME_ADB_BIN}"

resolve_device() {
  local state
  if [[ -n "$requested_serial" ]]; then
    state="$("$GAME_ADB_BIN" -s "$requested_serial" get-state 2>/dev/null || true)"
    state="${state//$'\r'/}"
    [[ "$state" == "device" ]] \
      || die "device '${requested_serial}' is not connected and ready"
    printf '%s' "$requested_serial"
    return
  fi

  local adb_devices
  if ! adb_devices="$("$GAME_ADB_BIN" devices 2>&1)"; then
    die "could not list adb devices: ${adb_devices}"
  fi

  local -a devices=()
  mapfile -t devices < <(
    printf '%s\n' "$adb_devices" \
      | awk 'NR > 1 && $2 == "device" { sub(/\r$/, "", $1); print $1 }'
  )

  case "${#devices[@]}" in
    0)
      die "no connected adb device is ready"
      ;;
    1)
      printf '%s' "${devices[0]}"
      ;;
    *)
      printf 'game_logcat: multiple adb devices are ready:\n' >&2
      printf '  %s\n' "${devices[@]}" >&2
      die "select one with --serial SERIAL or ANDROID_SERIAL"
      ;;
  esac
}

device_serial="$(resolve_device)"
readonly device_serial
adb_for_device=("$GAME_ADB_BIN" -s "$device_serial")

current_game_pid() {
  local pid
  pid="$("${adb_for_device[@]}" shell pidof -s "$APP_ID" 2>/dev/null || true)"
  pid="${pid//$'\r'/}"
  pid="${pid//[[:space:]]/}"
  [[ "$pid" =~ ^[0-9]+$ ]] || return 1
  printf '%s' "$pid"
}

if [[ "$mode" == "clear" ]]; then
  [[ "$dump_logs" == false ]] || die "--dump cannot be combined with clear"
  "${adb_for_device[@]}" logcat -b all -c
  note "cleared all logcat buffers on ${device_serial}"
  exit 0
fi

logcat_args=(logcat -b all -v time)
if [[ "$dump_logs" == true ]]; then
  logcat_args+=(-d)
fi

case "$mode" in
  dt)
    if game_pid="$(current_game_pid)"; then
      logcat_args+=("--pid=${game_pid}")
      note "showing only dt logs for ${APP_ID} (PID ${game_pid}) on ${device_serial}"
    else
      note "${APP_ID} is not running; showing only ${DT_TAG} logs device-wide"
    fi
    logcat_args+=("${DT_TAG}:D" "*:S")
    ;;
  game)
    game_pid="$(current_game_pid)" \
      || die "${APP_ID} is not running on ${device_serial}"
    logcat_args+=("--pid=${game_pid}")
    note "showing all logs for ${APP_ID} (PID ${game_pid}) on ${device_serial}"
    ;;
  all)
    note "showing all device logs on ${device_serial}"
    ;;
  *)
    die "internal error: unsupported mode '${mode}'"
    ;;
esac

if [[ "$dump_logs" == false ]]; then
  note "press Ctrl+C to stop"
fi
exec "${adb_for_device[@]}" "${logcat_args[@]}"
