#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

echo "Building and deploying all services..."
docker compose up --build -d

echo ""
echo "Waiting for services to be healthy..."
docker compose ps

echo ""
echo "Application available at: http://localhost:3000"
echo "Backend API at:           http://localhost:8080/api"
echo "PostgreSQL at:            localhost:5432"
