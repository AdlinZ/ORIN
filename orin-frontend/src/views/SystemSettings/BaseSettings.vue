<template>
  <div class="settings-page">
    <el-form :model="form" :rules="rules" label-width="140px" class="settings-form">
      <!-- 基础信息 -->
      <div class="settings-section">
        <h3 class="section-title">基础信息</h3>
        <el-card class="settings-card">
          <el-form-item label="系统名称" prop="systemName">
            <el-input v-model="form.systemName" placeholder="ORIN 智能体平台" />
          </el-form-item>
          <el-form-item label="系统描述">
            <el-input v-model="form.systemDescription" type="textarea" :rows="3" placeholder="简要描述系统功能" />
          </el-form-item>
        </el-card>
      </div>

      <!-- 会话配置 -->
      <div class="settings-section">
        <h3 class="section-title">会话配置</h3>
        <el-card class="settings-card">
          <el-form-item label="会话保留天数" prop="sessionRetentionDays">
            <el-input-number v-model="form.sessionRetentionDays" :min="1" :max="365" />
            <div class="form-tip">超过此天数的历史会话将被自动清理</div>
          </el-form-item>
          <el-form-item label="最大并发请求" prop="maxConcurrentRequests">
            <el-input-number v-model="form.maxConcurrentRequests" :min="1" :max="1000" />
            <div class="form-tip">系统允许的最大并发请求数</div>
          </el-form-item>
        </el-card>
      </div>

      <!-- 审计配置 -->
      <div class="settings-section">
        <h3 class="section-title">审计配置</h3>
        <el-card class="settings-card">
          <el-form-item label="启用审计日志">
            <el-switch v-model="form.auditLogEnabled" />
            <div class="form-tip">开启后记录用户操作日志</div>
          </el-form-item>
          <el-form-item label="审计日志保留天数" v-if="form.auditLogEnabled">
            <el-input-number v-model="form.auditLogRetentionDays" :min="7" :max="365" />
          </el-form-item>
        </el-card>
      </div>

      <!-- 操作按钮 -->
      <div class="form-actions">
        <el-button type="primary" :loading="saving" @click="handleSave">
          保存配置
        </el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>
    </el-form>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getSystemConfig, updateSystemConfig } from '@/api/system'

const saving = ref(false)

const form = reactive({
  systemName: '',
  systemDescription: '',
  sessionRetentionDays: 30,
  maxConcurrentRequests: 100,
  auditLogEnabled: true,
  auditLogRetentionDays: 90
})

const rules = {
  systemName: [{ required: true, message: '请输入系统名称', trigger: 'blur' }],
  sessionRetentionDays: [{ required: true, message: '请输入会话保留天数', trigger: 'blur' }],
  maxConcurrentRequests: [{ required: true, message: '请输入最大并发请求数', trigger: 'blur' }]
}

// 加载配置
const loadConfig = async () => {
  try {
    const res = await getSystemConfig()
    if (res) {
      Object.assign(form, res)
    }
  } catch (e) {
    console.error('加载配置失败:', e)
  }
}

// 保存配置
const handleSave = async () => {
  saving.value = true
  try {
    await updateSystemConfig(form)
    ElMessage.success('配置保存成功（当前为临时配置，重启后可能失效）')
  } catch (e) {
    ElMessage.error('配置保存失败')
  } finally {
    saving.value = false
  }
}

// 重置
const handleReset = () => {
  loadConfig()
}

onMounted(() => {
  loadConfig()
})
</script>

<style scoped>
.settings-page {
  max-width: 800px;
}

.settings-section {
  margin-bottom: 24px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 16px;
  color: var(--el-text-color-primary);
}

.settings-card {
  margin-bottom: 16px;
}

.form-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}

.form-actions {
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px solid var(--el-border-color-lighter);
  display: flex;
  gap: 12px;
}
</style>
