#!/usr/bin/env bash

set -u

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEVICE_IP="192.168.0.15"
APP_ID="com.example.game3d_opengl"
ACTIVITY="${APP_ID}/.OpenGLES20Activity"
APK_PATH="${PROJECT_ROOT}/app/build/outputs/apk/debug/app-debug.apk"
LOG_MODE="all"

usage() {
  cat <<'EOF'
Usage: ./launch_on_phone.sh [--logs all|dt|errors|none]

Log views:
  all     All output from the game process (default).
  dt      Only the periodic GameFrameTiming lines beginning with "dt=".
  errors  Only error-priority output from the game process.
  none    Build, install, and launch without streaming logcat.

Filtering changes only this terminal view; hidden messages remain in logcat.
EOF
}

info() {
  printf '[INFO] %s\n' "$1"
}

warn() {
  printf '[WARN] %s\n' "$1"
}

error() {
  printf '[ERROR] %s\n' "$1" >&2
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    error "Required command not found: $1"
    exit 1
  fi
}

restore_generated_build_ownership() {
  if (( EUID != 0 )); then
    return
  fi
  if [[ ! "${SUDO_UID:-}" =~ ^[0-9]+$ || ! "${SUDO_GID:-}" =~ ^[0-9]+$ ]]; then
    return
  fi

  # Gradle uses root's debug keystore when this launcher is invoked through sudo, which keeps
  # updates compatible with an already-installed root-signed APK. Return only generated build
  # output to the invoking user so their next ordinary Gradle command can replace its caches.
  local generated_dir
  for generated_dir in \
      "${PROJECT_ROOT}/app/build" \
      "${PROJECT_ROOT}/game-core/build" \
      "${PROJECT_ROOT}/build"; do
    if [[ -e "$generated_dir" ]]; then
      chown -R "${SUDO_UID}:${SUDO_GID}" -- "$generated_dir"
    fi
  done
}

parse_args() {
  while (( $# > 0 )); do
    case "$1" in
      --logs)
        if (( $# < 2 )); then
          error "--logs requires one of: all, dt, errors, none"
          return 2
        fi
        LOG_MODE="$2"
        shift 2
        ;;
      -h|--help)
        usage
        exit 0
        ;;
      *)
        error "Unknown argument: $1"
        usage >&2
        return 2
        ;;
    esac
  done

  case "$LOG_MODE" in
    all|dt|errors|none)
      ;;
    *)
      error "Invalid --logs mode: ${LOG_MODE}"
      error "Expected one of: all, dt, errors, none"
      return 2
      ;;
  esac
}

prompt_nonempty() {
  local prompt="$1"
  local value=""
  while [[ -z "$value" ]]; do
    read -r -p "$prompt" value
  done
  printf '%s' "$value"
}

get_connected_devices() {
  adb devices | awk 'NR>1 && $2=="device" {print $1}'
}

choose_device() {
  mapfile -t devices < <(get_connected_devices)

  if (( ${#devices[@]} == 0 )); then
    return 1
  fi

  if (( ${#devices[@]} == 1 )); then
    printf '%s' "${devices[0]}"
    return 0
  fi

  info "Multiple connected devices found:"
  local i=1
  for serial in "${devices[@]}"; do
    printf '  %d) %s\n' "$i" "$serial"
    ((i++))
  done

  local idx=""
  while true; do
    idx="$(prompt_nonempty 'Select device number: ')"
    if [[ "$idx" =~ ^[0-9]+$ ]] && (( idx >= 1 && idx <= ${#devices[@]} )); then
      printf '%s' "${devices[idx-1]}"
      return 0
    fi
    warn "Invalid selection."
  done
}

try_connect_wireless() {
  local device_port="$1"
  local out
  out="$(adb connect "${DEVICE_IP}:${device_port}" 2>&1 || true)"
  printf '%s\n' "$out"

  if grep -qiE 'connected to|already connected to' <<<"$out"; then
    return 0
  fi
  return 1
}

pair_wireless() {
  local pair_port="$1"
  local pair_code="$2"
  local out
  out="$(printf '%s\n' "$pair_code" | adb pair "${DEVICE_IP}:${pair_port}" 2>&1 || true)"
  printf '%s\n' "$out"

  if grep -qiE 'successfully paired|already paired' <<<"$out"; then
    return 0
  fi
  return 1
}

ensure_device_connected() {
  if [[ -n "$(get_connected_devices)" ]]; then
    return 0
  fi

  info "No connected device found."
  info "Trying wireless connect to ${DEVICE_IP}."

  local connect_port
  connect_port="$(prompt_nonempty 'Enter wireless debugging CONNECT port: ')"
  if try_connect_wireless "$connect_port"; then
    return 0
  fi

  warn "Connect failed. Device may not be paired yet."
  local pair_port
  local pair_code
  pair_port="$(prompt_nonempty 'Enter wireless PAIR port: ')"
  pair_code="$(prompt_nonempty 'Enter pairing code shown on phone: ')"

  if ! pair_wireless "$pair_port" "$pair_code"; then
    error "Pairing failed."
    return 1
  fi

  connect_port="$(prompt_nonempty 'Enter wireless CONNECT port again: ')"
  if ! try_connect_wireless "$connect_port"; then
    error "Connect failed after pairing."
    return 1
  fi
  return 0
}

build_and_install() {
  local serial="$1"

  info "Building debug APK..."
  "${PROJECT_ROOT}/gradlew" :app:assembleDebug || return 1

  if [[ ! -f "$APK_PATH" ]]; then
    error "APK not found at: ${APK_PATH}"
    return 1
  fi

  info "Installing APK on ${serial}..."
  adb -s "$serial" install -r -t "$APK_PATH" || return 1
  return 0
}

launch_game() {
  local serial="$1"
  info "Launching ${ACTIVITY} on ${serial}..."
  adb -s "$serial" shell am start -n "$ACTIVITY"
}

wait_for_app_pid() {
  local serial="$1"
  local timeout_seconds="$2"
  local pid=""
  local deadline=$((SECONDS + timeout_seconds))

  while (( SECONDS < deadline )); do
    pid="$(adb -s "$serial" shell pidof -s "$APP_ID" 2>/dev/null | tr -d '\r' || true)"
    if [[ -n "$pid" ]]; then
      printf '%s' "$pid"
      return 0
    fi
    sleep 1
  done
  return 1
}

stream_logs() {
  local serial="$1"
  local log_mode="$2"
  local pid=""
  local -a filters=()

  case "$log_mode" in
    dt)
      filters=("GameFrameTiming:D" "*:S")
      ;;
    errors)
      filters=("*:E")
      ;;
    all)
      ;;
    *)
      error "Unsupported streaming mode: ${log_mode}"
      return 2
      ;;
  esac

  if pid="$(wait_for_app_pid "$serial" 12)"; then
    info "Streaming ${log_mode} logs for ${APP_ID} (pid ${pid})."
    info "Press Ctrl+C to stop log streaming."
    if ! adb -s "$serial" logcat --pid="$pid" -v time "${filters[@]}"; then
      warn "PID-filtered logcat failed. Falling back to full logcat."
      adb -s "$serial" logcat -v time "${filters[@]}"
    fi
  else
    warn "Could not determine app PID. Falling back to full logcat."
    info "Press Ctrl+C to stop log streaming."
    adb -s "$serial" logcat -v time "${filters[@]}"
  fi
}

main() {
  parse_args "$@" || exit $?
  trap restore_generated_build_ownership EXIT
  require_cmd adb
  require_cmd awk

  if [[ ! -x "${PROJECT_ROOT}/gradlew" ]]; then
    error "gradlew not found or not executable in project root."
    exit 1
  fi

  cd "$PROJECT_ROOT" || exit 1
  adb start-server >/dev/null

  if ! ensure_device_connected; then
    error "Could not connect to any device."
    exit 1
  fi

  local serial
  if ! serial="$(choose_device)"; then
    error "No connected device available."
    exit 1
  fi

  info "Using device: ${serial}"
  if ! build_and_install "$serial"; then
    error "Build or install failed."
    exit 1
  fi
  restore_generated_build_ownership

  info "Clearing old logcat buffer..."
  adb -s "$serial" logcat -c || true

  launch_game "$serial"
  if [[ "$LOG_MODE" != "none" ]]; then
    stream_logs "$serial" "$LOG_MODE"
  fi
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
