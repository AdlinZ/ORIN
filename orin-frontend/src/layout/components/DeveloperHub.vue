<template>
  <el-dialog
    v-model="visible"
    title="开发者服务百宝箱"
    width="800px"
    class="dev-hub-dialog"
    destroy-on-close
    append-to-body
  >
    <div class="hub-container">
      <div class="hub-description">
        全面汇总了主流的 AI 开发者生态控制台，快速导航到各大平台。
      </div>
      
      <el-tabs v-model="activeTab" class="hub-tabs">
        <el-tab-pane 
          v-for="(category, index) in categories" 
          :key="index"
          :label="category.name" 
          :name="category.id"
        >
          <div class="hub-grid">
            <a 
              v-for="(item, itemIndex) in category.platforms" 
              :key="itemIndex"
              :href="item.url" 
              target="_blank" 
              class="hub-card"
              :style="{ '--brand-color': item.color }"
            >
              <div class="card-icon" :style="{ background: item.color + '15', color: item.color }">
                <el-icon><component :is="item.icon" /></el-icon>
              </div>
              <div class="card-content">
                <div class="card-title">{{ item.name }}</div>
                <div class="card-desc">{{ item.desc }}</div>
              </div>
              <div class="card-arrow">
                <el-icon><Right /></el-icon>
              </div>
            </a>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'
import { 
  Platform, Connection, DataAnalysis, Monitor, Message, Promotion, Key, 
  Link, Box, ChatDotRound, Star, Cpu, DataLine, Setting, Right
} from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const activeTab = ref('api')

const categories = [
  {
    id: 'api',
    name: '🎯 模型与 API',
    platforms: [
      { name: 'SiliconFlow (硅基流动)', desc: '高性价比模型分发', url: 'https://cloud.siliconflow.cn/models', icon: 'DataAnalysis', color: '#34d399' },
      { name: 'DeepSeek', desc: '深度求索 API 控制台', url: 'https://platform.deepseek.com/', icon: 'Monitor', color: '#0ea5e9' },
      { name: 'OpenAI', desc: '全球顶尖的大语言模型 API', url: 'https://platform.openai.com/api-keys', icon: 'Key', color: '#10b981' },
      { name: 'Anthropic (Claude)', desc: 'Claude 控制台与 API', url: 'https://console.anthropic.com/', icon: 'Promotion', color: '#d97757' },
      { name: 'Zhipu AI (智谱清言)', desc: '智谱 GLM 开放平台', url: 'https://open.bigmodel.cn/', icon: 'Message', color: '#8b5cf6' },
      { name: 'Moonshot (月之暗面)', desc: 'Kimi 的 API 平台', url: 'https://platform.moonshot.cn/', icon: 'ChatDotRound', color: '#3b82f6' },
      { name: 'Minimax (海螺AI)', desc: 'MiniMax 开放平台', url: 'https://platform.minimaxi.com/', icon: 'Star', color: '#ec4899' },
      { name: '零一万物 (01.AI)', desc: '李开复团队的 Yi 系列模型', url: 'https://platform.lingyiwanwu.com/', icon: 'Box', color: '#4f46e5' },
      { name: '百川智能 (Baichuan)', desc: '百川大模型 API', url: 'https://platform.baichuan-ai.com/', icon: 'Box', color: '#f59e0b' },
      { name: '阿里百炼', desc: '阿里云通义大模型平台', url: 'https://bailian.console.aliyun.com/', icon: 'Platform', color: '#f97316' },
      { name: '讯飞星火', desc: '各种认知大模型服务', url: 'https://xinghuo.xfyun.cn/sparkapi', icon: 'Cpu', color: '#06b6d4' }
    ]
  },
  {
    id: 'agent',
    name: '🤖 编排与智能体',
    platforms: [
      { name: 'n8n', desc: '强大的工作流自动化与 AI 编排', url: 'https://n8n.io/', icon: 'Connection', color: '#ff6d5a' },
      { name: 'Dify', desc: '强大的 LLM 应用编排平台', url: 'https://cloud.dify.ai/apps', icon: 'Connection', color: '#3b82f6' },
      { name: 'Coze (扣子)', desc: '字节跳动的智能体创建平台', url: 'https://www.coze.cn/', icon: 'DataLine', color: '#8b5cf6' },
      { name: 'Flowise', desc: 'LangChain 拖拽式 UI', url: 'https://flowiseai.com/', icon: 'Platform', color: '#10b981' },
      { name: 'Langflow', desc: '无需写代码构建 AI 管道', url: 'https://www.langflow.org/', icon: 'Setting', color: '#ec4899' },
      { name: 'FastGPT', desc: '基于 LLM 的知识库问答', url: 'https://fastgpt.in/', icon: 'Setting', color: '#10b981' },
      { name: 'Langfuse', desc: '大模型应用的可观测性平台', url: 'https://cloud.langfuse.com/', icon: 'Monitor', color: '#6366f1' },
      { name: 'Smith (LangChain)', desc: 'LangChain 的调试诊断平台', url: 'https://smith.langchain.com/', icon: 'Platform', color: '#f43f5e' }
    ]
  },
  {
    id: 'local',
    name: '💻 本地部署与框架',
    platforms: [
      { name: 'Ollama', desc: '在本地快速运行大型语言模型', url: 'https://ollama.com/', icon: 'Monitor', color: '#1f2937' },
      { name: 'vLLM', desc: '高吞吐低延迟的大模型推理引擎', url: 'https://vllm.ai/', icon: 'Cpu', color: '#0ea5e9' },
      { name: 'Xinference', desc: 'Xorbits 推理部署引擎', url: 'https://inference.readthedocs.io/', icon: 'Box', color: '#8b5cf6' },
      { name: 'LM Studio', desc: '桌面端本地模型探索工具', url: 'https://lmstudio.ai/', icon: 'DataAnalysis', color: '#4f46e5' },
      { name: 'AnythingLLM', desc: '本地私有化知识库', url: 'https://useanything.com/', icon: 'Promotion', color: '#10b981' }
    ]
  },
  {
    id: 'hub',
    name: '🌐 生态与社区',
    platforms: [
      { name: 'ModelScope (魔搭)', desc: '阿里主导的 AI 模型库', url: 'https://modelscope.cn/', icon: 'Platform', color: '#6366f1' },
      { name: 'HuggingFace', desc: '全球最大的 AI 模型社区', url: 'https://huggingface.co/', icon: 'Promotion', color: '#fbbf24' },
      { name: 'GitHub', desc: '代码托管与开源协作', url: 'https://github.com/', icon: 'Link', color: '#1f2937' },
      { name: 'Gitee', desc: '国内代码托管平台', url: 'https://gitee.com/', icon: 'Connection', color: '#ef4444' }
    ]
  }
]
</script>

<style scoped>
.dev-hub-dialog :deep(.el-dialog__body) {
  padding-top: 5px;
  padding-bottom: 20px;
}
.dev-hub-dialog :deep(.el-dialog__header) {
  margin-right: 0;
  border-bottom: 1px solid var(--neutral-gray-200);
  padding-bottom: 16px;
}

.dev-hub-dialog :deep(.el-dialog__title) {
  font-weight: 600;
  color: var(--neutral-gray-900);
}

.hub-container {
  padding: 10px 0;
}

.hub-description {
  margin-bottom: 20px;
  color: var(--neutral-gray-500);
  font-size: 14px;
}

.hub-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.hub-card {
  display: flex;
  align-items: center;
  padding: 16px;
  background-color: var(--neutral-gray-50);
  border: 1px solid var(--neutral-gray-200);
  border-radius: 12px;
  text-decoration: none;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;
}

.hub-card:hover {
  background-color: white;
  border-color: var(--brand-color, var(--orin-primary));
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  transform: translateY(-2px);
}

.card-icon {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  margin-right: 16px;
  flex-shrink: 0;
  transition: transform 0.3s;
}

.hub-card:hover .card-icon {
  transform: scale(1.1);
}

.card-content {
  flex-grow: 1;
  min-width: 0;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--neutral-gray-900);
  margin-bottom: 4px;
}

.card-desc {
  font-size: 13px;
  color: var(--neutral-gray-500);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.card-arrow {
  margin-left: 12px;
  opacity: 0;
  transform: translateX(-10px);
  transition: all 0.3s;
  color: var(--brand-color, var(--orin-primary));
  font-size: 16px;
}

.hub-card:hover .card-arrow {
  opacity: 1;
  transform: translateX(0);
}
</style>
