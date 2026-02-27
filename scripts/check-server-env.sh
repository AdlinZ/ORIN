#!/bin/bash
# ORIN 服务器环境检测脚本
# 在目标服务器上运行此脚本，将输出结果复制给 Systeara

echo "========================================"
echo "ORIN 部署环境检测"
echo "========================================"
echo ""

echo "【1. 操作系统】"
echo "Distribution: $(cat /etc/os-release 2>/dev/null | grep PRETTY_NAME | cut -d= -f2 | tr -d '\"' || echo 'Unknown')"
echo "Kernel: $(uname -r)"
echo "Architecture: $(uname -m)"
echo ""

echo "【2. 硬件资源】"
echo "CPU: $(nproc) cores"
echo "Memory: $(free -h 2>/dev/null | awk '/^Mem:/ {print $2}' || echo 'Unknown')"
echo "Disk: $(df -h / 2>/dev/null | awk 'NR==2 {print $4}' || echo 'Unknown') available"
echo ""

echo "【3. 已有软件环境】"
echo "--- Java ---"
java -version 2>&1 | head -3 || echo "Not installed"
echo ""

echo "--- Node.js ---"
node --version 2>/dev/null || echo "Not installed"
echo ""

echo "--- Python ---"
python3 --version 2>/dev/null || python --version 2>/dev/null || echo "Not installed"
echo ""

echo "--- Maven ---"
mvn --version 2>/dev/null | head -1 || echo "Not installed"
echo ""

echo "--- Docker ---"
docker --version 2>/dev/null || echo "Not installed"
docker-compose --version 2>/dev/null || docker compose version 2>/dev/null || echo "Docker Compose not installed"
echo ""

echo "--- MySQL ---"
mysql --version 2>/dev/null || echo "Not installed"
echo ""

echo "--- Redis ---"
redis-cli --version 2>/dev/null || echo "Not installed"
echo ""

echo "【4. 网络信息】"
echo "Public IP: $(curl -s ifconfig.me 2>/dev/null || echo 'Unknown')"
echo ""

echo "【5. 端口占用情况（关键端口）】"
for port in 80 443 3306 6379 8080 5173 19530; do
    if ss -tuln 2>/dev/null | grep -q ":$port "; then
        echo "Port $port: OCCUPIED"
    else
        echo "Port $port: FREE"
    fi
done
echo ""

echo "【6. 域名/证书信息】"
echo "Do you have a domain? (Y/N): ____"
echo "Do you have SSL certificate? (Y/N): ____"
echo ""

echo "========================================"
echo "检测完成，请将上述输出复制给我"
echo "========================================"
