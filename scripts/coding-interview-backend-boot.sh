#!/system/bin/sh

# Magisk service.d one-shot launcher for the native backend.
# It waits for the Ubuntu chroot to become usable, invokes rt.sh once, and exits.

CHROOT_DIR="${CHROOT_DIR:-/data/local/linux}"
CHROOT_BIN="${CHROOT_BIN:-/system/bin/chroot}"
RT_SCRIPT="${RT_SCRIPT:-/root/codes/coding-interview-practice-platform/scripts/rt.sh}"
APP_BIN="${APP_BIN:-/root/codes/coding-interview-practice-platform/backend/target/coding-interview-practice-platform-backend}"
CONFIG_FILE="${CONFIG_FILE:-/root/codes/coding-interview-practice-platform/backend/src/main/resources/application.yml}"
ANDROID_LOG="${ANDROID_LOG:-/data/local/tmp/coding-interview-backend-service.d.log}"
START_DELAY="${START_DELAY:-20}"
WAIT_INTERVAL="${WAIT_INTERVAL:-5}"
WAIT_TRIES="${WAIT_TRIES:-120}"

/system/bin/mkdir -p "$(/system/bin/dirname "$ANDROID_LOG")"

log() {
  echo "$(/system/bin/date '+%F %T') $*"
}

{
  log "coding-interview-backend one-shot launcher invoked"

  # Give Android late-start services and the chroot mount hook time to settle.
  /system/bin/sleep "$START_DELAY"

  tries=0
  while [ "$tries" -lt "$WAIT_TRIES" ]; do
    if [ -x "$CHROOT_BIN" ] \
      && [ -x "$CHROOT_DIR/bin/bash" ] \
      && [ -r "$CHROOT_DIR/proc/1/stat" ] \
      && [ -x "$CHROOT_DIR$RT_SCRIPT" ] \
      && [ -x "$CHROOT_DIR$APP_BIN" ] \
      && [ -f "$CHROOT_DIR$CONFIG_FILE" ]; then
      break
    fi

    tries=$((tries + 1))
    /system/bin/sleep "$WAIT_INTERVAL"
  done

  if [ ! -x "$CHROOT_BIN" ]; then
    log "missing chroot binary: $CHROOT_BIN"
    exit 1
  fi

  if [ ! -x "$CHROOT_DIR/bin/bash" ]; then
    log "missing chroot bash: $CHROOT_DIR/bin/bash"
    exit 1
  fi

  if [ ! -r "$CHROOT_DIR/proc/1/stat" ]; then
    log "chroot proc is not ready: $CHROOT_DIR/proc"
    exit 1
  fi

  if [ ! -x "$CHROOT_DIR$RT_SCRIPT" ]; then
    log "missing startup script: $CHROOT_DIR$RT_SCRIPT"
    exit 1
  fi

  if [ ! -x "$CHROOT_DIR$APP_BIN" ]; then
    log "missing native backend: $CHROOT_DIR$APP_BIN"
    exit 1
  fi

  if [ ! -f "$CHROOT_DIR$CONFIG_FILE" ]; then
    log "missing backend config: $CHROOT_DIR$CONFIG_FILE"
    exit 1
  fi

  log "chroot ready after ${tries} checks; invoking rt.sh start once"
  "$CHROOT_BIN" "$CHROOT_DIR" /usr/bin/env \
    HOME="/root" \
    USER="root" \
    LOGNAME="root" \
    SHELL="/bin/bash" \
    PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin" \
    CONFIG_FILE="$CONFIG_FILE" \
    /bin/bash "$RT_SCRIPT" start
  result="$?"

  log "rt.sh start finished result=$result; one-shot launcher exiting"
  exit "$result"
} >>"$ANDROID_LOG" 2>&1 </dev/null &

exit 0
