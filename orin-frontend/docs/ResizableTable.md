# ResizableTable 组件使用指南

## 概述

`ResizableTable` 是一个增强版的 Element Plus 表格组件，提供了统一的样式和可拖动调整列宽的功能。

## 特性

✨ **统一样式** - 所有表格使用一致的视觉风格
🎯 **列宽调整** - 支持拖动表头边缘调整列宽
💾 **持久化** - 列宽调整会保存到 localStorage
🎨 **美观设计** - 现代化的表格设计，包含悬停效果和条纹样式
⚡ **高性能** - 优化的渲染和交互性能

## 基本用法

### 1. 导入组件

```vue
<script setup>
import ResizableTable from '@/components/ResizableTable.vue';
</script>
```

### 2. 替换 el-table

**之前:**
```vue
<el-table :data="tableData" style="width: 100%" stripe>
  <el-table-column prop="name" label="名称" width="180" />
  <el-table-column prop="date" label="日期" width="180" />
</el-table>
```

**之后:**
```vue
<ResizableTable :data="tableData">
  <el-table-column prop="name" label="名称" width="180" />
  <el-table-column prop="date" label="日期" width="180" />
</ResizableTable>
```

## 完整示例

```vue
<template>
  <div class="page-container">
    <el-card shadow="never" class="table-card">
      <ResizableTable 
        v-loading="loading" 
        :data="tableData"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" />
        <el-table-column type="index" label="序号" width="80" align="center" />
        
        <el-table-column prop="name" label="名称" min-width="200">
          <template #default="{ row }">
            <div class="name-cell">
              <el-icon><User /></el-icon>
              <span>{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        
        <el-table-column prop="status" label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'info'">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </ResizableTable>
      
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import ResizableTable from '@/components/ResizableTable.vue';
import { User } from '@element-plus/icons-vue';

const loading = ref(false);
const tableData = ref([]);
const currentPage = ref(1);
const pageSize = ref(10);
const total = ref(0);

const handleSelectionChange = (val) => {
  console.log('Selected rows:', val);
};

const handleEdit = (row) => {
  console.log('Edit:', row);
};

const handleDelete = (row) => {
  console.log('Delete:', row);
};
</script>
```

## Props

| 属性 | 说明 | 类型 | 默认值 |
|------|------|------|--------|
| data | 表格数据 | Array | [] |
| tableClass | 自定义表格类名 | String | '' |
| headerCellStyle | 表头单元格样式 | Object | {} |
| cellStyle | 单元格样式 | Object | {} |
| stripe | 是否显示斑马纹 | Boolean | true |
| border | 是否显示边框 | Boolean | false |
| enableResize | 是否启用列宽调整 | Boolean | true |

## 列宽调整功能

### 如何使用

1. **鼠标悬停** - 将鼠标移动到表头列之间的边界
2. **拖动调整** - 当鼠标变为调整大小图标时，按住并拖动
3. **自动保存** - 调整后的列宽会自动保存到 localStorage

### 禁用列宽调整

```vue
<ResizableTable :data="tableData" :enable-resize="false">
  <!-- columns -->
</ResizableTable>
```

### 最小列宽

所有列的最小宽度为 50px，防止列变得过窄。

## 样式定制

### 自定义表头样式

```vue
<ResizableTable 
  :data="tableData"
  :header-cell-style="{ 
    background: '#f0f2f5', 
    color: '#333',
    fontWeight: '700'
  }"
>
  <!-- columns -->
</ResizableTable>
```

### 自定义单元格样式

```vue
<ResizableTable 
  :data="tableData"
  :cell-style="{ 
    fontSize: '14px',
    color: '#666'
  }"
>
  <!-- columns -->
</ResizableTable>
```

### 添加自定义类名

```vue
<ResizableTable 
  :data="tableData"
  table-class="my-custom-table"
>
  <!-- columns -->
</ResizableTable>
```

然后在样式中:

```css
.my-custom-table :deep(.el-table__row) {
  /* 自定义行样式 */
}
```

## 最佳实践

### 1. 使用 min-width 而不是 width

对于内容可能较长的列，使用 `min-width` 而不是固定的 `width`:

```vue
<el-table-column prop="description" label="描述" min-width="300" />
```

### 2. 固定操作列

将操作列固定在右侧，提供更好的用户体验:

```vue
<el-table-column label="操作" width="200" fixed="right" align="center">
  <template #default="{ row }">
    <el-button link type="primary">编辑</el-button>
  </template>
</el-table-column>
```

### 3. 使用 show-overflow-tooltip

对于可能溢出的内容，启用 tooltip:

```vue
<el-table-column 
  prop="longText" 
  label="长文本" 
  min-width="200" 
  show-overflow-tooltip 
/>
```

### 4. 统一使用 table-card 类

将表格包裹在带有 `table-card` 类的 card 中:

```vue
<el-card shadow="never" class="table-card">
  <ResizableTable :data="tableData">
    <!-- columns -->
  </ResizableTable>
</el-card>
```

## 迁移指南

### 从 el-table 迁移

1. **导入组件**
   ```javascript
   import ResizableTable from '@/components/ResizableTable.vue';
   ```

2. **替换标签**
   - 将 `<el-table>` 替换为 `<ResizableTable>`
   - 将 `</el-table>` 替换为 `</ResizableTable>`

3. **移除冗余属性**
   - 移除 `style="width: 100%"` (默认已设置)
   - 移除 `stripe` (默认为 true)
   - 移除自定义的 `:header-cell-style` (除非需要特殊定制)

4. **测试功能**
   - 验证数据加载
   - 测试列宽调整
   - 检查样式是否符合预期

## 常见问题

### Q: 列宽调整不生效？

A: 确保 `:enable-resize="true"` (默认值) 并且列定义了初始宽度。

### Q: 如何重置列宽？

A: 清除 localStorage 中对应的键值:
```javascript
localStorage.removeItem('table-column-width-columnName');
```

### Q: 可以在移动端使用吗？

A: 列宽调整功能主要针对桌面端。移动端建议禁用此功能:
```vue
<ResizableTable :enable-resize="false">
```

### Q: 如何处理大量数据？

A: 建议配合分页使用，并使用虚拟滚动(如果数据量特别大):
```vue
<ResizableTable 
  :data="paginatedData"
  height="500"
>
```

## 更新日志

### v1.0.0 (2026-01-19)
- ✨ 初始版本发布
- 🎨 统一表格样式
- 🖱️ 支持列宽拖动调整
- 💾 列宽持久化存储
- 📱 响应式设计优化

## 支持

如有问题或建议，请联系开发团队。
