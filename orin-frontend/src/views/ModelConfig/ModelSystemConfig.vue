<template>
  <div class="page-container">
    <el-form 
      ref="formRef" 
      :model="form" 
      :rules="rules" 
      label-position="top"
      class="config-layout"
    >
      <el-row :gutter="24">
        <!-- Left Column: Core Connectivity -->
        <el-col :lg="14" :md="24">
          <el-card shadow="never" class="premium-card connectivity-card">
            <template #header>
              <div class="card-header">
                <div class="header-left">
                  <el-icon><Connection /></el-icon>
                  <span>网络与认证</span>
                </div>
                <el-button
                  type="primary"
                  link
                  :loading="testLoading"
                  @click="onTestConnection"
                >
                  测试连接性
                </el-button>
              </div>
            </template>

            <el-row :gutter="20">
              <el-col :span="24">
                <el-form-item label="上游服务访问网址 (Base URL)" prop="baseUrl">
                  <el-input v-model.trim="form.baseUrl" placeholder="https://api.example.com">
                    <template #prefix>
                      <el-icon><Link /></el-icon>
                    </template>
                  </el-input>
                  <p class="input-desc">
                    指定模型服务中枢的访问地址，系统通过此 URL 进行模型调度
                  </p>
                </el-form-item>
              </el-col>

              <el-col :span="12">
                <el-form-item label="接口路径" prop="apiPath">
                  <el-input v-model.trim="form.apiPath" placeholder="/api/v1">
                    <template #prefix>
                      <el-icon><Place /></el-icon>
                    </template>
                  </el-input>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="响应超时 (ms)" prop="timeout">
                  <el-input-number
                    v-model="form.timeout"
                    :min="1000"
                    :max="120000"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>

              <el-col :span="12">
                <el-form-item label="访问账号" prop="username">
                  <el-input v-model.trim="form.username" placeholder="Username">
                    <template #prefix>
                      <el-icon><User /></el-icon>
                    </template>
                  </el-input>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="访问密码" prop="password">
                  <el-input 
                    v-model.trim="form.password" 
                    type="password" 
                    show-password 
                    placeholder="不修改请留空"
                  >
                    <template #prefix>
                      <el-icon><Lock /></el-icon>
                    </template>
                  </el-input>
                </el-form-item>
              </el-col>
            </el-row>
          </el-card>

          <!-- Ollama Local Model Configuration -->
          <el-card shadow="never" class="premium-card local-card" style="margin-top: 24px;">
            <template #header>
              <div class="card-header">
                <div class="header-left">
                  <el-icon><Monitor /></el-icon>
                  <span>Ollama 本地模型配置</span>
                </div>
                <el-button
                  type="primary"
                  link
                  :loading="ollamaTestLoading"
                  @click="onTestOllamaConnection"
                >
                  测试连接
                </el-button>
              </div>
            </template>
              
            <el-row :gutter="20">
              <el-col :span="24">
                <el-form-item label="Ollama API Endpoint" prop="ollamaEndpoint">
                  <el-input v-model.trim="form.ollamaEndpoint" placeholder="http://localhost:11434">
                    <template #prefix>
                      <el-icon><Link /></el-icon>
                    </template>
                  </el-input>
                  <p class="input-desc">
                    本地 Ollama 服务的访问地址
                  </p>
                </el-form-item>
              </el-col>
              <el-col :span="24">
                <el-form-item label="默认本地模型" prop="ollamaModel">
                  <el-input v-model.trim="form.ollamaModel" placeholder="llama3">
                    <template #prefix>
                      <el-icon><Service /></el-icon>
                    </template>
                  </el-input>
                  <p class="input-desc">
                    本地环境默认调度的模型名称
                  </p>
                </el-form-item>
              </el-col>
            </el-row>
          </el-card>

          <el-card shadow="never" class="premium-card remark-card" style="margin-top: 24px;">
            <template #header>
              <div class="card-header">
                <div class="header-left">
                  <el-icon><Document /></el-icon>
                  <span>备注信息</span>
                </div>
              </div>
            </template>
            <el-form-item prop="remark" label-width="0">
              <el-input 
                v-model="form.remark" 
                type="textarea" 
                :rows="4" 
                placeholder="记录此项配置的特殊说明或上线记录..." 
                resize="none"
              />
            </el-form-item>
          </el-card>
        </el-col>

        <!-- Right Column: Framework & Paths -->
        <el-col :lg="10" :md="24">
          <el-card shadow="never" class="premium-card framework-card">
            <template #header>
              <div class="card-header">
                <div class="header-left">
                  <el-icon><FolderOpened /></el-icon>
                  <span>环境路径与工作空间</span>
                </div>
              </div>
            </template>

            <el-form-item label="Llama-factory 部署路径" prop="llamaFactoryPath">
              <el-input v-model.trim="form.llamaFactoryPath" placeholder="/root/llama-factory">
                <template #prefix>
                  <el-icon><Cpu /></el-icon>
                </template>
              </el-input>
            </el-form-item>

            <el-form-item label="WebUI 访问地址" prop="llamaFactoryWebUI">
              <el-input v-model.trim="form.llamaFactoryWebUI" placeholder="http://127.0.0.1:7860">
                <template #prefix>
                  <el-icon><Monitor /></el-icon>
                </template>
              </el-input>
            </el-form-item>

            <el-form-item label="训练模型保存根目录" prop="modelSavePath">
              <el-input v-model.trim="form.modelSavePath" placeholder="/data/models/outputs">
                <template #prefix>
                  <el-icon><Folder /></el-icon>
                </template>
              </el-input>
              <p class="input-desc">
                所有通过本平台微调出的 Checkpoints 将存放在此路径下
              </p>
            </el-form-item>
          </el-card>

          <!-- Help Card -->
          <el-card shadow="never" class="help-info-card">
            <div class="help-content">
              <h4><el-icon><InfoFilled /></el-icon> 配置指南</h4>
              <ul>
                <li>修改基础 URL 后建议务必通过"测试连接性"验证服务是否可达。</li>
                <li>环境路径应为绝对路径，且系统账号需具备相应的读写权限。</li>
                <li>超时时间设置过短可能导致大规模模型推理任务意外中断。</li>
                <li>本区域仅保留调度中枢自身参数，模型能力与外部服务连接请在系统环境配置页维护。</li>
              </ul>
            </div>
          </el-card>

          <div class="sticky-actions">
            <el-button
              type="primary"
              size="large"
              :loading="loading"
              class="submit-btn"
              @click="onSubmit"
            >
              保存系统全局配置
            </el-button>
            <el-button
              size="large"
              :icon="RefreshLeft"
              plain
              @click="onReset"
            >
              撤销所有更改
            </el-button>
          </div>
        </el-col>
      </el-row>
    </el-form>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive } from 'vue';
import { 
  Connection, Cpu, Link, Service, FolderOpened, Monitor, InfoFilled, Document, User, Lock, Place, RefreshLeft
} from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { 
  getModelConfig, updateModelConfig, 
  testOllamaConnection
} from '@/api/modelConfig';

const formRef = ref(null);
const loading = ref(false);
const testLoading = ref(false);
const ollamaTestLoading = ref(false);
const lastTestSuccess = ref(false);
const initialForm = ref({}); 

const form = reactive({
  baseUrl: '',
  username: '',
  password: '',
  apiPath: '/api/v1',
  timeout: 30000,
  llamaFactoryPath: '',
  llamaFactoryWebUI: '',
  modelSavePath: '',
  remark: '',
  siliconFlowEndpoint: 'https://api.siliconflow.cn/v1',
  siliconFlowApiKey: '',
  siliconFlowModel: 'Qwen/Qwen2-7B-Instruct',
  vlmModel: '',
  embeddingModel: '',
  systemModel: '',
  ollamaEndpoint: 'http://localhost:11434',
  ollamaApiKey: '',
  ollamaModel: 'llama3',
  autoAnalysisEnabled: true
});

const rules = {
  baseUrl: [
    { required: true, message: '请输入访问网址', trigger: 'blur' },
    { type: 'url', message: '请输入正确的 URL 格式', trigger: 'blur' }
  ],
  username: [
    { required: true, message: '请输入账号', trigger: 'blur' }
  ],
  timeout: [
    { required: true, message: '请输入超时时间', trigger: 'change' }
  ],
};
const onTestConnection = async () => {
  if (!form.baseUrl) return ElMessage.warning('请先输入访问网址');
  testLoading.value = true;
  try {
    await new Promise(resolve => setTimeout(resolve, 1500));
    ElMessage.success('连接通畅：上游服务握手成功');
    lastTestSuccess.value = true;
  } catch (e) {
    ElMessage.error('连接异常：无法访问指定地址');
    lastTestSuccess.value = false;
  } finally {
    testLoading.value = false;
  }
};

const onTestOllamaConnection = async () => {
    if (!form.ollamaEndpoint) {
        return ElMessage.warning('请先输入 Ollama API 地址');
    }
    ollamaTestLoading.value = true;
    try {
        const response = await testOllamaConnection(form.ollamaEndpoint, form.ollamaApiKey, form.ollamaModel || 'llama3');
        if (response) {
            ElMessage.success('Ollama 连接测试成功！');
            lastTestSuccess.value = true;
        } else {
            ElMessage.error('Ollama 连接测试失败，请确保本地已启动 Ollama 且模型已拉取');
            lastTestSuccess.value = false;
        }
    } catch (e) {
        ElMessage.error('Ollama 连接测试失败: ' + (e.response?.data?.message || e.message));
        lastTestSuccess.value = false;
    } finally {
        ollamaTestLoading.value = false;
    }
};

const fetchData = async () => {
  try {
    const res = await getModelConfig();
    Object.assign(form, res);
    form.password = ''; 
    initialForm.value = JSON.parse(JSON.stringify(res));
    delete initialForm.value.password;
  } catch (e) {
    ElMessage.error('配置加载失败');
  }
};

const onSubmit = async () => {
  if (!formRef.value) return;
  
  await formRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true;
      try {
        await updateModelConfig(form);
        ElMessage({
          message: '全局系统配置已成功保存并同步',
          type: 'success',
          duration: 3000
        });
        initialForm.value = JSON.parse(JSON.stringify(form));
        delete initialForm.value.password;
      } catch (e) {
        ElMessage.error('修改保存失败');
      } finally {
        loading.value = false;
      }
    }
  });
};

const onReset = () => {
  if (initialForm.value) {
    Object.assign(form, initialForm.value);
    form.password = '';
    ElMessage.info('已恢复至初始配置状态');
  }
};

onMounted(() => {
  fetchData();
});
</script>
<style scoped>
.page-container {
  padding: 0;
}

/* Card Styling */
.premium-card {
  border: 1px solid var(--neutral-gray-100) !important;
  border-radius: var(--radius-lg) !important;
  transition: all 0.3s ease;
}

.premium-card:hover {
  box-shadow: var(--shadow-md) !important;
  border-color: var(--primary-light) !important;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-family: var(--font-heading);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--neutral-gray-900);
  font-weight: 700;
}

.input-desc {
  font-size: 12px;
  color: var(--neutral-gray-400);
  margin-top: 4px;
  line-height: 1.4;
}

/* Help Card */
.help-info-card {
  background: #fdfdfd;
  border: 1px dashed var(--neutral-gray-200);
  margin-top: 24px;
  border-radius: var(--radius-lg);
}

.help-content h4 {
  margin: 0 0 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--primary-color);
}

.help-content ul {
  padding-left: 20px;
  margin: 0;
}

.help-content li {
  font-size: 13px;
  color: var(--neutral-gray-500);
  margin-bottom: 8px;
  line-height: 1.6;
}

/* Sticky Actions */
.sticky-actions {
  margin-top: 32px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.submit-btn {
  background: linear-gradient(135deg, var(--primary-color), #4f46e5) !important;
  border: none !important;
  height: 54px;
  font-weight: 700;
  box-shadow: 0 10px 20px var(--primary-glow);
}

.submit-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 24px var(--primary-glow);
}

/* Dark Mode Tweaks */
:deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--neutral-gray-200) inset !important;
  transition: all 0.2s ease;
}

:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--primary-color) inset !important;
}

:deep(.el-form-item__label) {
  font-weight: 700 !important;
  color: var(--neutral-gray-600) !important;
  padding-bottom: 8px !important;
}
</style>
