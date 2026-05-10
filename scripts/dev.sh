#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# preflight
for cmd in docker java npm; do
  command -v "$cmd" >/dev/null 2>&1 || { echo "ERROR: '$cmd' not found in PATH" >&2; exit 1; }
done

if lsof -ti:8080 >/dev/null 2>&1; then
  echo "ERROR: port 8080 already in use (PID: $(lsof -ti:8080 | tr '\n' ' '))" >&2
  echo "       Run: kill \$(lsof -ti:8080)" >&2
  exit 1
fi

kill_tree() {
  local sig=${2:-TERM}
  for child in $(pgrep -P "$1" 2>/dev/null); do
    kill_tree "$child" "$sig"
  done
  kill "-$sig" "$1" 2>/dev/null || true
}

wait_for_exit() {
  local pid=$1 timeout=${2:-15}
  for i in $(seq 1 "$timeout"); do
    kill -0 "$pid" 2>/dev/null || return 0
    sleep 1
  done
  return 1
}

# db
echo "[DB] starting postgres..."
docker compose up -d postgres

echo "[DB] waiting for ready..."
for i in $(seq 1 30); do
  if docker compose exec -T postgres \
       pg_isready -U murdermystery -d murdermystery >/dev/null 2>&1; then
    echo "[DB] ready"
    break
  fi
  if [ "$i" = "30" ]; then
    echo "[DB] ERROR: postgres did not become ready within 30 seconds" >&2
    exit 1
  fi
  sleep 1
done

# backend
( cd "$ROOT/backend" && ./gradlew bootRun --args='--spring.profiles.active=dev' ) > >(sed 's/^/[BE] /') 2>&1 &
BE_PID=$!

# frontend
if [ ! -d "$ROOT/frontend/node_modules" ]; then
  echo "[FE] node_modules not found, running npm install..."
  ( cd "$ROOT/frontend" && npm install )
fi
( cd "$ROOT/frontend" && npm run dev ) > >(sed 's/^/[FE] /') 2>&1 &
FE_PID=$!

cleanup() {
  trap - INT TERM EXIT
  echo ""

  echo "[FE] stopping..."
  kill_tree "$FE_PID"
  wait_for_exit "$FE_PID" 5 || kill_tree "$FE_PID" KILL

  echo "[BE] stopping..."
  kill_tree "$BE_PID"
  wait_for_exit "$BE_PID" 10 || {
    echo "[BE] timeout — force killing"
    kill_tree "$BE_PID" KILL
  }

  echo "[DB] stopping..."
  docker compose down --volumes

  wait 2>/dev/null || true
  echo "done."
}
trap cleanup INT TERM EXIT

echo "[*] all services started. Ctrl+C to stop."
wait
