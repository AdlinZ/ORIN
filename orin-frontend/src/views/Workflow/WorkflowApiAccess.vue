<template>
  <div class="api-access-container">
    <!-- Web App Card -->
    <div class="service-card">
      <div class="card-header">
        <div class="header-left">
          <div class="icon-box blue">
            <el-icon><Monitor /></el-icon>
          </div>
          <span class="card-title">Web App</span>
        </div>
        <div class="header-right">
          <span class="status-indicator">
            <span class="dot running"></span>
            <span class="text">运行中</span>
          </span>
          <el-switch v-model="webAppActive" />
        </div>
      </div>
      
      <div class="card-content">
        <label>公开访问 URL</label>
        <div class="url-input-wrapper">
          <el-input v-model="webAppUrl" readonly class="custom-input">
            <template #suffix>
              <div class="suffix-actions">
                <el-icon class="action-icon" @click="copyToClipboard(webAppUrl)"><CopyDocument /></el-icon>
                <el-icon class="action-icon"><FullScreen /></el-icon>
                <el-icon class="action-icon"><Refresh /></el-icon>
              </div>
            </template>
          </el-input>
        </div>
      </div>

      <div class="card-footer">
        <el-button link class="footer-btn" @click="openWebApp"><el-icon><TopRight /></el-icon> 启动</el-button>
        <el-button link class="footer-btn"><el-icon><Postcard /></el-icon> 嵌入</el-button>
        <el-button link class="footer-btn"><el-icon><Operation /></el-icon> 定制化</el-button>
        <el-button link class="footer-btn"><el-icon><Setting /></el-icon> 设置</el-button>
      </div>
    </div>

    <!-- Backend Service API Card -->
    <div class="service-card">
      <div class="card-header">
        <div class="header-left">
          <div class="icon-box purple">
            <el-icon><Connection /></el-icon>
          </div>
          <span class="card-title">后端服务 API</span>
        </div>
        <div class="header-right">
          <span class="status-indicator">
            <span class="dot running"></span>
            <span class="text">运行中</span>
          </span>
          <el-switch v-model="apiActive" />
        </div>
      </div>
      
      <div class="card-content">
        <label>API 访问凭据</label>
        <div class="url-input-wrapper">
          <el-input v-model="apiUrl" readonly class="custom-input">
            <template #suffix>
              <div class="suffix-actions">
                <el-icon class="action-icon" @click="copyToClipboard(apiUrl)"><CopyDocument /></el-icon>
              </div>
            </template>
          </el-input>
        </div>
      </div>

      <div class="card-footer">
        <el-button link class="footer-btn"><el-icon><Key /></el-icon> API 密钥</el-button>
        <el-button link class="footer-btn" @click="openApiDocs"><el-icon><Document /></el-icon> 查阅 API 文档</el-button>
      </div>
    </div>

    <!-- MCP Service Card -->
    <div class="service-card">
      <div class="card-header">
        <div class="header-left">
          <div class="icon-box blue-dark">
            <el-icon><Cpu /></el-icon>
          </div>
          <span class="card-title">MCP 服务</span>
        </div>
        <div class="header-right">
          <span class="status-indicator">
            <span class="dot stopped"></span>
            <span class="text stopped">已停用</span>
          </span>
          <el-switch v-model="mcpActive" />
        </div>
      </div>
    </div>

  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { 
  Monitor, Connection, Cpu, CopyDocument, FullScreen, Refresh, 
  TopRight, Postcard, Operation, Setting, Key, Document 
} from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { getWorkflowAccess } from '@/api/workflow';

const route = useRoute();

const webAppActive = ref(true);
const webAppUrl = ref('');

const apiActive = ref(true);
const apiUrl = ref('');
const apiKey = ref('sk-...'); // Placeholder initially

const mcpActive = ref(false);

const loading = ref(false);

const fetchAccessInfo = async () => {
    // If not in a specific workflow context (e.g. creating new), skip
    if (!route.params.id) return;

    loading.value = true;
    try {
        const res = await getWorkflowAccess(route.params.id);
        if (res) {
            webAppUrl.value = res.webAppUrl;
            apiUrl.value = res.apiUrl;
            apiKey.value = res.apiKey || 'sk-...';
        }
    } catch (error) {
        console.error('Failed to fetch workflow access info:', error);
        ElMessage.error('获取访问信息失败');
    } finally {
        loading.value = false;
    }
};

onMounted(() => {
    fetchAccessInfo();
});

const copyToClipboard = (text) => {
  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success('已复制到剪贴板');
  });
};

const openWebApp = () => {
  if (webAppUrl.value) {
    window.open(webAppUrl.value, '_blank');
  }
};

const openApiDocs = () => {
  window.open('http://localhost:8080/swagger-ui/index.html', '_blank');
};
</script>

<style scoped>
.api-access-container {
  padding: 24px;
  background-color: #f9fafb;
  min-height: 100%;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.service-card {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #eaecf0;
  box-shadow: 0 1px 3px rgba(16, 24, 40, 0.05);
  overflow: hidden;
}

.card-header {
  padding: 16px 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.icon-box {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
}

.icon-box.blue { background-color: #155eef; }
.icon-box.purple { background-color: #7f56d9; }
.icon-box.blue-dark { background-color: #004eeb; }

.card-title {
  font-size: 14px;
  font-weight: 600;
  color: #101828;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.status-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 500;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}
.dot.running { background-color: #12b76a; }
.dot.stopped { background-color: #f79009; }

.text { color: #027a48; }
.text.stopped { color: #b54708; }

.card-content {
  padding: 0 20px 16px;
}

.card-content label {
  display: block;
  font-size: 12px;
  font-weight: 500;
  color: #344054;
  margin-bottom: 6px;
}

.url-input-wrapper {
  background: #f9fafb;
  border-radius: 8px;
}

:deep(.custom-input .el-input__wrapper) {
  background-color: #f9fafb;
  box-shadow: none !important;
  border: 1px solid #d0d5dd;
  border-radius: 8px;
}

:deep(.custom-input .el-input__wrapper:hover) {
  border-color: #84CAFF;
}

.suffix-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #667085;
}

.action-icon {
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
}
.action-icon:hover {
  background-color: #f2f4f7;
  color: #155eef;
}

.card-footer {
  padding: 12px 20px;
  background-color: #fcfcfd;
  border-top: 1px solid #eaecf0;
  display: flex;
  gap: 24px;
}

.footer-btn {
  color: #475467 !important;
  font-weight: 500 !important;
  font-size: 12px !important;
  display: flex;
  align-items: center;
  gap: 6px;
}
.footer-btn:hover {
  color: #155eef !important;
}
</style>
