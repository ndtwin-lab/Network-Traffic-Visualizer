#!/bin/bash

# Set API URL environment variable (override by exporting NDT_API_URL before running)
export NDT_API_URL="${NDT_API_URL:-http://localhost:8000}"

echo "Starting Network Traffic Visualizer..."
echo "API URL: $NDT_API_URL"
echo "=========================================="
echo ""

# Switch to script directory
cd "$(dirname "$0")"

./mvnw javafx:run
