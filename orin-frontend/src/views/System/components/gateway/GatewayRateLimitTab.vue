<template>
  <div class="gateway-rate-limit-tab">
    <el-card v-loading="loading">
      <template #header>
        <div class="card-header">
          <el-icon><Lightning /></el-icon>
          <span>全局限流配置</span>
          <el-button type="primary" size="small" :loading="saving" @click="saveConfig">
            <el-icon><Check /></el-icon>
            保存配置
          </el-button>
        </div>
      </template>

      <el-form :model="config" label-position="top" class="config-form">
        <!-- 启用限流 -->
        <el-form-item label="启用限流">
          <div class="flex-between w-100">
            <div class="form-info">
              <div class="form-label-desc">
                开启后，系统将对 API 请求进行限流控制。
              </div>
            </div>
            <el-switch v-model="config.enabled" />
          </div>
        </el-form-item>

        <el-divider border-style="dashed" />

        <!-- 限流算法 -->
        <el-form-item label="限流算法">
          <el-radio-group v-model="config.algorithm">
            <el-radio value="TOKEN_BUCKET">
              <div class="algorithm-option">
                <div class="algorithm-title">令牌桶算法</div>
                <div class="algorithm-desc">支持突发流量，允许一定程度的突发请求</div>
              </div>
            </el-radio>
            <el-radio value="SLIDING_WINDOW">
              <div class="algorithm-option">
                <div class="algorithm-title">滑动窗口算法</div>
                <div class="algorithm-desc">限流更平滑，避免突发流量</div>
              </div>
            </el-radio>
          </el-radio-group>
        </el-form-item>

        <el-divider border-style="dashed" />

        <!-- 限流阈值 -->
        <el-row :gutter="24">
          <el-col :span="12">
            <el-form-item label="每分钟请求次数限制">
              <el-input-number
                v-model="config.requestsPerMinute"
                :min="1"
                :max="10000"
                :step="10"
                style="width: 100%"
              />
              <p class="form-tip">单个用户/API Key 在一分钟内的最大请求数</p>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="每天请求次数限制">
              <el-input-number
                v-model="config.requestsPerDay"
                :min="1"
                :max="1000000"
                :step="100"
                style="width: 100%"
              />
              <p class="form-tip">单个用户/API Key 在一天内的最大请求数</p>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="24">
          <el-col :span="12">
            <el-form-item label="令牌桶容量">
              <el-input-number
                v-model="config.bucketSize"
                :min="1"
                :max="1000"
                :step="5"
                style="width: 100%"
              />
              <p class="form-tip">令牌桶最大容量（仅 TOKEN_BUCKET 算法生效）</p>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="令牌补充速率">
              <el-input-number
                v-model="config.refillRate"
                :min="0.1"
                :max="100"
                :step="0.5"
                :precision="1"
                style="width: 100%"
              />
              <p class="form-tip">每秒钟补充的令牌数（仅 TOKEN_BUCKET 算法生效）</p>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider border-style="dashed" />

        <!-- 多维度限流开关 -->
        <el-form-item label="多维度限流">
          <div class="dimension-switches">
            <div class="dimension-item">
              <el-switch v-model="config.enableUserLimit" />
              <div class="dimension-info">
                <div class="dimension-title">用户级别限流</div>
                <div class="dimension-desc">按用户 ID 进行限流</div>
              </div>
            </div>
            <div class="dimension-item">
              <el-switch v-model="config.enableApiKeyLimit" />
              <div class="dimension-info">
                <div class="dimension-title">API Key 级别限流</div>
                <div class="dimension-desc">按 API Key 进行限流</div>
              </div>
            </div>
            <div class="dimension-item">
              <el-switch v-model="config.enableAgentLimit" />
              <div class="dimension-info">
                <div class="dimension-title">Agent 级别限流</div>
                <div class="dimension-desc">按 Agent ID 进行限流</div>
              </div>
            </div>
          </div>
        </el-form-item>

        <el-divider border-style="dashed" />

        <!-- 描述 -->
        <el-form-item label="备注">
          <el-input
            v-model="config.description"
            type="textarea"
            :rows="3"
            placeholder="请输入限流配置的备注信息"
          />
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 当前限流状态 -->
    <el-card v-if="config.enabled" class="premium-card margin-top-lg">
      <template #header>
        <div class="card-header">
          <el-icon><InfoFilled /></el-icon>
          <span>限流说明</span>
        </div>
      </template>
      <div class="limit-info">
        <el-row :gutter="24">
          <el-col :span="8">
            <div class="info-item">
              <div class="info-value">{{ config.requestsPerMinute || 0 }}</div>
              <div class="info-label">每分钟限制</div>
            </div>
          </el-col>
          <el-col :span="8">
            <div class="info-item">
              <div class="info-value">{{ config.requestsPerDay || 0 }}</div>
              <div class="info-label">每天限制</div>
            </div>
          </el-col>
          <el-col :span="8">
            <div class="info-item">
              <div class="info-value">{{ config.algorithm }}</div>
              <div class="info-label">算法</div>
            </div>
          </el-col>
        </el-row>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, Lightning, InfoFilled } from '@element-plus/icons-vue'
import { getRateLimitConfig, updateRateLimitConfig } from '@/api/monitor'

const loading = ref(false)
const saving = ref(false)
const config = ref({
  id: 'DEFAULT',
  enabled: true,
  requestsPerMinute: 60,
  requestsPerDay: 10000,
  bucketSize: 60,
  refillRate: 1.0,
  enableUserLimit: true,
  enableApiKeyLimit: true,
  enableAgentLimit: false,
  algorithm: 'TOKEN_BUCKET',
  description: ''
})

const loadConfig = async () => {
  loading.value = true
  try {
    const res = await getRateLimitConfig()
    if (res.data) {
      config.value = { ...config.value, ...res.data }
    }
  } catch (error) {
    console.error('加载限流配置失败:', error)
  } finally {
    loading.value = false
  }
}

const saveConfig = async () => {
  saving.value = true
  try {
    await updateRateLimitConfig(config.value)
    ElMessage.success('限流配置保存成功')
  } catch (error) {
    console.error('保存限流配置失败:', error)
    ElMessage.error('保存失败: ' + (error.response?.data?.message || error.message))
  } finally {
    saving.value = false
  }
}

loadConfig()
</script>

<style scoped>
.gateway-rate-limit-tab {
  padding: 0;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 15px;
}

.card-header .el-icon {
  font-size: 18px;
  color: var(--el-color-primary);
}

.card-header .el-button {
  margin-left: auto;
}

.premium-card {
  border-radius: 8px;
  border: 1px solid var(--neutral-gray-100);
}

.margin-top-lg {
  margin-top: 20px;
}

.config-form {
  max-width: 800px;
}

.form-info {
  flex: 1;
}

.form-label-desc {
  color: var(--neutral-gray-600);
  font-size: 13px;
  line-height: 1.5;
}

.form-tip {
  color: var(--neutral-gray-400);
  font-size: 12px;
  margin-top: 4px;
}

.flex-between {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.w-100 {
  width: 100%;
}

.algorithm-option {
  padding: 8px 0;
}

.algorithm-title {
  font-weight: 500;
  margin-bottom: 4px;
}

.algorithm-desc {
  font-size: 12px;
  color: var(--neutral-gray-500);
}

.dimension-switches {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.dimension-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 16px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}

.dimension-info {
  flex: 1;
}

.dimension-title {
  font-weight: 500;
  margin-bottom: 4px;
}

.dimension-desc {
  font-size: 12px;
  color: var(--neutral-gray-500);
}

.limit-info {
  padding: 16px 0;
}

.info-item {
  text-align: center;
  padding: 16px;
}

.info-value {
  font-size: 24px;
  font-weight: 600;
  color: var(--el-color-primary);
  margin-bottom: 8px;
}

.info-label {
  font-size: 13px;
  color: var(--neutral-gray-500);
}
</style>
