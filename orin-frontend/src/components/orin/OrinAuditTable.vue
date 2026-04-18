<template>
  <el-table
    :data="rows"
    v-bind="$attrs"
    stripe
    border
  >
    <el-table-column type="expand" width="52">
      <template #default="{ row }">
        <div class="audit-detail">
          <div class="audit-detail-grid">
            <div><strong>ID：</strong>{{ row.id || '-' }}</div>
            <div><strong>Provider：</strong>{{ row.providerType || '-' }}</div>
            <div><strong>模型：</strong>{{ row.model || '-' }}</div>
            <div><strong>方法：</strong>{{ row.method || '-' }}</div>
            <div><strong>耗时(ms)：</strong>{{ row.responseTime ?? '-' }}</div>
            <div><strong>会话ID：</strong>{{ row.conversationId || '-' }}</div>
            <div><strong>工作流ID：</strong>{{ row.workflowId || '-' }}</div>
            <div><strong>API Key：</strong>{{ row.apiKeyId || '-' }}</div>
          </div>
          <div class="audit-detail-block">
            <div class="label">Endpoint</div>
            <pre>{{ row.endpoint || '-' }}</pre>
          </div>
          <div class="audit-detail-block">
            <div class="label">Request Params</div>
            <pre>{{ prettyText(row.requestParams) }}</pre>
          </div>
          <div class="audit-detail-block">
            <div class="label">Response Content</div>
            <pre>{{ prettyText(row.responseContent) }}</pre>
          </div>
        </div>
      </template>
    </el-table-column>
    <el-table-column prop="time" label="时间" width="180" />
    <el-table-column prop="actor" label="操作者" width="140" />
    <el-table-column prop="action" label="动作" min-width="180" />
    <el-table-column prop="resource" label="资源" min-width="180" />
    <el-table-column prop="result" label="结果" width="120">
      <template #default="{ row }">
        <el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'" size="small">
          {{ row.result || '-' }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="statusCode" label="状态码" width="100" />
    <el-table-column prop="errorMessage" label="错误信息" min-width="220" show-overflow-tooltip />
    <el-table-column
      prop="traceId"
      label="Trace ID"
      min-width="220"
      show-overflow-tooltip
    />
    <slot />
  </el-table>
</template>

<script setup>
defineOptions({ inheritAttrs: false })

defineProps({
  rows: {
    type: Array,
    default: () => []
  }
})

const prettyText = (value) => {
  if (value == null || value === '') return '-'
  if (typeof value === 'object') {
    try {
      return JSON.stringify(value, null, 2)
    } catch {
      return String(value)
    }
  }
  const text = String(value)
  try {
    return JSON.stringify(JSON.parse(text), null, 2)
  } catch {
    return text
  }
}
</script>

<style scoped>
.audit-detail {
  padding: 12px 8px 4px;
}

.audit-detail-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 8px 16px;
  margin-bottom: 12px;
  font-size: 13px;
}

.audit-detail-block {
  margin-top: 10px;
}

.audit-detail-block .label {
  margin-bottom: 6px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.audit-detail-block pre {
  margin: 0;
  padding: 10px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 240px;
  overflow: auto;
}
</style>
