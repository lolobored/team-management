#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

SERVICE="${1:-}"

if [ -n "$SERVICE" ]; then
    echo "Rebuilding $SERVICE..."
    docker compose up --build -d "$SERVICE"
else
    echo "Rebuilding all services..."
    docker compose up --build -d
fi

echo "Done."
docker compose ps
