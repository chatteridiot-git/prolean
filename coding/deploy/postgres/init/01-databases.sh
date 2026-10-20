#!/usr/bin/env bash
# Creates the four service databases and roles, then removes PUBLIC connect access so a
# service role cannot open another service's database. Runs once on a fresh postgres volume.
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v auth_pw="${AUTH_DB_PASSWORD:-auth_service}" \
  -v user_pw="${USER_DB_PASSWORD:-user_service}" \
  -v task_pw="${TASK_DB_PASSWORD:-task_service}" \
  -v timeline_pw="${TIMELINE_DB_PASSWORD:-timeline_service}" <<'EOSQL'
SELECT format('CREATE ROLE auth_service LOGIN PASSWORD %L', :'auth_pw')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'auth_service')\gexec
SELECT format('CREATE ROLE user_service LOGIN PASSWORD %L', :'user_pw')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'user_service')\gexec
SELECT format('CREATE ROLE task_service LOGIN PASSWORD %L', :'task_pw')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'task_service')\gexec
SELECT format('CREATE ROLE timeline_service LOGIN PASSWORD %L', :'timeline_pw')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'timeline_service')\gexec

SELECT 'CREATE DATABASE auth_db OWNER auth_service'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'auth_db')\gexec
SELECT 'CREATE DATABASE user_db OWNER user_service'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'user_db')\gexec
SELECT 'CREATE DATABASE task_db OWNER task_service'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'task_db')\gexec
SELECT 'CREATE DATABASE timeline_db OWNER timeline_service'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'timeline_db')\gexec

REVOKE CONNECT ON DATABASE auth_db FROM PUBLIC;
REVOKE CONNECT ON DATABASE user_db FROM PUBLIC;
REVOKE CONNECT ON DATABASE task_db FROM PUBLIC;
REVOKE CONNECT ON DATABASE timeline_db FROM PUBLIC;

GRANT CONNECT ON DATABASE auth_db TO auth_service;
GRANT CONNECT ON DATABASE user_db TO user_service;
GRANT CONNECT ON DATABASE task_db TO task_service;
GRANT CONNECT ON DATABASE timeline_db TO timeline_service;

\connect auth_db
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA public TO auth_service;

\connect user_db
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA public TO user_service;

\connect task_db
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA public TO task_service;

\connect timeline_db
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA public TO timeline_service;
EOSQL

echo "ProLean databases and roles created."
