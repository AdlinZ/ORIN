# ORIN Frontend 专项文档

## 一、路由与菜单

前端路由分三个文件：

| 文件 | 职责 |
|------|------|
| `src/router/routes.js` | 所有路由常量（含历史兼容路径） |
| `src/router/index.js` | 实际路由实例与守卫 |
| `src/router/topMenuConfig.js` | 顶部菜单暴露策略与状态标记 |

### 路由原则

- 路由常量存在 ≠ 能力交付，部分路径仅作历史兼容（如 `/dashboard/knowledge/center` → `/dashboard/resources/center`）
- `topMenuConfig.js` 中带 `status: 'placeholder'` 的菜单项是占位入口
- 同一能力存在多个路径别名时，必须在 `topMenuConfig.js` 标注主入口，避免误判"多个独立能力"

### 排查命令

```bash
sed -n '1,260p' src/router/routes.js
sed -n '1,260p' src/router/topMenuConfig.js
rg "status:\s*'placeholder'|开发中|ElMessage\.(info|warning)" src
```

## 二、组件规范

- 组合式 API（`<script setup>`），禁用 Options API 写新代码
- Props / Emits 必须声明类型
- 跨页状态走 Pinia，单页内组件通信用 props/emit
- API 请求统一通过 `src/api/*`，禁止组件内直接调 `axios`

### 表格

业务表格使用项目内的 `ResizableTable` 包装组件（位于 `src/components/`），支持列宽拖拽与持久化。新增表格时优先复用，避免直接用裸 `el-table`。

## 三、调试

```bash
# 启动
npm run dev

# 单元测试
npm run test:unit

# 构建产物本地预览
npm run build && npm run preview
```

浏览器调试：

- Vue Devtools 安装后可看到 Pinia store
- Network 面板观察 `X-Trace-Id` 响应头，便于在后端 Trace 页面联查

## 四、发布约定

发布前自检：

- [ ] `npm run build` 通过，无 TypeScript / 类型告警
- [ ] `npm run test:unit` 通过
- [ ] 关键路径手工烟测：登录 / 智能体列表 / 工作流可视化首屏 / 监控大屏
- [ ] 新增页面已在 `topMenuConfig.js` 注册并标注状态
- [ ] 历史路由别名未误删（参见 `src/router/routes.js` 中重定向项）

## 五、后端约定

- 后端基址通过 Vite proxy 转发到 `:8080`
- 接口前缀与鉴权方式见 [../../docs/API文档.md](../../docs/API文档.md)
- 涉及工作流 / 协作的能力，必须同时核对 Java 控制器与 Python 执行器，详见 [../../docs/架构设计.md](../../docs/架构设计.md)
