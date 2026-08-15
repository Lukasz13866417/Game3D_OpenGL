#!/usr/bin/env bash

set -u

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_ID="com.example.game3d_opengl"
ACTIVITY="${APP_ID}/.OpenGLES20Activity"
APK_PATH="${PROJECT_ROOT}/app/build/outputs/apk/debug/app-debug.apk"
LOG_MODE="all"

usage() {
  cat <<'EOF'
Usage: ./launch_on_phone.sh [--logs all|dt|errors|none]

Run this script as your normal user, without sudo. If no authorized USB device is
available, the launcher discovers Android wireless-debugging endpoints and guides
you through pairing.

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

refuse_root() {
  if (( EUID == 0 )); then
    error "Do not run this launcher with sudo."
    error "ADB pairing keys, the Android SDK, Gradle caches, and the debug keystore belong to your user account."
    error "Run it as your normal user: ./launch_on_phone.sh"
    exit 1
  fi
}

configure_android_tools() {
  if command -v adb >/dev/null 2>&1; then
    return
  fi

  local properties_sdk=""
  if [[ -f "${PROJECT_ROOT}/local.properties" ]]; then
    local key
    local value
    while IFS='=' read -r key value; do
      if [[ "$key" == "sdk.dir" ]]; then
        properties_sdk="$value"
      fi
    done < "${PROJECT_ROOT}/local.properties"
  fi

  local -a sdk_candidates=(
    "${ANDROID_SDK_ROOT:-}"
    "${ANDROID_HOME:-}"
    "$properties_sdk"
  )
  if [[ -n "${HOME:-}" ]]; then
    sdk_candidates+=("${HOME}/Android/Sdk")
  fi

  local sdk_dir
  for sdk_dir in "${sdk_candidates[@]}"; do
    if [[ -n "$sdk_dir" && -x "${sdk_dir}/platform-tools/adb" ]]; then
      export PATH="${sdk_dir}/platform-tools:${PATH:-}"
      return
    fi
  done
}

require_java() {
  if command -v java >/dev/null 2>&1; then
    return
  fi
  if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
    return
  fi
  error "Java was not found in PATH or JAVA_HOME."
  exit 1
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
    if ! IFS= read -r -p "$prompt" value; then
      error "Input closed while waiting for a response."
      return 1
    fi
  done
  printf '%s' "$value"
}

prompt_secret_nonempty() {
  local prompt="$1"
  local value=""
  while [[ -z "$value" ]]; do
    if ! IFS= read -r -s -p "$prompt" value; then
      printf '\n' >&2
      error "Input closed while waiting for a response."
      return 1
    fi
    printf '\n' >&2
  done
  printf '%s' "$value"
}

prompt_optional() {
  local prompt="$1"
  local value=""
  if ! IFS= read -r -p "$prompt" value; then
    error "Input closed while waiting for a response."
    return 1
  fi
  printf '%s' "$value"
}

get_discovered_connect_endpoints() {
  adb mdns services 2>/dev/null \
    | awk '$2=="_adb-tls-connect._tcp" && $3!="" && !seen[$3]++ {print $3}'
}

choose_device() {
  local listing
  if ! listing="$(adb devices -l)"; then
    error "Could not list ADB devices."
    return 1
  fi

  local -a devices=()
  mapfile -t devices < <(awk 'NR>1 && $2=="device" {print $1}' <<<"$listing")

  if (( ${#devices[@]} == 0 )); then
    return 1
  fi

  if (( ${#devices[@]} == 1 )); then
    printf '%s' "${devices[0]}"
    return 0
  fi

  info "Multiple connected devices found:" >&2
  local i=1
  local description
  for serial in "${devices[@]}"; do
    description="$(awk -v serial="$serial" '$1==serial {print; exit}' <<<"$listing")"
    printf '  %d) %s\n' "$i" "$description" >&2
    ((i++))
  done

  local idx=""
  while true; do
    if ! idx="$(prompt_nonempty 'Select device number: ')"; then
      return 1
    fi
    if [[ "$idx" =~ ^[0-9]+$ ]] && (( idx >= 1 && idx <= ${#devices[@]} )); then
      printf '%s' "${devices[idx-1]}"
      return 0
    fi
    warn "Invalid selection." >&2
  done
}

try_connect_wireless() {
  local endpoint="$1"
  local out
  out="$(adb connect "$endpoint" 2>&1 || true)"
  printf '%s\n' "$out"

  if grep -qiE 'connected to|already connected to' <<<"$out"; then
    return 0
  fi
  return 1
}

try_discovered_wireless() {
  local endpoint
  local found=false
  local connected=false
  while IFS= read -r endpoint; do
    [[ -z "$endpoint" ]] && continue
    found=true
    info "Trying discovered wireless device ${endpoint}."
    if try_connect_wireless "$endpoint"; then
      connected=true
    fi
  done < <(get_discovered_connect_endpoints)

  if [[ "$connected" == true ]]; then
    return 0
  fi
  [[ "$found" == true ]] && warn "Discovered wireless endpoints were not connectable."
  return 1
}

pair_wireless() {
  local pair_endpoint="$1"
  local pair_code="$2"
  local out
  out="$(printf '%s\n' "$pair_code" | adb pair "$pair_endpoint" 2>&1 || true)"
  printf '%s\n' "$out"

  if grep -qiE 'successfully paired|already paired' <<<"$out"; then
    return 0
  fi
  return 1
}

ensure_device_connected() {
  local listing
  if ! listing="$(adb devices -l)"; then
    error "Could not list ADB devices."
    return 1
  fi

  if awk 'NR>1 && $2=="device" {found=1} END {exit !found}' <<<"$listing"; then
    return 0
  fi

  local unavailable
  unavailable="$(awk 'NR>1 && NF>0 && $2!="device" {print}' <<<"$listing")"
  if [[ -n "$unavailable" ]]; then
    warn "ADB sees device(s) that are not ready:"
    printf '%s\n' "$unavailable"
    info "For an unauthorized USB device, unlock the phone and approve its debugging prompt."
  fi

  info "No connected device found."
  if try_discovered_wireless; then
    return 0
  fi

  warn "Automatic wireless connect failed."
  info "If this computer is already paired, enter the CONNECT address shown on the phone."
  local connect_endpoint
  if ! connect_endpoint="$(prompt_optional 'CONNECT address (IP:port), or Enter to pair again: ')"; then
    return 1
  fi
  if [[ -n "$connect_endpoint" ]]; then
    if try_connect_wireless "$connect_endpoint"; then
      return 0
    fi
    warn "Manual connect failed; pairing is required."
  fi

  info "On the phone, open Developer options > Wireless debugging > Pair device with pairing code."
  local pair_endpoint
  local pair_code
  if ! pair_endpoint="$(prompt_nonempty 'Enter wireless PAIR address (IP:port): ')"; then
    return 1
  fi
  if ! pair_code="$(prompt_secret_nonempty 'Enter pairing code shown on phone: ')"; then
    return 1
  fi

  if ! pair_wireless "$pair_endpoint" "$pair_code"; then
    error "Pairing failed."
    return 1
  fi

  local attempt
  for attempt in 1 2 3; do
    if try_discovered_wireless; then
      return 0
    fi
    (( attempt < 3 )) && sleep 1
  done

  if ! connect_endpoint="$(prompt_nonempty 'Enter wireless CONNECT address (IP:port): ')"; then
    return 1
  fi
  if ! try_connect_wireless "$connect_endpoint"; then
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
  local install_output
  if ! install_output="$(adb -s "$serial" install -r -t "$APK_PATH" 2>&1)"; then
    printf '%s\n' "$install_output" >&2
    if grep -q 'INSTALL_FAILED_UPDATE_INCOMPATIBLE' <<<"$install_output"; then
      error "The installed app was signed with a different debug key, possibly from an earlier sudo build."
      error "The launcher will not uninstall it automatically because uninstalling can erase app data."
    fi
    return 1
  fi
  printf '%s\n' "$install_output"
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

  if ! pid="$(wait_for_app_pid "$serial" 12)"; then
    error "Could not determine the game process ID; logs were not streamed."
    return 1
  fi

  info "Streaming ${log_mode} logs for ${APP_ID} (pid ${pid})."
  info "Press Ctrl+C to stop log streaming."
  if ! adb -s "$serial" logcat -T 1 --pid="$pid" -v time "${filters[@]}"; then
    error "PID-filtered logcat failed; full-device logs were not opened."
    return 1
  fi
}

main() {
  parse_args "$@" || exit $?
  refuse_root
  configure_android_tools
  require_cmd adb
  require_cmd awk
  require_cmd grep
  require_cmd sleep
  require_cmd tr
  require_java

  if [[ ! -x "${PROJECT_ROOT}/gradlew" ]]; then
    error "gradlew not found or not executable in project root."
    exit 1
  fi

  cd "$PROJECT_ROOT" || exit 1
  if ! adb start-server >/dev/null; then
    error "Could not start the user ADB server."
    exit 1
  fi

  if ! ensure_device_connected; then
    error "Could not connect to any device."
    exit 1
  fi

  local serial
  if ! serial="$(choose_device)"; then
    error "No connected device available."
    exit 1
  fi

  local model
  model="$(adb -s "$serial" shell getprop ro.product.model 2>/dev/null | tr -d '\r' || true)"
  if [[ -n "$model" ]]; then
    info "Using device: ${serial} (${model})"
  else
    info "Using device: ${serial}"
  fi
  if ! build_and_install "$serial"; then
    error "Build or install failed."
    exit 1
  fi

  if ! launch_game "$serial"; then
    error "The app could not be launched."
    exit 1
  fi
  if [[ "$LOG_MODE" != "none" ]]; then
    if ! stream_logs "$serial" "$LOG_MODE"; then
      exit 1
    fi
  fi
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
