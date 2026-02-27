#!/bin/bash
# ORIN 一键部署脚本 (低内存优化版)
# 适用于 1-2G 内存的服务器

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  ORIN 一键部署脚本 (低内存优化版)    ${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# 检查 root 权限
if [ "$EUID" -ne 0 ]; then
  echo -e "${RED}请使用 root 权限运行${NC}"
  exit 1
fi

# 内存检查
MEM_TOTAL=$(free -m | awk '/^Mem:/{print $2}')
if [ "$MEM_TOTAL" -lt 1500 ]; then
  echo -e "${YELLOW}警告: 内存不足 1.5G，部署可能失败${NC}"
  echo "建议: 使用 4G 内存服务器或添加 swap"
  read -p "是否继续? (y/n) " -n 1 -r
  echo
  if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    exit 1
  fi
fi

# 添加 swap（如果内存不足）
if [ "$MEM_TOTAL" -lt 4000 ] && [ ! -f /swapfile ]; then
  echo -e "${YELLOW}内存不足 4G，添加 swap 空间...${NC}"
  fallocate -l 2G /swapfile || dd if=/dev/zero of=/swapfile bs=1M count=2048
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
  echo -e "${GREEN}Swap 添加完成${NC}"
fi

# 设置工作目录
WORK_DIR="/opt/ORIN"
LOG_DIR="/var/log/orin"
UPLOAD_DIR="/var/orin/uploads"

# 安装依赖
echo -e "${YELLOW}[1/8] 安装基础依赖...${NC}"
apt-get update -qq
apt-get install -y -qq openjdk-17-jdk maven nodejs npm mysql-server redis-server nginx git curl sshpass

# 验证安装
echo -e "${GREEN}  Java: $(java -version 2>&1 | head -1)${NC}"
echo -e "${GREEN}  Maven: $(mvn -version 2>&1 | head -1)${NC}"
echo -e "${GREEN}  Node: $(node -v 2>/dev/null)${NC}"

# 创建目录
echo -e "${YELLOW}[2/8] 创建目录结构...${NC}"
mkdir -p $WORK_DIR $LOG_DIR $UPLOAD_DIR/{documents,multimodal}

# 克隆代码（如果不存在）
if [ ! -d "$WORK_DIR/.git" ]; then
  echo -e "${YELLOW}[3/8] 克隆代码...${NC}"
  git clone https://github.com/AdlinZ/ORIN.git $WORK_DIR
else
  echo -e "${YELLOW}[3/8] 更新代码...${NC}"
  cd $WORK_DIR && git pull
fi

cd $WORK_DIR

# 配置数据库
echo -e "${YELLOW}[4/8] 配置数据库...${NC}"
systemctl start mysql

# 生成随机密码
DB_PASS="Orin_$(openssl rand -base64 12 | tr -d '=+/' | cut -c1-16)!"
JWT_SECRET=$(openssl rand -base64 64)

# 获取 MySQL root 密码
if [ -f /etc/mysql/debian.cnf ]; then
  DB_MAINT_PASS=$(grep password /etc/mysql/debian.cnf | head -1 | awk '{print $3}')
  mysql -u debian-sys-maint -p"${DB_MAINT_PASS}" -e "
    CREATE DATABASE IF NOT EXISTS orindb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    DROP USER IF EXISTS 'orin'@'localhost';
    CREATE USER 'orin'@'localhost' IDENTIFIED BY '${DB_PASS}';
    GRANT ALL PRIVILEGES ON orindb.* TO 'orin'@'localhost';
    FLUSH PRIVILEGES;
  " 2>/dev/null || echo "Database may already exist"
fi

# 创建 .env 文件
cat > $WORK_DIR/.env << EOF
DB_HOST=localhost
DB_PORT=3306
DB_NAME=orindb
DB_USERNAME=orin
DB_PASSWORD=${DB_PASS}
JWT_SECRET=${JWT_SECRET}
REDIS_HOST=localhost
REDIS_PORT=6379
EOF

echo -e "${GREEN}  数据库密码: ${DB_PASS}${NC}"

# 启动 Redis
echo -e "${YELLOW}[5/8] 启动 Redis...${NC}"
systemctl start redis || redis-server --daemonize yes --port 6379

# 编译后端（低内存模式）
echo -e "${YELLOW}[6/8] 编译后端 (低内存模式)...${NC}"
cd $WORK_DIR/orin-backend
cp $WORK_DIR/.env .

export MAVEN_OPTS='-Xms128m -Xmx512m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m'
mvn clean package -DskipTests -q

if [ ! -f "target/orin-backend-1.0.0-SNAPSHOT.jar" ]; then
  echo -e "${RED}后端编译失败${NC}"
  exit 1
fi
echo -e "${GREEN}  后端编译成功${NC}"

# 启动后端
echo -e "${YELLOW}[7/8] 启动后端服务...${NC}"
pkill -f "orin-backend" 2>/dev/null || true
sleep 2

source $WORK_DIR/.env
nohup java -Xms128m -Xmx512m -jar target/orin-backend-1.0.0-SNAPSHOT.jar > $LOG_DIR/backend.log 2>&1 &
sleep 5

# 检查后端是否启动
if curl -s http://localhost:8080/actuator/health | grep -q "UP"; then
  echo -e "${GREEN}  后端启动成功${NC}"
else
  echo -e "${RED}后端启动失败，查看日志: $LOG_DIR/backend.log${NC}"
fi

# 配置 Nginx
echo -e "${YELLOW}[8/8] 配置 Nginx...${NC}"
cat > /etc/nginx/sites-available/orin << 'NGINX_EOF'
server {
    listen 80;
    server_name _;

    location / {
        proxy_pass http://localhost:5173;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_cache_bypass $http_upgrade;
    }

    location /api/ {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
NGINX_EOF

ln -sf /etc/nginx/sites-available/orin /etc/nginx/sites-enabled/
rm -f /etc/nginx/sites-enabled/default 2>/dev/null || true
nginx -t && systemctl reload nginx

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  ORIN 部署完成!                      ${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "  后端 API: http://<服务器IP>/api"
echo -e "  Health:   http://<服务器IP>/api/actuator/health"
echo ""
echo -e "${YELLOW}注意: 前端需要单独启动${NC}"
echo -e "  cd $WORK_DIR/orin-frontend"
echo -e "  npm run dev -- --host 0.0.0.0"
echo ""
echo -e "日志位置: $LOG_DIR/backend.log"
