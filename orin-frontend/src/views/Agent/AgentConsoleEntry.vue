<template>
  <div class="console-entry-page">
    <div class="ambient ambient-left"></div>
    <div class="ambient ambient-right"></div>

    <div class="entry-container">
      <section class="hero-panel">
        <div class="hero-copy">
          <div class="eyebrow">
            <el-icon><Monitor /></el-icon>
            <span>Agent Console</span>
          </div>
          <h1>为每个智能体准备一个更顺手的工作台</h1>
          <p>
            从最近访问快速继续，也可以按类型筛选全部智能体。这里更像一个控制中心，而不只是列表页。
          </p>

          <div class="hero-actions">
            <el-input
              v-model="searchQuery"
              placeholder="搜索智能体名称、描述或模型"
              :prefix-icon="Search"
              clearable
              class="hero-search"
            />
            <el-select
              v-model="typeFilter"
              placeholder="全部类型"
              clearable
              class="hero-filter"
            >
              <el-option label="全部类型" value="" />
              <el-option label="对话" value="CHAT" />
              <el-option label="工作流" value="WORKFLOW" />
              <el-option label="文生图" value="TEXT_TO_IMAGE" />
              <el-option label="图生图" value="IMAGE_TO_IMAGE" />
              <el-option label="语音合成" value="TEXT_TO_SPEECH" />
              <el-option label="转写文字" value="SPEECH_TO_TEXT" />
              <el-option label="视频生成" value="TEXT_TO_VIDEO" />
            </el-select>
          </div>
        </div>

        <div class="hero-stats">
          <div class="stat-card">
            <span class="stat-label">可用智能体</span>
            <strong class="stat-value">{{ agents.length }}</strong>
            <span class="stat-meta">覆盖对话、工作流、多模态生成</span>
          </div>
          <div class="stat-card">
            <span class="stat-label">最近访问</span>
            <strong class="stat-value">{{ recentAgents.length }}</strong>
            <span class="stat-meta">支持从最近任务快速回到现场</span>
          </div>
          <div class="stat-card accent">
            <span class="stat-label">当前筛选结果</span>
            <strong class="stat-value">{{ filteredAgents.length }}</strong>
            <span class="stat-meta">按关键词和能力类型即时过滤</span>
          </div>
        </div>
      </section>

      <section v-if="recentAgents.length > 0" class="panel-section">
        <div class="section-header">
          <div>
            <div class="section-kicker">继续工作</div>
            <h2>最近访问</h2>
          </div>
          <span class="section-note">保留最近打开过的智能体入口</span>
        </div>

        <div class="recent-grid">
          <button
            v-for="agent in recentAgents"
            :key="agent.id"
            class="recent-card"
            type="button"
            @click="openConsole(agent.id)"
          >
            <div class="recent-icon" :style="{ background: getAgentAccent(agent).soft, color: getAgentAccent(agent).strong }">
              <el-icon><component :is="getAgentIcon(agent.viewType)" /></el-icon>
            </div>
            <div class="recent-body">
              <div class="recent-title-row">
                <span class="recent-title">{{ agent.name }}</span>
                <el-tag size="small" effect="plain">{{ formatAgentType(agent.viewType) }}</el-tag>
              </div>
              <div class="recent-desc">{{ agent.description || '已准备好继续当前会话或执行流程。' }}</div>
              <div class="recent-meta">
                <span>{{ agent.model || '未配置模型' }}</span>
                <span>{{ formatTime(agent.lastAccess) }}</span>
              </div>
            </div>
            <el-icon class="recent-arrow"><ArrowRight /></el-icon>
          </button>
        </div>
      </section>

      <section class="panel-section">
        <div class="section-header">
          <div>
            <div class="section-kicker">全量入口</div>
            <h2>全部智能体</h2>
          </div>
          <span class="section-note">点击卡片直接进入对应控制台</span>
        </div>

        <div v-if="loading" class="skeleton-grid">
          <div v-for="n in 6" :key="n" class="skeleton-card"></div>
        </div>

        <div v-else-if="filteredAgents.length === 0" class="empty-state">
          <div class="empty-visual">
            <el-icon><Picture /></el-icon>
          </div>
          <h3>暂无匹配的智能体</h3>
          <p>可以尝试调整关键词，或者清空类型筛选后重新查看。</p>
        </div>

        <div v-else class="agent-grid">
          <button
            v-for="agent in filteredAgents"
            :key="agent.id"
            class="agent-card"
            type="button"
            @click="openConsole(agent.id)"
          >
            <div class="card-top">
              <div class="card-badge" :style="{ background: getAgentAccent(agent).soft, color: getAgentAccent(agent).strong }">
                <el-icon><component :is="getAgentIcon(agent.viewType)" /></el-icon>
              </div>
              <el-tag size="small" effect="plain">{{ formatAgentType(agent.viewType) }}</el-tag>
            </div>

            <div class="card-main">
              <h3>{{ agent.name }}</h3>
              <p>{{ agent.description || '暂无描述，进入控制台后可继续配置提示词、参数和运行方式。' }}</p>
            </div>

            <div class="card-footer">
              <span class="model-pill">{{ agent.model || '未配置模型' }}</span>
              <span class="enter-link">
                进入控制台
                <el-icon><ArrowRight /></el-icon>
              </span>
            </div>
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { getAgentList } from '@/api/monitor';
import {
  Search, Picture, ArrowRight, Monitor, ChatDotRound,
  Connection, Microphone, Headset, PictureFilled, VideoCamera
} from '@element-plus/icons-vue';

const router = useRouter();

const agents = ref([]);
const recentAgents = ref([]);
const loading = ref(false);
const searchQuery = ref('');
const typeFilter = ref('');

const normalizeAgent = (agent) => ({
  ...agent,
  id: agent.id || agent.agentId,
  name: agent.name || agent.agentName || '未命名智能体',
  description: agent.description || agent.agentDescription || '',
  model: agent.model || agent.modelName || '',
  viewType: agent.viewType || agent.mode || 'CHAT'
});

const filteredAgents = computed(() => {
  return agents.value.filter((agent) => {
    const q = searchQuery.value.trim().toLowerCase();
    const matchesSearch = !q
      || agent.name?.toLowerCase().includes(q)
      || agent.description?.toLowerCase().includes(q)
      || agent.model?.toLowerCase().includes(q);

    const matchesType = !typeFilter.value || agent.viewType?.toUpperCase() === typeFilter.value;
    return matchesSearch && matchesType;
  });
});

const fetchAgents = async () => {
  try {
    loading.value = true;
    const response = await getAgentList();
    const rawList = response?.data || response || [];
    agents.value = rawList.map(normalizeAgent).filter((agent) => agent.id);
    loadRecentAgents();
  } catch (error) {
    ElMessage.error('加载智能体列表失败');
  } finally {
    loading.value = false;
  }
};

const loadRecentAgents = () => {
  const recentIds = JSON.parse(localStorage.getItem('recent-agents') || '[]');
  const recentMeta = JSON.parse(localStorage.getItem('recent-agents-meta') || '{}');

  recentAgents.value = recentIds
    .map((id) => {
      const agent = agents.value.find((item) => item.id === id);
      if (!agent) return null;
      return {
        ...agent,
        lastAccess: recentMeta[id] || null
      };
    })
    .filter(Boolean)
    .slice(0, 4);
};

const openConsole = (agentId) => {
  const recentIds = JSON.parse(localStorage.getItem('recent-agents') || '[]');
  const recentMeta = JSON.parse(localStorage.getItem('recent-agents-meta') || '{}');
  const now = Date.now();
  const newRecent = [agentId, ...recentIds.filter((id) => id !== agentId)].slice(0, 10);

  recentMeta[agentId] = now;

  localStorage.setItem('recent-agents', JSON.stringify(newRecent));
  localStorage.setItem('recent-agents-meta', JSON.stringify(recentMeta));

  router.push(`/dashboard/agents/console/${agentId}`);
};

const formatAgentType = (type) => {
  if (!type) return '未知类型';
  const typeMap = {
    CHAT: '对话',
    WORKFLOW: '工作流',
    TEXT_TO_IMAGE: '文生图',
    IMAGE_TO_IMAGE: '图生图',
    TEXT_TO_SPEECH: '语音合成',
    SPEECH_TO_TEXT: '转写文字',
    TEXT_TO_VIDEO: '视频生成',
    TTI: '文生图',
    TTS: '语音合成',
    STT: '转写文字',
    TTV: '视频生成'
  };
  return typeMap[type.toUpperCase()] || type;
};

const formatTime = (timestamp) => {
  if (!timestamp) return '近期未打开';
  const date = new Date(timestamp);
  const now = new Date();
  const diff = now - date;

  if (diff < 60000) return '刚刚访问';
  if (diff < 3600000) return `${Math.floor(diff / 60000)} 分钟前`;
  if (diff < 86400000) return `${Math.floor(diff / 3600000)} 小时前`;
  return `${Math.floor(diff / 86400000)} 天前`;
};

const getAgentIcon = (type) => {
  const normalized = (type || '').toUpperCase();
  if (normalized === 'WORKFLOW') return Connection;
  if (normalized === 'TEXT_TO_IMAGE' || normalized === 'IMAGE_TO_IMAGE' || normalized === 'TTI') return PictureFilled;
  if (normalized === 'TEXT_TO_SPEECH' || normalized === 'TTS') return Headset;
  if (normalized === 'SPEECH_TO_TEXT' || normalized === 'STT') return Microphone;
  if (normalized === 'TEXT_TO_VIDEO' || normalized === 'TTV') return VideoCamera;
  return ChatDotRound;
};

const getAgentAccent = (agent) => {
  const normalized = (agent?.viewType || '').toUpperCase();
  if (normalized === 'WORKFLOW') {
    return { soft: 'rgba(14, 165, 233, 0.14)', strong: '#0369a1' };
  }
  if (normalized === 'TEXT_TO_IMAGE' || normalized === 'IMAGE_TO_IMAGE' || normalized === 'TTI') {
    return { soft: 'rgba(249, 115, 22, 0.14)', strong: '#c2410c' };
  }
  if (normalized === 'TEXT_TO_SPEECH' || normalized === 'TTS') {
    return { soft: 'rgba(217, 70, 239, 0.14)', strong: '#a21caf' };
  }
  if (normalized === 'SPEECH_TO_TEXT' || normalized === 'STT') {
    return { soft: 'rgba(34, 197, 94, 0.14)', strong: '#15803d' };
  }
  if (normalized === 'TEXT_TO_VIDEO' || normalized === 'TTV') {
    return { soft: 'rgba(239, 68, 68, 0.14)', strong: '#b91c1c' };
  }
  return { soft: 'rgba(20, 184, 166, 0.14)', strong: '#0f766e' };
};

onMounted(() => {
  fetchAgents();
});
</script>

<style scoped>
.console-entry-page {
  position: relative;
  min-height: 100%;
  background:
    radial-gradient(circle at top left, rgba(20, 184, 166, 0.18), transparent 28%),
    radial-gradient(circle at top right, rgba(14, 165, 233, 0.14), transparent 24%),
    linear-gradient(180deg, #f4fbfb 0%, #f7f9fc 52%, #f3f5f8 100%);
  overflow: hidden;
}

.ambient {
  position: absolute;
  border-radius: 999px;
  filter: blur(30px);
  pointer-events: none;
}

.ambient-left {
  width: 220px;
  height: 220px;
  left: -70px;
  top: 120px;
  background: rgba(20, 184, 166, 0.16);
}

.ambient-right {
  width: 300px;
  height: 300px;
  right: -110px;
  top: 40px;
  background: rgba(59, 130, 246, 0.12);
}

.entry-container {
  position: relative;
  z-index: 1;
  padding: 28px;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.hero-panel,
.panel-section {
  border: 1px solid rgba(255, 255, 255, 0.8);
  background: rgba(255, 255, 255, 0.76);
  backdrop-filter: blur(18px);
  box-shadow: 0 20px 60px rgba(15, 23, 42, 0.08);
}

.hero-panel {
  border-radius: 28px;
  padding: 30px;
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(300px, 1fr);
  gap: 24px;
}

.eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(20, 184, 166, 0.12);
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
  margin-bottom: 18px;
}

.hero-copy h1 {
  margin: 0;
  font-size: 34px;
  line-height: 1.15;
  color: #0f172a;
  letter-spacing: -0.03em;
}

.hero-copy p {
  margin: 14px 0 0;
  max-width: 720px;
  color: #475569;
  font-size: 15px;
  line-height: 1.75;
}

.hero-actions {
  margin-top: 24px;
  display: flex;
  gap: 14px;
}

.hero-search {
  flex: 1;
}

.hero-filter {
  width: 180px;
}

.hero-stats {
  display: grid;
  gap: 14px;
}

.stat-card {
  border-radius: 22px;
  padding: 20px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.88), rgba(243, 247, 249, 0.96));
  border: 1px solid rgba(148, 163, 184, 0.18);
}

.stat-card.accent {
  background: linear-gradient(135deg, #0f766e, #0f766e 18%, #155e75 100%);
  color: #f8fafc;
}

.stat-label {
  display: block;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  opacity: 0.78;
}

.stat-value {
  display: block;
  margin-top: 12px;
  font-size: 38px;
  line-height: 1;
  color: inherit;
}

.stat-meta {
  display: block;
  margin-top: 10px;
  font-size: 13px;
  line-height: 1.6;
  color: inherit;
  opacity: 0.82;
}

.panel-section {
  border-radius: 24px;
  padding: 26px;
}

.section-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.section-kicker {
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: #0f766e;
  margin-bottom: 6px;
}

.section-header h2 {
  margin: 0;
  font-size: 24px;
  color: #0f172a;
  letter-spacing: -0.02em;
}

.section-note {
  font-size: 13px;
  color: #64748b;
}

.recent-grid,
.agent-grid {
  display: grid;
  gap: 18px;
}

.recent-grid {
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
}

.agent-grid {
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
}

.recent-card,
.agent-card {
  width: 100%;
  border: 1px solid rgba(148, 163, 184, 0.18);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.95), #f8fafc);
  cursor: pointer;
  transition: transform 0.25s ease, box-shadow 0.25s ease, border-color 0.25s ease;
  text-align: left;
}

.recent-card:hover,
.agent-card:hover {
  transform: translateY(-4px);
  border-color: rgba(15, 118, 110, 0.28);
  box-shadow: 0 20px 30px rgba(15, 23, 42, 0.08);
}

.recent-card {
  border-radius: 22px;
  padding: 18px;
  display: flex;
  align-items: center;
  gap: 16px;
}

.recent-icon,
.card-badge {
  width: 52px;
  height: 52px;
  border-radius: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  flex-shrink: 0;
}

.recent-body {
  min-width: 0;
  flex: 1;
}

.recent-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.recent-title {
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
}

.recent-desc {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.65;
  color: #64748b;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.recent-meta {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px 14px;
  font-size: 12px;
  color: #94a3b8;
}

.recent-arrow {
  font-size: 18px;
  color: #94a3b8;
}

.agent-card {
  border-radius: 22px;
  padding: 18px;
  display: flex;
  flex-direction: column;
  min-height: 230px;
}

.card-top,
.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.card-main {
  flex: 1;
  padding: 18px 0;
}

.card-main h3 {
  margin: 0;
  font-size: 18px;
  color: #0f172a;
}

.card-main p {
  margin: 10px 0 0;
  font-size: 14px;
  line-height: 1.7;
  color: #64748b;
}

.model-pill {
  display: inline-flex;
  align-items: center;
  max-width: 62%;
  padding: 8px 12px;
  border-radius: 999px;
  background: #eef6f6;
  color: #155e63;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.enter-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #0f766e;
  font-size: 13px;
  font-weight: 700;
}

.skeleton-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 18px;
}

.skeleton-card {
  height: 230px;
  border-radius: 22px;
  background: linear-gradient(90deg, #f1f5f9 25%, #ffffff 37%, #f1f5f9 63%);
  background-size: 400% 100%;
  animation: shimmer 1.8s infinite linear;
}

.empty-state {
  padding: 56px 20px;
  text-align: center;
}

.empty-visual {
  width: 82px;
  height: 82px;
  margin: 0 auto 18px;
  border-radius: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(15, 118, 110, 0.1);
  color: #0f766e;
  font-size: 34px;
}

.empty-state h3 {
  margin: 0;
  font-size: 20px;
  color: #0f172a;
}

.empty-state p {
  margin: 10px auto 0;
  max-width: 420px;
  font-size: 14px;
  line-height: 1.7;
  color: #64748b;
}

@keyframes shimmer {
  0% {
    background-position: 100% 0;
  }
  100% {
    background-position: -100% 0;
  }
}

@media (max-width: 1100px) {
  .hero-panel {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .entry-container {
    padding: 18px;
  }

  .hero-panel,
  .panel-section {
    padding: 20px;
    border-radius: 20px;
  }

  .hero-copy h1 {
    font-size: 28px;
  }

  .hero-actions {
    flex-direction: column;
  }

  .hero-filter {
    width: 100%;
  }

  .section-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .recent-card {
    align-items: flex-start;
  }
}
</style>
