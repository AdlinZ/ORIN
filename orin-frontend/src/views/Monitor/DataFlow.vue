<template>
  <div class="page-container">
    <div class="action-bar">
      <div>
        <h2 class="page-title" style="margin-bottom: 0;">请求链路追踪</h2>
        <span class="text-muted">Trace ID: {{ traceId }}</span>
      </div>
      <div>
        <el-button @click="goBack">返回日志</el-button>
        <el-button type="primary" @click="loadTrace">刷新</el-button>
      </div>
    </div>

    <!-- Trace Visualization -->
    <el-card class="box-card" v-loading="loading">
       <div v-if="traceData" class="trace-container">
           <div class="trace-summary">
               <el-descriptions border>
                   <el-descriptions-item label="状态">
                       <el-tag :type="traceData.status === 'SUCCESS' ? 'success' : 'danger'">
                           {{ traceData.status }}
                       </el-tag>
                   </el-descriptions-item>
                   <el-descriptions-item label="总耗时">{{ traceData.totalDurationMs }} ms</el-descriptions-item>
                   <el-descriptions-item label="开始时间">
                        {{ traceData.stages && traceData.stages.length > 0 ? formatTime(traceData.stages[0].timestamp) : '-' }}
                   </el-descriptions-item>
               </el-descriptions>
           </div>
           
           <br><br>

           <el-timeline>
               <el-timeline-item
                 v-for="(stage, index) in traceData.stages"
                 :key="index"
                 :timestamp="formatTime(stage.timestamp)"
                 :type="stage.status === 'SUCCESS' ? 'primary' : 'danger'"
                 :hollow="stage.status === 'SUCCESS'"
                 placement="top"
               >
                 <el-card class="stage-card">
                   <h4>{{ stage.name }}</h4>
                   <p>{{ stage.details }}</p>
                 </el-card>
               </el-timeline-item>
           </el-timeline>
       </div>
       <div v-else class="empty-state">
           <el-empty description="无法找到该链路的追踪数据" />
       </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '@/utils/request'
import { ArrowLeft } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const traceId = ref(route.params.traceId)

const loading = ref(false)
const traceData = ref(null)

const loadTrace = async () => {
    loading.value = true
    try {
        const res = await request.get(`/api/v1/monitor/dataflow/${traceId.value}`)
        traceData.value = res
    } catch (error) {
        console.error(error)
    } finally {
        loading.value = false
    }
}

const goBack = () => {
    router.back()
}

const formatTime = (time) => {
    if (!time) return ''
    return new Date(time).toLocaleTimeString() + '.' + new Date(time).getMilliseconds()
}

onMounted(() => {
    if (traceId.value) {
        loadTrace()
    }
})
</script>

<style scoped>
.trace-container {
    padding: 20px;
}
.stage-card {
    border-left: 4px solid var(--primary-color);
}
</style>
