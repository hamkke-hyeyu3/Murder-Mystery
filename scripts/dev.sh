#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# preflight
for cmd in docker java npm; do
  command -v "$cmd" >/dev/null 2>&1 || { echo "ERROR: '$cmd' not found in PATH" >&2; exit 1; }
done

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
( cd "$ROOT/backend" && ./gradlew bootRun ) > >(sed 's/^/[BE] /') 2>&1 &
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
  echo "stopping backend and frontend..."
  kill "$BE_PID" "$FE_PID" 2>/dev/null || true
  wait 2>/dev/null || true
  echo "done. postgres container is still running. to stop it: docker compose down"
}
trap cleanup INT TERM EXIT

echo "[*] all services started. Ctrl+C to stop."
wait
