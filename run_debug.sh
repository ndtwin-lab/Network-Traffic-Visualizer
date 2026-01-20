#!/bin/bash

# 设置 API URL 环境变量
export NDT_API_URL="http://localhost:8000"

# 运行程序并保存输出
echo "Starting NDTwin GUI with debug mode..."
echo "API URL: $NDT_API_URL"
echo "=========================================="
echo ""

# 自动切换到脚本所在目录
cd "$(dirname "$0")"
./mvnw javafx:run 2>&1 | tee debug_output.log

echo ""
echo "=========================================="
echo "Debug log saved to: debug_output.log"


