#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

echo "Stopping all services..."
docker compose down
echo "All services stopped."
