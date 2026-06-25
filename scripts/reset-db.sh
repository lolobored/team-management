#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

echo "This will delete all data. Are you sure? (y/N)"
read -r confirm
if [ "$confirm" != "y" ] && [ "$confirm" != "Y" ]; then
    echo "Cancelled."
    exit 0
fi

echo "Stopping services..."
docker compose down

echo "Removing database data..."
rm -rf "$(dirname "$0")/../volumes/pgdata"

echo "Database reset. Run ./scripts/deploy.sh or ./scripts/dev.sh to start fresh."
