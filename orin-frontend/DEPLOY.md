# ORIN Monitor 部署指南

本项目采用前后端分离架构，前端基于 Vue 3 + Vite，后端基于 Spring Boot + JPA。

## 0. 快速管理 (一键开关)

在项目根目录下，我们提供了一个 `manage.sh` 脚本，可以方便地同时管理前后端：

- **一键启动**: `./manage.sh start`
- **一键停止**: `./manage.sh stop`
- **重启系统**: `./manage.sh restart`
- **查看状态**: `./manage.sh status`

---

## 1. 前端部署 (Vue 3 + Vite)

### 开发环境
1. 安装依赖：`npm install`
2. 运行项目：`npm run dev`
3. 访问地址：`http://localhost:5173`

### 生产环境
1. 编译打包：`npm run build`
2. 将生成的 `dist/` 目录内容上传至 Web 服务器（如 Nginx）。
3. Nginx 示例配置：
   ```nginx
   server {
       listen 80;
       server_name localhost;
       root /usr/share/nginx/html;
       index index.html;

       location / {
           try_files $uri $uri/ /index.html;
       }

       location /api {
           proxy_pass http://localhost:8080/api; # 转发到后端
           proxy_set_header Host $host;
       }
   }
   ```

## 2. 后端部署 (Spring Boot)

### 编译与打包
使用 Maven 进行打包：
```bash
mvn clean package -DskipTests
```

### 运行
运行生成的 jar 包：
```bash
java -jar target/orin-backend-0.0.1-SNAPSHOT.jar
```

## 3. 技术栈总结
- **前端**：Vue 3, Element Plus, Pinia, ECharts, Vitest
- **后端**：Java 17+, Spring Boot, JPA, H2 (In-memory)
