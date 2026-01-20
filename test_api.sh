#!/bin/bash

# 测试 API 连接
API_URL="${NDT_API_URL:-http://localhost:8000}"

echo "Testing API at: $API_URL"
echo ""
echo "1. Testing /ndt/get_graph_data..."
curl -s "$API_URL/ndt/get_graph_data" | head -c 200
echo ""
echo ""

echo "2. Testing /ndt/get_detected_flow_data..."
curl -s "$API_URL/ndt/get_detected_flow_data" | head -c 200
echo ""
echo ""

echo "3. Checking API response structure..."
curl -s "$API_URL/ndt/get_graph_data" | python3 -c "
import json
import sys
try:
    data = json.load(sys.stdin)
    print(f'Graph data received:')
    print(f'  - nodes: {len(data.get(\"nodes\", []))}')
    print(f'  - edges: {len(data.get(\"edges\", []))}')
    if data.get('nodes'):
        node = data['nodes'][0]
        print(f'  - First node: {node}')
except Exception as e:
    print(f'Error: {e}')
" 2>/dev/null || echo "Python3 not available for JSON parsing"

echo ""
echo "Done!"


