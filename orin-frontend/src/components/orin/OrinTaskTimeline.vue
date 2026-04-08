<template>
  <div class="orin-task-timeline">
    <el-timeline>
      <el-timeline-item
        v-for="item in items"
        :key="item.id"
        :timestamp="formatTime(item.timestamp)"
        :type="normalizeType(item.status)"
      >
        <div class="timeline-title">
          {{ item.title }}
        </div>
        <div class="timeline-meta">
          {{ item.actor }}
        </div>
        <div class="timeline-description">
          {{ item.description }}
        </div>
      </el-timeline-item>
    </el-timeline>
  </div>
</template>

<script setup>
import dayjs from 'dayjs'

defineProps({
  items: {
    type: Array,
    default: () => []
  }
})

const normalizeType = (status) => {
  const safe = String(status || '').toLowerCase()
  if (safe.includes('error') || safe.includes('fail')) return 'danger'
  if (safe.includes('warn')) return 'warning'
  if (safe.includes('success') || safe.includes('done') || safe.includes('completed')) return 'success'
  return 'primary'
}

const formatTime = (time) => {
  if (!time) return '-'
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
}
</script>

<style scoped>
.timeline-title {
  font-weight: 600;
}

.timeline-meta {
  color: var(--text-secondary);
  font-size: 12px;
  margin-top: 2px;
}

.timeline-description {
  margin-top: 6px;
  color: var(--text-primary);
}
</style>
