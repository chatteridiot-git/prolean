#!/usr/bin/env bash
# Verifies the Compose PostgreSQL isolation contract:
#   * each service role can connect to its own database
#   * each service role is denied on every other service database
#
# Run from coding/ or any directory: coding/scripts/db-check.sh
set -euo pipefail

cd "$(dirname "$0")/.."

AUTH_PW="${AUTH_DB_PASSWORD:-auth_service}"
USER_PW="${USER_DB_PASSWORD:-user_service}"
TASK_PW="${TASK_DB_PASSWORD:-task_service}"
TIMELINE_PW="${TIMELINE_DB_PASSWORD:-timeline_service}"

compose() {
  docker compose -f deploy/docker-compose.yml "$@"
}

if ! compose ps postgres --status running --format '{{.Name}}' | grep -q .; then
  echo "postgres is not running; start the Compose stack first" >&2
  exit 1
fi

can_connect() {
  local role="$1" password="$2" database="$3"
  compose exec -T -e PGPASSWORD="$password" postgres \
    psql -h 127.0.0.1 -U "$role" -d "$database" -v ON_ERROR_STOP=1 -q -c 'select 1' \
    >/dev/null 2>&1
}

check_allowed() {
  local role="$1" password="$2" database="$3"
  if ! can_connect "$role" "$password" "$database"; then
    echo "FAIL: $role cannot connect to its database $database" >&2
    exit 1
  fi
  echo "ok: $role -> $database"
}

check_denied() {
  local role="$1" password="$2" database="$3"
  if can_connect "$role" "$password" "$database"; then
    echo "FAIL: $role unexpectedly connected to $database" >&2
    exit 1
  fi
  echo "ok: $role denied on $database"
}

check_allowed auth_service "$AUTH_PW" auth_db
check_allowed user_service "$USER_PW" user_db
check_allowed task_service "$TASK_PW" task_db
check_allowed timeline_service "$TIMELINE_PW" timeline_db

for database in auth_db user_db task_db timeline_db; do
  case "$database" in
    auth_db) own=auth_service; own_pw="$AUTH_PW" ;;
    user_db) own=user_service; own_pw="$USER_PW" ;;
    task_db) own=task_service; own_pw="$TASK_PW" ;;
    timeline_db) own=timeline_service; own_pw="$TIMELINE_PW" ;;
  esac
  for role in auth_service user_service task_service timeline_service; do
    [ "$role" = "$own" ] && continue
    case "$role" in
      auth_service) pw="$AUTH_PW" ;;
      user_service) pw="$USER_PW" ;;
      task_service) pw="$TASK_PW" ;;
      timeline_service) pw="$TIMELINE_PW" ;;
    esac
    check_denied "$role" "$pw" "$database"
  done
done

echo "Cross-database isolation verified."
