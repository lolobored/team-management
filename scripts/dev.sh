#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

echo "Starting PostgreSQL for local development..."
docker compose up -d postgres

echo "Waiting for PostgreSQL..."
until docker compose exec postgres pg_isready -U tm -d teammanagement > /dev/null 2>&1; do
    sleep 1
done
echo "PostgreSQL is ready."

echo ""
echo "Start backend:  cd backend && ./gradlew bootRun"
echo "Start frontend: cd frontend && npm run dev"
echo ""
echo "Backend at:     http://localhost:8080/api"
echo "Frontend at:    http://localhost:5173"
