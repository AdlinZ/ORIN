# ORIN 多智能体协作系统 - 迁移与发布计划

## 一、发布概述

### 1.1 发布目标
- 将多智能体协作功能从开发环境迁移到生产环境
- 确保功能完整性和系统稳定性

### 1.2 发布范围
- 后端协作编排服务
- AI 引擎协作执行器
- 前端协作控制台
- OpenTelemetry 链路追踪
- Redis 共享记忆

### 1.3 发布策略
- **灰度发布**: 先内部租户，后全量租户
- **双写策略**: 新旧系统并行运行，逐步切换
- **回滚预案**: 快速回退到发布前状态

---

## 二、发布前检查

### 2.1 环境检查
```bash
# 1. 检查 MySQL 版本 (>= 8.0)
mysql --version

# 2. 检查 Redis 版本 (>= 6.0)
redis-cli --version

# 3. 检查 Java 版本 (>= 17)
java --version

# 4. 检查 Python 版本 (>= 3.10)
python3 --version
```

### 2.2 依赖检查
```bash
# 后端依赖
cd orin-backend
mvn dependency:tree | grep -E "opentelemetry|jaeger"

# 前端依赖
cd orin-frontend
npm list | grep -E "element-plus|vue"

# AI 引擎依赖
cd orin-ai-engine
pip list | grep -E "opentelemetry|asyncio"
```

### 2.3 配置检查
```properties
# application.properties 关键配置
otel.exporter.jaeger.enabled=true
otel.exporter.jaeger.endpoint=http://jaeger:14250
spring.data.redis.host=redis
spring.data.redis.port=6379
```

---

## 三、数据库迁移

### 3.1 执行 Flyway 迁移
```bash
# 后端会自动执行 V50__Collab_Package_Schema.sql
cd orin-backend
mvn flyway:migrate
```

### 3.2 验证表结构
```sql
-- 验证表是否存在
SHOW TABLES LIKE 'collab_%';

-- 验证索引
SHOW INDEX FROM collab_package;
SHOW INDEX FROM collab_subtask;
SHOW INDEX FROM collab_event_log;
```

---

## 四、发布步骤

### 4.1 阶段 1: 部署后端服务
```bash
# 1. 打包
cd orin-backend
mvn clean package -DskipTests

# 2. 停止旧服务
systemctl stop orin-backend

# 3. 启动新服务
java -jar target/orin-backend-1.0.0.jar --spring.profiles.active=prod

# 4. 验证服务健康
curl http://localhost:8080/actuator/health
```

### 4.2 阶段 2: 部署 AI 引擎
```bash
# 1. 安装依赖
cd orin-ai-engine
pip install -r requirements.txt

# 2. 启动引擎
uvicorn app.main:app --host 0.0.0.0 --port 8000

# 3. 验证服务健康
curl http://localhost:8000/health
```

### 4.3 阶段 3: 部署前端
```bash
# 1. 构建
cd orin-frontend
npm run build

# 2. 部署 (假设使用 Nginx)
cp -r dist/* /var/www/html/

# 3. 重载 Nginx
nginx -s reload
```

### 4.4 阶段 4: 验证功能
```bash
# 1. 测试协作 API
curl -X POST http://localhost:8080/api/v1/collaboration/packages \
  -H "Content-Type: application/json" \
  -d '{"intent": "测试任务", "category": "GENERATION"}'

# 2. 测试工作流执行
curl -X POST http://localhost:8000/api/v1/workflow/execute \
  -H "Content-Type: application/json" \
  -d '{"dsl": {...}}'

# 3. 测试前端页面
curl http://localhost/agent/collaboration/dashboard
```

---

## 五、灰度发布策略

### 5.1 内部租户试点
```yaml
# 内部租户配置
trial_tenants:
  - tenant_id: "internal_001"
    enabled: true
  - tenant_id: "internal_002"
    enabled: true
```

### 5.2 监控指标
- 协作任务成功率 >= 95%
- 平均延迟 <= 5s
- 错误率 <= 1%

### 5.3 全量启用
```bash
# 确认试点成功后，执行全量切换
kubectl rollout restart deployment/orin-backend
kubectl rollout restart deployment/orin-ai-engine
```

---

## 六、回滚预案

### 6.1 回滚触发条件
- 错误率 > 5%
- 平均延迟 > 30s
- 核心功能不可用

### 6.2 回滚命令
```bash
# 1. 回滚后端
git checkout HEAD~1
mvn clean package -DskipTests
java -jar target/orin-backend-1.0.0.jar

# 2. 回滚前端
git checkout HEAD~1
npm run build
cp -r dist/* /var/www/html/

# 3. 回滚 AI 引擎
git checkout HEAD~1
pip install -r requirements.txt
```

### 6.3 数据回滚
```sql
-- 保留协作数据（不删除）
-- 仅回滚配置和代码
```

---

## 七、验证清单

### 7.1 功能验证
- [ ] 创建协作任务包
- [ ] 任务分解生成子任务
- [ ] 子任务状态更新
- [ ] 事件发布与订阅
- [ ] Redis 黑板读写
- [ ] 前端协作仪表盘展示

### 7.2 性能验证
- [ ] 单任务延迟 < 5s
- [ ] 并发 10 任务正常
- [ ] Redis 响应 < 100ms

### 7.3 可观测性验证
- [ ] Jaeger 链路追踪可用
- [ ] 日志包含 traceId
- [ ] 指标正常采集

---

## 八、发布后监控

### 8.1 关键指标
```prometheus
# 协作任务指标
collaboration_tasks_total{status="completed"}
collaboration_tasks_total{status="failed"}

# Agent 指标
collaboration_llm_tokens{agent_id="xxx"}
collaboration_llm_latency{agent_id="xxx"}

# 系统指标
system_cpu_usage
system_memory_usage
```

### 8.2 告警配置
- 错误率 > 3% → 告警
- 延迟 > 10s → 告警
- 内存 > 80% → 告警