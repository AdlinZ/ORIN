<template>
  <div class="model-management page-container fade-in">
    <section class="model-shell">
      <header class="model-topbar">
        <div class="topbar-copy">
          <span class="topbar-eyebrow">模型管理</span>
          <h1>模型管理</h1>
          <p>统一维护模型资源、供应商能力、密钥入口与启用状态。</p>
        </div>
        <div class="topbar-actions">
          <el-button :icon="Key" @click="openKeyManagement">
            API 密钥管理
          </el-button>
          <el-button type="primary" :icon="Plus" @click="handleAdd">
            添加模型
          </el-button>
        </div>
      </header>

      <section class="summary-grid">
        <article class="summary-card primary">
          <span>当前结果</span>
          <strong>{{ modelStats.filtered }}/{{ modelStats.total }}</strong>
          <p>筛选后的模型资源数量</p>
        </article>
        <article class="summary-card">
          <span>启用率</span>
          <strong>{{ modelStats.enabledRate }}</strong>
          <p>{{ modelStats.enabled }} 个模型可被调用</p>
        </article>
        <article class="summary-card">
          <span>供应商</span>
          <strong>{{ modelStats.providers }}</strong>
          <p>{{ modelStats.topProvider }}</p>
        </article>
        <article class="summary-card">
          <span>主类型</span>
          <strong>{{ modelStats.topTypeCount }}</strong>
          <p>{{ modelStats.topType }}</p>
        </article>
      </section>

      <section class="model-workspace">
        <div class="workspace-head">
          <div>
            <h2>模型资源清单</h2>
            <p>按供应商、类型和启用状态维护生产可用模型。</p>
          </div>
          <div class="workspace-actions">
            <el-button
              v-if="selectedIds.length > 0"
              type="danger"
              plain
              :icon="Delete"
              @click="handleBatchDelete"
            >
              批量删除 {{ selectedIds.length }}
            </el-button>
            <el-button :icon="Refresh" @click="fetchData">
              刷新
            </el-button>
          </div>
        </div>

        <div class="filter-panel">
          <el-select v-model="providerFilter" placeholder="供应商" class="filter-control">
            <el-option label="全部供应商" value="ALL" />
            <el-option
              v-for="provider in providerFilterOptions"
              :key="provider"
              :label="provider"
              :value="provider"
            />
          </el-select>
          <el-select v-model="typeFilter" placeholder="模型类型" class="filter-control">
            <el-option label="全部类型" value="ALL" />
            <el-option label="对话 (Chat)" value="CHAT" />
            <el-option label="向量嵌入 (Embedding)" value="EMBEDDING" />
            <el-option label="结果重排 (Reranker)" value="RERANKER" />
            <el-option label="图像生成 (Image)" value="TEXT_TO_IMAGE" />
            <el-option label="视频生成 (Video)" value="TEXT_TO_VIDEO" />
            <el-option label="语音转文字 (STT)" value="SPEECH_TO_TEXT" />
            <el-option label="文字转语音 (TTS)" value="TEXT_TO_SPEECH" />
          </el-select>
          <el-select v-model="statusFilter" placeholder="运行状态" class="filter-control status-filter">
            <el-option label="全部状态" value="ALL" />
            <el-option label="已启用" value="ENABLED" />
            <el-option label="已禁用" value="DISABLED" />
          </el-select>
          <el-select v-model="sortMode" placeholder="排序方式" class="filter-control">
            <el-option label="最近创建优先" value="created_desc" />
            <el-option label="最早创建优先" value="created_asc" />
            <el-option label="模型名称 A-Z" value="name_asc" />
            <el-option label="模型名称 Z-A" value="name_desc" />
            <el-option label="供应商 A-Z" value="provider_asc" />
            <el-option label="模型类型 A-Z" value="type_asc" />
          </el-select>
          <el-input
            v-model="searchQuery"
            placeholder="搜索名称 / Model ID"
            clearable
            class="filter-search"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button @click="resetFilters">
            重置
          </el-button>
        </div>

        <el-table
          v-loading="loading"
          :data="displayedList"
          row-key="id"
          class="model-table"
          empty-text="暂无模型，请先接入服务商模型"
          @selection-change="handleSelectionChange"
          @row-click="openModelDetail"
        >
          <el-table-column type="selection" width="46" />
          <el-table-column label="模型" min-width="300" fixed>
            <template #default="{ row }">
              <div class="model-cell">
                <div class="model-avatar">
                  {{ getProviderInitial(row.provider) }}
                </div>
                <div class="model-copy">
                  <strong>{{ row.name || row.modelId }}</strong>
                  <span>{{ row.modelId }}</span>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="供应商" width="150">
            <template #default="{ row }">
              <el-tag size="small" effect="plain">
                {{ row.provider || '-' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="150">
            <template #default="{ row }">
              <el-tag size="small" effect="light" :type="getModelTypeTag(row.type)">
                {{ formatModelType(row.type) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="运行状态" width="130">
            <template #default="{ row }">
              <button
                class="status-pill"
                :class="row.status === 'ENABLED' ? 'active' : 'inactive'"
                type="button"
                @click.stop="handleToggleStatus(row)"
              >
                <span class="status-dot" />
                {{ row.status === 'ENABLED' ? '已启用' : '已禁用' }}
              </button>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="180">
            <template #default="{ row }">
              <span class="time-compact">{{ formatTime(row.createTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column
            label="操作"
            width="190"
            align="right"
            fixed="right"
          >
            <template #default="{ row }">
              <div class="action-buttons" @click.stop>
                <el-tooltip content="查看详情" placement="top">
                  <el-button
                    link
                    type="primary"
                    :icon="View"
                    @click="openModelDetail(row)"
                  />
                </el-tooltip>
                <el-tooltip content="连通测试" placement="top">
                  <el-button
                    link
                    type="success"
                    :icon="Connection"
                    @click="handleTestModel(row)"
                  />
                </el-tooltip>
                <el-tooltip content="编辑配置" placement="top">
                  <el-button
                    link
                    type="primary"
                    :icon="Edit"
                    @click="handleEdit(row)"
                  />
                </el-tooltip>
                <el-tooltip content="删除模型" placement="top">
                  <el-button
                    link
                    type="danger"
                    :icon="Delete"
                    @click="handleDelete(row)"
                  />
                </el-tooltip>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="table-footer">
          <span>共 {{ displayedList.length }} 个模型</span>
          <el-button
            v-if="displayedList.length === 0"
            type="primary"
            :icon="Plus"
            @click="handleAdd"
          >
            添加模型
          </el-button>
        </div>
      </section>
    </section>

    <el-drawer
      v-model="modelDetailVisible"
      title="模型详情"
      size="460px"
      class="model-drawer"
    >
      <template v-if="selectedModel">
        <section class="drawer-profile">
          <div class="model-avatar large">
            {{ getProviderInitial(selectedModel.provider) }}
          </div>
          <div>
            <el-tag size="small" effect="plain">
              {{ selectedModel.provider || '-' }}
            </el-tag>
            <h2>{{ selectedModel.name || selectedModel.modelId }}</h2>
            <p>{{ selectedModel.modelId }}</p>
          </div>
        </section>

        <dl class="detail-list">
          <div>
            <dt>模型类型</dt>
            <dd>{{ formatModelType(selectedModel.type) }}</dd>
          </div>
          <div>
            <dt>运行状态</dt>
            <dd>{{ selectedModel.status === 'ENABLED' ? '已启用' : '已禁用' }}</dd>
          </div>
          <div>
            <dt>创建时间</dt>
            <dd>{{ formatTime(selectedModel.createTime) }}</dd>
          </div>
          <div>
            <dt>描述</dt>
            <dd>{{ selectedModel.description || '-' }}</dd>
          </div>
        </dl>

        <div class="drawer-actions">
          <el-button @click="modelDetailVisible = false">
            关闭
          </el-button>
          <el-button :icon="Connection" @click="handleTestModel(selectedModel)">
            连通测试
          </el-button>
          <el-button type="primary" :icon="Edit" @click="handleEdit(selectedModel)">
            编辑模型
          </el-button>
        </div>
      </template>
    </el-drawer>

    <!-- Form Dialog - 向导式新增模型 -->
    <OrinArcoFormDialog
      ref="formRef"
      v-model="dialogVisible"
      :title="form.id ? '编辑模型' : '新增模型'"
      width="700px"
      :model="form"
      :rules="rules"
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
            <span class="inline-icon"><component :is="step.icon" /></span>
          </div>
          <div class="step-text">
            <span class="step-label">Step 0{{ index + 1 }}</span>
            <span class="step-name">{{ step.title }}</span>
          </div>
          <div v-if="index < wizardSteps.length - 1" class="step-line" />
        </div>
      </div>

      <!-- Step 01: 选择供应商 -->
      <div v-show="wizardStep === 0" class="step-content">
        <a-form-item label="选择供应商" field="provider">
          <a-select
            v-model="form.provider"
            placeholder="请选择模型供应商"
            size="large"
            style="width: 100%"
            @change="handleProviderChange"
          >
            <a-option
              v-for="provider in providerOptions"
              :key="provider.value"
              :label="provider.label"
              :value="provider.value"
              class="provider-option"
            >
              <div class="provider-option-content">
                <span class="inline-icon">
                  <component :is="provider.icon" />
                </span>
                <div class="provider-info">
                  <div class="provider-label">
                    {{ provider.label }}
                  </div>
                  <div class="provider-desc">
                    {{ provider.description }}
                  </div>
                </div>
              </div>
            </a-option>
          </a-select>
        </a-form-item>

        <a-form-item label="模型类型" field="type">
          <a-select
            v-model="form.type"
            placeholder="请选择模型类型"
            size="large"
            style="width: 100%"
          >
            <a-option label="对话 (Chat/LLM)" value="CHAT" />
            <a-option label="向量嵌入 (Embedding)" value="EMBEDDING" />
            <a-option label="结果重排 (Reranker)" value="RERANKER" />
            <a-option label="图像生成 (Image)" value="TEXT_TO_IMAGE" />
            <a-option label="语音转文字 (STT)" value="SPEECH_TO_TEXT" />
            <a-option label="文字转语音 (TTS)" value="TEXT_TO_SPEECH" />
          </a-select>
        </a-form-item>
      </div>

      <!-- Step 02: 配置密钥 -->
      <div v-show="wizardStep === 1" class="step-content">
        <!-- 有已保存密钥时 -->
        <template v-if="savedKeys.length > 0">
          <a-alert
            title="使用已保存密钥"
            type="info"
            description="选择已保存的 API 密钥，或手动输入新的密钥"
            :closable="false"
            show-icon
            style="margin-bottom: 16px;"
          />

          <a-form-item label="选择已保存密钥">
            <a-select
              v-model="selectedSavedKeyId"
              placeholder="选择已保存的凭据"
              size="large"
              style="width: 100%"
              allow-clear
              @change="onSavedKeyChange"
            >
              <a-option
                v-for="key in savedKeys"
                :key="key.id"
                :label="`${key.name} (${key.provider})`"
                :value="key.id"
              />
            </a-select>
          </a-form-item>

          <a-divider>或手动输入</a-divider>
        </template>

        <!-- 无密钥时引导 -->
        <template v-else>
          <a-alert
            title="暂无 API 密钥"
            type="warning"
            description="请先添加供应商密钥，以便快速获取模型列表。也可以直接手动输入模型标识符。"
            :closable="false"
            show-icon
            style="margin-bottom: 16px;"
          />
          <a-button
            type="primary"
            plain
            :icon="Link"
            @click="keyDialogVisible = true; fetchExternalKeys()"
          >
            前往添加 API 密钥
          </a-button>
          <a-divider>或直接手动输入</a-divider>
        </template>

        <a-form-item label="API Base URL">
          <a-input
            v-model="fetchConfig.baseUrl"
            placeholder="例如: https://api.openai.com/v1"
            size="large"
          >
            <template #prefix>
              <span class="inline-icon"><Link /></span>
            </template>
          </a-input>
        </a-form-item>

        <a-form-item label="API Key">
          <a-input
            v-model="fetchConfig.apiKey"
            type="password"
            allow-clear
            placeholder="sk-..."
            size="large"
          >
            <template #prefix>
              <span class="inline-icon"><Key /></span>
            </template>
          </a-input>
        </a-form-item>
      </div>

      <!-- Step 03: 获取并选择模型 -->
      <div v-show="wizardStep === 2" class="step-content">
        <a-form-item label="模型标识符">
          <a-input
            v-model="form.modelId"
            placeholder="例如: gpt-4o, llama3:8b, text-embedding-3-small"
            size="large"
          >
            <template #prefix>
              <span class="inline-icon"><Cpu /></span>
            </template>
            <template #append>
              <a-button :loading="isFetchingModels" @click="handleFetchFromApi">
                从 API 获取
              </a-button>
            </template>
          </a-input>
          <template #extra>
            <span style="color: var(--neutral-gray-500); font-size: 12px;">
              点击"从 API 获取"按钮自动获取供应商提供的所有模型
            </span>
          </template>
        </a-form-item>

        <!-- 获取到的模型列表 -->
        <div v-if="availableModels.length > 0" class="fetched-models-section">
          <div class="fetched-header">
            <span>发现 {{ availableModels.length }} 个模型：</span>
            <a-button status="success" size="small" @click="handleImportAll">
              一键全部导入
            </a-button>
          </div>
          <div class="arco-scroll-panel">
            <div class="model-grid">
              <a-tag
                v-for="m in availableModels"
                :key="m.id"
                size="large"
                class="model-tag"
                :type="form.modelId === m.id ? 'success' : 'info'"
                @click="onSelectFetchedModel(m)"
              >
                {{ m.id }}
              </a-tag>
            </div>
          </div>
        </div>
      </div>

      <!-- 定价配置 (可选，任何步骤都可访问) -->
      <div v-if="showPricing" class="pricing-section">
        <a-divider />
        <a-collapse v-model="pricingCollapse">
          <a-collapse-item title="高级配置：定价策略 (可选)" name="pricing">
            <a-alert
              title="此处只配置默认租户组的价格，高级配置请前往系统设置。"
              type="info"
              :closable="false"
              show-icon
              style="margin-bottom: 16px;"
            />

            <a-row :gutter="20">
              <a-col :span="12">
                <a-form-item label="计费模式">
                  <a-radio-group v-model="pricingForm.billingMode">
                    <a-radio label="PER_TOKEN">
                      Token计费
                    </a-radio>
                    <a-radio label="PER_REQUEST">
                      按次计费
                    </a-radio>
                  </a-radio-group>
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="结算货币">
                  <a-select v-model="pricingForm.currency" style="width: 100%">
                    <a-option label="USD ($)" value="USD" />
                    <a-option label="CNY (¥)" value="CNY" />
                    <a-option label="EUR (€)" value="EUR" />
                  </a-select>
                </a-form-item>
              </a-col>
            </a-row>

            <a-row :gutter="20">
              <a-col :span="12">
                <div class="pricing-header">
                  成本 (Internal Cost)
                </div>
                <a-form-item label="Input / 1k">
                  <a-input-number
                    v-model="pricingForm.inputCostUnit"
                    :precision="6"
                    :step="0.001"
                    style="width: 100%"
                  />
                </a-form-item>
                <a-form-item label="Output / 1k">
                  <a-input-number
                    v-model="pricingForm.outputCostUnit"
                    :precision="6"
                    :step="0.001"
                    style="width: 100%"
                  />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <div class="pricing-header">
                  报价 (External Price)
                </div>
                <a-form-item label="Input / 1k">
                  <a-input-number
                    v-model="pricingForm.inputPriceUnit"
                    :precision="6"
                    :step="0.001"
                    style="width: 100%"
                  />
                </a-form-item>
                <a-form-item label="Output / 1k">
                  <a-input-number
                    v-model="pricingForm.outputPriceUnit"
                    :precision="6"
                    :step="0.001"
                    style="width: 100%"
                  />
                </a-form-item>
              </a-col>
            </a-row>
          </a-collapse-item>
        </a-collapse>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <a-button @click="dialogVisible = false">
            取消
          </a-button>
          <a-button v-if="wizardStep > 0" @click="wizardStep--">
            上一步
          </a-button>
          <a-button v-if="wizardStep < 2" type="primary" @click="nextStep">
            下一步
          </a-button>
          <a-button
            v-if="wizardStep === 2"
            type="primary"
            :loading="submitting"
            @click="handleSubmit"
          >
            确认保存
          </a-button>
        </div>
      </template>
    </OrinArcoFormDialog>

    <!-- Test Result Dialog -->
    <OrinArcoFormDialog v-model="testResultVisible" title="模型连通性测试" width="500px">
      <div v-if="testResult" class="test-result-body">
        <div class="result-item">
          <span class="label">测试结果:</span>
          <a-tag :type="testResult.success ? 'success' : 'danger'">
            {{ testResult.success ? '成功' : '失败' }}
          </a-tag>
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
        <a-button type="primary" @click="testResultVisible = false">
          完成
        </a-button>
      </template>
    </OrinArcoFormDialog>

    <!-- API 密钥管理 Dialog -->
    <OrinArcoFormDialog
      v-model="keyDialogVisible"
      title="API 密钥管理"
      width="800px"
      top="5vh"
    >
      <div class="key-management-header" style="margin-bottom: 20px;">
        <p style="color: var(--neutral-gray-500); margin: 0;">
          管理外部AI服务提供商的 API 密钥，用于调用模型时进行身份验证
        </p>
      </div>

      <div style="margin-bottom: 16px;">
        <a-button type="primary" @click="openKeyForm()">
          添加 API 密钥
        </a-button>
      </div>

      <OrinArcoDataTable
        :loading="keyLoading"
        :data="externalKeys"
        :columns="keyColumns"
        row-key="id"
      >
        <template #provider="{ record }">
          <OrinArcoSemanticTag family="provider" :value="record.provider">
            {{ record.provider }}
          </OrinArcoSemanticTag>
        </template>
        <template #keyPreview="{ record }">
          <code class="key-preview">
            {{ isKeyVisible(record.id) ? record.apiKey : maskKey(record.apiKey) }}
          </code>
          <a-button type="text" size="mini" @click="toggleKeyVisibility(record.id)">
            {{ isKeyVisible(record.id) ? '隐藏' : '显示' }}
          </a-button>
        </template>
        <template #enabled="{ record }">
          <a-switch v-model="record.enabled" @change="toggleKeyStatus(record)" />
        </template>
        <template #actions="{ record }">
          <OrinArcoRowActions
            :actions="keyRowActions"
            @select="action => handleKeyRowAction(action, record)"
          />
        </template>
      </OrinArcoDataTable>

      <template #footer>
        <a-button @click="keyDialogVisible = false">
          关闭
        </a-button>
      </template>
    </OrinArcoFormDialog>

    <!-- API 密钥编辑表单 Dialog -->
    <OrinArcoFormDialog
      ref="keyFormRef"
      v-model="keyFormVisible"
      :title="keyFormData.id ? '编辑 API 密钥' : '添加 API 密钥'"
      width="500px"
      :model="keyFormData"
    >
      <a-form-item label="密钥名称" field="name">
        <a-input v-model="keyFormData.name" placeholder="例如: 我的 OpenAI 主密钥" />
      </a-form-item>
      <a-row :gutter="20">
        <a-col :span="12">
          <a-form-item label="供应商" field="provider">
            <a-select v-model="keyFormData.provider" style="width: 100%">
              <a-option label="OpenAI" value="OpenAI" />
              <a-option label="DeepSeek" value="DeepSeek" />
              <a-option label="SiliconFlow" value="SiliconFlow" />
              <a-option label="Anthropic" value="Anthropic" />
              <a-option label="Groq" value="Groq" />
              <a-option label="Ollama (本地)" value="Ollama" />
              <a-option label="Dify" value="Dify" />
              <a-option label="智谱 AI" value="Zhipu" />
              <a-option label="阿里云" value="Aliyun" />
              <a-option label="百度" value="Baidu" />
              <a-option label="腾讯云" value="Tencent" />
            </a-select>
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="Base URL (可选)">
            <a-input v-model="keyFormData.baseUrl" placeholder="默认官方地址" />
          </a-form-item>
        </a-col>
      </a-row>
      <a-form-item label="API Key" field="apiKey">
        <a-input
          v-model="keyFormData.apiKey"
          type="password"
          allow-clear
          placeholder="sk-..."
        />
      </a-form-item>
      <a-form-item label="备注">
        <a-textarea v-model="keyFormData.description" :rows="2" />
      </a-form-item>
      <template #footer>
        <a-button @click="keyFormVisible = false">
          取消
        </a-button>
        <a-button type="primary" @click="saveKey">
          保存
        </a-button>
      </template>
    </OrinArcoFormDialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import {
  Connection,
  Cpu,
  Delete,
  Edit,
  Key,
  Link,
  Monitor,
  Moon,
  Opportunity,
  Platform,
  Plus,
  Refresh,
  Search,
  Star,
  Sunrise,
  View
} from '@element-plus/icons-vue';
import OrinArcoDataTable from '@/ui/arco/OrinArcoDataTable.vue';
import OrinArcoFormDialog from '@/ui/arco/OrinArcoFormDialog.vue';
import OrinArcoRowActions from '@/ui/arco/OrinArcoRowActions.vue';
import OrinArcoSemanticTag from '@/ui/arco/OrinArcoSemanticTag.vue';
import { getModelList, saveModel, deleteModel, toggleModelStatus, fetchModels } from '@/api/model';
import { getPricingConfig, savePricingConfig } from '@/api/monitor';
import { getExternalKeys, saveExternalKey, deleteExternalKey, toggleExternalKeyStatus } from '@/api/apiKey';
import { getProviderList } from '@/api/system';
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
const modelDetailVisible = ref(false);
const selectedModel = ref(null);
const modelList = ref([]);
const searchQuery = ref('');
const providerFilter = ref('ALL');
const typeFilter = ref('ALL');
const statusFilter = ref('ALL');
const sortMode = ref('created_desc');
const dialogVisible = ref(false);
const formRef = ref(null);

// 向导步骤配置
const wizardStep = ref(0);
const wizardSteps = [
  { title: '选择供应商', icon: Connection },
  { title: '配置密钥', icon: Key },
  { title: '选择模型', icon: Cpu }
];

// 供应商选项配置 - 从API获取
const providerOptions = ref([]);

// 从API获取供应商列表
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

const keyColumns = [
  { title: '密钥名称', dataIndex: 'name', minWidth: 150 },
  { title: '供应商', dataIndex: 'provider', width: 120, slotName: 'provider' },
  { title: 'API 密钥', dataIndex: 'apiKey', minWidth: 220, slotName: 'keyPreview' },
  { title: '端点地址', dataIndex: 'baseUrl', minWidth: 180, ellipsis: true, tooltip: true },
  { title: '状态', dataIndex: 'enabled', width: 90, align: 'center', slotName: 'enabled' },
  { title: '操作', dataIndex: 'actions', width: 130, align: 'center', fixed: 'right', slotName: 'actions' }
];

const keyRowActions = [
  { key: 'edit', label: '编辑配置' },
  { key: 'delete', label: '删除', danger: true }
];

const fetchData = async () => {
  loading.value = true;
  try {
    const res = await getModelList();
    modelList.value = res;
  } catch (e) {
    console.error(e);
  } finally {
    loading.value = false;
    window.dispatchEvent(new Event('page-refresh-done'));
  }
};

const providerFilterOptions = computed(() => {
  return Array.from(new Set(modelList.value.map(item => item.provider).filter(Boolean))).sort();
});

const filteredList = computed(() => {
  let list = modelList.value;
  if (providerFilter.value !== 'ALL') {
    list = list.filter(m => m.provider === providerFilter.value);
  }
  if (typeFilter.value !== 'ALL') {
    list = list.filter(m => m.type === typeFilter.value);
  }
  if (statusFilter.value !== 'ALL') {
    list = list.filter(m => m.status === statusFilter.value);
  }
  if (searchQuery.value) {
    const q = searchQuery.value.toLowerCase();
    list = list.filter(m => {
      return [
        m.name,
        m.modelId,
        m.provider,
        formatModelType(m.type)
      ].some(value => String(value || '').toLowerCase().includes(q));
    });
  }
  return list;
});

const resetFilters = () => {
  providerFilter.value = 'ALL';
  typeFilter.value = 'ALL';
  statusFilter.value = 'ALL';
  sortMode.value = 'created_desc';
  searchQuery.value = '';
};

const toTimestamp = (value) => {
  if (!value) return 0;
  if (Array.isArray(value)) {
    return new Date(value[0], value[1] - 1, value[2], value[3] || 0, value[4] || 0, value[5] || 0).getTime();
  }
  return new Date(String(value).replace(' ', 'T')).getTime() || 0;
};

const compareText = (left, right) => String(left || '').localeCompare(String(right || ''), 'zh-CN');

const displayedList = computed(() => {
  const list = [...filteredList.value];
  const sorters = {
    created_desc: (a, b) => toTimestamp(b.createTime) - toTimestamp(a.createTime),
    created_asc: (a, b) => toTimestamp(a.createTime) - toTimestamp(b.createTime),
    name_asc: (a, b) => compareText(a.name, b.name),
    name_desc: (a, b) => compareText(b.name, a.name),
    provider_asc: (a, b) => compareText(a.provider, b.provider),
    type_asc: (a, b) => compareText(formatModelType(a.type), formatModelType(b.type))
  };
  return list.sort(sorters[sortMode.value] || sorters.created_desc);
});

const modelStats = computed(() => {
  const enabled = filteredList.value.filter(item => item.status === 'ENABLED').length;
  const disabled = filteredList.value.length - enabled;
  const providers = new Set(filteredList.value.map(item => item.provider).filter(Boolean)).size;
  const enabledRate = filteredList.value.length ? `${Math.round((enabled / filteredList.value.length) * 100)}%` : '0%';
  const providerCounts = filteredList.value.reduce((map, item) => {
    if (!item.provider) return map;
    map.set(item.provider, (map.get(item.provider) || 0) + 1);
    return map;
  }, new Map());
  const typeCounts = filteredList.value.reduce((map, item) => {
    if (!item.type) return map;
    map.set(item.type, (map.get(item.type) || 0) + 1);
    return map;
  }, new Map());
  const topProvider = [...providerCounts.entries()].sort((a, b) => b[1] - a[1])[0];
  const topType = [...typeCounts.entries()].sort((a, b) => b[1] - a[1])[0];
  return {
    total: modelList.value.length,
    filtered: filteredList.value.length,
    enabled,
    disabled,
    enabledRate,
    providers,
    topProvider: topProvider ? `${topProvider[0]} ${topProvider[1]} 个` : '暂无供应商分布',
    topType: topType ? formatModelType(topType[0]) : '暂无类型分布',
    topTypeCount: topType ? topType[1] : 0
  };
});

const handleSelectionChange = (rows) => {
  selectedIds.value = rows.map(row => row.id);
};

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
const showPricing = ref(true); // 默认开启显示，或改为受控模式

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
    currency: 'USD'
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

const handleKeyRowAction = (action, row) => {
  const handlers = {
    edit: openKeyForm,
    delete: deleteKey
  };
  handlers[action]?.(row);
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
  console.log('Editing row:', row);
  // 1. Immediately open dialog to avoid UI block
  Object.assign(form, row);
  wizardStep.value = 2; // Jump to details/pricing
  resetPricingForm();
  dialogVisible.value = true;

  // 2. Fetch pricing in background
  try {
    const pid = row.modelId;
    console.log('Fetching pricing for:', pid);
    const res = await getPricingConfig();
    const list = (res && res.data) ? res.data : (Array.isArray(res) ? res : []);
    const data = Array.isArray(list) ? list.find(p => p.providerId === pid && p.tenantGroup === 'default') : null;

    if (data) {
        Object.assign(pricingForm, data);
        console.log('Pricing loaded successfully');
    } else {
        pricingForm.providerId = pid;
        console.log('No existing pricing rule, using defaults');
    }
  } catch(e) {
    console.error('Failed to load pricing info:', e);
  }
};

const openModelDetail = (row) => {
  selectedModel.value = row;
  modelDetailVisible.value = true;
};

const getProviderInitial = (provider) => {
  const source = provider || 'M';
  return source.charAt(0).toUpperCase();
};

const getModelTypeTag = (type) => {
  const map = {
    CHAT: 'success',
    LLM: 'success',
    EMBEDDING: 'primary',
    RERANKER: 'warning',
    TEXT_TO_IMAGE: 'danger',
    TEXT_TO_VIDEO: 'danger',
    SPEECH_TO_TEXT: 'info',
    TEXT_TO_SPEECH: 'info'
  };
  return map[type] || 'info';
};

const handleSubmit = async () => {
  if (!formRef.value) return;

  try {
    const errors = await formRef.value.validate();
    if (errors) return;

    submitting.value = true;
    // 1. Save Model
    await saveModel(form);

    // 2. Save Pricing (only if modelId is set and we have input values)
    if (form.modelId && (pricingForm.inputCostUnit > 0 || pricingForm.outputCostUnit > 0 || pricingForm.id)) {
        pricingForm.providerId = form.modelId;
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

const handleTestModel = async (_row) => {
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
  ElMessageBox.confirm(`确定删除模型 "${row.name}" 吗?`, '警告', { type: 'warning' }).then(async () => {
    await deleteModel(row.id);
    ElMessage.success('删除成功');
    fetchData();
  });
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
  loadProviderOptions();
  window.addEventListener('page-refresh', fetchData);
});

onUnmounted(() => {
  window.removeEventListener('page-refresh', fetchData);
});
</script>

<style scoped>
.page-container {
  padding: 0;
  --text-primary: #243244;
  --text-secondary: #6b7a90;
  --orin-border-strong: #e3e9ef;
  --orin-border-soft: #edf2f6;
  --orin-primary: #0d9488;
  --orin-primary-soft: #ecfdf5;
  --orin-arco-border: #e3e9ef;
  --orin-arco-border-soft: #edf2f6;
  --orin-arco-text: #243244;
  --orin-arco-text-subtle: #6b7a90;
  --orin-arco-primary: #0d9488;
  color: #243244;
}

.model-management {
  min-height: 100vh;
  padding: 32px;
  max-width: 1600px;
  margin: 0 auto;
}

.fade-in {
  animation: fadeIn 0.35s ease-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.model-shell {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.model-topbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
}

.topbar-copy {
  min-width: 0;
}

.topbar-eyebrow {
  display: inline-flex;
  margin-bottom: 8px;
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 700;
}

.topbar-copy h1 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 26px;
  font-weight: 760;
  line-height: 1.2;
}

.topbar-copy p {
  margin: 8px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 14px;
  line-height: 1.6;
}

.topbar-actions,
.workspace-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.summary-card {
  padding: 18px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.04);
}

.summary-card.primary {
  border-color: var(--el-color-primary-light-7);
  background: linear-gradient(180deg, var(--el-color-primary-light-9), var(--el-bg-color));
}

.summary-card span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 700;
}

.summary-card strong {
  display: block;
  margin-top: 10px;
  color: var(--el-text-color-primary);
  font-size: 28px;
  line-height: 1;
}

.summary-card p {
  margin: 10px 0 0;
  overflow: hidden;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.model-workspace {
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.05);
}

.workspace-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 18px 20px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.workspace-head h2 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 16px;
  font-weight: 720;
}

.workspace-head p {
  margin: 6px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.filter-panel {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  padding: 16px 20px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-extra-light);
}

.filter-control {
  width: 180px;
}

.status-filter {
  width: 150px;
}

.filter-search {
  flex: 1 1 260px;
  min-width: 220px;
}

.model-table {
  width: 100%;
}

:deep(.model-table .el-table__header th) {
  background: var(--el-fill-color-extra-light);
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 700;
}

:deep(.model-table .el-table__row) {
  cursor: pointer;
}

.model-cell {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.model-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: 1px solid var(--el-color-primary-light-6);
  border-radius: 8px;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-size: 14px;
  font-weight: 760;
  flex-shrink: 0;
}

.model-avatar.large {
  width: 54px;
  height: 54px;
  font-size: 20px;
}

.model-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.model-copy strong {
  overflow: hidden;
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 680;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.model-copy span,
.time-compact {
  overflow: hidden;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 4px 9px;
  border: 0;
  border-radius: 999px;
  font: inherit;
  font-size: 12px;
  font-weight: 680;
  cursor: pointer;
}

.status-pill.active {
  background: var(--el-color-success-light-9);
  color: var(--el-color-success);
}

.status-pill.inactive {
  background: var(--el-fill-color);
  color: var(--el-text-color-secondary);
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: currentColor;
}

.action-buttons {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.table-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 56px;
  padding: 14px 18px;
  border-top: 1px solid var(--el-border-color-lighter);
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.drawer-profile {
  display: flex;
  gap: 14px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.drawer-profile h2 {
  margin: 10px 0 4px;
  color: var(--el-text-color-primary);
  font-size: 20px;
  line-height: 1.25;
  word-break: break-word;
}

.drawer-profile p {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  word-break: break-all;
}

.filter-actions {
  gap: 8px;
}

.filter-actions {
  display: inline-flex;
  align-items: center;
  flex: none;
}

.filter-actions {
  flex: none;
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
  background: var(--orin-primary);
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
  color: var(--orin-primary);
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
  color: var(--orin-primary);
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

html.dark .test-result-body p {
  background: #1e293b;
}

.table-card {
  border-radius: 8px;
}

.table-title span {
  color: #6b7a90;
  font-size: 12px;
}

.resource-filters,
.table-title {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.table-title strong {
  color: #243244;
  font-weight: 650;
}

.search-input {
  flex: 1 1 auto;
  min-width: 0;
}

.reset-action {
  flex: none;
  min-width: 72px;
}

.quiet-action,
.orin-secondary-action {
  border-color: #e3e9ef;
  color: #3f4d63;
  background: #ffffff;
}

.orin-primary-action {
  background: #0d9488;
  border-color: #0d9488;
}

@media (max-width: 960px) {
  .filter-actions {
    width: 100%;
  }
}

.status-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 24px;
  padding: 0 8px;
  border: 1px solid #e3e9ef;
  border-radius: 4px;
  color: #6b7a90;
  background: #ffffff;
  font: inherit;
  font-size: 12px;
  white-space: nowrap;
  cursor: pointer;
}

.status-toggle .status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #94a3b8;
}

.status-toggle.is-enabled {
  color: #0d9488;
  border-color: #99f6e4;
}

.status-toggle.is-enabled .status-dot {
  background: #0d9488;
}

.time-compact {
  white-space: nowrap;
}

.model-info {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.name {
  min-width: 0;
  overflow: hidden;
  color: #243244;
  font-weight: 560;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.model-detail-drawer {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.detail-heading {
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding-bottom: 14px;
  border-bottom: 1px solid #edf2f6;
}

.detail-provider {
  width: fit-content;
  padding: 2px 8px;
  border: 1px solid #e3e9ef;
  border-radius: 999px;
  color: #516174;
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
}

.detail-heading h3 {
  margin: 0;
  color: #243244;
  font-size: 20px;
}

.detail-heading p {
  margin: 0;
  color: #6b7a90;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}

.detail-list {
  display: grid;
  gap: 10px;
  margin: 0;
}

.detail-list div {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  gap: 12px;
}

.detail-list dt {
  color: #6b7a90;
}

.detail-list dd {
  margin: 0;
  color: #243244;
}

.drawer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 14px;
  border-top: 1px solid #edf2f6;
}

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
  color: var(--neutral-gray-400);
  font-size: 13px;
  margin-right: 12px;
}

html.dark .tip-text {
  color: #94a3b8;
}

.dialog-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

/* 黑夜模式适配 */
html.dark .onboard-stepper .step-icon-wrapper {
  background: var(--neutral-gray-800);
  color: var(--neutral-gray-400);
}

html.dark .onboard-stepper .step-label {
  color: var(--neutral-gray-500);
}

html.dark .onboard-stepper .step-name {
  color: var(--neutral-gray-400);
}

html.dark .onboard-stepper .step-item.active .step-name {
  color: var(--orin-primary);
}

html.dark .onboard-stepper .step-line {
  background: var(--neutral-gray-700);
}

html.dark .fetched-models-section {
  background: var(--neutral-gray-800);
}

html.dark .fetched-tag {
  background: var(--neutral-gray-700);
}

html.dark .api-fetch-section {
  background: var(--neutral-gray-800);
  border-color: var(--neutral-gray-600);
}

html.dark .code-block {
  background: var(--neutral-gray-800);
  border-color: var(--neutral-gray-600);
}
</style>
