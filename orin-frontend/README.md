# ORIN Frontend

ORIN 智能体管理平台的 Vue 3 管理台。

## 技术栈

- Vue 3（Composition API · `<script setup>`）
- Vite
- Element Plus
- Pinia
- Vue Router

## 开发

```bash
npm install
npm run dev          # http://localhost:5173
```

## 构建

```bash
npm run build        # 输出 dist/
npm run preview      # 本地预览构建产物
```

## 测试

```bash
npm run test:unit    # Vitest 单元测试
```

## 目录结构

```text
src/
├── api/              # 后端接口封装
├── components/       # 通用组件
├── views/            # 页面
├── router/           # 路由
│   ├── routes.js          # 路由常量
│   ├── index.js           # 实际挂载
│   └── topMenuConfig.js   # 顶部菜单与状态标记
├── stores/           # Pinia
├── utils/
└── styles/
```

## 后端约定

- 后端基址默认 `http://localhost:8080`（Vite proxy 配置见 `vite.config.js`）
- 鉴权：JWT 走业务接口 `/api/v1/*`，API Key 走统一网关 `/v1/*`
- 详细接口分组见 [../docs/API文档.md](../docs/API文档.md)

## 进一步阅读

- [docs/](./docs/) — 前端专项文档
- [../docs/使用指南.md](../docs/使用指南.md) — 前端导航与功能入口
- [../docs/开发规范.md](../docs/开发规范.md) — 编码与提交规范
