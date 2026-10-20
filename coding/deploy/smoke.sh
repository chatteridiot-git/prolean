#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
ADMIN_USERNAME="${ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-ChangeMe123!}"
COOKIE_JAR="$(mktemp)"
BODY_FILE="$(mktemp)"
trap 'rm -f "$COOKIE_JAR" "$BODY_FILE"' EXIT

request() {
  local method="$1" path="$2" expected="$3" body="${4:-}"
  local args=(-sS -o "$BODY_FILE" -w '%{http_code}' -X "$method"
    -H 'Content-Type: application/json' -b "$COOKIE_JAR" -c "$COOKIE_JAR")
  if [[ -n "${TOKEN:-}" ]]; then
    args+=(-H "Authorization: Bearer $TOKEN")
  fi
  if [[ -n "$body" ]]; then
    args+=(-d "$body")
  fi
  local status
  status="$(curl "${args[@]}" "$BASE_URL$path")"
  if [[ "$status" != "$expected" ]]; then
    echo "FAIL $method $path: expected $expected, got $status" >&2
    cat "$BODY_FILE" >&2
    exit 1
  fi
  cat "$BODY_FILE"
}

echo "1/7 login"
LOGIN="$(request POST /api/v1/auth/login 200 "{\"username\":\"$ADMIN_USERNAME\",\"password\":\"$ADMIN_PASSWORD\"}")"
TOKEN="$(jq -r '.accessToken' <<<"$LOGIN")"
USER_ID="$(jq -r '.user.id' <<<"$LOGIN")"
[[ "$TOKEN" != "null" && -n "$TOKEN" ]] || { echo "No access token" >&2; exit 1; }

echo "2/7 current user"
ME="$(request GET /api/v1/auth/me 200)"
[[ "$(jq -r '.id' <<<"$ME")" == "$USER_ID" ]] || { echo "Unexpected /me user" >&2; exit 1; }

echo "3/7 create project (and replay)"
PROJECT_REQUEST_ID="$(cat /proc/sys/kernel/random/uuid)"
PROJECT_CODE="SMOKE-$(date +%s)"
PROJECT_BODY="$(jq -nc --arg code "$PROJECT_CODE" --arg id "$USER_ID" --arg requestId "$PROJECT_REQUEST_ID" \
  '{code:$code,name:"Smoke project",description:"Compose smoke test",startDate:"2026-11-02",endDate:"2026-12-18",memberIds:[$id],requestId:$requestId}')"
PROJECT="$(request POST /api/v1/projects 201 "$PROJECT_BODY")"
PROJECT_ID="$(jq -r '.id' <<<"$PROJECT")"
REPLAY_HEADERS="$(curl -sS -D - -o /dev/null -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" -d "$PROJECT_BODY" "$BASE_URL/api/v1/projects")"
grep -qi '^Idempotent-Replay: true' <<<"$REPLAY_HEADERS" || {
  echo "Project replay did not return Idempotent-Replay: true" >&2
  exit 1
}

echo "4/7 list projects"
PROJECTS="$(request GET '/api/v1/projects?page=0&size=20' 200)"
jq -e --arg id "$PROJECT_ID" '.content | any(.id == $id)' <<<"$PROJECTS" >/dev/null || {
  echo "Created project not visible in list" >&2
  exit 1
}

echo "5/7 create task"
TASK="$(request POST "/api/v1/projects/$PROJECT_ID/tasks" 201 "$(jq -nc \
  --arg pic "$USER_ID" --arg requestId "$(cat /proc/sys/kernel/random/uuid)" \
  '{title:"Smoke task",description:"Created by smoke script",startDate:"2026-11-02",endDate:"2026-11-06",picUserId:$pic,status:"OPEN",percentComplete:0,requestId:$requestId}')")"
[[ "$(jq -r '.durationDays' <<<"$TASK")" == "5" ]] || { echo "Unexpected task duration" >&2; exit 1; }

echo "6/7 create and publish timeline"
TIMELINE_REQUEST_ID="$(cat /proc/sys/kernel/random/uuid)"
DRAFT="$(request POST "/api/v1/projects/$PROJECT_ID/timeline" 201 "$(jq -nc \
  --arg requestId "$TIMELINE_REQUEST_ID" \
  '{startDate:"2026-11-02",endDate:"2026-11-20",requestId:$requestId,revisionNotes:"Smoke baseline"}')")"
DRAFT_UPDATED_AT="$(jq -r '.updatedAt' <<<"$DRAFT")"
PUBLISHED="$(request POST "/api/v1/projects/$PROJECT_ID/timeline/draft/publish" 201 "$(jq -nc \
  --arg draft "$DRAFT_UPDATED_AT" --arg requestId "$(cat /proc/sys/kernel/random/uuid)" \
  '{requestId:$requestId,expectedDraftUpdatedAt:$draft}')")"
[[ "$(jq -r '.lifecycleStatus' <<<"$PUBLISHED")" == "PUBLISHED" ]] || { echo "Timeline not published" >&2; exit 1; }
CURRENT_UPDATED_AT="$(jq -r '.updatedAt' <<<"$PUBLISHED")"

echo "7/7 revise and publish revision"
REVISION="$(request POST "/api/v1/projects/$PROJECT_ID/timeline/revisions" 201 "$(jq -nc \
  --arg requestId "$(cat /proc/sys/kernel/random/uuid)" \
  '{startDate:"2026-11-09",endDate:"2026-11-27",requestId:$requestId,revisionNotes:"Smoke revision"}')")"
REVISION_UPDATED_AT="$(jq -r '.updatedAt' <<<"$REVISION")"
REVISED="$(request POST "/api/v1/projects/$PROJECT_ID/timeline/draft/publish" 201 "$(jq -nc \
  --arg draft "$REVISION_UPDATED_AT" --arg current "$CURRENT_UPDATED_AT" --arg requestId "$(cat /proc/sys/kernel/random/uuid)" \
  '{requestId:$requestId,expectedDraftUpdatedAt:$draft,expectedCurrentUpdatedAt:$current}')")"
[[ "$(jq -r '.versionNumber' <<<"$REVISED")" == "2" ]] || { echo "Revision not published" >&2; exit 1; }

echo "Smoke passed: project=$PROJECT_ID task=$(jq -r '.id' <<<"$TASK") timeline=$PROJECT_ID"
