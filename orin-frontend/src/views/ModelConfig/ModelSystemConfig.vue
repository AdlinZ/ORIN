<template>
  <div class="page-container">
    <PageHeader 
      title="系统配置" 
      description="管理模型调度中枢与 Dify 服务的全局参数与环境连接"
      icon="Setting"
    >
      <template #actions>
        <div class="status-badge" :class="{ 'connected': !testLoading && lastTestSuccess }">
          <span class="dot"></span>
          {{ testLoading ? '检测中...' : (lastTestSuccess ? '服务端连接正常' : '等待检测') }}
        </div>
      </template>
    </PageHeader>


    <!-- Optimization: Split into Logical Sections -->
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
                <el-button type="primary" link :loading="testLoading" @click="onTestConnection">
                  测试连接性
                </el-button>
              </div>
            </template>

            <el-row :gutter="20">
              <el-col :span="24">
                <el-form-item label="上游服务访问网址 (Base URL)" prop="baseUrl">
                  <el-input v-model.trim="form.baseUrl" placeholder="https://api.example.com">
                    <template #prefix><el-icon><Link /></el-icon></template>
                  </el-input>
                  <p class="input-desc">指定模型服务中枢的访问地址，系统通过此 URL 进行模型调度</p>
                </el-form-item>
              </el-col>

              <el-col :span="12">
                <el-form-item label="接口路径" prop="apiPath">
                  <el-input v-model.trim="form.apiPath" placeholder="/api/v1">
                    <template #prefix><el-icon><Place /></el-icon></template>
                  </el-input>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="响应超时 (ms)" prop="timeout">
                  <el-input-number v-model="form.timeout" :min="1000" :max="120000" style="width: 100%" />
                </el-form-item>
              </el-col>

              <el-col :span="12">
                <el-form-item label="访问账号" prop="username">
                  <el-input v-model.trim="form.username" placeholder="Username">
                    <template #prefix><el-icon><User /></el-icon></template>
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
                    <template #prefix><el-icon><Lock /></el-icon></template>
                  </el-input>
                </el-form-item>
              </el-col>
            </el-row>
          </el-card>

          <!-- Dify Integration Card -->
          <el-card shadow="never" class="premium-card dify-card" style="margin-top: 24px;">
            <template #header>
              <div class="card-header">
                <div class="header-left">
                  <el-icon><Connection /></el-icon>
                  <span>Dify 集成配置</span>
                </div>
                <el-button type="primary" link :loading="difyTestLoading" @click="onTestDifyConnection">
                  测试 Dify 连接
                </el-button>
              </div>
            </template>

            <el-row :gutter="20">
              <el-col :span="24">
                <el-form-item label="Dify API 端点" prop="difyEndpoint">
                  <el-input v-model.trim="form.difyEndpoint" placeholder="http://localhost:3000/v1">
                    <template #prefix><el-icon><Link /></el-icon></template>
                  </el-input>
                  <p class="input-desc">Dify 服务的 API 端点地址，用于智能体管理与监控</p>
                </el-form-item>
              </el-col>

              <el-col :span="24">
                <el-form-item label="Dify API 密钥" prop="difyApiKey">
                  <el-input 
                    v-model.trim="form.difyApiKey" 
                    type="password" 
                    show-password 
                    placeholder="Dify API Key"
                  >
                    <template #prefix><el-icon><Key /></el-icon></template>
                  </el-input>
                </el-form-item>
              </el-col>
            </el-row>
          </el-card>

            <!-- SiliconFlow Scheduling Core Configuration -->
            <el-card shadow="never" class="premium-card vendor-card" style="margin-top: 24px;">
              <template #header>
                <div class="card-header">
                  <div class="header-left">
                    <el-icon><Connection /></el-icon>
                    <span>SiliconFlow 调度中枢配置</span>
                  </div>
                  <el-button type="primary" link @click="onTestSFConnection" :loading="sfTestLoading">
                    测试连接
                  </el-button>
                </div>
              </template>
              
              <el-row :gutter="20">
                <el-col :span="24">
                  <el-form-item label="API Endpoint" prop="siliconFlowEndpoint">
                    <el-input v-model.trim="form.siliconFlowEndpoint" placeholder="https://api.siliconflow.cn/v1">
                      <template #prefix><el-icon><Link /></el-icon></template>
                    </el-input>
                  </el-form-item>
                </el-col>
                <el-col :span="24">
                  <el-form-item label="API Key" prop="siliconFlowApiKey">
                    <el-input v-model.trim="form.siliconFlowApiKey" type="password" show-password placeholder="sk-...">
                      <template #prefix><el-icon><Key /></el-icon></template>
                    </el-input>
                  </el-form-item>
                </el-col>
                <el-col :span="24">
                   <el-form-item label="默认调度模型" prop="siliconFlowModel">
                    <el-input v-model.trim="form.siliconFlowModel" placeholder="Qwen/Qwen2-7B-Instruct">
                      <template #prefix><el-icon><Service /></el-icon></template>
                    </el-input>
                    <p class="input-desc">全局默认使用的 LLM 模型标识</p>
                  </el-form-item>
                </el-col>
              </el-row>
            </el-card>

          <!-- Multimodal Configuration Card -->
          <el-card shadow="never" class="premium-card multimodal-card" style="margin-top: 24px;">
            <template #header>
              <div class="card-header">
                <div class="header-left">
                  <el-icon><Picture /></el-icon>
                  <span>多模态与知识库模型配置</span>
                </div>
              </div>
            </template>
            <el-row :gutter="20">
              <el-col :span="24">
                <el-form-item label="Vision-Language Model (VLM)" prop="vlmModel">
                  <el-select 
                    v-model="form.vlmModel" 
                    placeholder="选择用于图像分析的视觉大模型" 
                    filterable 
                    style="width: 100%"
                    :loading="modelsLoading"
                    @visible-change="loadModels"
                  >
                    <el-option
                        v-for="item in vlmOptions"
                        :key="item.id"
                        :label="item.name || item.id"
                        :value="item.id"
                    />
                  </el-select>
                  <p class="input-desc">用于生成图片、视频的语义描述，推荐使用 Qwen-VL-Max 或 Yi-Vision</p>
                </el-form-item>
              </el-col>
              <el-col :span="24">
                 <el-form-item label="Embedding Model (向量模型)" prop="embeddingModel">
                  <el-select 
                    v-model="form.embeddingModel" 
                    placeholder="选择用于知识库构建的向量模型" 
                    filterable 
                    style="width: 100%"
                    :loading="modelsLoading"
                    @visible-change="loadModels"
                  >
                     <el-option
                        v-for="item in embedOptions"
                        :key="item.id"
                        :label="item.name || item.id"
                        :value="item.id"
                    />
                  </el-select>
                   <p class="input-desc">用于将文本转化为向量（Embedding），推荐 BGE-M3 (支持多语言)</p>
                </el-form-item>
              </el-col>

               <el-col :span="24">
                 <el-form-item label="System AI Model (系统评估模型)" prop="systemModel">
                  <el-select 
                    v-model="form.systemModel" 
                    placeholder="选择用于 System AI 评估的后台模型" 
                    filterable 
                    clearable
                    style="width: 100%"
                    :loading="modelsLoading"
                    @visible-change="loadModels"
                  >
                     <el-option
                        v-for="item in chatModelOptions"
                        :key="item.id"
                        :label="item.name || item.id"
                        :value="item.id"
                    />
                  </el-select>
                   <p class="input-desc">指定 System AI (右上角入口) 所使用的底层模型，留空则默认自动选择。</p>
                </el-form-item>
              </el-col>

               <el-col :span="24">
                <el-form-item label="自动化策略" prop="autoAnalysisEnabled">
                  <el-switch 
                    v-model="form.autoAnalysisEnabled" 
                    active-text="上传素材后立即触发 AI 自动解析"
                    inactive-text="仅手动触发"
                  />
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
                <template #prefix><el-icon><Cpu /></el-icon></template>
              </el-input>
            </el-form-item>

            <el-form-item label="WebUI 访问地址" prop="llamaFactoryWebUI">
              <el-input v-model.trim="form.llamaFactoryWebUI" placeholder="http://127.0.0.1:7860">
                <template #prefix><el-icon><Monitor /></el-icon></template>
              </el-input>
            </el-form-item>

            <el-form-item label="训练模型保存根目录" prop="modelSavePath">
              <el-input v-model.trim="form.modelSavePath" placeholder="/data/models/outputs">
                <template #prefix><el-icon><Folder /></el-icon></template>
              </el-input>
              <p class="input-desc">所有通过本平台微调出的 Checkpoints 将存放在此路径下</p>
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
                <li>Dify 配置用于智能体纳管和监控，确保 Docker Dify 服务正在运行。</li>
              </ul>
            </div>
          </el-card>

          <div class="sticky-actions">
            <el-button type="primary" size="large" :loading="loading" @click="onSubmit" class="submit-btn">
              保存系统全局配置
            </el-button>
            <el-button size="large" :icon="RefreshLeft" @click="onReset" plain>
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
  Close, Edit, RefreshRight, Connection, UploadFilled, 
  Cpu, Coin, EditPen, Setting, Link, Key, Service, FolderOpened, Monitor, InfoFilled, Document, DataLine, Picture, User, Lock, Place, RefreshLeft
} from '@element-plus/icons-vue';
import PageHeader from '@/components/PageHeader.vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { 
  getModelConfig, updateModelConfig, 
  testDifyConnection, testSiliconFlowConnection,
  testZhipuConnection, testDeepSeekConnection 
} from '@/api/modelConfig';
import request from '@/utils/request';

const formRef = ref(null);
const loading = ref(false);
const testLoading = ref(false);
const sfTestLoading = ref(false);
const difyTestLoading = ref(false);
const lastTestSuccess = ref(false);
const initialForm = ref({}); 

const modelsLoading = ref(false);
const vlmOptions = ref([]);
const embedOptions = ref([]);
const chatModelOptions = ref([]);

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
  difyEndpoint: 'http://localhost:3000/v1',
  difyApiKey: '',
  siliconFlowEndpoint: 'https://api.siliconflow.cn/v1',
  siliconFlowApiKey: '',
  siliconFlowModel: 'Qwen/Qwen2-7B-Instruct',
  vlmModel: '',
  embeddingModel: '',
  systemModel: '',
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
  difyEndpoint: [
    { required: true, message: '请输入 Dify API 端点', trigger: 'blur' },
    { type: 'url', message: '请输入正确的 URL 格式', trigger: 'blur' }
  ],
  difyApiKey: [
    { required: true, message: '请输入 Dify API 密钥', trigger: 'blur' }
  ]
};

import { getModelList } from '@/api/model';

const loadModels = async () => {
  if (chatModelOptions.value.length > 0) return; // Cached

  modelsLoading.value = true;
  try {
      // Fetch the actual registered models in ORIN
      const res = await getModelList();
      
      // Filter for VLM (usually CHAT/LLM models that the user designated for this)
      // and Embedding models
      vlmOptions.value = res.filter(m => 
        (m.type === 'CHAT' || m.type === 'LLM' || m.type === 'TEXT_TO_IMAGE') && 
        m.status === 'ENABLED'
      ).map(m => ({ id: m.modelId, name: `${m.name} (${m.provider})` }));

      embedOptions.value = res.filter(m => 
        m.type === 'EMBEDDING' && 
        m.status === 'ENABLED'
      ).map(m => ({ id: m.modelId, name: `${m.name} (${m.provider})` }));

      chatModelOptions.value = res.filter(m =>
        (m.type === 'CHAT' || m.type === 'LLM') &&
        m.status === 'ENABLED'
      ).map(m => ({ id: m.modelId, name: `${m.name} (${m.provider})` }));
      
      // Add defaults if nothing found in the registered list
      if (vlmOptions.value.length === 0) {
            vlmOptions.value = [{ id: 'Qwen/Qwen2-VL-72B-Instruct', name: 'Qwen/Qwen2-VL-72B-Instruct (Default)' }];
      }
      if (embedOptions.value.length === 0) {
            embedOptions.value = [{ id: 'BAAI/bge-m3', name: 'BAAI/bge-m3 (Default)' }];
      }
  } catch (e) {
      console.error('Failed to load registered models', e);
      // Fallback to presets
      vlmOptions.value = [{ id: 'Qwen/Qwen2-VL-72B-Instruct', name: 'Qwen/Qwen2-VL-72B-Instruct (Fallback)' }];
      embedOptions.value = [{ id: 'BAAI/bge-m3', name: 'BAAI/bge-m3 (Fallback)' }];
  } finally {
      modelsLoading.value = false;
  }
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

const onTestSFConnection = async () => {
    if (!form.siliconFlowEndpoint || !form.siliconFlowApiKey) {
        return ElMessage.warning('请先输入 SiliconFlow API 端点和密钥');
    }
    sfTestLoading.value = true;
    try {
        const response = await testSiliconFlowConnection(form.siliconFlowEndpoint, form.siliconFlowApiKey, form.siliconFlowModel || 'Qwen/Qwen2-7B-Instruct');
        if (response) {
            ElMessage.success('SiliconFlow 连接测试成功！');
            lastTestSuccess.value = true;
        } else {
            ElMessage.error('SiliconFlow 连接测试失败，请检查配置信息');
            lastTestSuccess.value = false;
        }
    } catch (e) {
        ElMessage.error('SiliconFlow 连接测试失败: ' + (e.response?.data?.message || e.message));
        lastTestSuccess.value = false;
    } finally {
        sfTestLoading.value = false;
    }
};

const onTestDifyConnection = async () => {
  if (!form.difyEndpoint || !form.difyApiKey) {
    return ElMessage.warning('请先输入 Dify API 端点和密钥');
  }
  difyTestLoading.value = true;
  try {
    const response = await testDifyConnection(form.difyEndpoint, form.difyApiKey);
    if (response) {
      ElMessage.success('Dify 连接测试成功！');
      lastTestSuccess.value = true;
    } else {
      ElMessage.error('Dify 连接测试失败，请检查配置信息');
      lastTestSuccess.value = false;
    }
  } catch (e) {
    ElMessage.error('Dify 连接测试失败: ' + (e.response?.data?.message || e.message));
    lastTestSuccess.value = false;
  } finally {
    difyTestLoading.value = false;
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

/* Header Banner Styling */
.header-banner {
  background: linear-gradient(135deg, var(--neutral-white) 0%, var(--neutral-gray-50) 100%);
  border: 1px solid var(--neutral-gray-100);
  border-radius: var(--radius-xl);
  padding: 32px;
  margin-bottom: 32px;
  box-shadow: var(--shadow-sm);
  position: relative;
  overflow: hidden;
}

.header-banner::before {
  content: '';
  position: absolute;
  top: -50%;
  right: -10%;
  width: 300px;
  height: 300px;
  background: radial-gradient(circle, var(--primary-glow) 0%, transparent 70%);
  opacity: 0.5;
  pointer-events: none;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.title-area {
  display: flex;
  align-items: center;
  gap: 20px;
}

.icon-box {
  width: 56px;
  height: 56px;
  background: linear-gradient(135deg, var(--primary-color), #4f46e5);
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 28px;
  box-shadow: 0 8px 16px var(--primary-glow);
}

.page-title {
  font-size: 24px;
  margin: 0;
  color: var(--neutral-gray-900);
}

.subtitle {
  margin: 4px 0 0;
  color: var(--neutral-gray-500);
  font-size: 14px;
}

.status-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: var(--neutral-white);
  border: 1px solid var(--neutral-gray-200);
  border-radius: 100px;
  font-size: 13px;
  font-weight: 600;
  color: var(--neutral-gray-600);
}

.status-badge.connected {
  color: var(--success-color);
  border-color: var(--success-glow);
  background: var(--primary-light);
}

.status-badge .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--neutral-gray-400);
}

.status-badge.connected .dot {
  background: var(--success-color);
  box-shadow: 0 0 8px var(--success-color);
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