#!/usr/bin/env bash
# Verifies the database isolation required by design section 13.3:
# each service role can connect to its own database and is denied on the other three.
#
# Defaults match the Compose stack (PostgreSQL published on localhost:5432, one role per
# service whose password equals the role name). Override with PGHOST, PGPORT, and the
# per-role AUTH_DB_PASSWORD / USER_DB_PASSWORD / TASK_DB_PASSWORD / TIMELINE_DB_PASSWORD.
set -uo pipefail

PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGCONNECT_TIMEOUT="${PGCONNECT_TIMEOUT:-5}"

ROLES="auth_service user_service task_service timeline_service"
DATABASES="auth_db user_db task_db timeline_db"

password_for() {
  case "$1" in
    auth_service) printf '%s' "${AUTH_DB_PASSWORD:-auth_service}" ;;
    user_service) printf '%s' "${USER_DB_PASSWORD:-user_service}" ;;
    task_service) printf '%s' "${TASK_DB_PASSWORD:-task_service}" ;;
    timeline_service) printf '%s' "${TIMELINE_DB_PASSWORD:-timeline_service}" ;;
    *) printf '%s' "$1" ;;
  esac
}

database_for() {
  case "$1" in
    auth_service) printf '%s' auth_db ;;
    user_service) printf '%s' user_db ;;
    task_service) printf '%s' task_db ;;
    timeline_service) printf '%s' timeline_db ;;
    *) printf '%s' '' ;;
  esac
}

if ! command -v psql >/dev/null 2>&1; then
  echo "FAIL: psql is not installed or not on PATH" >&2
  exit 2
fi

run_psql() {
  # $1 = role, $2 = database, $3 = SQL statement
  PGPASSWORD="$(password_for "$1")" PGCONNECT_TIMEOUT="$PGCONNECT_TIMEOUT" LC_ALL=C \
    psql --no-psqlrc --quiet --no-align --tuples-only \
      --host "$PGHOST" --port "$PGPORT" --username "$1" --dbname "$2" \
      --command "$3" 2>&1
}

checks=0
failures=0

echo "Checking database isolation on ${PGHOST}:${PGPORT}"

for role in $ROLES; do
  owned="$(database_for "$role")"
  for db in $DATABASES; do
    checks=$((checks + 1))
    result="$(run_psql "$role" "$db" 'SELECT current_database();')"
    status=$?
    # Keep failure output on a single line.
    result="$(printf '%s' "$result" | tr '\n' ' ')"

    if [ "$db" = "$owned" ]; then
      if [ "$status" -eq 0 ] && [ "$result" = "$db" ]; then
        printf 'ok   %-16s -> %-12s own database\n' "$role" "$db"
      else
        printf 'FAIL %-16s -> %-12s expected success, got: %s\n' "$role" "$db" "$result" >&2
        failures=$((failures + 1))
      fi
    else
      case "$result" in
        *"permission denied for database"*)
          if [ "$status" -ne 0 ]; then
            printf 'ok   %-16s -/-> %-12s denied\n' "$role" "$db"
          else
            printf 'FAIL %-16s -/-> %-12s connected despite denial message\n' "$role" "$db" >&2
            failures=$((failures + 1))
          fi
          ;;
        *)
          printf 'FAIL %-16s -/-> %-12s expected denial, got: %s\n' "$role" "$db" "$result" >&2
          failures=$((failures + 1))
          ;;
      esac
    fi
  done
done

if [ "$failures" -ne 0 ]; then
  echo "FAILED: ${failures} of ${checks} database isolation checks did not behave as expected" >&2
  exit 1
fi

echo "PASSED: ${checks} checks, every role reaches only its own database"
