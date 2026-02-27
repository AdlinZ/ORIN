#!/bin/bash
# ORIN 部署脚本
# 在服务器上执行此脚本完成部署

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  ORIN 一键部署脚本${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# 检查 root 权限
if [ "$EUID" -ne 0 ]; then 
    echo -e "${RED}请使用 sudo 运行此脚本${NC}"
    exit 1
fi

# 检查必要软件
echo -e "${YELLOW}[1/8] 检查依赖软件...${NC}"

if ! command -v docker &> /dev/null; then
    echo -e "${YELLOW}安装 Docker...${NC}"
    curl -fsSL https://get.docker.com | sh
    systemctl enable docker
    systemctl start docker
fi

if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    echo -e "${YELLOW}安装 Docker Compose...${NC}"
    curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
    chmod +x /usr/local/bin/docker-compose
fi

echo -e "${GREEN}✓ 依赖检查完成${NC}"

# 创建目录结构
echo -e "${YELLOW}[2/8] 创建目录结构...${NC}"
mkdir -p volumes/{mysql/{data,conf.d},redis/data,backend/uploads,nginx/{ssl,logs}}
chmod -R 755 volumes/
echo -e "${GREEN}✓ 目录创建完成${NC}"

# 生成环境变量
echo -e "${YELLOW}[3/8] 配置环境变量...${NC}"

JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')
DB_PASSWORD=$(openssl rand -base64 24 | tr -d '\n' | cut -c1-16)
DB_ROOT_PASSWORD=$(openssl rand -base64 24 | tr -d '\n' | cut -c1-16)

cat > .env << EOF
# ORIN 环境变量配置
# 生成时间: $(date)

# 数据库配置
DB_ROOT_PASSWORD=${DB_ROOT_PASSWORD}
DB_PASSWORD=${DB_PASSWORD}
DB_HOST=mysql
DB_PORT=3306
DB_NAME=orindb

# Redis 配置
REDIS_HOST=redis
REDIS_PORT=6379

# JWT 密钥 (重要：请保存好，丢失后用户需要重新登录)
JWT_SECRET=${JWT_SECRET}

# 向量数据库配置 (暂时使用 Mock 模式，可后续修改为真实 Milvus)
MILVUS_HOST=mock
MILVUS_PORT=19530
MILVUS_TOKEN=

# API 密钥配置 (如使用 SiliconFlow 等第三方服务)
SILICONFLOW_API_KEY=
OPENAI_API_KEY=

# 服务器端口
SERVER_PORT=8080
EOF

echo -e "${GREEN}✓ 环境变量配置完成${NC}"
echo -e "${YELLOW}注意: 环境变量已保存到 .env 文件，请妥善保管${NC}"

# 配置优化文件
echo -e "${YELLOW}[4/8] 部署优化配置...${NC}"
cp scripts/mysql-low-memory.cnf volumes/mysql/conf.d/
cp scripts/redis-low-memory.conf volumes/redis/
echo -e "${GREEN}✓ 优化配置完成${NC}"

# 构建前端
echo -e "${YELLOW}[5/8] 构建前端项目...${NC}"
cd orin-frontend

if [ ! -d "node_modules" ]; then
    echo "安装前端依赖..."
    npm install
fi

echo "构建生产版本..."
npm run build

if [ ! -d "dist" ]; then
    echo -e "${RED}前端构建失败，请检查错误${NC}"
    exit 1
fi

cd ..
echo -e "${GREEN}✓ 前端构建完成${NC}"

# 启动服务
echo -e "${YELLOW}[6/8] 启动 Docker 服务...${NC}"
docker-compose down 2>/dev/null || true
docker-compose pull
docker-compose build --no-cache
docker-compose up -d

echo -e "${GREEN}✓ 服务已启动${NC}"

# 等待服务就绪
echo -e "${YELLOW}[7/8] 等待服务就绪...${NC}"
echo "等待 MySQL 启动..."
sleep 15

echo "检查服务状态..."
docker-compose ps

echo -e "${GREEN}✓ 服务状态检查完成${NC}"

# 显示访问信息
echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  部署完成！${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "访问地址: ${YELLOW}http://47.108.232.120${NC}"
echo -e "域名访问: ${YELLOW}http://orin.asia${NC}"
echo ""
echo -e "默认账号:"
echo -e "  用户名: ${YELLOW}admin${NC}"
echo -e "  密码: ${YELLOW}admin123${NC} (请在系统设置中立即修改)"
echo ""
echo -e "环境变量文件: ${YELLOW}.env${NC}"
echo -e "数据备份目录: ${YELLOW}volumes/${NC}"
echo ""
echo -e "常用命令:"
echo -e "  查看日志: ${YELLOW}docker-compose logs -f${NC}"
echo -e "  重启服务: ${YELLOW}docker-compose restart${NC}"
echo -e "  停止服务: ${YELLOW}docker-compose down${NC}"
echo ""
echo -e "${YELLOW}提示: 如果需要启用 HTTPS，请运行:${NC}"
echo -e "  ${YELLOW}./scripts/setup-ssl.sh${NC}"
echo ""
echo -e "${GREEN}========================================${NC}"
