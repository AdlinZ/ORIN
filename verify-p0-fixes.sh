#!/bin/bash

# P0问题修复验证脚本
# 用于验证所有P0级别的修复是否正确完成

echo "========================================="
echo "ORIN P0级别问题修复验证"
echo "========================================="
echo ""

PASS=0
FAIL=0

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查函数
check_file_exists() {
    if [ -f "$1" ]; then
        echo -e "${GREEN}✓${NC} $2"
        ((PASS++))
    else
        echo -e "${RED}✗${NC} $2"
        ((FAIL++))
    fi
}

check_file_not_exists() {
    if [ ! -f "$1" ]; then
        echo -e "${GREEN}✓${NC} $2"
        ((PASS++))
    else
        echo -e "${RED}✗${NC} $2"
        ((FAIL++))
    fi
}

check_content() {
    if grep -q "$2" "$1"; then
        echo -e "${GREEN}✓${NC} $3"
        ((PASS++))
    else
        echo -e "${RED}✗${NC} $3"
        ((FAIL++))
    fi
}

check_no_content() {
    if ! grep -q "$2" "$1"; then
        echo -e "${GREEN}✓${NC} $3"
        ((PASS++))
    else
        echo -e "${RED}✗${NC} $3"
        ((FAIL++))
    fi
}

echo "1. 检查环境变量配置文件"
echo "-----------------------------------"
check_file_exists "orin-backend/.env.example" ".env.example 文件存在"
check_file_not_exists "orin-backend/.env" ".env 文件不存在（需手动创建）"
echo ""

echo "2. 检查多环境配置文件"
echo "-----------------------------------"
check_file_exists "orin-backend/src/main/resources/application.properties" "主配置文件存在"
check_file_exists "orin-backend/src/main/resources/application-dev.properties" "开发环境配置存在"
check_file_exists "orin-backend/src/main/resources/application-prod.properties" "生产环境配置存在"
echo ""

echo "3. 检查敏感信息移除"
echo "-----------------------------------"
check_no_content "orin-backend/src/main/resources/application.properties" "password=password" "主配置文件无硬编码密码"
check_no_content "orin-backend/src/main/resources/application.properties" "spring.datasource.username=root" "主配置文件无硬编码用户名"
check_content "orin-backend/src/main/resources/application.properties" "spring.profiles.active" "主配置文件设置了默认profile"
echo ""

echo "4. 检查生产环境JPA配置"
echo "-----------------------------------"
check_content "orin-backend/src/main/resources/application-prod.properties" "ddl-auto=validate" "生产环境使用validate模式"
check_no_content "orin-backend/src/main/resources/application-prod.properties" "show-sql=true" "生产环境关闭SQL日志"
echo ""

echo "5. 检查开发环境配置"
echo "-----------------------------------"
check_content "orin-backend/src/main/resources/application-dev.properties" "ddl-auto=update" "开发环境使用update模式"
check_content "orin-backend/src/main/resources/application-dev.properties" "show-sql=true" "开发环境开启SQL日志"
echo ""

echo "6. 检查前端路由文件"
echo "-----------------------------------"
ROUTER_LINES=$(wc -l < orin-frontend/src/router/index.js)
if [ "$ROUTER_LINES" -lt 300 ]; then
    echo -e "${GREEN}✓${NC} 路由文件行数合理 ($ROUTER_LINES 行，已移除重复)"
    ((PASS++))
else
    echo -e "${RED}✗${NC} 路由文件可能仍有重复 ($ROUTER_LINES 行)"
    ((FAIL++))
fi
echo ""

echo "7. 检查文档文件"
echo "-----------------------------------"
check_file_exists "ENVIRONMENT_SETUP.md" "环境配置指南存在"
check_file_exists "P0_FIX_SUMMARY.md" "修复总结文档存在"
check_file_exists ".gitignore" ".gitignore 文件存在"
echo ""

echo "8. 检查.gitignore配置"
echo "-----------------------------------"
check_content ".gitignore" ".env" ".gitignore 包含 .env"
check_content ".gitignore" "!.env.example" ".gitignore 允许 .env.example"
check_content ".gitignore" "*.log" ".gitignore 忽略日志文件"
echo ""

echo "========================================="
echo "验证结果汇总"
echo "========================================="
echo -e "通过: ${GREEN}$PASS${NC}"
echo -e "失败: ${RED}$FAIL${NC}"
echo ""

if [ $FAIL -eq 0 ]; then
    echo -e "${GREEN}✓ 所有P0级别问题修复验证通过！${NC}"
    echo ""
    echo "下一步操作："
    echo "1. 复制 .env.example 为 .env 并填入实际配置"
    echo "   cd orin-backend && cp .env.example .env"
    echo ""
    echo "2. 生成安全的JWT密钥"
    echo "   openssl rand -base64 64"
    echo ""
    echo "3. 启动应用测试"
    echo "   mvn spring-boot:run"
    echo ""
    exit 0
else
    echo -e "${RED}✗ 发现 $FAIL 个问题，请检查修复${NC}"
    exit 1
fi
