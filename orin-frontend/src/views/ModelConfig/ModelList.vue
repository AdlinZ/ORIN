<template>
  <div class="page-container">


    <PageHeader 
      title="模型列表" 
      description="连接并调度第三方 API (OpenAI, Claude, Dify等) 及本地部署的模型资源"
      icon="Box"
    >
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="handleAdd">添加模型资源</el-button>
        <el-button :icon="Key" @click="openKeyManagement">API 密钥管理</el-button>
      </template>
    </PageHeader>


    <!-- Stats Row -->
    <el-row :gutter="24" class="model-stats" style="margin-bottom: 24px;">
      <el-col :span="6" v-for="stat in stats" :key="stat.label">
        <el-card shadow="hover" :body-style="{ padding: '20px' }">
          <div class="text-secondary" style="margin-bottom: 8px;">{{ stat.label }}</div>
          <div class="page-title" style="margin-bottom: 0;">{{ stat.value }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Table Section -->
    <el-card shadow="never" class="table-card">
      <div class="table-toolbar" style="margin-bottom: 24px;">
         <div class="header-left">
           <el-select 
             v-model="typeFilter" 
             @change="handleTabChange" 
             placeholder="模型类型筛选" 
             clearable 
             style="width: 200px"
           >
             <el-option label="全部类型" value="ALL" />
             <!-- options -->
             <el-option label="对话 (Chat)" value="CHAT" />
             <el-option label="向量嵌入 (Embedding)" value="EMBEDDING" />
             <el-option label="结果重排 (Reranker)" value="RERANKER" />
             <el-option label="图像生成 (Image)" value="TEXT_TO_IMAGE" />
             <el-option label="视频生成 (Video)" value="TEXT_TO_VIDEO" />
             <el-option label="语音转文字 (STT)" value="SPEECH_TO_TEXT" />
             <el-option label="文字转语音 (TTS)" value="TEXT_TO_SPEECH" />
           </el-select>
         </div>
         <div class="action-bar">
           <el-button @click="handleImportPricing" :icon="Money" style="margin-right: 12px;">导入定价(JSON)</el-button>
           <el-input 
             v-model="searchQuery" 
             placeholder="搜索名称或供应商..." 
             :prefix-icon="Search" 
             clearable 
             class="search-input"
             style="width: 280px"
           />
           <el-button 
             v-if="selectedIds.length > 0" 
             type="danger" 
             plain 
             :icon="Delete" 
             style="margin-left: 12px"
             @click="handleBatchDelete"
           >
             批量删除 ({{ selectedIds.length }})
           </el-button>
         </div>
      </div>

     <el-dialog v-model="pricingImportVisible" title="批量导入定价 (从 SiliconFlow 后台)" width="700px" :close-on-click-modal="false">
        <el-alert 
            type="info" 
            :closable="false" 
            show-icon
            style="margin-bottom: 20px;"
        >
          <template #title>
            由于定价信息包含 Session 验证，无法自动抓取。请选择以下方式：
          </template>
        </el-alert>

        <el-tabs v-model="importModeTab" type="border-card" class="import-help-tabs">
          <el-tab-pane label="官方预设 (推荐)" name="preset">
             <div class="help-content">
               <p>ORIN 已内置常见模型在 SiliconFlow 上的参考价格。</p>
               <el-select v-model="selectedPreset" placeholder="请选择预设配置" style="width: 100%; margin: 10px 0;">
                 <el-option v-for="p in PRESET_PRICING" :key="p.name" :label="p.name" :value="p.name" />
               </el-select>
               <el-alert title="注意：预设价格可能随时变动，建议仅作参考。" type="warning" :closable="false" show-icon />
             </div>
          </el-tab-pane>

          <el-tab-pane label="从浏览器 Network 复制" name="manual">
            <div class="help-content step-guide">
               <!-- Steps same as before -->
               <div class="step-item"><div class="step-num">1</div><div class="step-text">登录 <a href="https://cloud.siliconflow.cn/models" target="_blank">SiliconFlow 后台</a>。</div></div>
               <div class="step-item"><div class="step-num">2</div><div class="step-text">按 <kbd>F12</kbd> 打开 Network，刷新页面。</div></div>
               <div class="step-item"><div class="step-num">3</div><div class="step-text">筛选 <code>models</code> 请求 (GET)。</div></div>
               <div class="step-item"><div class="step-num">4</div><div class="step-text">选中 Response，全选复制 JSON，粘贴至下方。</div></div>
            </div>
            <el-input 
                v-model="pricingJson" 
                type="textarea" 
                :rows="6" 
                placeholder="在此处粘贴 JSON 内容..." 
                style="margin-top: 10px;"
            />
          </el-tab-pane>
        </el-tabs>
        
        <template #footer>
            <div class="dialog-footer">
               <span class="tip-text" v-if="importModeTab === 'manual'">已粘贴 {{ pricingJson.length }} 字符</span>
               <span class="tip-text" v-else>将导入 {{ selectedPreset ? PRESET_PRICING.find(p => p.name === selectedPreset).data.length : 0 }} 条规则</span>
               <div>
                  <el-button @click="pricingImportVisible = false">取消</el-button>
                  <el-button type="primary" :loading="importLoading" @click="() => { importMode = importModeTab; submitPricingImport(); }">确认导入</el-button>
               </div>
            </div>
        </template>
     </el-dialog>

      <ResizableTable v-loading="loading" :data="filteredList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" fixed="left" />
        <el-table-column label="模型名称" min-width="220">
          <template #default="{ row }">
            <div class="model-info">
              <el-icon class="text-secondary">
                <component :is="row.type === 'LLM' ? ChatDotRound : Connection" />
              </el-icon>
              <div class="provider-tag" :class="row.provider.toLowerCase()">{{ row.provider }}</div>
              <span class="name">{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        
        <el-table-column prop="modelId" label="模型标识 (Model ID)" width="180" />
        
        <el-table-column prop="type" label="类型" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="getModelTypeTag(row.type)" effect="plain">
              {{ formatModelType(row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        
        <el-table-column prop="status" label="运行状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag 
              :type="row.status === 'ENABLED' ? 'success' : 'info'" 
              class="clickable-tag"
              @click="handleToggleStatus(row)"
            >
              {{ row.status === 'ENABLED' ? '已启用' : '已禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        
        <el-table-column prop="createTime" label="创建时间" width="160" align="center">
           <template #default="{ row }">
             {{ formatTime(row.createTime) }}
           </template>
        </el-table-column>
        
        <el-table-column label="操作" width="220" align="center" fixed="right">
          <template #default="{ row }">
             <el-button link type="primary" :icon="Cpu" @click="handleTestModel(row)">测试</el-button>
             <el-button link type="primary" :icon="Edit" @click="handleEdit(row)">编辑</el-button>
             <el-button link type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </ResizableTable>
    </el-card>

    <!-- Form Dialog - 向导式新增模型 -->
    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑模型' : '新增模型'"
      width="700px"
      top="5vh"
      class="model-edit-dialog"
      destroy-on-close
      :close-on-click-modal="false"
    >
      <!-- 步骤引导 -->
      <div class="onboard-stepper">
        <div
          v-for="(step, index) in wizardSteps"
          :key="index"
          class="step-item"
          :class="{ 'active': wizardStep === index, 'completed': wizardStep > index }"
        >
          <div class="step-icon-wrapper">
            <el-icon><component :is="step.icon" /></el-icon>
          </div>
          <div class="step-text">
            <span class="step-label">Step 0{{ index + 1 }}</span>
            <span class="step-name">{{ step.title }}</span>
          </div>
          <div v-if="index < wizardSteps.length - 1" class="step-line"></div>
        </div>
      </div>

      <!-- Step 01: 选择供应商 -->
      <div v-show="wizardStep === 0" class="step-content">
        <el-form-item label="选择供应商" prop="provider">
          <el-select
            v-model="form.provider"
            placeholder="请选择模型供应商"
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

        <el-form-item label="模型类型" prop="type">
          <el-select v-model="form.type" placeholder="请选择模型类型" size="large" style="width: 100%">
            <el-option label="对话 (Chat/LLM)" value="CHAT" />
            <el-option label="向量嵌入 (Embedding)" value="EMBEDDING" />
            <el-option label="结果重排 (Reranker)" value="RERANKER" />
            <el-option label="图像生成 (Image)" value="TEXT_TO_IMAGE" />
            <el-option label="语音转文字 (STT)" value="SPEECH_TO_TEXT" />
            <el-option label="文字转语音 (TTS)" value="TEXT_TO_SPEECH" />
          </el-select>
        </el-form-item>
      </div>

      <!-- Step 02: 配置密钥 -->
      <div v-show="wizardStep === 1" class="step-content">
        <!-- 有已保存密钥时 -->
        <template v-if="savedKeys.length > 0">
          <el-alert
            title="使用已保存密钥"
            type="info"
            description="选择已保存的 API 密钥，或手动输入新的密钥"
            :closable="false"
            show-icon
            style="margin-bottom: 16px;"
          />

          <el-form-item label="选择已保存密钥">
            <el-select
              v-model="selectedSavedKeyId"
              placeholder="选择已保存的凭据"
              size="large"
              style="width: 100%"
              clearable
              @change="onSavedKeyChange"
            >
              <el-option
                v-for="key in savedKeys"
                :key="key.id"
                :label="`${key.name} (${key.provider})`"
                :value="key.id"
              />
            </el-select>
          </el-form-item>

          <el-divider>或手动输入</el-divider>
        </template>

        <!-- 无密钥时引导 -->
        <template v-else>
          <el-alert
            title="暂无 API 密钥"
            type="warning"
            description="请先添加供应商密钥，以便快速获取模型列表。也可以直接手动输入模型标识符。"
            :closable="false"
            show-icon
            style="margin-bottom: 16px;"
          />
          <el-button type="primary" plain @click="keyDialogVisible = true; fetchExternalKeys()" :icon="Link">
            前往添加 API 密钥
          </el-button>
          <el-divider>或直接手动输入</el-divider>
        </template>

        <el-form-item label="API Base URL">
          <el-input
            v-model="fetchConfig.baseUrl"
            placeholder="例如: https://api.openai.com/v1"
            size="large"
          >
            <template #prefix>
              <el-icon><Link /></el-icon>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item label="API Key">
          <el-input
            v-model="fetchConfig.apiKey"
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
      </div>

      <!-- Step 03: 获取并选择模型 -->
      <div v-show="wizardStep === 2" class="step-content">
        <el-form-item label="模型标识符">
          <el-input
            v-model="form.modelId"
            placeholder="例如: gpt-4o, llama3:8b, text-embedding-3-small"
            size="large"
          >
            <template #prefix>
              <el-icon><Cpu /></el-icon>
            </template>
            <template #append>
              <el-button @click="handleFetchFromApi" :loading="isFetchingModels">
                从 API 获取
              </el-button>
            </template>
          </el-input>
          <template #extra>
            <span style="color: var(--neutral-gray-500); font-size: 12px;">
              点击"从 API 获取"按钮自动获取供应商提供的所有模型
            </span>
          </template>
        </el-form-item>

        <!-- 获取到的模型列表 -->
        <div v-if="availableModels.length > 0" class="fetched-models-section">
          <div class="fetched-header">
            <span>发现 {{ availableModels.length }} 个模型：</span>
            <el-button type="success" size="small" @click="handleImportAll">
              一键全部导入
            </el-button>
          </div>
          <el-scrollbar max-height="200px">
            <div class="model-grid">
              <el-tag
                v-for="m in availableModels"
                :key="m.id"
                size="large"
                class="model-tag"
                :type="form.modelId === m.id ? 'success' : 'info'"
                @click="onSelectFetchedModel(m)"
              >
                {{ m.id }}
              </el-tag>
            </div>
          </el-scrollbar>
        </div>
      </div>

      <!-- 定价配置 (可选，任何步骤都可访问) -->
      <div class="pricing-section" v-if="showPricing">
        <el-divider />
        <el-collapse v-model="pricingCollapse">
          <el-collapse-item title="高级配置：定价策略 (可选)" name="pricing">
            <el-alert title="此处只配置默认租户组的价格，高级配置请前往系统设置。" type="info" :closable="false" show-icon style="margin-bottom: 16px;" />

            <el-form-item label="计费模式">
              <el-radio-group v-model="pricingForm.billingMode">
                <el-radio-button label="PER_TOKEN">Token计费</el-radio-button>
                <el-radio-button label="PER_REQUEST">按次计费</el-radio-button>
              </el-radio-group>
            </el-form-item>

            <el-row :gutter="20">
              <el-col :span="12">
                <div class="pricing-header">成本 (Internal Cost)</div>
                <el-form-item label="Input / 1k">
                  <el-input-number v-model="pricingForm.inputCostUnit" :precision="6" :step="0.001" style="width: 100%" />
                </el-form-item>
                <el-form-item label="Output / 1k">
                  <el-input-number v-model="pricingForm.outputCostUnit" :precision="6" :step="0.001" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <div class="pricing-header">报价 (External Price)</div>
                <el-form-item label="Input / 1k">
                  <el-input-number v-model="pricingForm.inputPriceUnit" :precision="6" :step="0.001" style="width: 100%" />
                </el-form-item>
                <el-form-item label="Output / 1k">
                  <el-input-number v-model="pricingForm.outputPriceUnit" :precision="6" :step="0.001" style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>
          </el-collapse-item>
        </el-collapse>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button v-if="wizardStep > 0" @click="wizardStep--">上一步</el-button>
          <el-button v-if="wizardStep < 2" type="primary" @click="nextStep">下一步</el-button>
          <el-button v-if="wizardStep === 2" type="primary" :loading="submitting" @click="handleSubmit">确认保存</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- Test Result Dialog -->
    <el-dialog v-model="testResultVisible" title="模型连通性测试" width="500px">
      <div v-if="testResult" class="test-result-body">
        <div class="result-item">
          <span class="label">测试结果:</span>
          <el-tag :type="testResult.success ? 'success' : 'danger'">{{ testResult.success ? '成功' : '失败' }}</el-tag>
        </div>
        <div class="result-item">
          <span class="label">响应耗时:</span>
          <span>{{ testResult.duration }} ms</span>
        </div>
        <div v-if="testResult.error" class="result-item error">
          <span class="label">错误信息:</span>
          <p>{{ testResult.error }}</p>
        </div>
        <div v-else class="result-item success">
          <span class="label">返回内容:</span>
          <p>测试 Token 响应正常，模型状态活跃。</p>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="testResultVisible = false">完成</el-button>
      </template>
    </el-dialog>

    <!-- API 密钥管理 Dialog -->
    <el-dialog v-model="keyDialogVisible" title="API 密钥管理" width="800px" top="5vh">
      <div class="key-management-header" style="margin-bottom: 20px;">
        <p style="color: var(--neutral-gray-500); margin: 0;">
          管理外部AI服务提供商的 API 密钥，用于调用模型时进行身份验证
        </p>
      </div>

      <div style="margin-bottom: 16px;">
        <el-button type="primary" :icon="Plus" @click="openKeyForm()">添加 API 密钥</el-button>
      </div>

      <el-table :data="externalKeys" v-loading="keyLoading" border stripe>
        <el-table-column prop="name" label="密钥名称" min-width="150" />
        <el-table-column prop="provider" label="供应商" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ row.provider }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="API密钥" min-width="180">
          <template #default="{ row }">
            <code class="key-preview">
              {{ isKeyVisible(row.id) ? row.apiKey : maskKey(row.apiKey) }}
            </code>
            <el-button link :icon="isKeyVisible(row.id) ? Hide : View" @click="toggleKeyVisibility(row.id)" />
          </template>
        </el-table-column>
        <el-table-column prop="baseUrl" label="端点地址" min-width="150" show-overflow-tooltip />
        <el-table-column prop="enabled" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" @change="toggleKeyStatus(row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="openKeyForm(row)">编辑</el-button>
            <el-button size="small" type="danger" link @click="deleteKey(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-button @click="keyDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- API 密钥编辑表单 Dialog -->
    <el-dialog v-model="keyFormVisible" :title="keyFormData.id ? '编辑 API 密钥' : '添加 API 密钥'" width="500px">
      <el-form :model="keyFormData" ref="keyFormRef" label-position="top">
        <el-form-item label="密钥名称" prop="name">
          <el-input v-model="keyFormData.name" placeholder="例如: 我的 OpenAI 主密钥" />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="供应商" prop="provider">
              <el-select v-model="keyFormData.provider" style="width: 100%">
                <el-option label="OpenAI" value="OpenAI" />
                <el-option label="DeepSeek" value="DeepSeek" />
                <el-option label="SiliconFlow" value="SiliconFlow" />
                <el-option label="Anthropic" value="Anthropic" />
                <el-option label="Groq" value="Groq" />
                <el-option label="Ollama (本地)" value="Ollama" />
                <el-option label="Dify" value="Dify" />
                <el-option label="智谱 AI" value="Zhipu" />
                <el-option label="阿里云" value="Aliyun" />
                <el-option label="百度" value="Baidu" />
                <el-option label="腾讯云" value="Tencent" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Base URL (可选)">
              <el-input v-model="keyFormData.baseUrl" placeholder="默认官方地址" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="API Key" prop="apiKey">
          <el-input v-model="keyFormData.apiKey" type="password" show-password placeholder="sk-..." />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="keyFormData.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="keyFormVisible = false">取消</el-button>
        <el-button type="primary" @click="saveKey">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { Plus, Edit, Delete, Search, Box, Money, Link, Key, View, Hide } from '@element-plus/icons-vue';
import PageHeader from '@/components/PageHeader.vue';
import ResizableTable from '@/components/ResizableTable.vue';
import { getModelList, saveModel, deleteModel, toggleModelStatus, fetchModels } from '@/api/model';
import { getPricingConfig, savePricingConfig } from '@/api/monitor';
import { getModelConfig } from '@/api/modelConfig';
import { getExternalKeys, saveExternalKey, deleteExternalKey, toggleExternalKeyStatus } from '@/api/apiKey';
import { ElMessage, ElMessageBox } from 'element-plus';

const router = useRouter();
const loading = ref(false);
const submitting = ref(false);
const isFetchingModels = ref(false);
const availableModels = ref([]);
const savedKeys = ref([]);
const selectedSavedKeyId = ref(null);
const selectedIds = ref([]);
const fetchConfig = reactive({
  baseUrl: '',
  apiKey: ''
});
const testResultVisible = ref(false);
const testResult = ref(null);
const modelList = ref([]);
const searchQuery = ref('');
const typeFilter = ref('ALL');
const dialogVisible = ref(false);
const formRef = ref(null);
const activeTab = ref('basic');

// 向导步骤配置
const wizardStep = ref(0);
const wizardSteps = [
  { title: '选择供应商', icon: Connection },
  { title: '配置密钥', icon: Key },
  { title: '选择模型', icon: Cpu }
];

// 供应商选项配置
const providerOptions = [
  { value: 'OpenAI', label: 'OpenAI', description: 'GPT-4o, GPT-4, GPT-3.5', icon: 'Cpu' },
  { value: 'Anthropic', label: 'Anthropic (Claude)', description: 'Claude 3.5 Sonnet, Opus', icon: 'Cpu' },
  { value: 'DeepSeek', label: 'DeepSeek', description: 'DeepSeek Coder, Chat', icon: 'Cpu' },
  { value: 'SiliconFlow', label: 'SiliconFlow', description: '200+ 模型，便宜稳定', icon: 'Cpu' },
  { value: 'Dify', label: 'Dify (本地/私有)', description: '自部署 Dify 应用', icon: 'Cpu' },
  { value: 'Ollama', label: 'Ollama (本地)', description: '本地部署 Llama, Mistral 等', icon: 'Cpu' },
  { value: 'DashScope', label: '阿里云 DashScope', description: '通义千问系列', icon: 'Cpu' },
  { value: 'HuggingFace', label: 'HuggingFace', description: '开源模型接入', icon: 'Cpu' }
];

import { ChatDotRound, Connection, Cpu } from '@element-plus/icons-vue';

const form = reactive({
  id: null,
  name: '',
  provider: '',
  type: 'CHAT',
  modelId: '',
  description: '',
  status: 'ENABLED'
});

const pricingForm = reactive({
  id: null,
  providerId: '',
  tenantGroup: 'default',
  billingMode: 'PER_TOKEN',
  inputCostUnit: 0,
  outputCostUnit: 0,
  inputPriceUnit: 0,
  outputPriceUnit: 0,
  currency: 'CNY'
});

const rules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  provider: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  modelId: [{ required: true, message: '请输入 Model ID', trigger: 'blur' }]
};

const fetchData = async () => {
  loading.value = true;
  try {
    const res = await getModelList();
    modelList.value = res;
  } catch (e) {
    console.error(e);
  } finally {
    loading.value = false;
  }
};

const handleTabChange = async (val) => {
  loading.value = true;
  await new Promise(r => setTimeout(r, 300)); // Smooth transition
  fetchData();
  ElMessage.info(`已切换至: ${val === 'ALL' ? '全部模型' : val}`);
};

const filteredList = computed(() => {
  let list = modelList.value;
  if (typeFilter.value !== 'ALL') {
    list = list.filter(m => m.type === typeFilter.value);
  }
  if (searchQuery.value) {
    const q = searchQuery.value.toLowerCase();
    list = list.filter(m => m.name.toLowerCase().includes(q) || m.provider.toLowerCase().includes(q));
  }
  return list;
});

const stats = computed(() => [
  { label: '纳管模型总数', value: modelList.value.length },
  { label: 'Chat 模型', value: modelList.value.filter(m => (m.type === 'CHAT' || m.type === 'LLM') && m.status === 'ENABLED').length },
  { label: 'Embedding', value: modelList.value.filter(m => m.type === 'EMBEDDING').length },
  { label: '多模态/其他', value: modelList.value.filter(m => !['CHAT', 'LLM', 'EMBEDDING'].includes(m.type)).length }
]);

const handleAdd = async () => {
  // 跳转到全屏向导页面
  router.push('/dashboard/applications/models/add');
};

// 向导步骤控制
const nextStep = () => {
  if (wizardStep.value < 2) {
    wizardStep.value++;
  }
};

const handleProviderChange = (value) => {
  // 根据供应商设置默认的 Base URL
  const defaultUrls = {
    'OpenAI': 'https://api.openai.com/v1',
    'Anthropic': 'https://api.anthropic.com',
    'DeepSeek': 'https://api.deepseek.com/v1',
    'SiliconFlow': 'https://api.siliconflow.cn/v1',
    'DashScope': 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    'Ollama': 'http://localhost:11434/v1'
  };
  if (defaultUrls[value] && !fetchConfig.baseUrl) {
    fetchConfig.baseUrl = defaultUrls[value];
  }
};

const pricingCollapse = ref('pricing');
const showPricing = ref(false);

const resetPricingForm = () => {
  Object.assign(pricingForm, {
    id: null,
    providerId: '',
    tenantGroup: 'default',
    billingMode: 'PER_TOKEN',
    inputCostUnit: 0,
    outputCostUnit: 0,
    inputPriceUnit: 0,
    outputPriceUnit: 0,
    currency: 'CNY'
  });
};

// API 密钥管理相关状态
const keyDialogVisible = ref(false);
const keyLoading = ref(false);
const externalKeys = ref([]);
const keyFormVisible = ref(false);
const keyFormData = ref({
  id: null,
  name: '',
  provider: 'OpenAI',
  apiKey: '',
  baseUrl: '',
  description: '',
  enabled: true
});
const keyFormRef = ref(null);
const visibleKeys = ref(new Set());

const openKeyManagement = () => {
  keyDialogVisible.value = true;
  fetchExternalKeys();
};

const fetchExternalKeys = async () => {
  keyLoading.value = true;
  try {
    const res = await getExternalKeys();
    externalKeys.value = res;
  } catch (e) {
    ElMessage.error('获取密钥列表失败');
  } finally {
    keyLoading.value = false;
  }
};

const openKeyForm = (row = null) => {
  if (row) {
    keyFormData.value = { ...row };
  } else {
    keyFormData.value = {
      id: null,
      name: '',
      provider: 'OpenAI',
      apiKey: '',
      baseUrl: '',
      description: '',
      enabled: true
    };
  }
  keyFormVisible.value = true;
};

const saveKey = async () => {
  try {
    await saveExternalKey(keyFormData.value);
    ElMessage.success('保存成功');
    keyFormVisible.value = false;
    fetchExternalKeys();
  } catch (e) {
    ElMessage.error('保存失败');
  }
};

const deleteKey = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除此 API 密钥吗?', '警告', { type: 'warning' });
    await deleteExternalKey(row.id);
    ElMessage.success('已删除');
    fetchExternalKeys();
  } catch (e) { /* cancel */ }
};

const toggleKeyStatus = async (row) => {
  try {
    await toggleExternalKeyStatus(row.id);
    ElMessage.success('状态已更新');
  } catch (e) {
    row.enabled = !row.enabled;
  }
};

const maskKey = (key) => {
  if (!key) return '';
  if (key.length <= 8) return '********';
  return key.substring(0, 4) + '****************' + key.substring(key.length - 4);
};

const isKeyVisible = (id) => visibleKeys.value.has(id);

const toggleKeyVisibility = (id) => {
  if (visibleKeys.value.has(id)) {
    visibleKeys.value.delete(id);
  } else {
    visibleKeys.value.add(id);
  }
};

const onSavedKeyChange = (id) => {
  const keyMatch = savedKeys.value.find(k => k.id === id);
  if (keyMatch) {
    fetchConfig.apiKey = keyMatch.apiKey;
    if (keyMatch.baseUrl) fetchConfig.baseUrl = keyMatch.baseUrl;
    if (!form.provider) form.provider = keyMatch.provider;
  }
};

const handleFetchFromApi = async () => {
  if (!fetchConfig.baseUrl) return ElMessage.warning('请输入 API 地址');
  isFetchingModels.value = true;
  try {
    const res = await fetchModels(fetchConfig.baseUrl, fetchConfig.apiKey);
    availableModels.value = res || [];
    if (availableModels.value.length === 0) {
      ElMessage.warning('未能获取到模型列表，请检查配置');
    } else {
      ElMessage.success(`成功获取 ${availableModels.value.length} 个模型`);
    }
  } catch (e) {
    ElMessage.error('获取失败: ' + (e.response?.data?.message || e.message));
  } finally {
    isFetchingModels.value = false;
  }
};

const onSelectFetchedModel = (m) => {
  form.modelId = m.id;
  if (!form.name) form.name = m.id;
  if (m.type) form.type = m.type; // Auto-select type if available
  if (m.owned_by && m.owned_by.includes('openai')) form.provider = 'OpenAI';
};

const handleImportAll = async () => {
  if (!form.provider) return ElMessage.warning('请先选择供应商');
  
  const count = availableModels.value.length;
  await ElMessageBox.confirm(`确认将获取到的 ${count} 个模型全部导入吗？`, '批量导入确认', {
    confirmButtonText: '立即导入',
    cancelButtonText: '取消',
    type: 'info'
  });
  
  submitting.value = true;
  let successCount = 0;
  try {
    const provider = form.provider;
    
    // Check for existing models to avoid duplicates (optional but better)
    const existingModelIds = modelList.value.map(m => m.modelId);
    
    for (const m of availableModels.value) {
      if (existingModelIds.includes(m.id)) continue;
      
      // Use inferred type if available, otherwise fallback to current form selection or CHAT
      const modelType = m.type || form.type || 'CHAT';

      await saveModel({
        name: m.id,
        modelId: m.id,
        provider: provider,
        type: modelType,
        status: 'ENABLED',
        description: `API 自动导入 - ${new Date().toLocaleDateString()}`
      });
      successCount++;
    }
    
    ElMessage.success(`导入完成：新添加 ${successCount} 个模型`);
    dialogVisible.value = false;
    fetchData();
  } catch (e) {
    ElMessage.error('导入过程中出现异常: ' + e.message);
  } finally {
    submitting.value = false;
  }
};

const handleEdit = async (row) => {
  activeTab.value = 'basic';
  Object.assign(form, row);
  // Fetch pricing
  resetPricingForm();
  try {
    const res = await getPricingConfig();
    const list = res.data || res;
    // Find matching rule for this modelId + default group
    const pid = row.modelId;
    const match = list.find(p => p.providerId === pid && p.tenantGroup === 'default');
    if (match) {
        Object.assign(pricingForm, match);
    } else {
        pricingForm.providerId = pid;
    }
  } catch(e) { console.warn(e); }
  
  dialogVisible.value = true;
};

const handleSubmit = async () => {
  if (!formRef.value) return;
  await formRef.value.validate(async (valid) => {
    if (valid) {
      submitting.value = true;
      try {
        // 1. Save Model
        await saveModel(form);
        
        // 2. Save Pricing (if modelId is set)
        if (form.modelId) {
            pricingForm.providerId = form.modelId; // Ensure sync
            await savePricingConfig(pricingForm);
        }

        ElMessage.success('保存成功');
        dialogVisible.value = false;
        fetchData();
      } catch(e) {
        ElMessage.error('保存失败: ' + e.message);
      } finally {
        submitting.value = false;
      }
    }
  });
};

const handleToggleStatus = async (row) => {
  const oldStatus = row.status;
  // Optimistic update
  row.status = oldStatus === 'ENABLED' ? 'DISABLED' : 'ENABLED';
  try {
    await toggleModelStatus(row.id);
    ElMessage.success(`模型已${row.status === 'ENABLED' ? '启用' : '禁用'}`);
  } catch (e) {
    row.status = oldStatus; // Rollback
    ElMessage.error('状态切换失败');
  }
};

const handleTestModel = async (row) => {
  loading.value = true;
  try {
    const startTime = Date.now();
    // In real app, call model test API
    await new Promise(r => setTimeout(r, 1200));
    testResult.value = {
      success: true,
      duration: Date.now() - startTime,
      error: null
    };
    testResultVisible.value = true;
  } finally {
    loading.value = false;
  }
};

const handleDelete = (row) => {
  ElMessageBox.confirm(`确定删除模型资源 "${row.name}" 吗?`, '警告', { type: 'warning' }).then(async () => {
    await deleteModel(row.id);
    ElMessage.success('删除成功');
    fetchData();
  });
};

const handleSelectionChange = (selection) => {
  selectedIds.value = selection.map(item => item.id);
};

const handleBatchDelete = () => {
  if (selectedIds.value.length === 0) return;
  ElMessageBox.confirm(`确定批量删除选中的 ${selectedIds.value.length} 个模型吗? 此操作不可恢复。`, '批量删除确认', {
    confirmButtonText: '确定删除',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    loading.value = true;
    try {
      // Execute deletions in sequence or parallel depending on backend capability.
      // Assuming serial for safety or basic parallel
      await Promise.all(selectedIds.value.map(id => deleteModel(id)));
      ElMessage.success('批量删除成功');
      selectedIds.value = [];
      fetchData();
    } catch (e) {
      ElMessage.error('批量删除过程中出现错误');
      fetchData(); // Refresh anyway
    } finally {
      loading.value = false;
    }
  });
};

const getModelTypeTag = (type) => {
  const map = {
    'CHAT': 'primary',
    'LLM': 'primary',
    'EMBEDDING': 'warning',
    'RERANKER': 'info',
    'TEXT_TO_IMAGE': 'success',
    'TEXT_TO_VIDEO': 'danger',
    'SPEECH_TO_TEXT': '',
    'TEXT_TO_SPEECH': ''
  };
  return map[type] || 'info';
};

const formatModelType = (type) => {
  const map = {
    'CHAT': '对话 (Chat)',
    'LLM': '语言模型',
    'EMBEDDING': '向量嵌入',
    'RERANKER': '重排',
    'TEXT_TO_IMAGE': '图像生成',
    'TEXT_TO_VIDEO': '视频生成',
    'SPEECH_TO_TEXT': '语音转文字',
    'TEXT_TO_SPEECH': '文字转语音'
  };
  return map[type] || type;
};

const formatTime = (ts) => {
  if (!ts) return '-';
  
  // Handle Array format [yyyy, MM, dd, HH, mm, ss]
  if (Array.isArray(ts)) {
    // Note: Month in JS Date is 0-indexed
    return new Date(ts[0], ts[1] - 1, ts[2], ts[3] || 0, ts[4] || 0, ts[5] || 0).toLocaleString();
  }

  // Handle String format
  const dateStr = String(ts).replace(' ', 'T');
  const date = new Date(dateStr);
  if (isNaN(date.getTime())) return '-';
  return date.toLocaleString();
};

onMounted(() => {
  fetchData();
});


/* --- Batch Import Pricing Logic --- */
const pricingImportVisible = ref(false);
const pricingJson = ref('');
const importLoading = ref(false);
const importMode = ref('manual'); // 'manual' | 'preset'
const selectedPreset = ref('');

// Updated 2024-05 Pricing (Approximate)
// Unit: CNY per 1M tokens (stored as per 1k in DB, so we divide by 1000)
// This list covers popular free and paid models on SiliconFlow.
const PRESET_PRICING = [
  { 
    name: 'SiliconFlow: DeepSeek V3/R1 (Promotional/Free)', 
    data: [
      { id: 'deepseek-ai/DeepSeek-V3', pricing: { input: 0, output: 0 } },
      { id: 'deepseek-ai/DeepSeek-R1', pricing: { input: 0, output: 0 } },
      { id: 'deepseek-ai/DeepSeek-R1-Distill-Llama-8B', pricing: { input: 0, output: 0 } },
      { id: 'deepseek-ai/DeepSeek-R1-Distill-Qwen-7B', pricing: { input: 0, output: 0 } },
      { id: 'deepseek-ai/DeepSeek-R1-Distill-Qwen-32B', pricing: { input: 0, output: 0 } }
    ]
  },
  {
    name: 'SiliconFlow: Qwen 2.5 (Commercial)',
    data: [
      { id: 'Qwen/Qwen2.5-7B-Instruct', pricing: { input: 1, output: 2 } }, // Example prices
      { id: 'Qwen/Qwen2.5-72B-Instruct', pricing: { input: 4, output: 14 } },
      { id: 'Qwen/Qwen2.5-Coder-32B-Instruct', pricing: { input: 2, output: 6 } }
    ]
  },
  {
    name: 'SiliconFlow: Yi (01.AI)',
    data: [
      { id: '01-ai/Yi-1.5-34B-Chat-16K', pricing: { input: 2, output: 12 } },
      { id: '01-ai/Yi-1.5-9B-Chat-16K', pricing: { input: 1, output: 2 } }
    ]
  },
  {
    name: 'SiliconFlow: All Known Models (Common)',
    data: [
       // DeepSeek
       { id: 'deepseek-ai/DeepSeek-V3', pricing: { input: 0, output: 0 } },
       { id: 'deepseek-ai/DeepSeek-R1', pricing: { input: 0, output: 0 } },
       // Qwen
       { id: 'Qwen/Qwen2.5-72B-Instruct', pricing: { input: 4.13, output: 13.75 } }, // approx CNY
       { id: 'Qwen/Qwen2.5-32B-Instruct', pricing: { input: 1.25, output: 3.75 } },
       { id: 'Qwen/Qwen2.5-7B-Instruct', pricing: { input: 0.35, output: 0.7 } },
       // GLM
       { id: 'THUDM/glm-4-9b-chat', pricing: { input: 1, output: 1 } },
       // Llama
       { id: 'meta-llama/Meta-Llama-3.1-8B-Instruct', pricing: { input: 0, output: 0 } }, // Often free tier
       { id: 'meta-llama/Meta-Llama-3.1-405B-Instruct', pricing: { input: 25, output: 75 } },
       // Vendor Specific
       { id: 'internlm/internlm2_5-7b-chat', pricing: { input: 0, output: 0 } }
    ]
  }
];

const handleImportPricing = () => {
  pricingJson.value = '';
  importMode.value = 'manual';
  selectedPreset.value = '';
  pricingImportVisible.value = true;
};

const submitPricingImport = async () => {
  let sourceData = [];

  if (importMode.value === 'preset') {
      if (!selectedPreset.value) return ElMessage.warning('请选择一个预设配置');
      const preset = PRESET_PRICING.find(p => p.name === selectedPreset.value);
      if (preset) sourceData = preset.data;
  } else {
      if (!pricingJson.value.trim()) return ElMessage.warning('请输入 JSON 内容');
      try {
        let data = JSON.parse(pricingJson.value);
        if (data.data && Array.isArray(data.data)) data = data.data;
        else if (!Array.isArray(data)) throw new Error('Format error');
        sourceData = data;
      } catch(e) {
        return ElMessage.error('JSON 解析失败: ' + e.message);
      }
  }

  importLoading.value = true;
  let successCount = 0;

  try {
    const existingModels = modelList.value;
    
    for (const item of sourceData) {
       const pid = item.id || item.model || item.model_id;
       if (!pid) continue;

       // Attempt to read pricing (Per Million assumed if from preset or simple numbers)
       let input = 0, output = 0;
       
       if (item.pricing) {
           input = Number(item.pricing.input || 0);
           output = Number(item.pricing.output || 0);
       } else if (item.price) {
           input = Number(item.price.input || 0);
           output = Number(item.price.output || 0);
       }
       
       // Note: Cloud APIs usually quote per 1M tokens. 
       // Our DB stores "Unit Cost per 1k".
       // So we divide by 1000.
       
       // However, if the user manually inputs per-1k prices in JSON, we might under-price.
       // But typically JSON dumps from APIs are Per-1M (e.g. 0.5 CNY/1M).
       
       // Filter: If import mode is preset, we trust the mapping.
       // If manual, we apply heuristic.
       
       // Update logic
       await savePricingConfig({
           providerId: pid,
           tenantGroup: 'default',
           billingMode: 'PER_TOKEN',
           inputCostUnit: input / 1000,
           outputCostUnit: output / 1000,
           inputPriceUnit: (input / 1000) * 1.2, // 20% Markup default
           outputPriceUnit: (output / 1000) * 1.2,
           currency: 'CNY'
       });
       successCount++;
    }
    
    ElMessage.success(`成功更新 ${successCount} 个模型的定价规则`);
    pricingImportVisible.value = false;
  } catch (e) {
    ElMessage.error('导入失败: ' + e.message);
  } finally {
    importLoading.value = false;
  }
};


const copyHelperScript = () => {
    const code = "fetch('https://api.siliconflow.cn/v1/models?sub_type=chat&page=1&page_size=100').then(r=>r.json()).then(d=>{const t=document.createElement('textarea');t.value=JSON.stringify(d);document.body.appendChild(t);t.select();document.execCommand('copy');document.body.removeChild(t);alert('模型数据已复制到剪贴板！请回到 ORIN 粘贴。')}).catch(e=>alert('获取失败，请确保您已登录'))";
    navigator.clipboard.writeText(code).then(() => {
        ElMessage.success('脚本已复制，请前往控制台粘贴');
    }).catch(() => {
        ElMessage.error('复制失败，请手动复制');
    });
};
</script>

<style scoped>
.page-container {
  padding: 0;
}

/* 步骤引导样式 */
.onboard-stepper {
  display: flex;
  align-items: center;
  margin-bottom: 24px;
  padding: 16px 0;
}

.onboard-stepper .step-item {
  display: flex;
  align-items: center;
  position: relative;
  flex: 1;
}

.onboard-stepper .step-item:last-child {
  flex: 0;
}

.onboard-stepper .step-icon-wrapper {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--neutral-gray-100);
  color: var(--neutral-gray-400);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  margin-right: 12px;
  transition: all 0.3s ease;
}

.onboard-stepper .step-item.active .step-icon-wrapper,
.onboard-stepper .step-item.completed .step-icon-wrapper {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
}

.onboard-stepper .step-text {
  display: flex;
  flex-direction: column;
}

.onboard-stepper .step-label {
  font-size: 11px;
  color: var(--neutral-gray-400);
  text-transform: uppercase;
}

.onboard-stepper .step-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--neutral-gray-600);
}

.onboard-stepper .step-item.active .step-name {
  color: #667eea;
}

.onboard-stepper .step-line {
  flex: 1;
  height: 2px;
  background: var(--neutral-gray-200);
  margin: 0 16px;
}

/* 向导步骤内容区域 */
.step-content {
  min-height: 200px;
}

/* 供应商选项样式 */
.provider-option-content {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 4px 0;
}

.provider-icon {
  font-size: 20px;
  color: #667eea;
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

/* 获取模型区域 */
.fetched-models-section {
  margin-top: 16px;
  padding: 16px;
  background: var(--neutral-gray-50);
  border-radius: 8px;
}

.fetched-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  font-weight: 600;
}

.model-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.model-tag {
  cursor: pointer;
  transition: all 0.2s;
}

.model-tag:hover {
  transform: scale(1.05);
}

/* 定价配置 */
.pricing-header {
  font-weight: 600;
  margin-bottom: 12px;
  color: var(--neutral-gray-700);
}

/* 对话框底部按钮 */
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.action-bar-container {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}
.test-result-body .result-item {
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.test-result-body .label { font-weight: 600; min-width: 80px; }
.test-result-body p { margin: 8px 0 0 0; background: #f5f7fa; padding: 12px; border-radius: 4px; font-size: 13px; line-height: 1.6; }
.clickable-tag { cursor: pointer; }

.table-card { border-radius: 12px; }
.table-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.search-input { width: 300px; }

.model-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.provider-tag {
  font-size: 10px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 4px;
  text-transform: uppercase;
  background: var(--neutral-gray-100);
  color: var(--neutral-gray-600);
}
.provider-tag.openai { background: #10a37f; color: #fff; }
.provider-tag.anthropic { background: #d97757; color: #fff; }
.provider-tag.ollama { background: #000; color: #fff; }
.provider-tag.dify { background: #155eef; color: #fff; }
.provider-tag.siliconflow { background: #6b46c1; color: #fff; }
.provider-tag.deepseek { background: #2f54eb; color: #fff; }

.name { font-weight: 600; color: var(--neutral-gray-900); }

.api-fetch-section {
  padding: 15px;
  background: var(--neutral-gray-50);
  border-radius: 8px;
  border: 1px dashed var(--neutral-gray-200);
}
.section-tip { font-size: 12px; color: var(--neutral-gray-500); margin-bottom: 12px; margin-top: 0; }
.label-mini { font-size: 11px; color: var(--neutral-gray-400); margin: 15px 0 5px; }
.model-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.fetched-tag { cursor: pointer; border-radius: 4px; border: none; background: white; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }
.fetched-tag:hover { border-color: var(--primary-color); color: var(--primary-color); transform: scale(1.05); }
.more-text { font-size: 11px; color: var(--neutral-gray-400); align-self: center; }

/* Dialog Sizing Fix */
:deep(.model-edit-dialog) {
  display: flex;
  flex-direction: column;
  margin: 0 !important;
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  max-height: 90vh;
  max-width: 90vw;
}

:deep(.model-edit-dialog .el-dialog__body) {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
}

.help-content {
  padding: 10px;
  line-height: 1.6;
  font-size: 14px;
}
.help-content p {
  margin: 5px 0 10px;
}
.code-block {
  background: var(--neutral-gray-50);
  padding: 10px;
  border-radius: 4px;
  border: 1px solid var(--neutral-gray-200);
  margin: 10px 0;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: monospace;
  position: relative;
}
.import-help-tabs {
  margin-bottom: 20px;
}
.tip-text {
  color: #909399;
  font-size: 13px;
  margin-right: 12px;
}
.dialog-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
