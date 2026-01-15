# ORIN 前端样式统一修复计划

## 问题分析

### 1. CSS变量命名不一致
- **错误用法**: `--border-radius-xl`, `--border-radius-lg`, `--border-radius-base`
- **正确用法**: `--radius-xl`, `--radius-lg`, `--radius-base`

### 2. 硬编码圆角值
发现的硬编码值及其应该使用的变量：
- `2px` → `var(--radius-xs)` (4px)
- `4px` → `var(--radius-xs)` (4px)
- `6px` → `var(--radius-sm)` (6px)
- `8px` → `var(--radius-base)` (8px)
- `10px` → `var(--radius-lg)` (12px) 或自定义
- `12px` → `var(--radius-lg)` (12px)
- `14px` → `var(--radius-xl)` (16px) 或自定义
- `16px` → `var(--radius-xl)` (16px)
- `20px` → `var(--radius-2xl)` (24px) 或自定义
- `24px` → `var(--radius-2xl)` (24px)
- `50%` → `var(--radius-full)` (9999px)

### 3. 标准圆角规范

根据main.css中定义的设计系统：
```css
--radius-xs: 4px;    /* 小元素：标签、徽章 */
--radius-sm: 6px;    /* 按钮、输入框 */
--radius-base: 8px;  /* 卡片、表格 */
--radius-lg: 12px;   /* 大卡片、对话框 */
--radius-xl: 16px;   /* 模态框、抽屉 */
--radius-2xl: 24px;  /* 特殊装饰性元素 */
--radius-full: 9999px; /* 圆形元素 */
```

## 修复策略

### 阶段1：修复CSS变量名错误
需要修复的文件：
- Profile.vue (309, 393行)
- FileList.vue (86, 90行)
- ModelTrain.vue (174行)
- MonitorDashboard.vue (414, 465, 470, 489行)
- Checkpoints.vue (62行)

### 阶段2：替换硬编码值
需要修复的文件（按优先级）：
1. **高优先级** - 主要页面：
   - Home.vue
   - MonitorDashboard.vue
   - Profile.vue
   - Login.vue

2. **中优先级** - 功能页面：
   - AgentList.vue
   - KBList.vue
   - ChatLogs.vue
   - ModelList.vue

3. **低优先级** - 其他页面：
   - AgentOnboarding.vue
   - AuditLogs.vue
   - ModelTrain.vue
   - FileList.vue
   - Checkpoints.vue
   - AlertManagement.vue

## 修复原则

1. **保持视觉一致性**：相同类型的组件使用相同的圆角
2. **使用设计令牌**：优先使用CSS变量而非硬编码
3. **语义化选择**：根据元素大小和重要性选择合适的圆角级别
4. **向下兼容**：确保修改不破坏现有布局

## 组件圆角映射

| 组件类型 | 推荐圆角 | CSS变量 |
|---------|---------|---------|
| 小标签、徽章 | 4px | `var(--radius-xs)` |
| 按钮、输入框 | 8px | `var(--radius-base)` |
| 普通卡片 | 12px | `var(--radius-lg)` |
| 大卡片、对话框 | 16px | `var(--radius-xl)` |
| 模态框 | 16px | `var(--radius-xl)` |
| 头像、圆形按钮 | 50% | `var(--radius-full)` |
| 特殊装饰 | 24px | `var(--radius-2xl)` |
