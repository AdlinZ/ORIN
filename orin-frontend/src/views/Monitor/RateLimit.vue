<template>
  <div class="rate-limit-page">
    <OrinPageShell
      title="限流配置"
      description="配置运行时用户、API Key 与 Agent 维度的请求限流策略"
      icon="Lightning"
      domain="运行监控"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="fetchConfig">
          刷新
        </el-button>
        <el-button
          type="primary"
          :icon="Check"
          :loading="saving"
          @click="saveConfig"
        >
          保存配置
        </el-button>
      </template>
    </OrinPageShell>

    <div class="rate-limit-grid">
      <section class="settings-panel">
        <div class="panel-heading">
          <div>
            <h2>基本设置</h2>
            <p>控制单个用户或 API Key 的整体请求额度。</p>
          </div>
        </div>
        <el-form :model="config" label-width="140px">
          <el-form-item label="启用限流">
            <el-switch v-model="config.enabled" />
          </el-form-item>
            
          <el-form-item label="每分钟请求数">
            <el-input-number v-model="config.requestsPerMinute" :min="1" :max="10000" />
            <span class="form-tip">单个用户/API Key 每分钟最大请求数</span>
          </el-form-item>
            
          <el-form-item label="每天请求数">
            <el-input-number v-model="config.requestsPerDay" :min="1" :max="1000000" />
            <span class="form-tip">单个用户/API Key 每天最大请求数</span>
          </el-form-item>
            
          <el-form-item label="限流算法">
            <el-select v-model="config.algorithm">
              <el-option label="令牌桶 (Token Bucket)" value="TOKEN_BUCKET" />
              <el-option label="滑动窗口 (Sliding Window)" value="SLIDING_WINDOW" />
            </el-select>
          </el-form-item>
        </el-form>
      </section>

      <section class="settings-panel">
        <div class="panel-heading">
          <div>
            <h2>令牌桶设置</h2>
            <p>仅在使用令牌桶算法时生效。</p>
          </div>
        </div>
        <el-form :model="config" label-width="140px">
          <el-form-item label="令牌桶容量">
            <el-input-number v-model="config.bucketSize" :min="1" :max="1000" />
            <span class="form-tip">桶中最多存放的令牌数</span>
          </el-form-item>
            
          <el-form-item label="令牌补充速率">
            <el-input-number
              v-model="config.refillRate"
              :min="0.1"
              :max="100"
              :step="0.1"
              :precision="1"
            />
            <span class="form-tip">每秒钟补充的令牌数</span>
          </el-form-item>
        </el-form>
      </section>

      <section class="settings-panel">
        <div class="panel-heading">
          <div>
            <h2>限流维度</h2>
            <p>按调用主体分别启用策略，避免影响无关流量。</p>
          </div>
        </div>
        <el-form :model="config" label-width="160px">
          <el-form-item label="启用用户级别限流">
            <el-switch v-model="config.enableUserLimit" />
            <span class="form-tip">按用户 ID 进行限流</span>
          </el-form-item>
            
          <el-form-item label="启用API Key限流">
            <el-switch v-model="config.enableApiKeyLimit" />
            <span class="form-tip">按 API Key 进行限流</span>
          </el-form-item>
            
          <el-form-item label="启用Agent限流">
            <el-switch v-model="config.enableAgentLimit" />
            <span class="form-tip">按 Agent ID 进行限流（需传递 agent_id 参数）</span>
          </el-form-item>
        </el-form>
      </section>

      <section class="settings-panel">
        <div class="panel-heading">
          <div>
            <h2>配置说明</h2>
            <p>记录策略用途，并确认当前生效版本的更新时间。</p>
          </div>
        </div>
        <el-form :model="config" label-width="100px">
          <el-form-item label="描述">
            <el-input
              v-model="config.description"
              type="textarea"
              :rows="4"
              placeholder="限流配置描述..."
            />
          </el-form-item>
        </el-form>
          
        <!-- 当前状态 -->
        <el-divider />
          
        <div class="status-info">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="创建时间">
              {{ config.createdAt || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="更新时间">
              {{ config.updatedAt || '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </section>
    </div>

    <section class="reference-panel">
      <div class="panel-heading">
        <div>
          <h2>算法说明</h2>
          <p>根据流量形态选择限流算法。</p>
        </div>
      </div>
      <el-row :gutter="20">
        <el-col :span="12">
          <h4>令牌桶算法 (Token Bucket)</h4>
          <p>令牌桶算法以固定速率向桶中添加令牌，请求时从桶中获取令牌。</p>
          <ul>
            <li>优点：允许一定程度的突发流量</li>
            <li>适用场景：API 限流、流量控制</li>
          </ul>
        </el-col>
        <el-col :span="12">
          <h4>滑动窗口算法 (Sliding Window)</h4>
          <p>滑动窗口算法将时间划分为固定大小的窗口，统计每个窗口内的请求数。</p>
          <ul>
            <li>优点：限流更平滑，避免突发</li>
            <li>适用场景：严格限流、支付限流</li>
          </ul>
        </el-col>
      </el-row>
    </section>

    <OrinDataTable compact class="response-header-table">
      <template #header>
        <div class="table-heading">
          <div>
            <strong>响应头信息</strong>
            <span>限流启用后，符合条件的请求会返回以下响应头。</span>
          </div>
        </div>
      </template>
      <el-table :data="responseHeaders">
        <el-table-column prop="header" label="响应头" width="200" />
        <el-table-column prop="desc" label="说明" />
        <el-table-column prop="example" label="示例" width="150" />
      </el-table>
    </OrinDataTable>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Check } from '@element-plus/icons-vue'
import OrinDataTable from '@/components/orin/OrinDataTable.vue'
import OrinPageShell from '@/components/orin/OrinPageShell.vue'
import { getRateLimitConfig, updateRateLimitConfig } from '@/api/monitor'

const loading = ref(false)
const saving = ref(false)

const config = ref({
  enabled: true,
  requestsPerMinute: 60,
  requestsPerDay: 10000,
  bucketSize: 60,
  refillRate: 1.0,
  enableUserLimit: true,
  enableApiKeyLimit: true,
  enableAgentLimit: false,
  algorithm: 'TOKEN_BUCKET',
  description: '',
  createdAt: '',
  updatedAt: ''
})

const responseHeaders = [
  { header: 'X-RateLimit-Limit', desc: '允许的请求数上限', example: '60' },
  { header: 'X-RateLimit-Remaining', desc: '剩余可用请求数', example: '45' },
  { header: 'X-RateLimit-Reset', desc: '限流重置时间戳(秒)', example: '1709123400' },
  { header: 'Retry-After', desc: '限流后需等待秒数(429响应)', example: '30' }
]

const fetchConfig = async () => {
  loading.value = true
  try {
    const res = await getRateLimitConfig()
    if (res.data) {
      config.value = { ...config.value, ...res.data }
    }
  } catch (error) {
    console.error('获取限流配置失败:', error)
    ElMessage.error('获取限流配置失败')
  } finally {
    loading.value = false
  }
}

const saveConfig = async () => {
  saving.value = true
  try {
    await updateRateLimitConfig(config.value)
    ElMessage.success('限流配置保存成功')
    await fetchConfig()
  } catch (error) {
    console.error('保存限流配置失败:', error)
    ElMessage.error('保存限流配置失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  fetchConfig()
})
</script>

<style scoped>
.rate-limit-page {
  padding: 0;
}

.rate-limit-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.settings-panel,
.reference-panel {
  padding: 20px;
  border: 1px solid var(--orin-border, var(--el-border-color-lighter));
  border-radius: var(--radius-base, 8px);
  background: var(--orin-surface, var(--el-bg-color));
}

.reference-panel {
  margin-bottom: 16px;
}

.panel-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.panel-heading h2 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 15px;
  line-height: 1.4;
}

.panel-heading p,
.table-heading span {
  display: block;
  margin: 4px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.5;
}

.form-tip {
  display: block;
  font-size: 12px;
  color: var(--neutral-gray-400);
  margin-top: 4px;
}

.status-info {
  margin-top: 10px;
}

.table-heading strong {
  color: var(--el-text-color-primary);
  font-size: 14px;
}

@media (max-width: 960px) {
  .rate-limit-grid {
    grid-template-columns: 1fr;
  }
}
</style>
