<template>
  <div class="page-container">
    <PageHeader 
      title="接入新 Agent" 
      description="选择 Provider 类型，配置连接信息，快速纳管您的智能体实例"
      icon="CirclePlus"
    />


    <el-card shadow="hover" class="onboard-card">
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
        <!-- Provider类型选择 -->
        <el-form-item label="Provider 类型" prop="providerType">
          <el-select 
            v-model="form.providerType" 
            placeholder="请选择Provider类型"
            size="large"
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

        <!-- 动态表单字段 -->
        <template v-if="form.providerType === 'dify'">
          <el-form-item label="Dify API Endpoint" prop="endpointUrl">
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

          <el-form-item label="App API Key" prop="apiKey">
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
            </el-input>
          </el-form-item>

          <el-form-item label="Dataset API Key（可选）" prop="datasetApiKey">
            <el-input 
              v-model.trim="form.datasetApiKey" 
              type="password" 
              show-password 
              placeholder="dataset-..."
              size="large"
            >
              <template #prefix>
                <el-icon><Collection /></el-icon>
              </template>
            </el-input>
            <template #extra>
              <span style="color: var(--neutral-gray-500); font-size: var(--text-sm);">
                用于同步知识库，如不需要可留空
              </span>
            </template>
          </el-form-item>
        </template>

        <template v-else-if="form.providerType === 'siliconflow'">
          <el-form-item label="SiliconFlow API Endpoint" prop="endpointUrl">
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
            </el-input>
          </el-form-item>

          <el-form-item label="智能体名称（可选）" prop="agentName">
            <el-input 
              v-model.trim="form.agentName" 
              placeholder="自定义智能体名称，留空则自动生成"
              size="large"
            >
              <template #prefix>
                <el-icon><User /></el-icon>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item label="模型名称" prop="model">
            <el-input 
              v-model.trim="form.model" 
              placeholder="Qwen/Qwen2-7B-Instruct"
              size="large"
            >
              <template #prefix>
                <el-icon><Service /></el-icon>
              </template>
            </el-input>
            <template #extra>
              <span style="color: var(--neutral-gray-500); font-size: var(--text-sm);">
                例如：Pro/zai-org/GLM-4.7 或 Qwen/Qwen2-7B-Instruct
              </span>
            </template>
          </el-form-item>
        </template>

        <template v-else-if="form.providerType === 'openai'">
          <el-form-item label="OpenAI API Endpoint" prop="endpointUrl">
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
            </el-input>
          </el-form-item>

          <el-form-item label="Organization ID（可选）" prop="organizationId">
            <el-input 
              v-model.trim="form.organizationId" 
              placeholder="org-..."
              size="large"
            >
              <template #prefix>
                <el-icon><OfficeBuilding /></el-icon>
              </template>
            </el-input>
          </el-form-item>
        </template>

        <template v-else-if="form.providerType === 'local'">
          <el-form-item label="本地模型 API Endpoint" prop="endpointUrl">
            <el-input 
              v-model.trim="form.endpointUrl" 
              placeholder="http://localhost:11434/v1"
              size="large"
            >
              <template #prefix>
                <el-icon><Link /></el-icon>
              </template>
            </el-input>
            <template #extra>
              <span style="color: var(--neutral-gray-500); font-size: var(--text-sm);">
                支持Ollama、LocalAI等本地模型服务
              </span>
            </template>
          </el-form-item>

          <el-form-item label="模型名称" prop="model">
            <el-input 
              v-model.trim="form.model" 
              placeholder="llama2"
              size="large"
            >
              <template #prefix>
                <el-icon><Service /></el-icon>
              </template>
            </el-input>
          </el-form-item>
        </template>

        <!-- 操作按钮 -->
        <div v-if="form.providerType" class="action-bar" style="margin-top: var(--spacing-2xl); gap: var(--spacing-md);">
          <el-button 
            type="primary" 
            @click="testConnection" 
            :loading="testLoading"
            size="large"
          >
            <el-icon v-if="!testLoading"><Connection /></el-icon>
            {{ testLoading ? '测试连接中...' : '测试连接' }}
          </el-button>
          
          <el-button 
            type="success" 
            @click="onSubmit" 
            :loading="loading"
            size="large"
          >
            <el-icon v-if="!loading"><Check /></el-icon>
            立即接入
          </el-button>
          
          <el-button @click="handleReset" size="large">
            <el-icon><RefreshLeft /></el-icon>
            重置
          </el-button>
          
          <el-button @click="$router.back()" size="large">
            取消
          </el-button>
        </div>

        <div v-else class="action-bar" style="margin-top: var(--spacing-2xl);">
          <el-button @click="$router.back()" size="large">
            取消并返回列表
          </el-button>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue';
import { useRouter } from 'vue-router';
import { onboardAgent } from '../api/agent';
import { testDifyConnection, testSiliconFlowConnection } from '../api/modelConfig';
import { onboardSiliconFlowAgent } from '../api/siliconFlowAgent';
import PageHeader from '@/components/PageHeader.vue';
import { ElMessage } from 'element-plus';
import { 
  Link, Key, InfoFilled, Collection, Service, OfficeBuilding,
  Connection, Check, RefreshLeft, Platform, Cpu, Document, Monitor, CirclePlus,
  Opportunity, Star, Sunrise, Moon
} from '@element-plus/icons-vue';

const router = useRouter();
const formRef = ref(null);
const loading = ref(false);
const testLoading = ref(false);
const connectionTested = ref(false);

const currentStepIndex = computed(() => {
  if (loading.value) return 2; // Step 03: 完成接入
  if (form.providerType) return 1; // Step 02: 配置与测试
  return 0; // Step 01: 选择 Provider
});

// Provider选项
const providerOptions = [
  {
    value: 'dify',
    label: 'Dify',
    icon: Platform,
    description: '开源 AI 应用开发平台'
  },
  {
    value: 'siliconflow',
    label: '硅基流动',
    icon: Cpu,
    description: '高性能 AI 推理服务'
  },
  {
    value: 'deepseek',
    label: 'DeepSeek',
    icon: Opportunity,
    description: '国产之光 - 极高性价比大模型'
  },
  {
    value: 'google',
    label: 'Google Gemini',
    icon: Star,
    description: '谷歌多模态大模型系列'
  },
  {
    value: 'anthropic',
    label: 'Anthropic Claude',
    icon: Sunrise,
    description: '顶级逻辑与创意对话模型'
  },
  {
    value: 'moonshot',
    label: 'Moonshot (Kimi)',
    icon: Moon,
    description: '超长文本处理专家'
  },
  {
    value: 'zhipu',
    label: '智谱 AI',
    icon: Connection,
    description: '中英双语性能领先大模型'
  },
  {
    value: 'openai',
    label: 'OpenAI',
    icon: Platform,
    description: 'GPT 系列模型官方服务'
  },
  {
    value: 'local',
    label: '本地模型',
    icon: Monitor,
    description: 'Ollama、LocalAI 等私有化部署'
  }
];

// 表单数据
const form = reactive({
  providerType: '',
  endpointUrl: '',
  apiKey: '',
  datasetApiKey: '',
  model: '',
  organizationId: ''
});

// 验证规则
const rules = computed(() => {
  const baseRules = {
    providerType: [
      { required: true, message: '请选择Provider类型', trigger: 'change' }
    ],
    endpointUrl: [
      { required: true, message: '请输入API Endpoint', trigger: 'blur' }
    ],
    apiKey: [
      { required: true, message: '请输入API Key', trigger: 'blur' }
    ]
  };

  if (form.providerType === 'siliconflow' || form.providerType === 'local') {
    baseRules.model = [
      { required: true, message: '请输入模型名称', trigger: 'blur' }
    ];
  }

  return baseRules;
});

// 当前Provider信息
const currentProviderInfo = computed(() => {
  const infoMap = {
    dify: {
      title: 'Dify 接入提示',
      type: 'info',
      description: '如果您使用 Docker 部署了 Dify，请确保容器已启动并可以从 ORIN 访问。默认地址通常是 http://localhost:3000/v1'
    },
    siliconflow: {
      title: '硅基流动接入提示',
      type: 'info',
      description: '请提供硅基流动 API 的访问凭证和模型信息。端点地址通常为 https://api.siliconflow.cn/v1'
    },
    deepseek: {
      title: 'DeepSeek 接入提示',
      type: 'info',
      description: '国产模型之光，支持标准 OpenAI API 格式。端点通常为 https://api.deepseek.com'
    },
    google: {
      title: 'Gemini 接入提示',
      type: 'warning',
      description: '接入 Gemini 需确保网络环境能够访问 Google 服务，并使用 API Key 认证。'
    },
    anthropic: {
      title: 'Claude 接入提示',
      type: 'info',
      description: 'Anthropic 官方 API 接口，逻辑能力卓越。'
    },
    moonshot: {
      title: 'Moonshot (Kimi) 接入提示',
      type: 'success',
      description: '支持超长内容上下文处理。端点 https://api.moonshot.cn/v1'
    },
    zhipu: {
      title: '智谱 AI 接入提示',
      type: 'info',
      description: '国内领先模型。端点 https://open.bigmodel.cn/api/paas/v4'
    },
    openai: {
      title: 'OpenAI 接入提示',
      type: 'info',
      description: '请提供您的 OpenAI API Key。如果使用组织账号，请同时提供 Organization ID'
    },
    local: {
      title: '本地模型接入提示',
      type: 'success',
      description: '支持 Ollama、LocalAI 等本地模型服务。请确保服务已启动并可访问'
    }
  };
  return infoMap[form.providerType] || { title: '', type: 'info', description: '' };
});

// 接入指南步骤
const guideSteps = [
  {
    icon: Platform,
    title: '选择Provider',
    description: '选择您要接入的AI服务提供商'
  },
  {
    icon: Connection,
    title: '测试连接',
    description: '验证配置信息是否正确'
  },
  {
    icon: Check,
    title: '完成接入',
    description: '开始管理和监控您的Agent'
  }
];

// Provider类型改变
const handleProviderChange = () => {
  connectionTested.value = false;
  // 重置表单字段
  form.endpointUrl = '';
  form.apiKey = '';
  form.datasetApiKey = '';
  form.model = '';
  form.organizationId = '';
  
  // 设置默认值
  if (form.providerType === 'dify') {
    form.endpointUrl = 'http://localhost:3000/v1';
  } else if (form.providerType === 'siliconflow') {
    form.endpointUrl = 'https://api.siliconflow.cn/v1';
    form.model = 'Qwen/Qwen2-7B-Instruct';
  } else if (form.providerType === 'deepseek') {
    form.endpointUrl = 'https://api.deepseek.com';
    form.model = 'deepseek-chat';
  } else if (form.providerType === 'google') {
    form.endpointUrl = 'https://generativelanguage.googleapis.com';
    form.model = 'gemini-pro';
  } else if (form.providerType === 'moonshot') {
    form.endpointUrl = 'https://api.moonshot.cn/v1';
    form.model = 'moonshot-v1-8k';
  } else if (form.providerType === 'zhipu') {
    form.endpointUrl = 'https://open.bigmodel.cn/api/paas/v4';
    form.model = 'glm-4';
  } else if (form.providerType === 'openai') {
    form.endpointUrl = 'https://api.openai.com/v1';
    form.model = 'gpt-3.5-turbo';
  } else if (form.providerType === 'anthropic') {
    form.endpointUrl = 'https://api.anthropic.com/v1';
    form.model = 'claude-3-opus-20240229';
  } else if (form.providerType === 'local') {
    form.endpointUrl = 'http://localhost:11434/v1';
    form.model = 'llama2';
  }
};

// 重置表单
const handleReset = () => {
  if (formRef.value) {
    formRef.value.resetFields();
  }
  ElMessage.info('表单已重置');
};

// 测试连接
const testConnection = async () => {
  if (!formRef.value) return;
  
  await formRef.value.validate(async (valid) => {
    if (valid) {
      testLoading.value = true;
      try {
        if (form.providerType === 'dify') {
          const response = await testDifyConnection(form.endpointUrl, form.apiKey);
          if (response.data) {
            ElMessage.success('Dify 连接测试成功！');
            connectionTested.value = true;
          } else {
            ElMessage.error('Dify 连接测试失败，请检查配置信息');
          }
        } else if (form.providerType === 'siliconflow') {
          const response = await testSiliconFlowConnection(
            form.endpointUrl, 
            form.apiKey, 
            form.model
          );
          if (response) {
            ElMessage.success('硅基流动连接测试成功！');
            connectionTested.value = true;
          } else {
            ElMessage.error('硅基流动连接测试失败，请检查配置信息');
          }
        } else {
          ElMessage.warning('该Provider暂不支持连接测试');
        }
      } catch (e) {
        ElMessage.error('连接测试失败: ' + (e.response?.data?.message || e.message));
      } finally {
        testLoading.value = false;
      }
    }
  });
};

// 提交表单
const onSubmit = async () => {
  if (!formRef.value) return;
  
  await formRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true;
      try {
        if (form.providerType === 'dify') {
          await onboardAgent({
            endpointUrl: form.endpointUrl,
            apiKey: form.apiKey,
            datasetApiKey: form.datasetApiKey
          });
          ElMessage.success('Dify Agent 接入成功！');
        } else if (form.providerType === 'siliconflow') {
          await onboardSiliconFlowAgent(
            form.endpointUrl, 
            form.apiKey, 
            form.model,
            form.agentName
          );
          ElMessage.success('硅基流动 Agent 接入成功！');
        } else {
          ElMessage.warning('该Provider接入功能正在开发中');
          loading.value = false;
          return;
        }
        
        setTimeout(() => {
          router.push('/dashboard/agent/list');
        }, 1500);
      } catch (e) {
        console.error('Onboard error:', e);
        ElMessage.error('Agent 接入失败: ' + (e.response?.data?.message || e.message));
        loading.value = false;
      }
    } else {
      ElMessage.warning('请完善表单必填项');
    }
  });
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
  box-shadow: 0 0 15px rgba(var(--primary-color-rgb), 0.3);
}

.step-item.active .step-name {
  color: var(--primary-color);
}

.step-item.active .step-line {
  background: var(--primary-color);
}

html.dark .onboard-stepper {
  background: rgba(255, 255, 255, 0.03);
  border-color: var(--neutral-gray-300);
}

html.dark .step-icon-wrapper {
  background: var(--neutral-gray-200);
  border-color: var(--neutral-gray-300);
}

html.dark .step-name {
  color: var(--neutral-gray-100);
}

html.dark .step-line {
  background: var(--neutral-gray-300);
}

html.dark .step-item.active .step-line {
  background: var(--primary-color);
}

@media (max-width: 992px) {
  .onboard-stepper {
    flex-wrap: wrap;
    gap: var(--spacing-lg);
  }
  .step-line {
    display: none;
  }
}

/* Provider Option Styling */
.provider-option {
  height: auto !important;
  padding: 8px 12px !important;
}

.provider-option-content {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  white-space: normal;
}

.provider-icon {
  font-size: 20px;
  color: var(--primary-color);
  margin-top: 4px;
}

.provider-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.provider-label {
  font-weight: 600;
  color: var(--neutral-gray-900);
}

.provider-desc {
  font-size: 12px;
  color: var(--neutral-gray-500);
  line-height: 1.4;
}

html.dark .provider-label {
  color: var(--neutral-gray-100);
}
</style>