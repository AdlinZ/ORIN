<template>
  <div class="page-container">
    <PageHeader
      title="添加模型"
      description="选择供应商类型，配置连接信息，快速添加模型资源"
      icon="CirclePlus"
    />

    <el-card shadow="hover" class="onboard-card">
      <!-- 步骤引导 -->
      <div class="onboard-stepper">
        <div
          v-for="(step, index) in guideSteps"
          :key="index"
          class="step-item"
          :class="{ 'active': currentStepIndex >= index }"
        >
          <div class="step-icon-wrapper">
            <el-icon><component :is="step.icon" /></el-icon>
          </div>
          <div class="step-text">
            <span class="step-label">Step 0{{ index + 1 }}</span>
            <span class="step-name">{{ step.title }}</span>
          </div>
          <div v-if="index < guideSteps.length - 1" class="step-line"></div>
        </div>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        class="onboard-form"
      >
        <!-- 选择供应商 -->
        <el-form-item label="供应商类型" prop="providerType">
          <el-select
            v-model="form.providerType"
            placeholder="请选择供应商类型"
            size="large"
            style="width: 100%"
            @change="handleProviderChange"
          >
            <el-option
              v-for="provider in providerOptions"
              :key="provider.value"
              :label="provider.label"
              :value="provider.value"
              class="provider-option"
            >
              <div class="provider-option-content">
                <el-icon class="provider-icon">
                  <component :is="provider.icon" />
                </el-icon>
                <div class="provider-info">
                  <div class="provider-label">{{ provider.label }}</div>
                  <div class="provider-desc">{{ provider.description }}</div>
                </div>
              </div>
            </el-option>
          </el-select>
        </el-form-item>

        <!-- 提示信息 -->
        <el-alert
          v-if="form.providerType"
          :title="currentProviderInfo.title"
          :type="currentProviderInfo.type"
          :description="currentProviderInfo.description"
          :closable="false"
          show-icon
          style="margin-bottom: var(--spacing-lg);"
        />

        <!-- 动态表单 - OpenAI -->
        <template v-if="form.providerType === 'openai'">
          <el-form-item label="API Endpoint" prop="endpointUrl">
            <el-input
              v-model.trim="form.endpointUrl"
              placeholder="https://api.openai.com/v1"
              size="large"
            >
              <template #prefix>
                <el-icon><Link /></el-icon>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item label="API Key" prop="apiKey">
            <el-input
              v-model.trim="form.apiKey"
              type="password"
              show-password
              placeholder="sk-..."
              size="large"
            >
              <template #prefix>
                <el-icon><Key /></el-icon>
              </template>
              <template #append>
                <el-select
                  v-model="selectedSavedKeyId"
                  placeholder="使用保存的密钥"
                  size="large"
                  style="width: 160px;"
                  clearable
                  @change="handleSavedKeySelect"
                >
                  <el-option
                    v-for="key in providerKeys"
                    :key="key.id"
                    :label="key.name"
                    :value="key.id"
                  />
                </el-select>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item label="模型标识符" prop="modelId">
            <el-input
              v-model.trim="form.modelId"
              placeholder="例如: gpt-4o, gpt-4o-mini"
              size="large"
            >
              <template #prefix>
                <el-icon><Cpu /></el-icon>
              </template>
            </el-input>
          </el-form-item>
        </template>

        <!-- 动态表单 - Anthropic -->
        <template v-else-if="form.providerType === 'anthropic'">
          <el-form-item label="API Endpoint" prop="endpointUrl">
            <el-input
              v-model.trim="form.endpointUrl"
              placeholder="https://api.anthropic.com"
              size="large"
            >
              <template #prefix>
                <el-icon><Link /></el-icon>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item label="API Key" prop="apiKey">
            <el-input
              v-model.trim="form.apiKey"
              type="password"
              show-password
              placeholder="sk-ant-..."
              size="large"
            >
              <template #prefix>
                <el-icon><Key /></el-icon>
              </template>
              <template #append>
                <el-select
                  v-model="selectedSavedKeyId"
                  placeholder="使用保存的密钥"
                  size="large"
                  style="width: 160px;"
                  clearable
                  @change="handleSavedKeySelect"
                >
                  <el-option
                    v-for="key in providerKeys"
                    :key="key.id"
                    :label="key.name"
                    :value="key.id"
                  />
                </el-select>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item label="模型标识符" prop="modelId">
            <el-input
              v-model.trim="form.modelId"
              placeholder="例如: claude-3-5-sonnet-20241022"
              size="large"
            >
              <template #prefix>
                <el-icon><Cpu /></el-icon>
              </template>
            </el-input>
          </el-form-item>
        </template>

        <!-- 动态表单 - DeepSeek -->
        <template v-else-if="form.providerType === 'deepseek'">
          <el-form-item label="API Endpoint" prop="endpointUrl">
            <el-input
              v-model.trim="form.endpointUrl"
              placeholder="https://api.deepseek.com/v1"
              size="large"
            >
              <template #prefix>
                <el-icon><Link /></el-icon>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item label="API Key" prop="apiKey">
            <el-input
              v-model.trim="form.apiKey"
              type="password"
              show-password
              placeholder="sk-..."
              size="large"
            >
              <template #prefix>
                <el-icon><Key /></el-icon>
              </template>
              <template #append>
                <el-select
                  v-model="selectedSavedKeyId"
                  placeholder="使用保存的密钥"
                  size="large"
                  style="width: 160px;"
                  clearable
                  @change="handleSavedKeySelect"
                >
                  <el-option
                    v-for="key in providerKeys"
                    :key="key.id"
                    :label="key.name"
                    :value="key.id"
                  />
                </el-select>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item label="模型标识符" prop="modelId">
            <el-input
              v-model.trim="form.modelId"
              placeholder="例如: deepseek-chat"
              size="large"
            >
              <template #prefix>
                <el-icon><Cpu /></el-icon>
              </template>
            </el-input>
          </el-form-item>
        </template>

        <!-- 动态表单 - SiliconFlow -->
        <template v-else-if="form.providerType === 'siliconflow'">
          <el-form-item label="API Endpoint" prop="endpointUrl">
            <el-input
              v-model.trim="form.endpointUrl"
              placeholder="https://api.siliconflow.cn/v1"
              size="large"
            >
              <template #prefix>
                <el-icon><Link /></el-icon>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item label="API Key" prop="apiKey">
            <el-input
              v-model.trim="form.apiKey"
              type="password"
              show-password
              placeholder="sk-..."
              size="large"
            >
              <template #prefix>
                <el-icon><Key /></el-icon>
              </template>
              <template #append>
                <el-select
                  v-model="selectedSavedKeyId"
                  placeholder="使用保存的密钥"
                  size="large"
                  style="width: 160px;"
                  clearable
                  @change="handleSavedKeySelect"
                >
                  <el-option
                    v-for="key in providerKeys"
                    :key="key.id"
                    :label="key.name"
                    :value="key.id"
                  />
                </el-select>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item label="模型标识符" prop="modelId">
            <el-input
              v-model.trim="form.modelId"
              placeholder="例如: Qwen/Qwen2-7B-Instruct"
              size="large"
            >
              <template #prefix>
                <el-icon><Cpu /></el-icon>
              </template>
            </el-input>
          </el-form-item>

          <!-- 一键导入区域 -->
          <div style="margin-top: 24px;">
            <el-divider><span class="divider-text">或从 SiliconFlow 一键导入</span></el-divider>
          </div>

          <div class="batch-import-section">
            <el-alert
              title="一键导入"
              description="点击按钮获取模型列表，选择要导入的模型"
              type="info"
              :closable="false"
              show-icon
              style="margin-bottom: 16px;"
            />
            <el-button
              type="primary"
              :loading="isBatchImporting"
              @click="handleSiliconFlowBatchImport"
              :icon="Refresh"
              size="large"
            >
              获取模型列表
            </el-button>

            <!-- 识别到的模型列表 -->
            <div v-if="detectedModels.length > 0" class="detected-models-section">
              <el-divider content-position="left" style="margin: 16px 0;">识别到的模型（共 {{ detectedModels.length }} 个）</el-divider>
              <el-table :data="detectedModels" max-height="300" style="width: 100%">
                <el-table-column prop="id" label="模型标识符" min-width="1000" />
                <el-table-column prop="_sub_type" label="子类型" width="200" />
                <el-table-column label="操作" width="150">
                  <template #default="scope">
                    <el-button
                      type="primary"
                      size="small"
                      @click="importSingleModel(scope.row)"
                      :icon="Plus"
                    >
                      导入
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
              <div class="batch-actions">
                <el-button
                  type="success"
                  @click="importAllModels"
                  :icon="Check"
                  size="large"
                >
                  全部导入
                </el-button>
                <el-button @click="detectedModels = []" size="large">
                  清除列表
                </el-button>
              </div>
            </div>
          </div>
        </template>

        <!-- 动态表单 - Dify -->
        <template v-else-if="form.providerType === 'dify'">
          <el-form-item label="API Endpoint" prop="endpointUrl">
            <el-input
              v-model.trim="form.endpointUrl"
              placeholder="http://localhost:3000/v1"
              size="large"
            >
              <template #prefix>
                <el-icon><Link /></el-icon>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item label="API Key" prop="apiKey">
            <el-input
              v-model.trim="form.apiKey"
              type="password"
              show-password
              placeholder="app-..."
              size="large"
            >
              <template #prefix>
                <el-icon><Key /></el-icon>
              </template>
              <template #append>
                <el-select
                  v-model="selectedSavedKeyId"
                  placeholder="使用保存的密钥"
                  size="large"
                  style="width: 160px;"
                  clearable
                  @change="handleSavedKeySelect"
                >
                  <el-option
                    v-for="key in providerKeys"
                    :key="key.id"
                    :label="key.name"
                    :value="key.id"
                  />
                </el-select>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item label="模型标识符" prop="modelId">
            <el-input
              v-model.trim="form.modelId"
              placeholder="例如: dify-chat"
              size="large"
            >
              <template #prefix>
                <el-icon><Cpu /></el-icon>
              </template>
            </el-input>
          </el-form-item>
        </template>

        <!-- 动态表单 - Ollama 本地 -->
        <template v-else-if="form.providerType === 'ollama'">
          <el-form-item label="API Endpoint" prop="endpointUrl">
            <el-input
              v-model.trim="form.endpointUrl"
              placeholder="http://localhost:11434/v1"
              size="large"
            >
              <template #prefix>
                <el-icon><Link /></el-icon>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item label="模型标识符" prop="modelId">
            <el-input
              v-model.trim="form.modelId"
              placeholder="例如: llama3, mistral"
              size="large"
            >
              <template #prefix>
                <el-icon><Cpu /></el-icon>
              </template>
            </el-input>
          </el-form-item>
        </template>

        <!-- 动态表单 - 智谱 AI -->
        <template v-else-if="form.providerType === 'zhipu'">
          <el-form-item label="API Endpoint" prop="endpointUrl">
            <el-input
              v-model.trim="form.endpointUrl"
              placeholder="https://open.bigmodel.cn/api/paas/v4"
              size="large"
            >
              <template #prefix>
                <el-icon><Link /></el-icon>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item label="API Key" prop="apiKey">
            <el-input
              v-model.trim="form.apiKey"
              type="password"
              show-password
              placeholder="sk-..."
              size="large"
            >
              <template #prefix>
                <el-icon><Key /></el-icon>
              </template>
              <template #append>
                <el-select
                  v-model="selectedSavedKeyId"
                  placeholder="使用保存的密钥"
                  size="large"
                  style="width: 160px;"
                  clearable
                  @change="handleSavedKeySelect"
                >
                  <el-option
                    v-for="key in providerKeys"
                    :key="key.id"
                    :label="key.name"
                    :value="key.id"
                  />
                </el-select>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item label="模型标识符" prop="modelId">
            <el-input
              v-model.trim="form.modelId"
              placeholder="例如: glm-4"
              size="large"
            >
              <template #prefix>
                <el-icon><Cpu /></el-icon>
              </template>
            </el-input>
          </el-form-item>
        </template>

        <!-- 动态表单 - 阿里云 DashScope -->
        <template v-else-if="form.providerType === 'dashscope'">
          <el-form-item label="API Endpoint" prop="endpointUrl">
            <el-input
              v-model.trim="form.endpointUrl"
              placeholder="https://dashscope.aliyuncs.com/compatible-mode/v1"
              size="large"
            >
              <template #prefix>
                <el-icon><Link /></el-icon>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item label="API Key" prop="apiKey">
            <el-input
              v-model.trim="form.apiKey"
              type="password"
              show-password
              placeholder="sk-..."
              size="large"
            >
              <template #prefix>
                <el-icon><Key /></el-icon>
              </template>
              <template #append>
                <el-select
                  v-model="selectedSavedKeyId"
                  placeholder="使用保存的密钥"
                  size="large"
                  style="width: 160px;"
                  clearable
                  @change="handleSavedKeySelect"
                >
                  <el-option
                    v-for="key in providerKeys"
                    :key="key.id"
                    :label="key.name"
                    :value="key.id"
                  />
                </el-select>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item label="模型标识符" prop="modelId">
            <el-input
              v-model.trim="form.modelId"
              placeholder="例如: qwen-turbo"
              size="large"
            >
              <template #prefix>
                <el-icon><Cpu /></el-icon>
              </template>
            </el-input>
          </el-form-item>
        </template>

        <!-- 动态表单 - HuggingFace -->
        <template v-else-if="form.providerType === 'huggingface'">
          <el-form-item label="API Endpoint" prop="endpointUrl">
            <el-input
              v-model.trim="form.endpointUrl"
              placeholder="https://api-inference.huggingface.co"
              size="large"
            >
              <template #prefix>
                <el-icon><Link /></el-icon>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item label="API Key" prop="apiKey">
            <el-input
              v-model.trim="form.apiKey"
              type="password"
              show-password
              placeholder="hf_..."
              size="large"
            >
              <template #prefix>
                <el-icon><Key /></el-icon>
              </template>
              <template #append>
                <el-select
                  v-model="selectedSavedKeyId"
                  placeholder="使用保存的密钥"
                  size="large"
                  style="width: 160px;"
                  clearable
                  @change="handleSavedKeySelect"
                >
                  <el-option
                    v-for="key in providerKeys"
                    :key="key.id"
                    :label="key.name"
                    :value="key.id"
                  />
                </el-select>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item label="模型标识符" prop="modelId">
            <el-input
              v-model.trim="form.modelId"
              placeholder="例如: meta-llama/Llama-2-7b-hf"
              size="large"
            >
              <template #prefix>
                <el-icon><Cpu /></el-icon>
              </template>
            </el-input>
          </el-form-item>
        </template>

        <!-- 模型类型 -->
        <el-form-item v-if="form.providerType !== 'siliconflow'" label="模型类型" prop="modelType">
          <el-select v-model="form.modelType" placeholder="请选择模型类型" size="large" style="width: 100%">
            <el-option label="对话 (Chat/LLM)" value="CHAT" />
            <el-option label="向量嵌入 (Embedding)" value="EMBEDDING" />
            <el-option label="结果重排 (Reranker)" value="RERANKER" />
            <el-option label="图像生成 (Image)" value="TEXT_TO_IMAGE" />
            <el-option label="语音转文字 (STT)" value="SPEECH_TO_TEXT" />
            <el-option label="文字转语音 (TTS)" value="TEXT_TO_SPEECH" />
          </el-select>
        </el-form-item>

        <!-- 模型名称（可选） -->
        <el-form-item v-if="form.providerType !== 'siliconflow'" label="模型显示名称（可选）">
          <el-input
            v-model.trim="form.modelName"
            placeholder="留空则使用模型标识符作为名称"
            size="large"
          >
            <template #prefix>
              <el-icon><Edit /></el-icon>
            </template>
          </el-input>
        </el-form-item>

        <!-- 描述（可选） -->
        <el-form-item v-if="form.providerType !== 'siliconflow'" label="描述（可选）">
          <el-input
            v-model.trim="form.description"
            type="textarea"
            :rows="2"
            placeholder="模型描述信息"
            size="large"
          />
        </el-form-item>
      </el-form>

      <!-- 底部按钮 -->
      <div class="form-actions">
        <el-button @click="goBack" size="large">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting" size="large">
          确认添加
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import {
  Link, Key, Cpu, Platform, Opportunity, Star, Sunrise, Connection, Monitor, Moon, Edit, Refresh, Plus, Check
} from '@element-plus/icons-vue';
import PageHeader from '@/components/PageHeader.vue';
import { ElMessage } from 'element-plus';
import { getExternalKeys } from '@/api/apiKey';
import { saveModel } from '@/api/model';
import { getProviderList } from '@/api/system';

const router = useRouter();
const formRef = ref(null);
const submitting = ref(false);
const providerKeys = ref([]);
const selectedSavedKeyId = ref(null);
const currentStepIndex = ref(0);

// 批量导入相关
const isBatchImporting = ref(false);
const detectedModels = ref([]);

// 步骤引导
const guideSteps = [
  { title: '选择供应商', icon: Platform },
  { title: '配置密钥', icon: Key },
  { title: '填写信息', icon: Cpu }
];

// Provider选项 - 从API获取
const providerOptions = ref([]);

// 图标映射
const iconMap = {
  'Cpu': Cpu,
  'Moon': Moon,
  'Star': Star,
  'Connection': Connection,
  'Platform': Platform,
  'Monitor': Monitor,
  'Opportunity': Opportunity,
  'Sunrise': Sunrise
};

// 从API获取供应商列表并转换格式
const loadProviderOptions = async () => {
  try {
    const res = await getProviderList();
    providerOptions.value = (res || []).map(p => ({
      value: p.providerKey,
      label: p.providerName,
      description: p.description || '',
      icon: iconMap[p.icon] || Cpu
    }));
  } catch (e) {
    console.error('加载供应商列表失败', e);
  }
};

// 表单数据
const form = reactive({
  providerType: '',
  endpointUrl: '',
  apiKey: '',
  modelId: '',
  modelType: 'CHAT',
  modelName: '',
  description: ''
});

// 验证规则
const rules = computed(() => ({
  providerType: [
    { required: true, message: '请选择供应商类型', trigger: 'change' }
  ],
  endpointUrl: [
    { required: true, message: '请输入 API Endpoint', trigger: 'blur' }
  ],
  apiKey: [
    { required: form.providerType !== 'ollama', message: '请输入 API Key', trigger: 'blur' }
  ],
  modelId: [
    { required: true, message: '请输入模型标识符', trigger: 'blur' }
  ],
  modelType: [
    { required: true, message: '请选择模型类型', trigger: 'change' }
  ]
}));

// 当前Provider信息
const currentProviderInfo = computed(() => {
  const infoMap = {
    openai: {
      title: 'OpenAI 接入提示',
      type: 'info',
      description: '使用 OpenAI 官方 API 服务，需要 OpenAI 账号和 API Key。'
    },
    anthropic: {
      title: 'Anthropic Claude 接入提示',
      type: 'info',
      description: '使用 Anthropic Claude 模型，需要 Claude 账号和 API Key。'
    },
    deepseek: {
      title: 'DeepSeek 接入提示',
      type: 'info',
      description: '国产模型之光，支持标准 OpenAI API 格式。端点通常为 https://api.deepseek.com'
    },
    siliconflow: {
      title: 'SiliconFlow 接入提示',
      type: 'info',
      description: '提供 200+ 模型，便宜稳定。端点地址通常为 https://api.siliconflow.cn/v1'
    },
    dify: {
      title: 'Dify 接入提示',
      type: 'info',
      description: '如果您使用 Docker 部署了 Dify，请确保容器已启动并可以从 ORIN 访问。'
    },
    ollama: {
      title: 'Ollama 本地模型提示',
      type: 'info',
      description: 'Ollama 运行在本地，默认端口 11434。请确保 Ollama 服务已启动。'
    },
    zhipu: {
      title: '智谱 AI 接入提示',
      type: 'info',
      description: '使用智谱 AI 的 GLM 系列模型，需要智谱 AI 账号。'
    },
    dashscope: {
      title: '阿里云 DashScope 接入提示',
      type: 'info',
      description: '使用阿里云的通义千问系列模型，需要阿里云账号。'
    },
    huggingface: {
      title: 'HuggingFace 接入提示',
      type: 'info',
      description: '使用 HuggingFace 的开源模型，需要 HuggingFace 账号和 API Key。'
    }
  };
  return infoMap[form.providerType] || { title: '', type: 'info', description: '' };
});

onMounted(async () => {
  // 加载已保存的密钥
  try {
    providerKeys.value = await getExternalKeys();
  } catch (e) {
    console.error('加载密钥失败', e);
  }
  // 加载供应商列表
  await loadProviderOptions();
});

const goBack = () => {
  router.push('/dashboard/applications/models');
};

// Provider类型改变
const handleProviderChange = () => {
  form.endpointUrl = '';
  form.apiKey = '';
  form.modelId = '';
  selectedSavedKeyId.value = null;

  // 设置默认值
  if (form.providerType === 'openai') {
    form.endpointUrl = 'https://api.openai.com/v1';
  } else if (form.providerType === 'anthropic') {
    form.endpointUrl = 'https://api.anthropic.com';
  } else if (form.providerType === 'deepseek') {
    form.endpointUrl = 'https://api.deepseek.com/v1';
  } else if (form.providerType === 'siliconflow') {
    form.endpointUrl = 'https://api.siliconflow.cn/v1';
  } else if (form.providerType === 'dify') {
    form.endpointUrl = 'http://localhost:3000/v1';
  } else if (form.providerType === 'ollama') {
    form.endpointUrl = 'http://localhost:11434/v1';
  } else if (form.providerType === 'zhipu') {
    form.endpointUrl = 'https://open.bigmodel.cn/api/paas/v4';
  } else if (form.providerType === 'dashscope') {
    form.endpointUrl = 'https://dashscope.aliyuncs.com/compatible-mode/v1';
  } else if (form.providerType === 'huggingface') {
    form.endpointUrl = 'https://api-inference.huggingface.co';
  }
};

// 选择已保存密钥
const handleSavedKeySelect = (id) => {
  const keyMatch = providerKeys.value.find(k => k.id === id);
  if (keyMatch) {
    form.apiKey = keyMatch.apiKey;
    if (keyMatch.baseUrl) form.endpointUrl = keyMatch.baseUrl;
    ElMessage.info(`已应用保存的密钥: ${keyMatch.name}`);
  }
};

// 一键导入 SiliconFlow 全部模型 - 获取模型列表
const handleSiliconFlowBatchImport = async () => {
  if (!form.apiKey) {
    ElMessage.warning('请先输入 API Key');
    return;
  }

  isBatchImporting.value = true;

  try {
    // 分别获取不同 sub_type 的模型
    const subTypes = ['chat', 'embedding', 'reranker', 'text-to-image', 'image-to-image', 'speech-to-text', 'text-to-video'];
    let allModels = [];

    for (const subType of subTypes) {
      const response = await fetch(`https://api.siliconflow.cn/v1/models?sub_type=${subType}`, {
        method: 'GET',
        headers: {
          'Authorization': `Bearer ${form.apiKey}`,
          'Content-Type': 'application/json'
        }
      });

      if (response.ok) {
        const data = await response.json();
        const models = data.data || [];
        // 为每个模型添加 sub_type 字段
        models.forEach(m => {
          m._sub_type = subType;
        });
        allModels = [...allModels, ...models];
      }
    }

    console.log('SiliconFlow models:', allModels);

    if (allModels.length === 0) {
      ElMessage.warning('未获取到任何模型');
      return;
    }

    detectedModels.value = allModels;
    ElMessage.success(`成功识别到 ${allModels.length} 个模型，请选择要导入的模型`);
  } catch (e) {
    ElMessage.error('获取模型列表失败: ' + e.message);
  } finally {
    isBatchImporting.value = false;
  }
};

// 导入单个模型
const importSingleModel = async (model) => {
  try {
    const modelType = getModelTypeFromSiliconFlow(model);
    console.log('Importing model:', model.id, '_sub_type:', model._sub_type, 'mapped to:', modelType);
    await saveModel({
      name: model.id,
      modelId: model.id,
      provider: 'SiliconFlow',
      type: modelType,
      description: `SiliconFlow ${model._sub_type}`,
      status: 'ENABLED'
    });
    ElMessage.success(`成功导入模型: ${model.id} (${modelType})`);
    // 从列表中移除已导入的模型
    detectedModels.value = detectedModels.value.filter(m => m.id !== model.id);
  } catch (e) {
    ElMessage.error('导入失败: ' + (e.response?.data?.message || e.message));
  }
};

// 批量导入所有模型
const importAllModels = async () => {
  if (detectedModels.value.length === 0) {
    ElMessage.warning('没有可导入的模型');
    return;
  }

  submitting.value = true;
  try {
    let successCount = 0;
    for (const model of detectedModels.value) {
      const modelType = getModelTypeFromSiliconFlow(model);
      await saveModel({
        name: model.id,
        modelId: model.id,
        provider: 'SiliconFlow',
        type: modelType,
        description: `SiliconFlow ${model._sub_type}`,
        status: 'ENABLED'
      });
      successCount++;
    }

    ElMessage.success(`成功导入 ${successCount} 个模型`);
    goBack();
  } catch (e) {
    ElMessage.error('导入失败: ' + (e.response?.data?.message || e.message));
  } finally {
    submitting.value = false;
  }
};

// 根据 SiliconFlow API 返回的 sub_type 转换为模型类型
const getModelTypeFromSiliconFlow = (model) => {
  // 使用 _sub_type (根据 API 查询参数设置的)
  const subType = model._sub_type;
  const subTypeMap = {
    'chat': 'CHAT',
    'embedding': 'EMBEDDING',
    'reranker': 'RERANKER',
    'text-to-image': 'TEXT_TO_IMAGE',
    'image-to-image': 'TEXT_TO_IMAGE',
    'speech-to-text': 'SPEECH_TO_TEXT',
    'text-to-video': 'TEXT_TO_VIDEO'
  };

  if (subType && subTypeMap[subType]) {
    return subTypeMap[subType];
  }
  return 'CHAT';
};

const handleSubmit = async () => {
  const valid = await formRef.value.validate();
  if (!valid) return;

  submitting.value = true;
  try {
    await saveModel({
      name: form.modelName || form.modelId,
      modelId: form.modelId,
      provider: form.providerType,
      type: form.modelType,
      description: form.description,
      status: 'ENABLED'
    });
    ElMessage.success('添加成功');
    goBack();
  } catch (e) {
    ElMessage.error('添加失败: ' + (e.response?.data?.message || e.message));
  } finally {
    submitting.value = false;
  }
};
</script>

<style scoped>
.page-container {
  padding: 0;
}

.onboard-card {
  width: 100%;
}

.onboard-form {
  padding: var(--spacing-xl) var(--spacing-2xl);
  margin-top: var(--spacing-lg);
}

.onboard-stepper {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--spacing-2xl) var(--spacing-xl);
  background: var(--neutral-gray-50);
  border-bottom: 2px solid var(--neutral-gray-200);
  border-radius: var(--radius-xl) var(--radius-xl) 0 0;
}

.step-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-sm);
  position: relative;
  flex: 1;
  z-index: 1;
}

.step-icon-wrapper {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-full);
  display: flex;
  align-items: center;
  justify-content: center;
  background: white;
  color: var(--neutral-gray-400);
  border: 2px solid var(--neutral-gray-200);
  font-size: 24px;
  transition: all var(--transition-base);
  z-index: 2;
}

.step-text {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.step-label {
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.1em;
  color: var(--neutral-gray-500);
  font-weight: var(--font-bold);
}

.step-name {
  font-size: var(--text-base);
  font-weight: var(--font-semibold);
  color: var(--neutral-gray-900);
}

.step-line {
  position: absolute;
  top: 24px;
  left: 50%;
  width: 100%;
  height: 2px;
  background: var(--neutral-gray-200);
  z-index: 1;
  transition: all var(--transition-base);
}

/* Active State */
.step-item.active .step-icon-wrapper {
  background: var(--primary-color);
  color: white;
  border-color: var(--primary-color);
  box-shadow: 0 0 15px rgba(var(--orin-primary-rgb), 0.3);
}

.step-item.active .step-name {
  color: var(--primary-color);
}

.step-item.active .step-line {
  background: var(--primary-color);
}

/* Provider选项 */
.provider-option-content {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 4px 0;
}

.provider-icon {
  font-size: 20px;
  color: var(--primary-color);
}

.provider-info {
  flex: 1;
}

.provider-label {
  font-weight: 600;
  color: var(--neutral-gray-900);
}

.provider-desc {
  font-size: 12px;
  color: var(--neutral-gray-500);
}

/* SiliconFlow 分隔线 */
.siliconflow-divider {
  margin: 32px 0 24px 0;
}

/* 分隔线文字 */
.divider-text {
  font-size: 14px;
  color: var(--neutral-gray-500);
}

/* 批量导入区域 */
.batch-import-section {
  padding: 24px;
  background: var(--neutral-gray-50);
  border-radius: 8px;
  text-align: center;
}

/* 识别到的模型列表 */
.detected-models-section {
  margin-top: 24px;
  padding: 20px;
  background: var(--neutral-gray-50);
  border-radius: 8px;
}

.batch-actions {
  margin-top: 20px;
  display: flex;
  justify-content: center;
  gap: 16px;
}

/* 底部按钮 */
.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: var(--spacing-xl) var(--spacing-2xl);
  border-top: 1px solid var(--neutral-gray-200);
}
</style>
