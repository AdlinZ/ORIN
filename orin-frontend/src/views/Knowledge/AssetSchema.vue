<template>
  <div class="asset-schema-container">
    <PageHeader 
      title="知识资产架构 Asset Schema" 
      description="定义非结构化数据的 ETL 流水线。配置解析规则、分段策略与向量化模型，并实时预览解析效果。"
      icon="Grid"
    />

    <div class="schema-layout">
        <!-- Pipeline Config Area -->
        <div class="pipeline-area">
            <div class="pipeline-header">
                <h3>处理流水线 (ETL Pipeline)</h3>
                <el-button type="primary" size="small" @click="savePipeline">保存配置</el-button>
            </div>
            
            <div class="visual-pipeline">
                <!-- Node 1: Source -->
                <div class="pipe-node source" :class="{ active: selectedNode === 'source' }" @click="selectNode('source')">
                    <div class="node-icon"><el-icon><Document /></el-icon></div>
                    <div class="node-label">数据源</div>
                    <div class="node-sub">支持 PDF, DOCX, IMG</div>
                </div>

                <div class="pipe-arrow"><el-icon><Right /></el-icon></div>

                <!-- Node 2: Parsing -->
                <div class="pipe-node parsing" :class="{ active: selectedNode === 'parsing' }" @click="selectNode('parsing')">
                    <div class="node-icon"><el-icon><View /></el-icon></div>
                    <div class="node-label">解析 (Parsing)</div>
                    <div class="node-sub">{{ config.parsingMode }}</div>
                </div>

                <div class="pipe-arrow"><el-icon><Right /></el-icon></div>

                <!-- Node 3: Chunking -->
                <div class="pipe-node chunking" :class="{ active: selectedNode === 'chunking' }" @click="selectNode('chunking')">
                    <div class="node-icon"><el-icon><Scissor /></el-icon></div>
                    <div class="node-label">分段 (Chunking)</div>
                    <div class="node-sub">{{ config.chunkSize }} tokens</div>
                </div>

                <div class="pipe-arrow"><el-icon><Right /></el-icon></div>

                <!-- Node 4: Embedding -->
                <div class="pipe-node embedding" :class="{ active: selectedNode === 'embedding' }" @click="selectNode('embedding')">
                    <div class="node-icon"><el-icon><Coin /></el-icon></div>
                    <div class="node-label">向量化 (Embedding)</div>
                    <div class="node-sub">{{ config.embeddingModel }}</div>
                </div>
            </div>

            <!-- Configuration Panel for Selected Node -->
            <transition name="fade">
                <div class="node-config-panel" v-if="selectedNode">
                    <h4>Step Config: {{ getNodeTitle(selectedNode) }}</h4>
                    
                    <el-form label-position="left" label-width="120px" v-if="selectedNode === 'source'">
                        <el-form-item label="允许格式">
                             <el-checkbox-group v-model="config.allowedFormats">
                                <el-checkbox label="PDF" />
                                <el-checkbox label="DOCX" />
                                <el-checkbox label="TXT" />
                                <el-checkbox label="Images (OCR)" />
                             </el-checkbox-group>
                        </el-form-item>
                    </el-form>

                    <el-form label-position="left" label-width="120px" v-if="selectedNode === 'parsing'">
                         <el-form-item label="解析模式">
                             <el-select v-model="config.parsingMode">
                                 <el-option label="General (通用)" value="GENERAL" />
                                 <el-option label="Layout Analysis (版面分析)" value="LAYOUT" />
                                 <el-option label="OCR Only" value="OCR" />
                             </el-select>
                         </el-form-item>
                         <el-form-item label="VLM 加强">
                             <el-switch v-model="config.vlmEnhanced" active-text="启用多模态图片描述" />
                         </el-form-item>
                    </el-form>

                    <el-form label-position="left" label-width="120px" v-if="selectedNode === 'chunking'">
                         <el-form-item label="分段方法">
                             <el-select v-model="config.chunkMethod">
                                 <el-option label="Fixed Size (固定大小)" value="FIXED" />
                                 <el-option label="Recursive (递归字符)" value="RECURSIVE" />
                                 <el-option label="Semantic (语义分割)" value="SEMANTIC" />
                             </el-select>
                         </el-form-item>
                         <el-form-item label="块大小 (Tokens)">
                             <el-input-number v-model="config.chunkSize" :min="100" :max="2000" :step="100" />
                         </el-form-item>
                         <el-form-item label="重叠 (Overlap)">
                             <el-input-number v-model="config.chunkOverlap" :min="0" :max="500" :step="50" />
                         </el-form-item>
                    </el-form>

                    <el-form label-position="left" label-width="120px" v-if="selectedNode === 'embedding'">
                         <el-form-item label="模型提供商">
                             <el-select v-model="config.embeddingProvider">
                                 <el-option label="SiliconFlow" value="siliconflow" />
                                 <el-option label="OpenAI" value="openai" />
                                 <el-option label="Local (Ollama)" value="local" />
                             </el-select>
                         </el-form-item>
                         <el-form-item label="模型名称">
                             <el-input v-model="config.embeddingModel" placeholder="e.g. text-embedding-3-small" />
                         </el-form-item>
                    </el-form>

                </div>
            </transition>
        </div>

        <!-- Preview Area -->
        <div class="preview-area">
            <div class="area-header">实时解析预览 (Real-time Preview)</div>
            
            <div class="upload-zone" v-show="!previewFile">
                <el-upload
                    drag
                    action="#"
                    :auto-upload="false"
                    :on-change="handlePreviewFile"
                    :show-file-list="false"
                >
                    <el-icon class="el-icon--upload"><upload-filled /></el-icon>
                    <div class="el-upload__text">拖拽样本文件至此测试解析效果</div>
                </el-upload>
            </div>

            <div class="preview-content" v-if="previewFile">
                <div class="file-status-bar">
                    <span><el-icon><Document /></el-icon> {{ previewFile.name }}</span>
                    <el-button link type="danger" @click="clearPreview">清除</el-button>
                </div>

                <div class="chunks-visualizer" v-loading="previewLoading">
                    <div v-if="previewChunks.length > 0">
                        <div class="chunk-stats">
                            生成 {{ previewChunks.length }} 个切片 | 耗时 {{ previewTime }}ms
                        </div>
                        <div class="chunk-list">
                            <div v-for="(chunk, idx) in previewChunks" :key="idx" class="chunk-item">
                                <div class="chunk-idx">#{{ idx + 1 }} [{{ chunk.length }} chars]</div>
                                <div class="chunk-text">{{ chunk }}</div>
                            </div>
                        </div>
                    </div>
                    <el-empty v-else description="点击上方节点调整参数以查看预期效果" />
                </div>
            </div>
        </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, watch } from 'vue';
import PageHeader from '@/components/PageHeader.vue';
import { Document, Right, View, Scissor, Coin, UploadFilled } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';

// State
const selectedNode = ref('chunking'); // Default select
const config = reactive({
    allowedFormats: ['PDF', 'DOCX'],
    parsingMode: 'LAYOUT',
    vlmEnhanced: true,
    chunkMethod: 'RECURSIVE',
    chunkSize: 500,
    chunkOverlap: 50,
    embeddingProvider: 'siliconflow',
    embeddingModel: 'BAAI/bge-m3'
});

// Preview Logic
const previewFile = ref(null);
const previewLoading = ref(false);
const previewChunks = ref([]);
const previewTime = ref(0);

const getNodeTitle = (node) => {
    const map = {
        'source': '数据源配置',
        'parsing': '文档解析策略',
        'chunking': '分段(Chunking) 规则',
        'embedding': 'Embeddings 模型'
    };
    return map[node] || 'Configuration';
};

const selectNode = (node) => {
    selectedNode.value = node;
};

const savePipeline = () => {
    ElMessage.success('流水线配置已保存并应用');
};

const handlePreviewFile = (file) => {
    previewFile.value = file.raw;
    runPreview();
};

const clearPreview = () => {
    previewFile.value = null;
    previewChunks.value = [];
};

const runPreview = () => {
    if(!previewFile.value) return;
    
    previewLoading.value = true;
    
    // Mock processing delay
    setTimeout(() => {
        // Generate pseudo chunks based on config
        const chunks = [];
        const count = Math.floor(Math.random() * 5) + 3; // 3-8 chunks
        
        for(let i=0; i<count; i++) {
            chunks.push(`[Chunk ${i+1}] (由 ${config.parsingMode} 模式解析) This is simulated content reflecting the current configuration of chunk size ${config.chunkSize}. In a real environment, this would be actual text from the uploaded file.`);
        }
        
        previewChunks.value = chunks;
        previewTime.value = Math.floor(Math.random() * 500) + 100;
        previewLoading.value = false;
    }, 1000);
};

// Auto update preview when config changes
watch(config, () => {
    if(previewFile.value) {
        runPreview();
    }
}, { deep: true });

</script>

<style scoped>
.asset-schema-container {
    height: 100vh;
    display: flex;
    flex-direction: column;
}

.schema-layout {
    flex: 1;
    display: flex;
    flex-direction: column;
    padding: 20px;
    background: var(--app-bg);
    gap: 20px;
    overflow-y: auto;
}

/* Pipeline Visual */
.pipeline-area {
    background: var(--glass-bg);
    padding: 24px;
    border-radius: 12px;
    box-shadow: 0 2px 12px rgba(0,0,0,0.03);
}

.pipeline-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 30px;
}

.pipeline-header h3 {
    color: var(--text-primary);
    margin: 0;
}

.visual-pipeline {
    display: flex;
    justify-content: center;
    align-items: center;
    margin-bottom: 40px;
}

.pipe-node {
    width: 140px;
    height: 100px;
    border: 1px solid var(--border-subtle);
    border-radius: 12px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    transition: all 0.3s;
    background: var(--glass-bg);
}

.pipe-node:hover {
    border-color: var(--orin-primary-glow);
    transform: translateY(-2px);
}

.pipe-node.active {
    border-color: var(--orin-primary);
    background: var(--orin-primary-soft);
    box-shadow: 0 0 0 4px var(--orin-primary-glow);
}

.node-icon { font-size: 24px; margin-bottom: 8px; color: var(--text-secondary); }
.pipe-node.active .node-icon { color: var(--orin-primary); }

.node-label { font-size: 14px; font-weight: 600; color: var(--text-primary); }
.node-sub { font-size: 11px; color: var(--text-secondary); margin-top: 4px; }

.pipe-arrow {
    margin: 0 20px;
    color: var(--text-secondary);
    font-size: 20px;
}

.node-config-panel {
    background: var(--glass-bg);
    border: 1px dashed var(--border-subtle);
    padding: 20px;
    border-radius: 8px;
    max-width: 800px;
    margin: 0 auto;
}

.node-config-panel h4 {
    margin-top: 0;
    margin-bottom: 20px;
    padding-bottom: 10px;
    border-bottom: 1px solid var(--border-subtle);
    color: var(--text-primary);
}

/* Preview Area */
.preview-area {
    flex: 1;
    background: var(--glass-bg);
    padding: 24px;
    border-radius: 12px;
    box-shadow: 0 2px 12px rgba(0,0,0,0.03);
    min-height: 400px;
    display: flex;
    flex-direction: column;
}

.area-header {
    font-weight: 600;
    margin-bottom: 20px;
    font-size: 16px;
    color: var(--text-primary);
}

.upload-zone {
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: center;
    border: 2px dashed var(--border-subtle);
    border-radius: 8px;
    background: var(--orin-primary-soft);
}

.upload-zone :deep(.el-upload-dragger) {
    border: none;
    background: transparent;
}

.preview-content {
    flex: 1;
    display: flex;
    flex-direction: column;
}

.file-status-bar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    background: var(--orin-primary-soft);
    padding: 10px 15px;
    border-radius: 8px 8px 0 0;
    font-size: 13px;
    color: var(--text-primary);
}

.chunks-visualizer {
    flex: 1;
    border: 1px solid var(--border-subtle);
    border-top: none;
    border-radius: 0 0 8px 8px;
    padding: 20px;
    background: var(--app-bg);
    overflow-y: auto;
    max-height: 500px;
}

.chunk-stats {
    font-size: 12px;
    color: var(--text-secondary);
    margin-bottom: 15px;
    text-align: right;
}

.chunk-list {
    display: flex;
    flex-direction: column;
    gap: 12px;
}

.chunk-item {
    background: var(--glass-bg);
    padding: 15px;
    border: 1px solid var(--border-subtle);
    border-radius: 6px;
    font-size: 13px;
    line-height: 1.6;
    color: var(--text-primary);
}

.chunk-idx {
    font-size: 11px;
    color: var(--orin-primary);
    font-weight: 600;
    margin-bottom: 6px;
    display: block;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
