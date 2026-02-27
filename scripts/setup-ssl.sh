#!/bin/bash
# SSL 证书自动申请脚本 (Let's Encrypt)

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

DOMAIN="orin.asia"
EMAIL="admin@orin.asia"  # 修改为你的邮箱

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Let's Encrypt SSL 证书申请${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# 检查域名解析
echo -e "${YELLOW}检查域名解析...${NC}"
SERVER_IP=$(curl -s ifconfig.me)
DOMAIN_IP=$(dig +short $DOMAIN 2>/dev/null || nslookup $DOMAIN 2>/dev/null | tail -2 | head -1 | awk '{print $2}')

if [ "$SERVER_IP" != "$DOMAIN_IP" ]; then
    echo -e "${RED}警告: 域名 $DOMAIN 解析 IP ($DOMAIN_IP) 与服务器 IP ($SERVER_IP) 不一致${NC}"
    echo -e "${RED}请先将域名解析到本服务器后再运行此脚本${NC}"
    exit 1
fi

echo -e "${GREEN}✓ 域名解析正确${NC}"

# 安装 certbot
echo -e "${YELLOW}安装 certbot...${NC}"
apt-get update
apt-get install -y certbot

# 申请证书
echo -e "${YELLOW}申请 SSL 证书...${NC}"
certbot certonly --standalone -d $DOMAIN -d www.$DOMAIN --agree-tos --email $EMAIL --non-interactive

# 复制证书到 nginx 目录
echo -e "${YELLOW}配置证书...${NC}"
cp /etc/letsencrypt/live/$DOMAIN/fullchain.pem volumes/nginx/ssl/orin.asia.crt
cp /etc/letsencrypt/live/$DOMAIN/privkey.pem volumes/nginx/ssl/orin.asia.key

# 更新 nginx 配置启用 HTTPS
echo -e "${YELLOW}更新 Nginx 配置...${NC}"
cat > docker/orin-ssl.conf << 'EOF'
upstream backend {
    server backend:8080;
    keepalive 32;
}

upstream ai-engine {
    server ai-engine:8000;
    keepalive 32;
}

# HTTP 重定向到 HTTPS
server {
    listen 80;
    server_name orin.asia www.orin.asia;
    return 301 https://$server_name$request_uri;
}

# HTTPS 配置
server {
    listen 443 ssl http2;
    server_name orin.asia www.orin.asia;

    # SSL 证书
    ssl_certificate /etc/nginx/ssl/orin.asia.crt;
    ssl_certificate_key /etc/nginx/ssl/orin.asia.key;

    # SSL 优化
    ssl_session_timeout 1d;
    ssl_session_cache shared:SSL:50m;
    ssl_session_tickets off;

    # 现代 TLS 配置
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384;
    ssl_prefer_server_ciphers off;

    # HSTS
    add_header Strict-Transport-Security "max-age=63072000" always;

    # 日志
    access_log /var/log/nginx/orin-access.log;
    error_log /var/log/nginx/orin-error.log;

    # 前端静态文件
    location / {
        root /usr/share/nginx/html;
        index index.html;
        try_files $uri $uri/ /index.html;
        
        location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
            expires 30d;
            add_header Cache-Control "public, immutable";
        }
    }

    # API 代理
    location /api/ {
        proxy_pass http://backend;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_cache_bypass $http_upgrade;
        proxy_read_timeout 300s;
        proxy_connect_timeout 75s;
    }

    # AI Engine API
    location /ai-api/ {
        proxy_pass http://ai-engine/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_read_timeout 300s;
    }

    # 健康检查
    location /actuator/health {
        proxy_pass http://backend/actuator/health;
        access_log off;
    }
}
EOF

# 替换配置
cp docker/orin-ssl.conf docker/orin.conf

# 重启 nginx
docker-compose restart nginx

# 设置自动续期
echo -e "${YELLOW}设置自动续期...${NC}"
echo "0 0 * * * root certbot renew --quiet && docker-compose -f /root/orin/docker-compose.yml restart nginx" > /etc/cron.d/orin-ssl-renew

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  SSL 证书配置完成！${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "HTTPS 访问: ${YELLOW}https://orin.asia${NC}"
echo -e "证书有效期: ${YELLOW}90天${NC} (自动续期)"
echo ""
echo -e "${GREEN}========================================${NC}"
