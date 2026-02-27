#!/bin/bash

# ORIN Ubuntu 一键部署脚本
# 适用系统: Ubuntu 20.04 / 22.04+ (Clean Install)
# 作用: 自动安装 JDK, Maven, Node.js, Python, MySQL, Redis, Docker, Nginx 并配置服务

set -e # 出错即停止

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=========================================${NC}"
echo -e "${BLUE}   ORIN 系统一键部署工具 (Ubuntu版)       ${NC}"
echo -e "${BLUE}=========================================${NC}"

# 1. 检查权限
if [ "$EUID" -ne 0 ]; then
  echo -e "${RED}请使用 root 权限运行此脚本 (sudo ./deploy_ubuntu.sh)${NC}"
  exit 1
fi

# 获取当前非root用户名以设置权限
REAL_USER=${SUDO_USER:-$USER}
USER_HOME=$(eval echo ~$REAL_USER)
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo -e "${YELLOW}当前部署路径: $PROJECT_DIR${NC}"
echo -e "${YELLOW}运行用户: $REAL_USER${NC}"

# 2. 更新系统与基础工具
echo -e "${BLUE}[1/8] 更新系统软件包并安装基础工具...${NC}"
apt update && apt upgrade -y
apt install -y curl wget git build-essential software-properties-common lsof unzip jq openssl

# 3. 安装 Java 17 & Maven
echo -e "${BLUE}[2/8] 检查并安装 Java 17 & Maven...${NC}"
if ! command -v java &> /dev/null || [[ $(java -version 2>&1 | head -n 1) != *"17"* ]]; then
    apt install -y openjdk-17-jdk
else
    echo -e "${GREEN}✓ Java 17 已安装${NC}"
fi
apt install -y maven

# 4. 安装 Node.js (使用 NodeSource v18)
echo -e "${BLUE}[3/8] 安装 Node.js v18...${NC}"
if ! command -v node &> /dev/null; then
    curl -fsSL https://deb.nodesource.com/setup_18.x | bash -
    apt install -y nodejs
else
    echo -e "${GREEN}✓ Node.js $(node -v) 已安装${NC}"
fi

# 5. 安装 Python 3.10+ & Venv
echo -e "${BLUE}[4/8] 安装 Python 环境...${NC}"
apt install -y python3-pip python3-venv python3-dev

# 6. 安装 MySQL & Redis
echo -e "${BLUE}[5/8] 安装 MySQL & Redis 服务...${NC}"
apt install -y mysql-server redis-server
systemctl start mysql
systemctl enable mysql
systemctl start redis-server
systemctl enable redis-server

# 配置 MySQL
DB_NAME="orindb"
DB_USER="orin"
DB_PASS=$(openssl rand -base64 12)

echo -e "${YELLOW}正在配置数据库 (生成随机密码)...${NC}"
mysql -e "CREATE DATABASE IF NOT EXISTS $DB_NAME CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -e "CREATE USER IF NOT EXISTS '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASS';"
mysql -e "GRANT ALL PRIVILEGES ON $DB_NAME.* TO '$DB_USER'@'localhost';"
mysql -e "FLUSH PRIVILEGES;"

# 7. 安装 Docker & Docker Compose (用于 Milvus)
echo -e "${BLUE}[6/8] 检查 Docker 环境...${NC}"
if ! command -v docker &> /dev/null; then
    apt install -y docker.io
    systemctl start docker
    systemctl enable docker
    usermod -aG docker $REAL_USER
else
    echo -e "${GREEN}✓ Docker 已安装${NC}"
fi
if ! command -v docker-compose &> /dev/null; then
    apt install -y docker-compose
fi

# 8. 编译各个模块
echo -e "${BLUE}[7/8] 开始编译项目模块...${NC}"

# 后端配置与打包
echo -e "${YELLOW}编译后端 orin-backend...${NC}"
cd $PROJECT_DIR/orin-backend
if [ ! -f .env ]; then
    if [ -f .env.example ]; then
        cp .env.example .env
        sed -i "s/DB_NAME=.*/DB_NAME=$DB_NAME/" .env
        sed -i "s/DB_USERNAME=.*/DB_USERNAME=$DB_USER/" .env
        sed -i "s/DB_PASSWORD=.*/DB_PASSWORD=$DB_PASS/" .env
        sed -i "s/JWT_SECRET=.*/JWT_SECRET=$(openssl rand -base64 48)/" .env
    fi
fi
mvn clean package -DskipTests

# 前端安装与打包
echo -e "${YELLOW}编译前端 orin-frontend...${NC}"
cd $PROJECT_DIR/orin-frontend
npm install
npm run build

# AI 引擎虚拟环境准备
echo -e "${YELLOW}准备 AI 引擎 Python 环境...${NC}"
cd $PROJECT_DIR/orin-ai-engine
python3 -m venv venv
./venv/bin/pip install --upgrade pip
./venv/bin/pip install fastapi uvicorn pydantic pydantic-settings httpx jinja2 openai python-dotenv

# 9. 配置 Systemd 服务实现开机自启和自动维护
echo -e "${BLUE}[8/8] 配置 Systemd 系统服务...${NC}"

# 后端服务配置
cat > /etc/systemd/system/orin-backend.service <<EOF
[Unit]
Description=ORIN Backend Service
After=network.target mysql.service redis-server.service

[Service]
Type=simple
User=$REAL_USER
WorkingDirectory=$PROJECT_DIR/orin-backend
ExecStart=/usr/bin/java -jar $PROJECT_DIR/orin-backend/target/orin-backend-1.0.0-SNAPSHOT.jar
Restart=on-failure
RestartSec=10
StandardOutput=append:$PROJECT_DIR/backend.log
StandardError=append:$PROJECT_DIR/backend.log

[Install]
WantedBy=multi-user.target
EOF

# AI 引擎服务配置
cat > /etc/systemd/system/orin-ai-engine.service <<EOF
[Unit]
Description=ORIN AI Engine Service
After=network.target

[Service]
Type=simple
User=$REAL_USER
WorkingDirectory=$PROJECT_DIR/orin-ai-engine
ExecStart=$PROJECT_DIR/orin-ai-engine/venv/bin/uvicorn app.main:app --host 0.0.0.0 --port 8000
Restart=on-failure
RestartSec=10
StandardOutput=append:$PROJECT_DIR/ai_engine.log
StandardError=append:$PROJECT_DIR/ai_engine.log

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable orin-backend
systemctl enable orin-ai-engine

# 10. Nginx 前端反向代理配置
echo -e "${BLUE}配置 Nginx 前端代理...${NC}"
apt install -y nginx
cat > /etc/nginx/sites-available/orin <<EOF
server {
    listen 80;
    server_name localhost;

    # 前端静态文件
    location / {
        root $PROJECT_DIR/orin-frontend/dist;
        index index.html;
        try_files \$uri \$uri/ /index.html;
    }

    # 后端 API 转发
    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }
}
EOF
ln -sf /etc/nginx/sites-available/orin /etc/nginx/sites-enabled/
rm -f /etc/nginx/sites-enabled/default
systemctl restart nginx

# 11. 启动周边组件
echo -e "${YELLOW}启动 Milvus 向量引擎 (Docker Compose)...${NC}"
cd $PROJECT_DIR
if [ -f milvus-docker-compose.yml ]; then
    docker-compose -f milvus-docker-compose.yml up -d
fi

# 12. 启动核心服务
echo -e "${YELLOW}正在启动 ORIN 核心服务...${NC}"
systemctl start orin-backend
systemctl start orin-ai-engine

echo -e "${GREEN}=========================================${NC}"
echo -e "${GREEN}      部署成功！访问地址: http://服务器IP   ${NC}"
echo -e "${GREEN}      MySQL 用户: $DB_USER                ${NC}"
echo -e "${GREEN}      MySQL 密码: $DB_PASS                ${NC}"
echo -e "${GREEN}=========================================${NC}"
echo -e "${YELLOW}提示: 后端日志路径: $PROJECT_DIR/backend.log${NC}"
echo -e "${YELLOW}提示: AI引擎日志路径: $PROJECT_DIR/ai_engine.log${NC}"
