# 回滚 Checklist

> I2.3: 补一份回滚 checklist

---

## 回滚触发条件

- 核心功能 (P0) 验证失败
- 服务启动失败
- 数据库迁移失败
- 重大 Bug 影响业务

---

## 回滚步骤

### 1. 服务回滚

```bash
# Docker 部署
docker-compose down
docker-compose up -d orin-backend:上一版本

# 或使用镜像回滚
docker tag orin-backend:v上一版本 orin-backend:latest
docker-compose restart
```

### 2. 数据库回滚

```bash
# Flyway 回滚 (如支持)
flyway:undo

# 手动回滚 SQL
mysql -u orin -p orin_db < rollback_v1.0.0.sql
```

### 3. 前端回滚

```bash
# 回滚 CDN/静态资源
# 或重新部署上一版本
npm run build:prev-version
```

---

## 回滚验证

- [ ] 服务正常启动
- [ ] 登录功能正常
- [ ] 核心 API 正常
- [ ] 数据库数据完整

---

## 回滚后行动

1. 记录回滚原因
2. 修复问题
3. 重新测试
4. 重新发布

---

*最后更新: 2026-03-28*