<template>
  <div class="kb-create-page">
    <!-- Header -->
    <div class="create-header">
      <div class="header-left" @click="$router.back()">
         <el-icon class="back-icon"><ArrowLeft /></el-icon>
         <div class="title-block">
            <span class="main-title">创建知识库</span>
            <span class="sub-title">Create Knowledge Base</span>
         </div>
      </div>
      <div class="header-right"></div>
    </div>

    <!-- Main Content -->
    <div class="create-container" :class="{ 'wide-container': currentStep === 2, 'finish-container': currentStep === 3 }">
       <!-- Steps (Hidden in Step 3) -->
       <div class="steps-wrapper" v-if="currentStep < 3">
          <div class="step-item" :class="{ active: currentStep === 1, done: currentStep > 1 }">
             <div class="step-num">1</div>
             <div class="step-title">选择数据源</div>
          </div>
          <div class="step-line"></div>
          <div class="step-item" :class="{ active: currentStep === 2, done: currentStep > 2 }">
             <div class="step-num">2</div>
             <div class="step-title">文本分段与清洗</div>
          </div>
          <div class="step-line"></div>
          <div class="step-item" :class="{ active: currentStep === 3 }">
             <div class="step-num">3</div>
             <div class="step-title">处理并完成</div>
          </div>
       </div>

       <!-- Step 3 Header only-->
       <div class="steps-wrapper simple" v-else>
           <div class="step-item done">1 选择数据源</div>
           <div class="sep">/</div>
           <div class="step-item done">2 文本分段与清洗</div>
           <div class="sep">/</div>
           <div class="step-item active">3 处理并完成</div>
       </div>

       <!-- Step 1: Data Source Selection -->
       <div v-if="currentStep === 1" class="step-content step-1">
          <h2 class="section-title">选择数据源</h2>
          <div class="source-cards">
             <div class="source-card selected">
                <el-icon class="source-icon blue"><Document /></el-icon>
                <div class="source-info">
                   <h3>导入已有文本</h3>
                   <p>从本地导入 PDF、TXT、DOCX 等文本文件</p>
                </div>
                <div class="check-mark"><el-icon><Check /></el-icon></div>
             </div>
             <!-- Other cards disabled -->
             <div class="source-card disabled">
                <el-icon class="source-icon green"><Link /></el-icon>
                <div class="source-info">
                   <h3>同步自 Web 站点</h3>
                   <p>抓取网页内容作为知识库源</p>
                </div>
             </div>
             <div class="source-card disabled">
                <el-icon class="source-icon purple"><Notebook /></el-icon>
                <div class="source-info">
                   <h3>同步自 Notion</h3>
                   <p>连接 Notion 数据源</p>
                </div>
             </div>
          </div>

          <!-- Upload Zone -->
          <div 
             class="upload-zone" 
             :class="{ dragging: isDragging }"
             @dragover.prevent="handleDragOver"
             @dragleave.prevent="handleDragLeave"
             @drop.prevent="handleDrop"
             @click="triggerUpload"
          >
             <input type="file" ref="fileInput" style="display: none" multiple accept=".pdf,.txt,.doc,.docx,.md" @change="handleFileChange" />
             <div class="upload-area">
                <el-icon class="upload-icon"><UploadFilled /></el-icon>
                <div class="upload-text">
                   <span class="link">点击上传</span> 或将文件拖拽至此
                </div>
                <div class="upload-hint">支持 PDF, TXT, DOC, DOCX, MD 等格式，每个文件不超过 15MB</div>
             </div>
          </div>

          <!-- File List Preview -->
          <TransitionGroup name="list" tag="div" v-if="fileList.length > 0" class="file-preview-list">
             <div v-for="(file, index) in fileList" :key="file.name + index" class="file-item">
                <div class="file-info">
                   <el-icon class="file-icon"><Document /></el-icon>
                   <span class="file-name text-ellipsis">{{ file.name }}</span>
                   <span class="file-size">{{ file.size }}</span>
                </div>
                <el-icon class="remove-btn" @click="removeFile(index)"><Close /></el-icon>
             </div>
          </TransitionGroup>
          
          <div class="empty-kb-link">
             <el-button link type="primary" @click="createEmpty"><el-icon><FolderAdd /></el-icon> 创建一个空知识库</el-button>
          </div>
       </div>

       <!-- Step 2: Cleaning & Segmentation (Dify Style) -->
       <div v-if="currentStep === 2" class="step-content step-2-layout">
          <div class="step-2-config">
             <!-- 1. Segmentation -->
             <div class="config-section">
                <div class="section-label">分段设置</div>
                <div class="segment-mode-cards">
                   <div class="mode-card" :class="{ active: form.segmentMode === 'general' }" @click="form.segmentMode = 'general'">
                      <div class="mode-icon"><el-icon><Setting /></el-icon></div>
                      <div class="mode-info">
                         <div class="mode-title">通用</div>
                         <div class="mode-desc">通用文本分块模式，检索和召回的块是相同的</div>
                      </div>
                      <div class="check-circle" v-if="form.segmentMode === 'general'"><el-icon><Check /></el-icon></div>
                   </div>
                   <div class="mode-card" :class="{ active: form.segmentMode === 'parent_child' }" @click="form.segmentMode = 'parent_child'">
                      <div class="mode-icon"><el-icon><CopyDocument /></el-icon></div>
                      <div class="mode-info">
                         <div class="mode-title">父子分段</div>
                         <div class="mode-desc">使用父子模式时，子块用于检索，父块用作上下文</div>
                      </div>
                       <div class="check-circle" v-if="form.segmentMode === 'parent_child'"><el-icon><Check /></el-icon></div>
                   </div>
                </div>
                <!-- General Params -->
                <div v-if="form.segmentMode === 'general'" class="general-params">
                   <div class="params-row">
                      <div class="param-item">
                         <label>分段标识符 <el-tooltip content="例如 \n\n"><el-icon class="info-icon"><QuestionFilled /></el-icon></el-tooltip></label>
                         <el-input v-model="form.separator" placeholder="\n\n" size="default" />
                      </div>
                       <div class="param-item">
                         <label>分段最大长度</label>
                         <el-input-number v-model="form.maxTokens" :min="100" :max="4000" size="default" controls-position="right" class="full-width-input" />
                      </div>
                       <div class="param-item">
                         <label>分段重叠长度</label>
                         <el-input-number v-model="form.overlap" :min="0" :max="500" size="default" controls-position="right" class="full-width-input" />
                      </div>
                   </div>
                   <div class="cleaning-rules">
                      <label>文本预处理规则</label>
                      <div class="rules-box">
                         <el-checkbox v-model="form.cleanSpaces">替换掉连续的空格、换行符和制表符</el-checkbox>
                         <el-checkbox v-model="form.cleanUrls">删除所有 URL 和电子邮件地址</el-checkbox>
                      </div>
                   </div>
                   <div class="preview-actions">
                      <el-button class="start-preview-btn" :loading="previewLoading" @click="handlePreview">
                         <el-icon style="margin-right: 4px;"><View /></el-icon> 预览块
                      </el-button>
                      <el-button link type="info" @click="handleResetParams">重置</el-button>
                   </div>
                </div>
             </div>
             
             <!-- 2. Index Method -->
             <div class="config-section">
                 <div class="section-label">索引方式</div>
                 <div class="index-cards">
                     <div class="index-card" :class="{ active: form.indexType === 'high_quality' }" @click="form.indexType = 'high_quality'">
                        <div class="card-head">
                           <h4>高质量</h4>
                           <el-tag size="small" type="primary" effect="light">推荐</el-tag>
                        </div>
                        <p>调用嵌入模型处理文档以实现更精确的检索。</p>
                        <div class="radio-circle">
                           <div class="inner" v-if="form.indexType === 'high_quality'"></div>
                        </div>
                     </div>
                     <div class="index-card" :class="{ active: form.indexType === 'economy' }" @click="form.indexType = 'economy'">
                        <div class="card-head">
                           <h4>经济</h4>
                        </div>
                        <p>使用关键词进行检索，降低精度以节省 Token。</p>
                        <div class="radio-circle">
                           <div class="inner" v-if="form.indexType === 'economy'"></div>
                        </div>
                     </div>
                 </div>
             </div>
             
             <!-- 3. Embedding Model -->
             <div v-if="form.indexType === 'high_quality'" class="config-section">
                 <div class="section-label">Embedding 模型</div>
                 <el-select v-model="form.embeddingModel" class="w-full" size="large" placeholder="请选择 Embedding 模型">
                    <el-option v-for="item in embeddingOptions" :key="item.value" :label="item.label" :value="item.value">
                       <span style="display: flex; align-items: center; gap: 8px;">
                          <el-icon class="icon-green"><Cpu /></el-icon> {{ item.label }}
                       </span>
                    </el-option>
                 </el-select>
             </div>
             
             <!-- 4. Retrieval Settings -->
             <div class="config-section">
                <div class="section-label">检索设置</div>
                <div class="retrieval-box">
                   <div class="retrieval-option active">
                      <div class="option-header">
                         <div class="icon-box"><el-icon><Operation /></el-icon></div>
                         <span>向量检索</span>
                      </div>
                      <div class="option-desc">通过生成查询嵌入并查询与其向量表示最相似的文本分段</div>
                      <div class="rerank-section">
                         <div class="rerank-toggle">
                            <el-switch v-model="form.enableRerank" /> <span>Rerank 模型</span>
                            <el-tooltip content="重排序模型"><el-icon class="info-icon"><QuestionFilled /></el-icon></el-tooltip>
                         </div>
                         <div class="rerank-select" v-if="form.enableRerank">
                            <el-select v-model="form.rerankModel" size="default" placeholder="Select Rerank Model">
                               <el-option v-for="item in rerankOptions" :key="item.value" :label="item.label" :value="item.value" />
                            </el-select>
                         </div>
                      </div>
                      <div class="sliders-row">
                         <div class="slider-item">
                            <div class="slider-label">Top K <span>{{ form.topK }}</span></div>
                            <el-slider v-model="form.topK" :max="10" :min="1" size="small" />
                         </div>
                         <div class="slider-item">
                            <div class="slider-label">Score 阈值 <span>{{ form.scoreThreshold }}</span></div>
                            <el-slider v-model="form.scoreThreshold" :max="1" :step="0.01" size="small" />
                         </div>
                      </div>
                   </div>
                </div>
             </div>
          </div>

          <!-- Right Side: Preview -->
          <div class="step-2-preview">
             <div class="preview-header">
                 <div class="header-content" v-if="fileList.length > 0">
                    <el-icon class="doc-icon"><Document /></el-icon>
                    <span class="doc-name text-ellipsis">{{ fileList[0].name }}</span>
                    <el-tag v-if="showPreview" size="small" type="info">{{ previewChunks.length }} 预估块</el-tag>
                    <el-tag v-else size="small" type="info">0 预估块</el-tag>
                 </div>
                 <div v-else>预览</div>
             </div>
             <div class="preview-body" v-loading="previewLoading">
                <div v-if="showPreview" class="chunk-list">
                    <div v-for="chunk in previewChunks" :key="chunk.id" class="chunk-item">
                       <div class="chunk-meta">#Chunk-{{chunk.id}} · {{ chunk.length }} characters</div>
                       <div class="chunk-content">{{ chunk.content }}</div>
                    </div>
                 </div>
                 <div v-else class="empty-preview">
                    <div class="empty-icon-wrapper"><el-icon><Search /></el-icon></div>
                    <p>点击左侧的“预览块”按钮来加载预览</p>
                 </div>
             </div>
          </div>
       </div>

       <!-- Step 3: Finish & Processing (Dify Style) -->
       <div v-if="currentStep === 3" class="step-content step-3-layout">
          <div class="step-3-main">
             <div class="success-header">
                <div class="emoji-icon">🎉</div>
                <div class="header-text">
                   <h3>知识库已创建</h3>
                   <p>我们自动为该知识库起了一个名称，您也可以随时修改</p>
                </div>
             </div>

             <!-- KB Name Card -->
             <div class="kb-name-card">
                 <div class="kb-icon">
                    <el-icon><Notebook /></el-icon>
                 </div>
                 <el-input v-model="kbName" class="name-input" placeholder="Knowledge Base Name" />
             </div>

             <!-- Embedding Status -->
             <div class="process-section">
                <div class="section-title">
                   <el-icon class="spin-icon" v-if="!isFinished"><Loading /></el-icon>
                   <el-icon class="success-icon" v-else><Check /></el-icon>
                   {{ isFinished ? '嵌入已完成' : '嵌入处理中...' }}
                </div>
                
                <div class="file-process-list">
                   <div v-for="file in creatingFiles" :key="file.name" class="process-item">
                      <div class="file-row">
                         <div class="file-name">
                            <el-icon><Document /></el-icon> {{ file.name }}
                         </div>
                         <div class="process-status">{{ file.progress }}%</div>
                      </div>
                      <el-progress :percentage="file.progress" :show-text="false" :stroke-width="6" :status="file.progress === 100 ? 'success' : ''"/>
                   </div>
                </div>
             </div>

             <!-- Config Summary -->
             <div class="summary-section">
                <div class="summary-row">
                   <span class="label">分段模式</span>
                   <span class="value">{{ form.segmentMode === 'general' ? '通用' : '父子分段' }}</span>
                </div>
                <div class="summary-row">
                   <span class="label">最大分段长度</span>
                   <span class="value">{{ form.maxTokens }}</span>
                </div>
                <div class="summary-row">
                   <span class="label">文本预处理规则</span>
                   <span class="value">
                      {{ form.cleanSpaces ? '替换掉连续的空格、换行符' : '' }}
                      {{ form.cleanSpaces && form.cleanUrls ? '、' : '' }}
                      {{ form.cleanUrls ? '删除所有 URL' : '' }}
                   </span>
                </div>
                <div class="summary-row">
                   <span class="label">索引方式</span>
                   <span class="value">
                      <el-icon style="vertical-align: middle; margin-right: 4px; color: #f59e0b;"><Aim /></el-icon>
                      {{ form.indexType === 'high_quality' ? '高质量' : '经济' }}
                   </span>
                </div>
                <div class="summary-row" v-if="form.embeddingModel">
                   <span class="label">Embedding 模型</span>
                   <span class="value">{{ embeddingOptions.find(o => o.value === form.embeddingModel)?.label || form.embeddingModel }}</span>
                </div>
             </div>

             <!-- Final Actions -->
             <div class="step-3-actions">
                <el-button class="api-btn" :icon="Connection">Access the API</el-button>
                <el-button type="primary" class="go-btn" @click="handleGoToDocument" :loading="!isFinished">
                   {{ isFinished ? '前往文档' : '处理中...' }}
                   <el-icon class="el-icon--right"><Right /></el-icon>
                </el-button>
             </div>
          </div>

          <!-- Right Sidebar: What's next -->
          <div class="step-3-sidebar">
             <div class="next-card">
                <div class="card-icon"><el-icon><Reading /></el-icon></div>
                <h4>接下来做什么</h4>
                <p>当文档完成索引后，您可以管理和编辑文档、运行检索测试以及修改知识库设置。知识库即可集成到应用程序内作为上下文使用。</p>
                <el-button link type="primary">了解更多</el-button>
             </div>
          </div>
       </div>

       <!-- Footer Actions (Hidden in Step 3) -->
       <div class="create-footer" v-if="currentStep < 3">
          <div class="footer-left"></div>
          <div class="footer-right">
             <el-button v-if="currentStep > 1" @click="currentStep--">上一步</el-button>
             <el-button v-if="currentStep < 3" type="primary" @click="handleNext" :disabled="currentStep === 1 && fileList.length === 0">下一步</el-button>
          </div>
       </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { ROUTES } from '@/router/routes';
import { 
  ArrowLeft, Document, Check, UploadFilled, FolderAdd, Link, Notebook,
  Close, Setting, CopyDocument, QuestionFilled, Cpu, Operation, View, Search,
  Loading, Connection, Right, Reading, Aim
} from '@element-plus/icons-vue';
import { ElMessage, ElNotification } from 'element-plus';
import request from '@/utils/request';
import { getModelList } from '@/api/model';

const router = useRouter();
const currentStep = ref(1);
const kbName = ref('');
const creating = ref(false);
const allModels = ref([]);

// Step 3 specific state
const creatingFiles = ref([]);
const isFinished = ref(false);

const showPreview = ref(false);
const previewLoading = ref(false);
const previewChunks = ref([]);

const form = reactive({
   segmentMode: 'general', 
   separator: '\\n\\n',
   maxTokens: 500,
   overlap: 50,
   cleanSpaces: true,
   cleanUrls: false,
   indexType: 'high_quality', 
   embeddingModel: '', 
   enableRerank: false,
   rerankModel: '',
   topK: 3,
   scoreThreshold: 0.5
});

const embeddingOptions = computed(() => {
   return allModels.value.filter(m => m.type === 'EMBEDDING').map(m => ({
      label: m.name,
      value: m.modelId
   }));
});

const rerankOptions = computed(() => {
   return allModels.value.filter(m => m.type === 'RERANKER').map(m => ({
      label: m.name,
      value: m.modelId
   }));
});

onMounted(async () => {
   try {
      const res = await getModelList();
      allModels.value = res || [];
      if (!form.embeddingModel && embeddingOptions.value.length > 0) {
         const preferred = embeddingOptions.value.find(o => o.value.includes('text-embedding-3-large'));
         form.embeddingModel = preferred ? preferred.value : embeddingOptions.value[0].value;
      }
      if (!form.rerankModel && rerankOptions.value.length > 0) {
          const preferred = rerankOptions.value.find(o => o.value.includes('rerank'));
          form.rerankModel = preferred ? preferred.value : rerankOptions.value[0].value;
      }
   } catch (e) { console.warn(e); }
});

const fileInput = ref(null);
const fileList = ref([]);
const isDragging = ref(false);

const triggerUpload = () => { fileInput.value.click(); };
const handleFileChange = (e) => { addFiles(Array.from(e.target.files)); e.target.value = ''; };
const handleDragOver = (e) => { e.preventDefault(); isDragging.value = true; };
const handleDragLeave = (e) => { e.preventDefault(); isDragging.value = false; };
const handleDrop = (e) => { e.preventDefault(); isDragging.value = false; addFiles(Array.from(e.dataTransfer.files)); };

const addFiles = (files) => {
   const validFiles = files.filter(f => f.size <= 15 * 1024 * 1024); 
   if(validFiles.length < files.length) ElMessage.warning('部分文件超过 15MB 限制');
   validFiles.forEach(f => {
      fileList.value.push({
         name: f.name,
         size: (f.size / 1024).toFixed(1) + ' KB',
         type: f.type,
         status: 'ready',
         rawFile: f
      });
   });
   if(fileList.value.length > 0 && !kbName.value) {
      const firstName = fileList.value[0].name;
      const dotIndex = firstName.lastIndexOf('.');
      kbName.value = dotIndex > 0 ? firstName.substring(0, dotIndex) : firstName;
   }
};
const removeFile = (i) => { fileList.value.splice(i, 1); };

const handleNext = () => {
   if(currentStep.value === 1 && fileList.value.length === 0) return ElMessage.warning('请先上传文件');
   if(currentStep.value === 2) {
      startProcessing();
   }
   currentStep.value++;
};

const createEmpty = () => { currentStep.value = 3; kbName.value = '未命名知识库'; startProcessing(); };

const readFileAsText = (file) => {
   return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = (e) => resolve(e.target.result);
      reader.onerror = (e) => reject(e);
      reader.readAsText(file);
   });
};

const recursiveSplit = (text, maxLength, overlap) => {
   if (text.length <= maxLength) return [text];
   const separators = ['\n', '.', '!', '?', '；', '。', ' ', ''];
   let chosenSep = '';
   let splitParts = [];
   for (let sep of separators) {
      if (text.includes(sep)) {
         chosenSep = sep;
         splitParts = text.split(sep);
         if (sep === ' ' || sep === '\n') splitParts = splitParts.filter(p => p.length > 0);
         if (splitParts.length > 1) break;
      }
   }
   let result = [];
   let current = '';
   for (let i = 0; i < splitParts.length; i++) {
      let part = splitParts[i];
      if (['.', '!', '?', '；', '。'].includes(chosenSep)) part += chosenSep;
      const potential = current ? (current + ([' ', '\n'].includes(chosenSep) ? chosenSep : '') + part) : part;
      if (potential.length > maxLength) {
         if (current) { result.push(current); current = part; } 
         else {
            for (let j = 0; j < part.length; j += maxLength - overlap) result.push(part.slice(j, j + maxLength));
            current = '';
         }
      } else { current = potential; }
   }
   if (current) result.push(current);
   return result;
};

const chunkText = (text, maxLength, overlap, separator) => {
   const normSep = separator.replace(/\\n/g, '\n');
   const primaryChunks = text.split(normSep);
   let finalChunks = [];
   for (let chunk of primaryChunks) {
      if (!chunk.trim()) continue;
      if (chunk.length <= maxLength) finalChunks.push(chunk.trim());
      else {
         const subChunks = recursiveSplit(chunk, maxLength, overlap);
         finalChunks.push(...subChunks.map(c => c.trim()));
      }
   }
   return finalChunks;
};

const getMockText = (i) => {
    const texts = [
      "提及起是本事，放得下是格局。年轻时别人总觉得两手空空是输了。你得先把手里的烂泥甩干净，才能腾出手去接那一盏热茶。",
      "昨天的雨湿不了今天的衣，明天的风吹不散昨天的云。你总在深夜里反复咀嚼那些过去的事，除了让自己更苦，没别的用。",
      "赢了名利若丢了清欢，也是惨胜；输了世界若赢了自在，也是大成。别被那些花里胡哨的标准给困住了。"
   ];
   return texts[i-1] || texts[0];
}

const handlePreview = async () => {
   if (fileList.value.length === 0) return;
   previewLoading.value = true;
   try {
      const targetFile = fileList.value[0];
      let text = '';
      
      // Try to parse file content from backend (for real data preview)
      const formData = new FormData();
      formData.append('file', targetFile.rawFile);
      
      try {
         const res = await request.post('/knowledge/documents/parse-text', formData, {
            headers: { 'Content-Type': 'multipart/form-data' }
         });
         text = res.text;
      } catch (err) {
         console.warn('Backend parsing failed, falling back to local reading', err);
      }

      if (!text) {
         if (targetFile.type.startsWith('text/') || targetFile.name.endsWith('.md') || targetFile.name.endsWith('.json')) {
            text = await readFileAsText(targetFile.rawFile);
         } else {
            text = "（注意：此处显示示例文本。）\n\n" + getMockText(1) + "\n\n" + getMockText(2) + "\n\n" + getMockText(3);
         }
      }

      if (form.cleanSpaces) text = text.replace(/[ \t]+/g, ' '); 
      if (form.cleanUrls) text = text.replace(/https?:\/\/[^\s]+/g, '');
      const chunks = chunkText(text, form.maxTokens, form.overlap, form.separator);
      previewChunks.value = chunks.map((c, i) => ({ id: i + 1, content: c, length: c.length }));
      showPreview.value = true;
   } catch (e) {
      console.error('Preview error:', e);
      ElMessage.error(e.message); 
   } finally {
      previewLoading.value = false;
   }
};

const handleResetParams = () => {
   form.separator = '\\n\\n';
   form.maxTokens = 500;
   form.overlap = 50;
   showPreview.value = false;
   previewChunks.value = [];
};

// --- Step 3 Simulation ---
const startProcessing = () => {
    isFinished.value = false;
    creatingFiles.value = fileList.value.map(f => ({ name: f.name, progress: 0 }));
    
    if(creatingFiles.value.length === 0) { isFinished.value = true; return; }

    creatingFiles.value.forEach((f, idx) => {
        const interval = setInterval(() => {
            if(f.progress < 99) {
                f.progress += Math.floor(Math.random() * 5) + 1;
                if(f.progress > 99) f.progress = 99;
            } else {
                clearInterval(interval);
                if(idx === creatingFiles.value.length - 1) {
                     setTimeout(() => {
                         creatingFiles.value.forEach(x => x.progress = 100);
                         isFinished.value = true;
                         saveKB(); 
                     }, 500);
                }
            }
        }, 100);
    });
};

const saveKB = async () => {
    try {
        // Prepare creating status for UI
        isFinished.value = false;
        
        const payload = {
            name: kbName.value || '未命名知识库',
            description: form.indexType === 'high_quality' ? 'High Quality Index' : 'Economy Index',
            type: 'UNSTRUCTURED',
            status: 'ENABLED'
        };

        // 1. Create Knowledge Base via API
        const kb = await request.post('/knowledge', payload);
        const newKbId = kb.id;

        // 2. Upload files if any
        if (fileList.value.length > 0) {
            for (let i = 0; i < fileList.value.length; i++) {
                const f = fileList.value[i];
                const formData = new FormData();
                formData.append('file', f.rawFile);
                
                try {
                    const doc = await request.post(`/knowledge/${newKbId}/documents/upload`, formData, {
                        headers: { 'Content-Type': 'multipart/form-data' }
                    });
                    
                    // Trigger vectorization right away
                    await request.post(`/knowledge/documents/${doc.id}/vectorize`);
                    
                    creatingFiles.value[i].progress = 100;
                } catch (uploadErr) {
                    console.error(`Failed to upload ${f.name}:`, uploadErr);
                    ElNotification({
                        title: '上传失败',
                        message: `文件 ${f.name} 上传失败，请稍后手动重试。`,
                        type: 'error'
                    });
                }
            }
        }

        isFinished.value = true;
        ElMessage.success('知识库创建成功');
    } catch (err) {
        console.error('Failed to create KB:', err);
        ElMessage.error('创建知识库失败: ' + (err.response?.data?.message || err.message));
        isFinished.value = true;
    }
};

const handleGoToDocument = () => {
    router.push(ROUTES.RESOURCES.KNOWLEDGE); 
};
</script>

<style scoped>
.kb-create-page { height: 100vh; display: flex; flex-direction: column; background: #fff; }
.create-header { height: 64px; border-bottom: 1px solid #E5E7EB; display: flex; align-items: center; padding: 0 24px; justify-content: space-between; }
.header-left { display: flex; align-items: center; gap: 16px; cursor: pointer; }
.back-icon { font-size: 20px; color: #6B7280; padding: 8px; border-radius: 8px; border: 1px solid #E5E7EB; }
.title-block { display: flex; align-items: baseline; gap: 12px; }
.main-title { font-size: 18px; font-weight: 600; color: #111827; }
.sub-title { font-size: 12px; color: #9CA3AF; font-weight: 500; }

.create-container { flex: 1; max-width: 1000px; width: 100%; margin: 0 auto; padding: 40px 24px; display: flex; flex-direction: column; transition: max-width 0.3s; }
.create-container.wide-container { max-width: 1400px; }
.create-container.finish-container { max-width: 1200px; }

/* Steps */
.steps-wrapper { display: flex; align-items: center; justify-content: center; margin-bottom: 48px; }
.steps-wrapper.simple { gap: 12px; font-size: 14px; color: #9CA3AF; margin-bottom: 32px; justify-content: flex-start; }
.steps-wrapper.simple .step-item.active { color: #111827; font-weight: 600; }
.steps-wrapper.simple .step-item.done { color: #6B7280; }

.step-item { display: flex; align-items: center; gap: 8px; opacity: 0.5; }
.step-item.active, .step-item.done { opacity: 1; }
.step-num { width: 24px; height: 24px; border-radius: 50%; background: #E5E7EB; color: #6B7280; display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 700; }
.step-item.active .step-num { background: #2563EB; color: #fff; }
.step-item.done .step-num { background: #10B981; color: #fff; }
.step-title { font-size: 14px; font-weight: 500; color: #374151; }
.step-line { width: 60px; height: 1px; background: #E5E7EB; margin: 0 16px; }
.step-content { flex: 1; }
.section-title { font-size: 24px; font-weight: 600; color: #111827; margin-bottom: 32px; text-align: center; }

/* Step 1 Styles */
.source-cards { display: flex; gap: 20px; margin-bottom: 32px; justify-content: center; }
.source-card { width: 240px; padding: 24px; border: 1px solid #E5E7EB; border-radius: 12px; cursor: pointer; position: relative; transition: all 0.2s; }
.source-card.selected { border-color: #2563EB; background: #EFF6FF; }
.source-card.disabled { opacity: 0.5; cursor: not-allowed; background: #F9FAFB; }
.source-icon { font-size: 32px; margin-bottom: 16px; }
.source-icon.blue { color: #2563EB; }
.source-icon.green { color: #10B981; }
.source-icon.purple { color: #9333EA; }
.source-info h3 { font-size: 16px; font-weight: 600; margin: 0 0 8px 0; }
.source-info p { font-size: 13px; color: #6B7280; margin: 0; line-height: 1.4; }
.check-mark { position: absolute; top: 12px; right: 12px; color: #2563EB; }
.upload-zone { border: 2px dashed #E5E7EB; border-radius: 12px; padding: 48px; text-align: center; transition: all 0.2s; margin-bottom: 24px; cursor: pointer; }
.upload-zone:hover { border-color: #2563EB; background: #F9FAFB; }
.upload-icon { font-size: 48px; color: #D1D5DB; margin-bottom: 16px; }
.upload-text .link { color: #2563EB; font-weight: 500; }
.file-preview-list { max-width: 600px; margin: 0 auto 32px auto; display: flex; flex-direction: column; gap: 12px; }
.file-item { display: flex; align-items: center; justify-content: space-between; padding: 12px 16px; border: 1px solid #E5E7EB; border-radius: 8px; background: #fff; }
.file-info { display: flex; align-items: center; gap: 10px; overflow: hidden; }
.file-icon { color: #9CA3AF; font-size: 18px; }
.remove-btn:hover { color: #DC2626; background: #FEE2E2; }
.empty-kb-link { text-align: center; }

/* Step 2 Styles */
.step-2-layout { display: flex; gap: 32px; height: calc(100vh - 250px); overflow: hidden; }
.step-2-config { flex: 1; overflow-y: auto; padding-right: 12px; }
.step-2-preview { width: 500px; background: #F9FAFB; border: 1px solid #E5E7EB; border-radius: 12px; display: flex; flex-direction: column; overflow: hidden; }
.config-section { margin-bottom: 32px; }
.section-label { font-size: 14px; font-weight: 600; color: #374151; margin-bottom: 12px; }
.segment-mode-cards { display: flex; gap: 16px; margin-bottom: 20px; }
.mode-card { flex: 1; border: 1px solid #E5E7EB; border-radius: 8px; padding: 16px; display: flex; gap: 12px; cursor: pointer; position: relative; }
.mode-card.active { border-color: #2563EB; background: #EFF6FF; }
.mode-icon { width: 32px; height: 32px; background: #fff; border-radius: 6px; display: flex; align-items: center; justify-content: center; color: #2563EB; font-size: 16px; border: 1px solid #E5E7EB; }
.mode-info .mode-title { font-weight: 600; font-size: 14px; }
.mode-info .mode-desc { font-size: 12px; color: #6B7280; line-height: 1.4; }
.check-circle { position: absolute; top: 10px; right: 10px; color: #2563EB; }
.general-params { background: #F9FAFB; border: 1px solid #E5E7EB; border-radius: 8px; padding: 16px; }
.params-row { display: flex; gap: 16px; margin-bottom: 16px; }
.param-item { flex: 1; display: flex; flex-direction: column; gap: 8px; }
.param-item label { font-size: 12px; font-weight: 500; color: #374151; display: flex; align-items: center; gap: 4px; }
.full-width-input { width: 100%; }
.preview-actions { margin-top: 16px; display: flex; align-items: center; gap: 12px; }
.start-preview-btn { color: #2563EB; border-color: #2563EB; background: white; font-weight: 500; }
.index-cards { display: flex; gap: 16px; }
.index-card { flex: 1; padding: 16px; border: 1px solid #E5E7EB; border-radius: 8px; cursor: pointer; position: relative; }
.index-card.active { border-color: #2563EB; background: #EFF6FF; }
.card-head { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.index-card h4 { margin: 0; font-size: 14px; font-weight: 600; }
.index-card p { margin: 0; font-size: 12px; color: #6B7280; }
.radio-circle { position: absolute; top: 16px; right: 16px; width: 16px; height: 16px; border-radius: 50%; border: 1px solid #D1D5DB; display: flex; align-items: center; justify-content: center; }
.index-card.active .radio-circle { border-color: #2563EB; }
.radio-circle .inner { width: 8px; height: 8px; background: #2563EB; border-radius: 50%; }
.retrieval-box { border: 1px solid #E5E7EB; border-radius: 8px; overflow: hidden; }
.retrieval-option { padding: 16px; background: #fff; }
.option-header { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
.icon-box { width: 28px; height: 28px; background: #F3E8FF; color: #9333EA; border-radius: 6px; display: flex; align-items: center; justify-content: center; }
.rerank-section { background: #fff; border: 1px solid #E5E7EB; border-radius: 8px; padding: 12px; margin-bottom: 16px; }
.sliders-row { display: flex; gap: 24px; }
.slider-item { flex: 1; }
.preview-header { padding: 16px 20px; font-weight: 600; color: #1F2937; border-bottom: 1px solid #E5E7EB; background: #fff; min-height: 57px; display: flex; align-items: center; }
.header-content { display: flex; align-items: center; gap: 8px; width: 100%; }
.doc-name { flex: 1; font-size: 13px; font-weight: 500; }
.preview-body { flex: 1; overflow-y: auto; padding: 20px; position: relative; }
.chunk-list { display: flex; flex-direction: column; gap: 16px; }
.chunk-item { background: #fff; border: 1px solid #E5E7EB; border-radius: 8px; padding: 16px; font-size: 13px; }
.chunk-meta { color: #9CA3AF; font-size: 12px; margin-bottom: 8px; display: flex; align-items: center; gap: 8px; }
.chunk-content { color: #374151; line-height: 1.6; }
.empty-preview { height: 100%; display: flex; flex-direction: column; align-items: center; justify-content: center; color: #9CA3AF; gap: 16px; }
.create-footer { margin-top: auto; padding-top: 24px; border-top: 1px solid #E5E7EB; display: flex; justify-content: space-between; }

/* Step 3 Styles */
.step-3-layout { display: flex; gap: 40px; }
.step-3-main { flex: 1; }
.step-3-sidebar { width: 300px; }
.success-header { display: flex; gap: 16px; margin-bottom: 32px; }
.emoji-icon { font-size: 48px; }
.header-text h3 { margin: 0 0 8px 0; font-size: 24px; font-weight: 600; color: #111827; }
.header-text p { margin: 0; color: #6B7280; font-size: 14px; }
.kb-name-card { background: #F9FAFB; padding: 16px; border-radius: 12px; display: flex; align-items: center; gap: 16px; margin-bottom: 32px; border: 1px solid #E5E7EB; }
.kb-icon { width: 40px; height: 40px; background: #FCD34D; border-radius: 8px; display: flex; align-items: center; justify-content: center; font-size: 20px; color: #92400E; }
.name-input { font-size: 16px; font-weight: 500; }
.name-input :deep(.el-input__wrapper) { box-shadow: none; background: transparent; padding: 0; }
.name-input :deep(.el-input__inner) { font-size: 16px; color: #111827; }
.process-section { margin-bottom: 32px; }
.process-section .section-title { font-weight: 600; margin-bottom: 16px; display: flex; align-items: center; gap: 8px; }
.spin-icon { animation: spin 1s linear infinite; color: #F59E0B; }
.success-icon { color: #10B981; font-size: 20px; }
@keyframes spin { 100% { transform: rotate(360deg); } }
.file-process-list { border: 1px solid #E5E7EB; border-radius: 8px; overflow: hidden; }
.process-item { padding: 16px; background: #fff; border-bottom: 1px solid #E5E7EB; }
.process-item:last-child { border-bottom: none; }
.file-row { display: flex; justify-content: space-between; margin-bottom: 8px; font-size: 14px; font-weight: 500; }
.file-name { display: flex; align-items: center; gap: 8px; color: #374151; }
.process-status { color: #6B7280; }
.summary-section { margin-bottom: 32px; }
.summary-row { display: flex; justify-content: space-between; padding: 12px 0; border-bottom: 1px solid #E5E7EB; font-size: 14px; }
.summary-row .label { color: #6B7280; }
.summary-row .value { color: #111827; font-weight: 500; }
.step-3-actions { display: flex; gap: 16px; }
.step-3-actions .api-btn { flex: 1; }
.step-3-actions .go-btn { flex: 2; }
.next-card { background: #EFF6FF; border-radius: 12px; padding: 24px; text-align: center; }
.next-card .card-icon { width: 48px; height: 48px; background: #fff; border-radius: 50%; color: #2563EB; display: flex; align-items: center; justify-content: center; font-size: 24px; margin: 0 auto 16px auto; }
.next-card h4 { font-size: 16px; font-weight: 600; margin-bottom: 12px; }
.next-card p { font-size: 13px; color: #4B5563; line-height: 1.5; margin-bottom: 16px; text-align: left; }
</style>
