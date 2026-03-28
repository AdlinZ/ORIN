<template>
  <div class="settings-page">
    <el-card class="settings-card">
      <template #header>
        <div class="card-header">
          <span>模型默认参数配置</span>
          <el-button
            type="primary"
            size="small"
            :loading="saving"
            @click="handleSave"
          >
            保存配置
          </el-button>
        </div>
      </template>

      <el-form :model="form" label-width="150px">
        <!-- 默认模型选择 -->
        <el-form-item label="默认对话模型">
          <el-select v-model="form.defaultChatModel" placeholder="选择默认模型" clearable>
            <el-option
              v-for="model in availableModels"
              :key="model.modelId || model.identifier"
              :label="model.name"
              :value="model.modelId || model.identifier"
            >
              <span>{{ model.name }}</span>
              <el-tag size="small" type="info" style="margin-left: 8px">
                {{ model.provider }}
              </el-tag>
            </el-option>
          </el-select>
          <div class="form-tip">
            新会话默认使用的对话模型
          </div>
        </el-form-item>

        <el-form-item label="默认 Embedding 模型">
          <el-select v-model="form.defaultEmbeddingModel" placeholder="选择 Embedding 模型" clearable>
            <el-option
              v-for="model in embeddingModels"
              :key="model.modelId || model.identifier"
              :label="model.name"
              :value="model.modelId || model.identifier"
            >
              <span>{{ model.name }}</span>
              <el-tag size="small" type="info" style="margin-left: 8px">
                {{ model.provider }}
              </el-tag>
            </el-option>
          </el-select>
          <div class="form-tip">
            知识库 embedding 默认使用的模型
          </div>
        </el-form-item>

        <el-form-item label="默认 VLM 模型">
          <el-select v-model="form.defaultVlmModel" placeholder="选择 VLM 模型" clearable>
            <el-option
              v-for="model in vlmModels"
              :key="model.modelId || model.identifier"
              :label="model.name"
              :value="model.modelId || model.identifier"
            >
              <span>{{ model.name }}</span>
              <el-tag size="small" type="info" style="margin-left: 8px">
                {{ model.provider }}
              </el-tag>
            </el-option>
          </el-select>
          <div class="form-tip">
            多模态理解默认使用的模型
          </div>
        </el-form-item>

        <el-form-item label="默认评估模型">
          <el-select v-model="form.defaultEvalModel" placeholder="选择评估模型" clearable>
            <el-option
              v-for="model in availableModels"
              :key="model.modelId || model.identifier"
              :label="model.name"
              :value="model.modelId || model.identifier"
            >
              <span>{{ model.name }}</span>
              <el-tag size="small" type="info" style="margin-left: 8px">
                {{ model.provider }}
              </el-tag>
            </el-option>
          </el-select>
          <div class="form-tip">
            系统评估默认使用的模型
          </div>
        </el-form-item>

        <el-divider />

        <!-- 模型参数默认值 -->
        <h4 class="section-subtitle">
          默认参数
        </h4>

        <el-form-item label="默认 Temperature">
          <el-slider
            v-model="form.defaultTemperature"
            :min="0"
            :max="2"
            :step="0.1"
            show-stops
          />
          <div class="form-tip">
            控制生成随机性，0 为确定性输出，2 为高度随机
          </div>
        </el-form-item>

        <el-form-item label="默认 Max Tokens">
          <el-input-number
            v-model="form.defaultMaxTokens"
            :min="100"
            :max="128000"
            :step="100"
          />
          <div class="form-tip">
            单次回复最大 token 数
          </div>
        </el-form-item>

        <el-form-item label="默认 Top P">
          <el-slider
            v-model="form.defaultTopP"
            :min="0"
            :max="1"
            :step="0.05"
          />
          <div class="form-tip">
            核采样参数
          </div>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { getModelConfig, updateModelConfig } from '@/api/modelConfig'
import { getModelList } from '@/api/model'

const saving = ref(false)
const models = ref([])

const form = reactive({
  defaultChatModel: '',
  defaultEmbeddingModel: '',
  defaultVlmModel: '',
  defaultEvalModel: '',
  defaultTemperature: 0.7,
  defaultMaxTokens: 4096,
  defaultTopP: 0.9
})

// 获取模型列表
const loadModels = async () => {
  try {
    const res = await getModelList()
    models.value = res || []
  } catch (e) {
    console.error('加载模型列表失败:', e)
  }
}

// 加载配置
const loadConfig = async () => {
  try {
    const res = await getModelConfig()
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
    await updateModelConfig(form)
    ElMessage.success('配置保存成功')
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.message || '未知错误'))
  } finally {
    saving.value = false
  }
}

// 过滤模型类型（兼容大小写）
const availableModels = computed(() => {
  return models.value.filter(m => {
    const t = m.type?.toLowerCase()
    return t === 'chat' || t === 'llm'
  })
})

const embeddingModels = computed(() => {
  return models.value.filter(m => {
    const t = m.type?.toLowerCase()
    return t === 'embedding'
  })
})

const vlmModels = computed(() => {
  return models.value.filter(m => {
    const t = m.type?.toLowerCase()
    return t === 'vlm' || t === 'multimodal'
  })
})

onMounted(() => {
  loadModels()
  loadConfig()
})
</script>

<style scoped>
.settings-page {
  max-width: 800px;
}

.settings-card {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}

.section-subtitle {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 16px;
  color: var(--el-text-color-primary);
}

.form-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}
</style>
