#!/usr/bin/env bash
set -euo pipefail

APP_NAME="coding-interview-practice-platform-backend"
APP_DIR="/root/codes/coding-interview-practice-platform/backend"
APP_BIN="$APP_DIR/target/$APP_NAME"
RUNTIME_DIR="$APP_DIR/runtime"
PID_FILE="$RUNTIME_DIR/backend-native.pid"
LOG_FILE="$RUNTIME_DIR/backend-native.log"
CONFIG_FILE="${CONFIG_FILE:-$APP_DIR/src/main/resources/application.yml}"

export RABBITMQ_USERNAME="${RABBITMQ_USERNAME:-kangnasi}"
export RABBITMQ_PASSWORD="${RABBITMQ_PASSWORD:-}"

usage() {
  echo "Usage: $0 {start|stop|restart|启动|停止|重启}" >&2
  echo "Environment: CONFIG_FILE=/path/to/application.yml" >&2
}

is_running() {
  local pid="$1"
  [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null
}

read_pid() {
  if [[ -f "$PID_FILE" ]]; then
    tr -d '[:space:]' < "$PID_FILE"
  fi
}

find_running_pid() {
  pgrep -f "$APP_BIN" | head -n 1 || true
}

start_app() {
  mkdir -p "$RUNTIME_DIR"

  if [[ ! -x "$APP_BIN" ]]; then
    echo "Binary is not executable: $APP_BIN" >&2
    exit 1
  fi

  if [[ ! -f "$CONFIG_FILE" ]]; then
    echo "Config file does not exist: $CONFIG_FILE" >&2
    exit 1
  fi

  local pid
  pid="$(read_pid || true)"
  if is_running "$pid"; then
    echo "$APP_NAME is already running, pid=$pid"
    exit 0
  fi

  pid="$(find_running_pid)"
  if is_running "$pid"; then
    echo "$pid" > "$PID_FILE"
    echo "$APP_NAME is already running, pid=$pid"
    exit 0
  fi

  {
    printf '\n===== %s starting %s =====\n' "$(date '+%F %T %z')" "$APP_NAME"
    printf 'binary=%s\n' "$APP_BIN"
    printf 'config=%s\n' "$CONFIG_FILE"
    printf 'rabbitmq=%s:%s user=%s\n' "${RABBITMQ_HOST:-127.0.0.1}" "${RABBITMQ_PORT:-8090}" "$RABBITMQ_USERNAME"
  } >> "$LOG_FILE"

  if command -v setsid >/dev/null 2>&1; then
    setsid bash -c 'cd "$1"; shift; exec "$@"' bash "$APP_DIR" "$APP_BIN" \
      "--spring.config.additional-location=file:$CONFIG_FILE" \
      >> "$LOG_FILE" 2>&1 < /dev/null &
  else
    nohup bash -c 'cd "$1"; shift; exec "$@"' bash "$APP_DIR" "$APP_BIN" \
      "--spring.config.additional-location=file:$CONFIG_FILE" \
      >> "$LOG_FILE" 2>&1 < /dev/null &
  fi
  pid="$!"
  echo "$pid" > "$PID_FILE"
  sleep 1
  if ! is_running "$pid"; then
    pid="$(find_running_pid)"
    if is_running "$pid"; then
      echo "$pid" > "$PID_FILE"
    fi
  fi
  echo "$APP_NAME started, pid=$pid, log=$LOG_FILE"
}

stop_app() {
  local allow_continue="${1:-false}"
  local pid
  pid="$(read_pid || true)"

  if ! is_running "$pid"; then
    pid="$(find_running_pid)"
  fi

  if ! is_running "$pid"; then
    rm -f "$PID_FILE"
    echo "$APP_NAME is not running"
    if [[ "$allow_continue" == "true" ]]; then
      return
    fi
    exit 0
  fi

  echo "Stopping $APP_NAME, pid=$pid"
  kill "$pid"

  for _ in $(seq 1 30); do
    if ! is_running "$pid"; then
      rm -f "$PID_FILE"
      echo "$APP_NAME stopped"
      return
    fi
    sleep 1
  done

  echo "Graceful stop timed out, killing pid=$pid"
  kill -9 "$pid" 2>/dev/null || true
  rm -f "$PID_FILE"
  echo "$APP_NAME stopped"
}

case "${1:-}" in
  start|启动)
    start_app
    ;;
  stop|停止)
    stop_app
    ;;
  restart|重启)
    stop_app true
    start_app
    ;;
  *)
    usage
    exit 2
    ;;
esac
