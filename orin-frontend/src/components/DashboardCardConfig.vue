<template>
  <el-drawer
    v-model="visible"
    title="自定义监控面板"
    direction="rtl"
    size="400px"
    :before-close="handleClose"
  >
    <div class="config-container">
      <el-alert
        title="个性化配置"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 20px;"
      >
        <template #default>
          选择您需要在监控大屏显示的卡片模块，配置将自动保存。
        </template>
      </el-alert>

      <div class="section-title">统计卡片</div>
      <div class="card-list">
        <div 
          v-for="card in availableCards.stats" 
          :key="card.id"
          class="card-item"
          :class="{ disabled: !isCardEnabled(card.id) }"
          @click="toggleCard(card.id)"
        >
          <div class="card-info">
            <el-icon :style="{ color: card.color }"><component :is="card.icon" /></el-icon>
            <div class="card-text">
              <div class="card-name">{{ card.name }}</div>
              <div class="card-desc">{{ card.description }}</div>
            </div>
          </div>
          <el-switch 
            :model-value="isCardEnabled(card.id)" 
            @change="toggleCard(card.id)"
            @click.stop
          />
        </div>
      </div>

      <el-divider />

      <div class="section-title">监控模块</div>
      <div class="card-list">
        <div 
          v-for="card in availableCards.modules" 
          :key="card.id"
          class="card-item"
          :class="{ disabled: !isCardEnabled(card.id) }"
          @click="toggleCard(card.id)"
        >
          <div class="card-info">
            <el-icon :style="{ color: card.color }"><component :is="card.icon" /></el-icon>
            <div class="card-text">
              <div class="card-name">{{ card.name }}</div>
              <div class="card-desc">{{ card.description }}</div>
            </div>
          </div>
          <el-switch 
            :model-value="isCardEnabled(card.id)" 
            @change="toggleCard(card.id)"
            @click.stop
          />
        </div>
      </div>

      <el-divider />

      <div class="action-buttons">
        <el-button @click="resetToDefault" :icon="RefreshLeft">恢复默认</el-button>
        <el-button type="primary" @click="handleClose" :icon="Check">完成配置</el-button>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, computed } from 'vue';
import { 
  Cpu, TrendCharts, Connection, Monitor, 
  DataAnalysis, Histogram, Setting, Check, RefreshLeft 
} from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';

const props = defineProps({
  modelValue: Boolean
});

const emit = defineEmits(['update:modelValue', 'config-change']);

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
});

// 可用卡片配置
const availableCards = {
  stats: [
    { id: 'stat-agents', name: '智能体总数', description: '系统中所有智能体数量', icon: Cpu, color: '#409EFF' },
    { id: 'stat-requests', name: '今日请求', description: '当日 API 调用总量', icon: TrendCharts, color: '#67C23A' },
    { id: 'stat-tokens', name: 'Token 消耗', description: '累计 Token 使用量', icon: DataAnalysis, color: '#E6A23C' },
    { id: 'stat-latency', name: '平均延迟', description: '系统响应时间', icon: Connection, color: '#F56C6C' }
  ],
  modules: [
    { id: 'module-agents', name: '智能体实例', description: '活跃智能体列表与状态', icon: Cpu, color: '#409EFF' },
    { id: 'module-distribution', name: '分布统计', description: '智能体类型与状态分布', icon: Histogram, color: '#67C23A' },
    { id: 'module-activity', name: '活动日志', description: '最近系统活动记录', icon: Monitor, color: '#E6A23C' },
    { id: 'module-server', name: '服务器硬件', description: 'Dify 服务器 CPU/内存/磁盘', icon: Setting, color: '#909399', optional: true }
  ]
};

// 默认启用的卡片
const defaultEnabledCards = [
  'stat-agents', 'stat-requests', 'stat-tokens', 'stat-latency',
  'module-agents', 'module-distribution', 'module-activity'
];

// 当前启用的卡片
const enabledCards = ref([]);

// 初始化配置
const initConfig = () => {
  const saved = localStorage.getItem('dashboard_card_config');
  if (saved) {
    try {
      enabledCards.value = JSON.parse(saved);
    } catch (e) {
      enabledCards.value = [...defaultEnabledCards];
    }
  } else {
    enabledCards.value = [...defaultEnabledCards];
  }
};

initConfig();

const isCardEnabled = (cardId) => {
  return enabledCards.value.includes(cardId);
};

const toggleCard = (cardId) => {
  const index = enabledCards.value.indexOf(cardId);
  if (index > -1) {
    enabledCards.value.splice(index, 1);
  } else {
    enabledCards.value.push(cardId);
  }
  saveConfig();
};

const saveConfig = () => {
  localStorage.setItem('dashboard_card_config', JSON.stringify(enabledCards.value));
  emit('config-change', enabledCards.value);
};

const resetToDefault = () => {
  enabledCards.value = [...defaultEnabledCards];
  saveConfig();
  ElMessage.success('已恢复默认配置');
};

const handleClose = () => {
  visible.value = false;
};

// 暴露方法供父组件调用
defineExpose({
  getEnabledCards: () => enabledCards.value,
  initConfig
});
</script>

<style scoped>
.config-container {
  padding: 0 4px;
}

.section-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--neutral-gray-800);
  margin-bottom: 16px;
  padding-left: 4px;
}

.card-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 20px;
}

.card-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  background: var(--neutral-gray-50);
  border-radius: var(--radius-lg);
  border: 2px solid transparent;
  cursor: pointer;
  transition: all 0.3s;
}

.card-item:hover {
  background: white;
  border-color: var(--primary-color);
  transform: translateX(4px);
}

.card-item.disabled {
  opacity: 0.5;
  background: var(--neutral-gray-100);
}

.card-info {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
}

.card-info .el-icon {
  font-size: 24px;
}

.card-text {
  flex: 1;
}

.card-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--neutral-gray-900);
  margin-bottom: 4px;
}

.card-desc {
  font-size: 12px;
  color: var(--neutral-gray-500);
  line-height: 1.4;
}

.action-buttons {
  display: flex;
  gap: 12px;
  margin-top: 24px;
}

.action-buttons .el-button {
  flex: 1;
}
</style>
