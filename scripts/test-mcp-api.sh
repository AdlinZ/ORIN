#!/bin/bash

# MCP 接口测试脚本
# 用于测试 Skill-Hub 的 MCP 标准接口

BASE_URL="http://localhost:8080"

echo "========================================="
echo "MCP 接口测试脚本"
echo "========================================="
echo ""

# 1. 测试 MCP 服务器信息
echo "1. 测试 GET /mcp/info"
echo "-----------------------------------------"
curl -X GET "${BASE_URL}/mcp/info" \
  -H "Content-Type: application/json" | jq .
echo ""
echo ""

# 2. 测试获取所有工具
echo "2. 测试 GET /mcp/tools"
echo "-----------------------------------------"
curl -X GET "${BASE_URL}/mcp/tools" \
  -H "Content-Type: application/json" | jq .
echo ""
echo ""

# 3. 测试获取单个工具详情 (假设有 ID 为 1 的技能)
echo "3. 测试 GET /mcp/tools/1"
echo "-----------------------------------------"
curl -X GET "${BASE_URL}/mcp/tools/1" \
  -H "Content-Type: application/json" | jq .
echo ""
echo ""

# 4. 测试执行工具
echo "4. 测试 POST /mcp/tools/1/execute"
echo "-----------------------------------------"
curl -X POST "${BASE_URL}/mcp/tools/1/execute" \
  -H "Content-Type: application/json" \
  -d '{
    "query": "test query",
    "param1": "value1"
  }' | jq .
echo ""
echo ""

# 5. 测试获取资源
echo "5. 测试 GET /mcp/resources"
echo "-----------------------------------------"
curl -X GET "${BASE_URL}/mcp/resources" \
  -H "Content-Type: application/json" | jq .
echo ""
echo ""

# 6. 测试创建技能 (通过标准 API)
echo "6. 测试创建 API 技能"
echo "-----------------------------------------"
curl -X POST "${BASE_URL}/api/skills" \
  -H "Content-Type: application/json" \
  -d '{
    "skillName": "WeatherAPI",
    "skillType": "API",
    "description": "获取天气信息",
    "apiEndpoint": "https://api.weatherapi.com/v1/current.json",
    "apiMethod": "GET",
    "inputSchema": {
      "type": "object",
      "properties": {
        "location": {"type": "string"}
      }
    },
    "outputSchema": {
      "type": "object",
      "properties": {
        "temperature": {"type": "number"},
        "condition": {"type": "string"}
      }
    }
  }' | jq .
echo ""
echo ""

# 7. 再次获取所有工具,验证新创建的技能
echo "7. 验证新技能已注册到 MCP"
echo "-----------------------------------------"
curl -X GET "${BASE_URL}/mcp/tools" \
  -H "Content-Type: application/json" | jq '.tools[] | select(.name == "WeatherAPI")'
echo ""
echo ""

echo "========================================="
echo "测试完成!"
echo "========================================="
