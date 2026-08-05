#!/usr/bin/env bash
set -euo pipefail

APP_NAME="coding-interview-practice-platform-backend"
APP_DIR="/root/codes/coding-interview-practice-platform/backend"
APP_BIN="$APP_DIR/target/$APP_NAME"
SCRIPT_PATH="$(readlink -f "${BASH_SOURCE[0]}")"
RUNTIME_DIR="$APP_DIR/runtime"
PID_FILE="$RUNTIME_DIR/backend-native.pid"
LOG_DIR="$RUNTIME_DIR/logs"
LOG_BASENAME="backend-native.log"
LOG_PIPE="$RUNTIME_DIR/backend-native.log.pipe"
LOG_WRITER_PID_FILE="$RUNTIME_DIR/backend-native-log-writer.pid"
CONFIG_FILE="${CONFIG_FILE:-$APP_DIR/src/main/resources/application.yml}"
BACKEND_ENV_FILE="${BACKEND_ENV_FILE:-/root/.config/coding-interview-backend/backend.env}"

if [[ -r "$BACKEND_ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$BACKEND_ENV_FILE"
  set +a
fi

export MYSQL_USERNAME="${MYSQL_USERNAME:-kangnasi}"
export MYSQL_PASSWORD="${MYSQL_PASSWORD:-}"
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

current_log_file() {
  local log_day
  printf -v log_day '%(%F)T' -1
  printf '%s/%s/%s\n' "$LOG_DIR" "$log_day" "$LOG_BASENAME"
}

daily_log() {
  local line current_day active_day=""

  while IFS= read -r line || [[ -n "$line" ]]; do
    printf -v current_day '%(%F)T' -1
    if [[ "$current_day" != "$active_day" ]]; then
      if [[ -n "$active_day" ]]; then
        exec 3>&-
      fi
      mkdir -p "$LOG_DIR/$current_day"
      exec 3>> "$LOG_DIR/$current_day/$LOG_BASENAME"
      active_day="$current_day"
    fi
    printf '%s\n' "$line" >&3
  done

  if [[ -n "$active_day" ]]; then
    exec 3>&-
  fi
}

read_log_writer_pid() {
  if [[ -f "$LOG_WRITER_PID_FILE" ]]; then
    tr -d '[:space:]' < "$LOG_WRITER_PID_FILE"
  fi
}

is_log_writer_running() {
  local pid="$1" cmdline
  is_running "$pid" || return 1
  cmdline="$(tr '\0' ' ' < "/proc/$pid/cmdline" 2>/dev/null || true)"
  [[ "$cmdline" == *"__daily-log"* ]]
}

stop_log_writer() {
  local writer_pid
  writer_pid="$(read_log_writer_pid || true)"

  if is_log_writer_running "$writer_pid"; then
    kill "$writer_pid" 2>/dev/null || true
    for _ in $(seq 1 20); do
      if ! is_log_writer_running "$writer_pid"; then
        break
      fi
      sleep 0.1
    done
  fi

  rm -f -- "$LOG_WRITER_PID_FILE"
  if [[ -p "$LOG_PIPE" ]]; then
    rm -f -- "$LOG_PIPE"
  fi
}

start_log_writer() {
  local writer_pid

  stop_log_writer
  if [[ -e "$LOG_PIPE" ]]; then
    echo "Log pipe path exists and is not a named pipe: $LOG_PIPE" >&2
    exit 1
  fi
  mkfifo -m 600 "$LOG_PIPE"

  if command -v setsid >/dev/null 2>&1; then
    setsid bash "$SCRIPT_PATH" __daily-log < "$LOG_PIPE" > /dev/null 2>&1 &
  else
    nohup bash "$SCRIPT_PATH" __daily-log < "$LOG_PIPE" > /dev/null 2>&1 &
  fi
  writer_pid="$!"
  echo "$writer_pid" > "$LOG_WRITER_PID_FILE"
}

find_running_pid() {
  local proc_dir executable
  for proc_dir in /proc/[0-9]*; do
    executable="$(readlink "$proc_dir/exe" 2>/dev/null || true)"
    executable="${executable% (deleted)}"
    if [[ "$executable" == "$APP_BIN" ]]; then
      printf '%s\n' "${proc_dir##*/}"
      return
    fi
  done
}

start_app() {
  mkdir -p "$RUNTIME_DIR" "$LOG_DIR"

  if [[ ! -x "$APP_BIN" ]]; then
    echo "Binary is not executable: $APP_BIN" >&2
    exit 1
  fi

  if [[ ! -f "$CONFIG_FILE" ]]; then
    echo "Config file does not exist: $CONFIG_FILE" >&2
    exit 1
  fi

  local pid log_file
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

  log_file="$(current_log_file)"
  mkdir -p "${log_file%/*}"
  {
    printf '\n===== %s starting %s =====\n' "$(date '+%F %T %z')" "$APP_NAME"
    printf 'binary=%s\n' "$APP_BIN"
    printf 'config=%s\n' "$CONFIG_FILE"
    printf 'rabbitmq=%s:%s user=%s\n' "${RABBITMQ_HOST:-127.0.0.1}" "${RABBITMQ_PORT:-8090}" "$RABBITMQ_USERNAME"
  } >> "$log_file"

  start_log_writer
  if command -v setsid >/dev/null 2>&1; then
    setsid bash -c 'cd "$1"; shift; exec "$@"' bash "$APP_DIR" "$APP_BIN" \
      "--spring.config.additional-location=file:$CONFIG_FILE" \
      > "$LOG_PIPE" 2>&1 < /dev/null &
  else
    nohup bash -c 'cd "$1"; shift; exec "$@"' bash "$APP_DIR" "$APP_BIN" \
      "--spring.config.additional-location=file:$CONFIG_FILE" \
      > "$LOG_PIPE" 2>&1 < /dev/null &
  fi
  pid="$!"
  echo "$pid" > "$PID_FILE"
  sleep 1
  if ! is_running "$pid"; then
    pid="$(find_running_pid)"
    if is_running "$pid"; then
      echo "$pid" > "$PID_FILE"
    else
      stop_log_writer
      echo "$APP_NAME failed to start, log=$log_file" >&2
      exit 1
    fi
  fi
  echo "$APP_NAME started, pid=$pid, log=$log_file"
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
    stop_log_writer
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
      stop_log_writer
      echo "$APP_NAME stopped"
      return
    fi
    sleep 1
  done

  echo "Graceful stop timed out, killing pid=$pid"
  kill -9 "$pid" 2>/dev/null || true
  rm -f "$PID_FILE"
  stop_log_writer
  echo "$APP_NAME stopped"
}

case "${1:-}" in
  __daily-log)
    daily_log
    ;;
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
