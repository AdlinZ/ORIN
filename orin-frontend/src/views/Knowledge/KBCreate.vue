<template>
  <div class="kb-create-page">
      <PageHeader
         title="创建知识库"
         description="按步骤完成数据源接入、解析与分段配置"
         icon="Reading"
      />
    <!-- Header with Steps -->
    <div class="create-header">
      <div class="header-left" @click="$router.back()">
         <el-icon class="back-icon"><ArrowLeft /></el-icon>
      </div>
      <div class="header-steps">
         <div class="step-item" :class="{ active: currentStep === 1, done: currentStep > 1 }">
            <div class="step-num">1</div>
            <span class="step-title">选择数据源</span>
         </div>
         <div class="step-line"></div>
         <div class="step-item" :class="{ active: currentStep === 2, done: currentStep > 2 }">
            <div class="step-num">2</div>
            <span class="step-title">内容解析</span>
         </div>
         <div class="step-line"></div>
         <div class="step-item" :class="{ active: currentStep === 3, done: currentStep > 3 }">
            <div class="step-num">3</div>
            <span class="step-title">文本分段</span>
         </div>
         <div class="step-line"></div>
         <div class="step-item" :class="{ active: currentStep === 4 }">
            <div class="step-num">4</div>
            <span class="step-title">完成</span>
         </div>
      </div>
    </div>

    <!-- Main Content -->
    <div class="create-container" :class="{ 'wide-container': currentStep === 2 || currentStep === 3, 'finish-container': currentStep === 4 }">

       <!-- Step 1: Data Source Selection -->
       <div v-if="currentStep === 1" class="step-content step-1">
          <h2 class="section-title">选择数据源</h2>
          <div class="source-cards">
             <div class="source-card" :class="{ selected: selectedSource === 'file' }" @click="selectedSource = 'file'">
                <el-icon class="source-icon blue"><Document /></el-icon>
                <div class="source-info">
                   <h3>导入已有文本</h3>
                   <p>从本地导入 PDF、TXT、DOCX 等文本文件</p>
                </div>
                <div class="check-mark" v-if="selectedSource === 'file'"><el-icon><Check /></el-icon></div>
             </div>
             <div class="source-card" :class="{ selected: selectedSource === 'web' }" @click="selectedSource = 'web'">
                <el-icon class="source-icon green"><Link /></el-icon>
                <div class="source-info">
                   <h3>同步自 Web 站点</h3>
                   <p>抓取网页内容作为知识库源</p>
                </div>
                <div class="check-mark" v-if="selectedSource === 'web'"><el-icon><Check /></el-icon></div>
             </div>
             <div class="source-card" :class="{ selected: selectedSource === 'notion' }" @click="selectedSource = 'notion'">
                <el-icon class="source-icon purple"><Notebook /></el-icon>
                <div class="source-info">
                   <h3>同步自 Notion</h3>
                   <p>连接 Notion 数据源</p>
                </div>
                <div class="check-mark" v-if="selectedSource === 'notion'"><el-icon><Check /></el-icon></div>
             </div>
             <div class="source-card" :class="{ selected: selectedSource === 'database' }" @click="selectedSource = 'database'">
                <el-icon class="source-icon orange"><Connection /></el-icon>
                <div class="source-info">
                   <h3>连接数据库</h3>
                   <p>连接 MySQL、PostgreSQL 等数据库</p>
                </div>
                <div class="check-mark" v-if="selectedSource === 'database'"><el-icon><Check /></el-icon></div>
             </div>
          </div>

          <!-- Upload Zone (only for file source) -->
          <div v-if="selectedSource === 'file'"
             class="upload-zone"
             :class="{ dragging: isDragging }"
             @dragover.prevent="handleDragOver"
             @dragleave.prevent="handleDragLeave"
             @drop.prevent="handleDrop"
             @click="triggerUpload"
          >
             <input type="file" ref="fileInput" style="display: none" multiple accept=".pdf,.txt,.doc,.docx,.md,.jpg,.jpeg,.png,.gif,.bmp,.webp,.mp3,.wav,.m4a,.ogg,.mp4,.avi,.mov,.mkv" @change="handleFileChange" />
             <div class="upload-area">
                <el-icon class="upload-icon"><UploadFilled /></el-icon>
                <div class="upload-text">
                   <span class="link">点击上传</span> 或将文件拖拽至此
                </div>
                <div class="upload-hint">支持 PDF、TXT、Word、图片、音频、视频等格式，每个文件不超过 15MB</div>
             </div>
          </div>

          <!-- Web Source Config -->
          <div v-if="selectedSource === 'web'" class="source-config">
             <h3 class="config-title">Web 站点配置</h3>
             <div class="config-form">
                <div class="form-item">
                   <label>URL 列表 (每行一个)</label>
                   <el-input
                      v-model="webConfig.urlsText"
                      type="textarea"
                      :rows="5"
                      placeholder="https://example.com/page1&#10;https://example.com/page2"
                   />
                </div>
                <div class="form-item">
                   <label>爬取深度</label>
                   <el-input-number v-model="webConfig.maxDepth" :min="1" :max="5" />
                </div>
                <div class="form-actions">
                   <el-button type="primary" @click="testWebUrl" :loading="testingWeb">测试连接</el-button>
                </div>
             </div>
          </div>

          <!-- Notion Source Config -->
          <div v-if="selectedSource === 'notion'" class="source-config">
             <h3 class="config-title">Notion 配置</h3>
             <div class="config-form">
                <div class="form-item">
                   <label>Integration Token</label>
                   <el-input v-model="notionConfig.integrationToken" placeholder="secret_xxx" show-password />
                   <div class="form-tip">在 Notion 中创建 Integration 后获取</div>
                </div>
                <div class="form-item">
                   <label>数据库 ID</label>
                   <el-input v-model="notionConfig.databaseId" placeholder="32位字符的数据库ID" />
                </div>
                <div class="form-actions">
                   <el-button @click="testNotionConnection" :loading="testingNotion">测试连接</el-button>
                   <el-button type="primary" @click="loadNotionDatabases" :loading="loadingNotionDatabases" :disabled="!notionConfig.integrationToken">
                      获取数据库列表
                   </el-button>
                </div>
                <div v-if="notionDatabases.length > 0" class="form-item">
                   <label>选择数据库</label>
                   <el-select v-model="notionConfig.databaseId" placeholder="选择 Notion 数据库">
                      <el-option v-for="db in notionDatabases" :key="db.id" :label="db.title" :value="db.id" />
                   </el-select>
                </div>
             </div>
          </div>

          <!-- Database Source Config -->
          <div v-if="selectedSource === 'database'" class="source-config">
             <h3 class="config-title">数据库连接</h3>
             <div class="config-form">
                <div class="form-item">
                   <label>JDBC 连接 URL</label>
                   <el-input v-model="dbConfig.connectionUrl" placeholder="jdbc:mysql://localhost:3306/mydb" />
                   <div class="form-tip">例如: jdbc:mysql://localhost:3306/mydb 或 jdbc:postgresql://localhost:5432/mydb</div>
                </div>
                <div class="form-row">
                   <div class="form-item">
                      <label>用户名</label>
                      <el-input v-model="dbConfig.username" placeholder="root" />
                   </div>
                   <div class="form-item">
                      <label>密码</label>
                      <el-input v-model="dbConfig.password" type="password" placeholder="密码" show-password />
                   </div>
                </div>
                <div class="form-actions">
                   <el-button @click="testDatabaseConnection" :loading="testingDb">测试连接</el-button>
                </div>
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

       <!-- Step 2: Content Parsing (Multimodal) -->
       <div v-if="currentStep === 2" class="step-content step-2-layout">
          <div class="step-2-config">
             <!-- 1. Parsing Settings -->
             <div class="config-section">
                <div class="section-label">解析设置</div>
                <el-alert title="多模态内容解析用于从图片、音频、视频中提取文本内容" type="info" :closable="false" style="margin-bottom: 16px" />
                <div class="parsing-options">
                   <div class="parsing-card" :class="{ active: form.parsingEnabled }" @click="form.parsingEnabled = true">
                      <div class="parsing-header">
                         <div class="parsing-icon"><el-icon><PictureFilled /></el-icon></div>
                         <div class="parsing-info">
                            <div class="parsing-title">启用多模态解析</div>
                            <div class="parsing-desc">对图片、音频、视频进行 OCR 识别或语音转文字</div>
                         </div>
                         <div class="check-circle" v-if="form.parsingEnabled"><el-icon><Check /></el-icon></div>
                      </div>
                   </div>
                   <div class="parsing-card" :class="{ active: !form.parsingEnabled }" @click="form.parsingEnabled = false">
                      <div class="parsing-header">
                         <div class="parsing-icon"><el-icon><Document /></el-icon></div>
                         <div class="parsing-info">
                            <div class="parsing-title">跳过解析</div>
                            <div class="parsing-desc">直接对文本内容进行分段和向量化</div>
                         </div>
                         <div class="check-circle" v-if="!form.parsingEnabled"><el-icon><Check /></el-icon></div>
                      </div>
                   </div>
                </div>
             </div>

             <!-- Parsing Configuration -->
             <div v-if="form.parsingEnabled" class="config-section">
                <div class="section-label">OCR 图片文字识别</div>
                <div class="provider-cards">
                   <div
                      class="provider-card"
                      :class="{ active: form.ocrProvider === 'local' }"
                      @click="form.ocrProvider = 'local'; form.ocrModel = ''"
                   >
                      <div class="provider-icon"><el-icon><Monitor /></el-icon></div>
                      <div class="provider-info">
                         <div class="provider-name">本地 (Tesseract)</div>
                         <div class="provider-desc">开源 OCR 引擎，无需联网</div>
                      </div>
                      <div class="check-circle" v-if="form.ocrProvider === 'local'"><el-icon><Check /></el-icon></div>
                   </div>
                   <div
                      class="provider-card"
                      :class="{ active: form.ocrProvider !== 'local' }"
                      @click="form.ocrProvider = 'azure'"
                   >
                      <div class="provider-icon"><el-icon><Cloudy /></el-icon></div>
                      <div class="provider-info">
                         <div class="provider-name">云服务 API</div>
                         <div class="provider-desc">Azure/Google/阿里云/腾讯云</div>
                      </div>
                      <div class="check-circle" v-if="form.ocrProvider !== 'local'"><el-icon><Check /></el-icon></div>
                   </div>
                </div>

                <!-- OCR Model Selection (when cloud selected) -->
                <div v-if="form.ocrProvider !== 'local'" class="cloud-config">
                   <el-select v-model="form.ocrModel" placeholder="选择 OCR 模型" size="large" style="width: 100%">
                      <el-option v-for="item in ocrModelOptions" :key="item.value" :label="item.label" :value="item.value">
                         <span style="display: flex; align-items: center; gap: 8px;">
                            <el-icon class="icon-blue"><Cpu /></el-icon> {{ item.label }}
                         </span>
                      </el-option>
                   </el-select>
                </div>

                <div class="section-label" style="margin-top: 24px;">ASR 语音识别</div>
                <div class="provider-cards">
                   <div
                      class="provider-card"
                      :class="{ active: form.asrProvider === 'local' }"
                      @click="form.asrProvider = 'local'; form.asrModel = ''"
                   >
                      <div class="provider-icon"><el-icon><Microphone /></el-icon></div>
                      <div class="provider-info">
                         <div class="provider-name">本地 (Whisper)</div>
                         <div class="provider-desc">开源语音识别模型</div>
                      </div>
                      <div class="check-circle" v-if="form.asrProvider === 'local'"><el-icon><Check /></el-icon></div>
                   </div>
                   <div
                      class="provider-card"
                      :class="{ active: form.asrProvider !== 'local' }"
                      @click="form.asrProvider = 'azure'"
                   >
                      <div class="provider-icon"><el-icon><Cloudy /></el-icon></div>
                      <div class="provider-info">
                         <div class="provider-name">云服务 API</div>
                         <div class="provider-desc">Azure/Google/阿里云/腾讯云</div>
                      </div>
                      <div class="check-circle" v-if="form.asrProvider !== 'local'"><el-icon><Check /></el-icon></div>
                   </div>
                </div>

                <!-- ASR Model Selection (when cloud selected) -->
                <div v-if="form.asrProvider !== 'local'" class="cloud-config">
                   <el-select v-model="form.asrModel" placeholder="选择 ASR 模型" size="large" style="width: 100%">
                      <el-option v-for="item in asrModelOptions" :key="item.value" :label="item.label" :value="item.value">
                         <span style="display: flex; align-items: center; gap: 8px;">
                            <el-icon class="icon-blue"><Cpu /></el-icon> {{ item.label }}
                         </span>
                      </el-option>
                   </el-select>
                </div>

                <!-- Rich Text Parsing Options -->
                <div class="section-label" style="margin-top: 24px;">文档解析</div>
                <el-alert title="解析文档时保留格式信息（标题层级、段落结构等），便于后续知识检索" type="info" :closable="false" style="margin-bottom: 16px" />
                <div class="parsing-options">
                   <div class="parsing-card" :class="{ active: form.richTextEnabled }" @click="form.richTextEnabled = true">
                      <div class="parsing-header">
                         <div class="parsing-icon"><el-icon><Document /></el-icon></div>
                         <div class="parsing-info">
                            <div class="parsing-title">富文本解析</div>
                            <div class="parsing-desc">保留文档格式信息（标题、段落、列表等）</div>
                         </div>
                         <div class="check-circle" v-if="form.richTextEnabled"><el-icon><Check /></el-icon></div>
                      </div>
                   </div>
                   <div class="parsing-card" :class="{ active: !form.richTextEnabled }" @click="form.richTextEnabled = false">
                      <div class="parsing-header">
                         <div class="parsing-icon"><el-icon><Document /></el-icon></div>
                         <div class="parsing-info">
                            <div class="parsing-title">纯文本解析</div>
                            <div class="parsing-desc">仅提取文本内容，不保留格式</div>
                         </div>
                         <div class="check-circle" v-if="!form.richTextEnabled"><el-icon><Check /></el-icon></div>
                      </div>
                   </div>
                </div>
             </div>

             <!-- File Type Preview -->
             <div class="config-section">
                <div class="section-label">待处理文件</div>
                <div class="file-type-list" v-if="fileList.length > 0">
                   <div class="file-type-item" v-for="(file, index) in fileList.slice(0, 5)" :key="index">
                      <el-icon><Document /></el-icon>
                      <span class="file-name">{{ file.name }}</span>
                      <el-tag size="small" :type="getFileTypeTag(file.name)">{{ getFileType(file.name) }}</el-tag>
                   </div>
                   <div v-if="fileList.length > 5" class="more-files">
                      还有 {{ fileList.length - 5 }} 个文件
                   </div>
                </div>
                <div v-else class="no-files-tip">
                   <el-icon><InfoFilled /></el-icon>
                   <span>请在第一步选择数据源并上传文件</span>
                </div>
             </div>
          </div>

          <!-- Right Side: Parsing Result / Status -->
          <div class="step-2-preview">
             <div class="preview-header">
                <el-icon class="doc-icon"><InfoFilled /></el-icon>
                <span>{{ parsingComplete ? '解析结果' : '解析状态' }}</span>
                <el-button v-if="parsingComplete && !parsingLoading" type="primary" link size="small" @click="reparseFiles" :disabled="parsingLoading">
                  <el-icon><Refresh /></el-icon> 重新解析
                </el-button>
             </div>
             <div class="preview-body">
                <!-- Loading State -->
                <div v-if="parsingLoading" class="parsing-loading">
                   <el-icon class="loading-icon"><Loading /></el-icon>
                   <div class="loading-text">正在解析文件...</div>
                   <div class="loading-progress">
                      <div v-for="(file, idx) in creatingFiles" :key="idx" class="file-progress-item">
                         <span class="file-name">{{ file.name }}</span>
                         <el-progress :percentage="file.progress" :status="file.status === 'error' ? 'exception' : undefined" :show-text="false" />
                      </div>
                   </div>
                </div>

                <!-- Parsed Content Display (after parsing complete) -->
                <div v-else-if="parsingComplete && parsedContent.length > 0" class="parsed-content-list">
                   <div v-for="(item, idx) in parsedContent" :key="idx" class="parsed-item">
                      <div class="parsed-item-header">
                         <el-icon><Document /></el-icon>
                         <span class="parsed-filename">{{ item.fileName }}</span>
                         <el-tag size="small" type="success">{{ item.text.length }} 字符</el-tag>
                      </div>
                      <div class="parsed-item-content">{{ item.text.substring(0, 500) }}{{ item.text.length > 500 ? '...' : '' }}</div>
                   </div>
                </div>

                <!-- No Content or Failed -->
                <div v-else-if="parsingComplete && parsedContent.length === 0" class="parsing-failed">
                   <el-icon class="failed-icon"><WarningFilled /></el-icon>
                   <div class="failed-text">解析失败或未能获取内容</div>
                   <div class="failed-hint">请检查文件格式或配置后重试</div>
                </div>

                <!-- Initial State: Show parsing info -->
                <div v-else class="parsing-info-list">
                   <div class="info-item">
                      <div class="info-icon"><el-icon><Picture /></el-icon></div>
                      <div class="info-content">
                         <div class="info-title">图片 (PNG, JPG, JPEG, BMP)</div>
                         <div class="info-desc">使用 OCR 识别图片中的文字内容</div>
                      </div>
                   </div>
                   <div class="info-item">
                      <div class="info-icon"><el-icon><Microphone /></el-icon></div>
                      <div class="info-content">
                         <div class="info-title">音频 (MP3, WAV, M4A)</div>
                         <div class="info-desc">使用 ASR 将语音转写为文字</div>
                      </div>
                   </div>
                   <div class="info-item">
                      <div class="info-icon"><el-icon><VideoCamera /></el-icon></div>
                      <div class="info-content">
                         <div class="info-title">视频 (MP4, AVI, MOV)</div>
                         <div class="info-desc">提取音频并使用 ASR 转写</div>
                      </div>
                   </div>
                   <div class="info-item">
                      <div class="info-icon"><el-icon><Document /></el-icon></div>
                      <div class="info-content">
                         <div class="info-title">文档 (PDF, DOCX, TXT)</div>
                         <div class="info-desc">直接提取文本，跳过解析步骤</div>
                      </div>
                   </div>
                </div>
             </div>
          </div>
       </div>

       <!-- Step 3: Cleaning & Segmentation (Dify Style) -->
       <div v-if="currentStep === 3" class="step-content step-2-layout">
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
                <!-- Parent-Child Params -->
                <div v-if="form.segmentMode === 'parent_child'" class="parent-child-params">
                   <div class="params-info">
                      <el-alert title="使用 Parent-Child 分块策略" type="info" :closable="false">
                        <template #default>
                          <div>• 父分块 (Parent): ~1000 字符，表示完整语义段落</div>
                          <div>• 子分块 (Child): ~200 字符，用于精确向量检索</div>
                          <div>• 检索时返回父分块内容作为 LLM 上下文</div>
                        </template>
                      </el-alert>
                   </div>
                   <div class="preview-actions">
                      <el-button class="start-preview-btn" :loading="previewLoading" @click="handlePreview">
                         <el-icon style="margin-right: 4px;"><View /></el-icon> 预览分块
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
                      <!-- 知识库级别检索配置覆盖 -->
                      <div class="retrieval-config-section">
                         <div class="section-header">
                            <el-checkbox v-model="useCustomRetrievalConfig">覆盖默认检索配置</el-checkbox>
                            <span class="section-tip">启用后该知识库将使用以下配置，而非系统默认配置</span>
                         </div>
                         <div v-if="useCustomRetrievalConfig" class="config-fields">
                            <div class="config-row">
                               <div class="config-item">
                                  <label>Chunk 最大字符数</label>
                                  <el-input-number v-model="form.chunkSize" :min="100" :max="2000" :step="100" size="small" />
                               </div>
                               <div class="config-item">
                                  <label>Chunk 重叠字符数</label>
                                  <el-input-number v-model="form.chunkOverlap" :min="0" :max="500" :step="50" size="small" />
                               </div>
                            </div>
                            <div class="config-row">
                               <div class="config-item">
                                  <label>检索返回结果数 (Top K)</label>
                                  <el-input-number v-model="form.topK" :min="1" :max="20" :step="1" size="small" />
                               </div>
                               <div class="config-item">
                                  <label>相似度阈值</label>
                                  <el-input-number v-model="form.similarityThreshold" :min="0" :max="1" :step="0.05" :precision="2" size="small" />
                               </div>
                               <div class="config-item">
                                  <label>向量检索权重 (alpha)</label>
                                  <el-input-number v-model="form.alpha" :min="0" :max="1" :step="0.1" :precision="2" size="small" />
                               </div>
                            </div>
                            <!-- Rerank 配置 -->
                            <div class="config-row">
                               <div class="config-item" style="flex: 1;">
                                  <label>启用 Rerank</label>
                                  <el-switch v-model="form.enableRerank" />
                               </div>
                               <div class="config-item" style="flex: 2;" v-if="form.enableRerank">
                                  <label>Rerank 模型</label>
                                  <el-select v-model="form.rerankModel" placeholder="选择 Rerank 模型" size="small" style="width: 100%" filterable clearable>
                                     <el-option
                                        v-for="model in rerankModels"
                                        :key="model.id"
                                        :label="model.name"
                                        :value="model.id"
                                     />
                                  </el-select>
                               </div>
                            </div>
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
                    <el-tag v-if="showPreview" size="small" type="info">
                    {{ previewChunks.length > maxPreviewChunks ? `${maxPreviewChunks}/${previewChunks.length}` : previewChunks.length }} 预估块
                 </el-tag>
                    <el-tag v-else size="small" type="info">0 预估块</el-tag>
                 </div>
                 <div v-else>预览</div>
             </div>
             <div class="preview-body" v-loading="previewLoading">
                <div v-if="showPreview" class="chunk-list">
                    <div v-for="chunk in displayChunks" :key="chunk.id" class="chunk-item">
                       <div class="chunk-meta">
                         <el-tag v-if="chunk.chunkType === 'child'" size="small" type="success">Child</el-tag>
                         <el-tag v-else size="small">Chunk</el-tag>
                         #{{chunk.id}} · {{ chunk.length }} characters
                         <span v-if="chunk.parentIndex">(Parent {{ chunk.parentIndex }})</span>
                         <el-button
                           v-if="chunk.length > 150"
                           link
                           size="small"
                           style="margin-left: auto; color: #2563EB;"
                           @click="toggleChunk(chunk.id)"
                         >
                           {{ collapsedChunks.has(chunk.id) ? '展开' : '收起' }}
                         </el-button>
                       </div>
                       <div class="chunk-content" :class="{ collapsed: collapsedChunks.has(chunk.id) }">
                         {{ collapsedChunks.has(chunk.id) && chunk.length > 150 ? chunk.content.slice(0, 150) + '...' : chunk.content }}
                       </div>
                    </div>
                 </div>
                 <div v-else class="empty-preview">
                    <div class="empty-icon-wrapper"><el-icon><Search /></el-icon></div>
                    <p>点击左侧的“预览块”按钮来加载预览</p>
                 </div>
             </div>
          </div>
       </div>

       <!-- Step 3: Finish & Processing -->
       <div v-if="currentStep === 4" class="step-content step-3-layout">
          <div class="step-3-main">
             <!-- Success Header -->
             <div class="success-header">
                <div class="success-icon-wrap">
                   <el-icon class="success-icon"><Check /></el-icon>
                </div>
                <div class="header-text">
                   <h3>知识库已创建</h3>
                   <p>我们自动为该知识库起了一个名称，您也可以随时修改</p>
                </div>
             </div>

             <!-- Main Cards Grid -->
             <div class="cards-grid">
                <!-- KB Name & Description -->
                <div class="info-card kb-info-card">
                   <div class="card-header">
                      <el-icon><Notebook /></el-icon>
                      <span>基本信息</span>
                   </div>
                   <div class="card-body">
                      <div class="form-item">
                         <div class="label-row">
                            <label>知识库名称</label>
                            <el-button
                              type="primary"
                              size="small"
                              link
                              :loading="generatingName"
                              @click="generateName"
                              :disabled="fileList.length === 0 && selectedSource !== 'file'"
                            >
                               <el-icon><MagicStick /></el-icon>
                               AI生成
                            </el-button>
                         </div>
                         <el-input v-model="kbName" placeholder="输入知识库名称" />
                      </div>
                      <div class="form-item">
                         <div class="label-row">
                            <label>知识库描述</label>
                            <el-button
                              type="primary"
                              size="small"
                              link
                              :loading="generatingDesc"
                              @click="generateDescription"
                              :disabled="fileList.length === 0 && selectedSource !== 'file'"
                            >
                               <el-icon><MagicStick /></el-icon>
                               AI生成
                            </el-button>
                         </div>
                         <el-input
                           v-model="kbDescription"
                           type="textarea"
                           :rows="2"
                           placeholder="输入知识库描述（可选）"
                         />
                      </div>
                   </div>
                </div>

                <!-- Processing Status -->
                <div class="info-card process-card">
                   <div class="card-header">
                      <el-icon><Loading v-if="!isFinished" /><Check v-else /></el-icon>
                      <span>{{ isFinished ? '嵌入已完成' : '嵌入处理中...' }}</span>
                      <el-tag :type="isFinished ? 'success' : 'warning'" size="small">
                         {{ isFinished ? '完成' : '处理中' }}
                      </el-tag>
                   </div>
                   <div class="card-body">
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
                         <div v-if="creatingFiles.length === 0" class="empty-process">
                            <el-icon><Check /></el-icon>
                            <span>暂无处理中的文件</span>
                         </div>
                      </div>
                   </div>
                </div>

                <!-- Config Summary -->
                <div class="info-card config-card">
                   <div class="card-header">
                      <el-icon><Setting /></el-icon>
                      <span>配置信息</span>
                   </div>
                   <div class="card-body">
                      <div class="config-grid">
                         <div class="config-item">
                            <span class="config-label">多模态解析</span>
                            <span class="config-value">{{ form.parsingEnabled ? '已启用' : '已禁用' }}</span>
                         </div>
                         <div class="config-item" v-if="form.parsingEnabled">
                            <span class="config-label">OCR</span>
                            <span class="config-value">{{ form.ocrProvider === 'local' ? '本地 (Tesseract)' : (form.ocrModel || '云服务') }}</span>
                         </div>
                         <div class="config-item" v-if="form.parsingEnabled">
                            <span class="config-label">ASR</span>
                            <span class="config-value">{{ form.asrProvider === 'local' ? '本地 (Whisper)' : (form.asrModel || '云服务') }}</span>
                         </div>
                         <div class="config-item">
                            <span class="config-label">分段模式</span>
                            <span class="config-value">{{ form.segmentMode === 'general' ? '通用' : '父子分段' }}</span>
                         </div>
                         <div class="config-item">
                            <span class="config-label">最大分段长度</span>
                            <span class="config-value">{{ form.maxTokens }}</span>
                         </div>
                         <div class="config-item">
                            <span class="config-label">索引方式</span>
                            <span class="config-value">
                               <el-icon style="color: #f59e0b;"><Aim /></el-icon>
                               {{ form.indexType === 'high_quality' ? '高质量' : '经济' }}
                            </span>
                         </div>
                         <div class="config-item" v-if="form.embeddingModel">
                            <span class="config-label">Embedding 模型</span>
                            <span class="config-value">{{ embeddingOptions.find(o => o.value === form.embeddingModel)?.label || form.embeddingModel }}</span>
                         </div>
                         <div class="config-item full-width">
                            <span class="config-label">文本预处理</span>
                            <span class="config-value preprocess-tags">
                               <el-tag v-if="form.cleanSpaces" size="small" type="info">去空格</el-tag>
                               <el-tag v-if="form.cleanUrls" size="small" type="info">删URL</el-tag>
                               <span v-if="!form.cleanSpaces && !form.cleanUrls" class="no-preprocess">无</span>
                            </span>
                         </div>
                      </div>
                   </div>
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
       </div>
    </div>

    <!-- Footer with Navigation Buttons -->
    <div class="create-footer">
      <el-button v-if="currentStep > 1 && currentStep < 4" @click="currentStep--">上一步</el-button>
      <el-button v-if="currentStep < 4" type="primary" @click="handleNext" :disabled="(currentStep === 1 && !canProceed) || (currentStep === 2 && (parsingLoading || (parsingComplete && parsingFailed)))" :loading="currentStep === 2 && parsingLoading">
        {{ currentStep === 3 ? '完成创建' : '下一步' }}
      </el-button>
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
  Loading, Connection, Right, Aim, MagicStick,
  PictureFilled, Picture, VideoCamera, Microphone, InfoFilled, WarningFilled,
  Monitor, Cloudy, Refresh
} from '@element-plus/icons-vue';
import { ElMessage, ElNotification } from 'element-plus';
import request from '@/utils/request';
import { getModelList } from '@/api/model';
import PageHeader from '@/components/PageHeader.vue';

const router = useRouter();
const currentStep = ref(1);
const kbName = ref('');
const kbDescription = ref('');
const descModel = ref('');
const generatingDesc = ref(false);
const generatingName = ref(false);
const creating = ref(false);
const allModels = ref([]);
// Rerank 模型列表 (从 allModels 筛选)
const rerankModels = computed(() => {
   return allModels.value
      .filter(m => {
         const type = m.type?.toUpperCase();
         return type === 'RERANK' || type === 'RERANKER';
      })
      .map(m => ({
         id: m.modelId || m.name || m.modelName,
         name: m.name || m.modelName || m.modelId
      }));
});
const createdKbId = ref(null); // Track created KB to avoid duplicate creation
const uploadedFiles = ref(new Set()); // Track uploaded files to avoid duplicate upload

// Chat models for description generation
const chatModels = computed(() => {
   return allModels.value.filter(m => m.type === 'CHAT' || m.type === 'LLM' || m.type === undefined).map(m => ({
      name: m.name,
      modelId: m.modelId || m.name
   }));
});

// Step 3 specific state
const creatingFiles = ref([]);
const isFinished = ref(false);

const showPreview = ref(false);
const previewLoading = ref(false);
const previewChunks = ref([]);
const collapsedChunks = ref(new Set()); // Track collapsed chunk IDs
const maxPreviewChunks = 10; // Limit preview to first 10 chunks

// Computed property to limit displayed chunks
const displayChunks = computed(() => {
  return previewChunks.value.slice(0, maxPreviewChunks);
});

// Source selection
const selectedSource = ref('file');

// Web source config
const webConfig = reactive({
   urlsText: '',
   maxDepth: 2
});
const testingWeb = ref(false);

// Notion source config
const notionConfig = reactive({
   integrationToken: '',
   databaseId: ''
});
const testingNotion = ref(false);
const loadingNotionDatabases = ref(false);
const notionDatabases = ref([]);

// Database source config
const dbConfig = reactive({
   connectionUrl: '',
   username: '',
   password: ''
});
const testingDb = ref(false);
const useCustomRetrievalConfig = ref(false); // 是否使用自定义检索配置覆盖系统默认

const form = reactive({
   // Parsing settings (Step 2)
   parsingEnabled: true,
   ocrProvider: 'local',
   asrProvider: 'local',
   ocrModel: '',
   asrModel: 'base',
   richTextEnabled: true,
   // Segmentation settings (Step 3)
   segmentMode: 'parent_child',
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
   scoreThreshold: 0.5,
   // Retrieval config (知识库级别覆盖默认配置)
   chunkSize: null,       // null 表示使用系统默认
   chunkOverlap: null,     // null 表示使用系统默认
   similarityThreshold: null, // null 表示使用系统默认
   alpha: null,           // null 表示使用系统默认
   enableRerank: null,    // null 表示使用系统默认
   rerankModel: null      // null 表示使用系统默认
});

const embeddingOptions = computed(() => {
   const options = allModels.value.filter(m => m.type === 'EMBEDDING').map(m => ({
      label: m.name,
      value: m.modelId
   }));
   // Fallback to default if no models loaded
   if (options.length === 0) {
      return [
         { label: 'BGE-M3 (Default)', value: 'BAAI/bge-m3' },
         { label: 'Text-Embedding-3-Small', value: 'text-embedding-3-small' }
      ];
   }
   return options;
});

const rerankOptions = computed(() => {
   const options = allModels.value.filter(m => m.type === 'RERANKER').map(m => ({
      label: m.name,
      value: m.modelId
   }));
   // Fallback to default if no models loaded
   if (options.length === 0) {
      return [
         { label: 'BGE-Reranker-v2-Mini', value: 'BAAI/bge-reranker-v2-mini' }
      ];
   }
   return options;
});

// OCR Provider Options (service-based, not model-based)
const ocrProviderOptions = [
   { label: '本地 (Tesseract)', value: 'local', icon: 'Monitor' },
   { label: 'Azure Vision', value: 'azure', icon: 'Cloud' },
   { label: 'Google Cloud Vision', value: 'google', icon: 'Cloudy' },
   { label: '阿里云 OCR', value: 'aliyun', icon: 'Cloud' },
   { label: '腾讯云 OCR', value: 'tencent', icon: 'Cloudy' }
];

// ASR Provider Options (service-based)
const asrProviderOptions = [
   { label: '本地 (Whisper)', value: 'local', icon: 'Monitor' },
   { label: 'Azure Speech', value: 'azure', icon: 'Cloud' },
   { label: 'Google Speech', value: 'google', icon: 'Cloudy' },
   { label: '阿里云 ASR', value: 'aliyun', icon: 'Cloud' },
   { label: '腾讯云 ASR', value: 'tencent', icon: 'Cloudy' },
   { label: 'OpenAI Whisper API', value: 'openai', icon: 'MagicStick' }
];

// OCR Model Options - dynamically from models (OCR/VISION type)
const ocrModelOptions = computed(() => {
   const options = allModels.value
      .filter(m => {
         const type = m.type?.toUpperCase();
         const isOCR = type === 'OCR';
         const isVision = type === 'VISION' || type === 'VLM' || type === 'MULTIMODAL' || type === 'VISUAL';
         const hasVisionKeywords = (m.modelId + m.name + (m.description || '')).toLowerCase().match(/ocr|vision|vl|multimodal|图片|文字|识别/);
         return (isOCR || isVision || hasVisionKeywords) && m.status === 'ENABLED';
      })
      .map(m => ({
         label: m.name,
         value: m.modelId
      }));
   return options;
});

// ASR Model Options - dynamically from models (SPEECH_TO_TEXT type)
const asrModelOptions = computed(() => {
   // Filter models with SPEECH_TO_TEXT or ASR type
   const options = allModels.value
      .filter(m => m.type === 'SPEECH_TO_TEXT' || m.type === 'ASR' || m.type === 'STT')
      .map(m => ({
         label: m.name,
         value: m.modelId
      }));

   // If no models found, add cloud options as fallback
   if (options.length === 0) {
      return [
         { label: 'Azure Speech (default)', value: 'azure-speech' },
         { label: 'Google Speech-to-Text', value: 'google-speech' },
         { label: '阿里云 ASR', value: 'aliyun-asr' },
         { label: '腾讯云 ASR', value: 'tencent-asr' },
         { label: 'OpenAI Whisper-1', value: 'whisper-1' }
      ];
   }
   return options;
});

const canProceed = computed(() => {
   if (selectedSource.value === 'file') {
      return fileList.value.length > 0;
   } else if (selectedSource.value === 'web') {
      return !!webConfig.urlsText && webConfig.urlsText.trim().length > 0;
   } else if (selectedSource.value === 'notion') {
      return !!notionConfig.integrationToken && !!notionConfig.databaseId;
   } else if (selectedSource.value === 'database') {
      return !!dbConfig.connectionUrl && !!dbConfig.username;
   }
   return false;
});

onMounted(async () => {
   try {
      console.log('Loading models...');
      const res = await getModelList();
      console.log('Models response:', res);
      allModels.value = res || [];
      console.log('All models:', allModels.value);
      console.log('Embedding options:', embeddingOptions.value);

      if (!form.embeddingModel && embeddingOptions.value.length > 0) {
         const preferred = embeddingOptions.value.find(o => o.value.includes('text-embedding-3-large'));
         form.embeddingModel = preferred ? preferred.value : embeddingOptions.value[0].value;
      }
      if (!form.rerankModel && rerankOptions.value.length > 0) {
          const preferred = rerankOptions.value.find(o => o.value.includes('rerank'));
          form.rerankModel = preferred ? preferred.value : rerankOptions.value[0].value;
      }
   } catch (e) { console.error('Failed to load models:', e); }
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

// File type helpers
const getFileType = (filename) => {
   const ext = filename.split('.').pop().toLowerCase();
   const imageExts = ['png', 'jpg', 'jpeg', 'bmp', 'gif', 'webp'];
   const audioExts = ['mp3', 'wav', 'm4a', 'aac', 'flac', 'ogg'];
   const videoExts = ['mp4', 'avi', 'mov', 'mkv', 'flv'];
   const docExts = ['pdf', 'doc', 'docx', 'txt', 'md', 'ppt', 'pptx'];

   if (imageExts.includes(ext)) return '图片';
   if (audioExts.includes(ext)) return '音频';
   if (videoExts.includes(ext)) return '视频';
   if (docExts.includes(ext)) return '文档';
   return '其他';
};

const getFileTypeTag = (filename) => {
   const ext = filename.split('.').pop().toLowerCase();
   const imageExts = ['png', 'jpg', 'jpeg', 'bmp', 'gif', 'webp'];
   const audioExts = ['mp3', 'wav', 'm4a', 'aac', 'flac', 'ogg'];
   const videoExts = ['mp4', 'avi', 'mov', 'mkv', 'flv'];

   if (imageExts.includes(ext)) return 'success';
   if (audioExts.includes(ext)) return 'warning';
   if (videoExts.includes(ext)) return 'danger';
   return 'info';
};

// AI Generate Name
const generateName = async () => {
   if (fileList.value.length === 0 && selectedSource.value !== 'file') {
      ElMessage.warning('请先上传文档后再生成名称');
      return;
   }
   generatingName.value = true;
   try {
      let newKbId = createdKbId.value;

      // 如果还没有创建知识库，则先创建
      if (!newKbId) {
         const payload = {
            name: kbName.value || '未命名知识库',
            description: kbDescription.value || '临时描述',
            type: 'UNSTRUCTURED',
            status: 'ENABLED',
            parsingEnabled: form.parsingEnabled,
            ocrProvider: form.ocrProvider,
            asrProvider: form.asrProvider,
            ocrModel: form.ocrModel,
            asrModel: form.asrModel,
            richTextEnabled: form.richTextEnabled,
            // 检索配置（知识库级别覆盖默认配置）
            chunkSize: form.chunkSize || null,
            chunkOverlap: form.chunkOverlap || null,
            topK: form.topK || null,
            similarityThreshold: form.similarityThreshold || null,
            alpha: form.alpha || null,
            enableRerank: form.enableRerank || null,
            rerankModel: form.rerankModel || null
         };
         const kb = await request.post('/knowledge', payload);
         newKbId = kb.id;
         createdKbId.value = newKbId;

         // 上传文档
         if (fileList.value.length > 0 && selectedSource.value === 'file') {
            for (const f of fileList.value) {
               const formData = new FormData();
               formData.append('file', f.rawFile || f.raw);
               await request.post(`/knowledge/${newKbId}/documents/upload`, formData);
               uploadedFiles.value.add(f.name);
            }
         }
      }

      // 调用AI生成描述（会同时返回 title 和 description）
      const res = await request.post(`/knowledge/${newKbId}/generate-description`, {
         model: descModel.value
      });

      console.log('Generate name response:', res);

      if (res.title) {
         kbName.value = res.title;
         // 更新知识库名称
         await request.put(`/knowledge/${newKbId}`, {
            name: res.title,
            description: kbDescription.value || ''
         });
         ElMessage.success('名称生成成功');
      } else {
         ElMessage.warning('AI未返回名称');
      }
   } catch (e) {
      console.error('生成名称失败:', e);
      ElMessage.error('生成名称失败: ' + (e.message || '未知错误'));
   } finally {
      generatingName.value = false;
   }
};

// AI Generate Description
const generateDescription = async () => {
   if (fileList.value.length === 0 && selectedSource.value !== 'file') {
      ElMessage.warning('请先上传文档后再生成描述');
      return;
   }
   generatingDesc.value = true;
   try {
      let newKbId = createdKbId.value;

      // 如果还没有创建知识库，则先创建
      if (!newKbId) {
         const payload = {
            name: kbName.value || '未命名知识库',
            description: '临时描述',
            type: 'UNSTRUCTURED',
            status: 'ENABLED',
            parsingEnabled: form.parsingEnabled,
            ocrProvider: form.ocrProvider,
            asrProvider: form.asrProvider,
            ocrModel: form.ocrModel,
            asrModel: form.asrModel,
            richTextEnabled: form.richTextEnabled,
            chunkSize: form.chunkSize || null,
            chunkOverlap: form.chunkOverlap || null,
            topK: form.topK || null,
            similarityThreshold: form.similarityThreshold || null,
            alpha: form.alpha || null,
            enableRerank: form.enableRerank || null,
            rerankModel: form.rerankModel || null
         };
         const kb = await request.post('/knowledge', payload);
         newKbId = kb.id;
         createdKbId.value = newKbId;

         // 上传文档
         if (fileList.value.length > 0 && selectedSource.value === 'file') {
            for (const f of fileList.value) {
               const formData = new FormData();
               formData.append('file', f.rawFile || f.raw);
               await request.post(`/knowledge/${newKbId}/documents/upload`, formData);
               // 标记已上传
               uploadedFiles.value.add(f.name);
            }
         }
      }

      // 调用AI生成描述
      const res = await request.post(`/knowledge/${newKbId}/generate-description`, {
         model: descModel.value
      });

      console.log('Generate description response:', res);

      if (res.description || res.title) {
         // 同时更新名称和描述
         if (res.title) {
            kbName.value = res.title;
         }
         if (res.description) {
            kbDescription.value = res.description;
         }
         // 更新知识库名称和描述
         const updateRes = await request.put(`/knowledge/${newKbId}`, {
            name: kbName.value || '未命名知识库',
            description: res.description || ''
         });
         console.log('Update KB response:', updateRes);
         ElMessage.success(res.title ? '名称和描述生成成功' : '描述生成成功');
      } else {
         ElMessage.warning('AI未返回内容');
      }
   } catch (e) {
      console.error('生成描述失败:', e);
      ElMessage.error('生成描述失败: ' + (e.message || '未知错误'));
   } finally {
      generatingDesc.value = false;
   }
};

// Web source methods
const testWebUrl = async () => {
   if (!webConfig.urlsText) return ElMessage.warning('请输入 URL');
   testingWeb.value = true;
   try {
      const urls = webConfig.urlsText.split('\n').filter(u => u.trim());
      const firstUrl = urls[0];
      const res = await request.get('/knowledge/sync/web/test', { params: { url: firstUrl } });
      if (res.success) {
         ElMessage.success('连接成功: ' + res.title);
      } else {
         ElMessage.error(res.message || '连接失败');
      }
   } catch (e) {
      ElMessage.error('测试失败: ' + e.message);
   } finally {
      testingWeb.value = false;
   }
};

// Notion source methods
const testNotionConnection = async () => {
   if (!notionConfig.integrationToken) return ElMessage.warning('请输入 Integration Token');
   testingNotion.value = true;
   try {
      const res = await request.post('/knowledge/sync/notion/test', {
         integrationToken: notionConfig.integrationToken
      });
      if (res.success) {
         ElMessage.success('连接成功');
      } else {
         ElMessage.error(res.message || '连接失败');
      }
   } catch (e) {
      ElMessage.error('测试失败: ' + e.message);
   } finally {
      testingNotion.value = false;
   }
};

const loadNotionDatabases = async () => {
   if (!notionConfig.integrationToken) return ElMessage.warning('请输入 Integration Token');
   loadingNotionDatabases.value = true;
   try {
      const res = await request.post('/knowledge/sync/notion/databases', {
         integrationToken: notionConfig.integrationToken
      });
      notionDatabases.value = res || [];
      ElMessage.success('获取到 ' + res.length + ' 个数据库');
   } catch (e) {
      ElMessage.error('获取数据库失败: ' + e.message);
   } finally {
      loadingNotionDatabases.value = false;
   }
};

// Database source methods
const testDatabaseConnection = async () => {
   if (!dbConfig.connectionUrl) return ElMessage.warning('请输入连接 URL');
   testingDb.value = true;
   try {
      const res = await request.post('/knowledge/sync/database/test', dbConfig);
      if (res.success) {
         ElMessage.success('连接成功: ' + res.databaseProductName);
      } else {
         ElMessage.error(res.message || '连接失败');
      }
   } catch (e) {
      ElMessage.error('测试失败: ' + e.message);
   } finally {
      testingDb.value = false;
   }
};

const handleNext = async () => {
   // Validate step 1 based on selected source
   if (currentStep.value === 1) {
      if (selectedSource.value === 'file' && fileList.value.length === 0) {
         return ElMessage.warning('请先上传文件');
      }
      if (selectedSource.value === 'web' && !webConfig.urlsText) {
         return ElMessage.warning('请输入 Web URL');
      }
      if (selectedSource.value === 'notion' && (!notionConfig.integrationToken || !notionConfig.databaseId)) {
         return ElMessage.warning('请填写 Notion 配置');
      }
      if (selectedSource.value === 'database' && (!dbConfig.connectionUrl || !dbConfig.username)) {
         return ElMessage.warning('请填写数据库连接信息');
      }
   }

   // Step 2: Parsing - upload files, trigger parsing, and wait for completion
   if(currentStep.value === 2) {
      // 1. 创建知识库
      // 2. 上传所有文件并触发解析
      // 3. 等待解析完成
      // 4. 获取解析后的文本内容
      const parseSuccess = await processFilesAndParse();
      // Only proceed to step 3 if parsing succeeded (or skipped)
      if (!parseSuccess) {
         ElMessage.error('解析失败，请检查文件或配置后重试');
         return;
      }
      // 不自动跳转，让用户手动点击下一步
      ElMessage.success('解析完成，请点击"下一步"继续');
      currentStep.value = 3; // 跳转到 step 3，等待用户确认
      return;
   }

   // Step 3: Segmentation - proceed to finish (vectorization)
   if(currentStep.value === 3) {
      startProcessing(); // This will create KB and vectorize
   }
   currentStep.value++;
};

const createEmpty = () => { currentStep.value = 4; kbName.value = '未命名知识库'; startProcessing(); };

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

      // Step 1: Use parsed content from step 2 (already processed)
      if (parsedContent.value && parsedContent.value.length > 0) {
         const parsed = parsedContent.value.find(p => p.fileName === targetFile.name);
         if (parsed && parsed.text) {
            text = parsed.text;
            console.log('Using pre-parsed content:', text.length, 'chars');
         }
      }

      // Step 2: If no pre-parsed content, try to get from document
      if (!text && createdKbId.value) {
         try {
            const docs = await request.get(`/knowledge/${createdKbId.value}/documents`);
            const uploadedDoc = docs.find(d => d.fileName === targetFile.name);
            if (uploadedDoc && uploadedDoc.id) {
               const contentRes = await request.get(`/knowledge/documents/${uploadedDoc.id}/content`);
               if (contentRes.text && contentRes.text.length > 0) {
                  text = contentRes.text;
               }
            }
         } catch (err) {
            console.warn('Failed to get parsed content:', err);
         }
      }

      // Step 3: Fallback to local file reading or mock
      if (!text || text.startsWith('Error:') || text.startsWith('Failed')) {
         if (targetFile.type.startsWith('text/') || targetFile.name.endsWith('.md') || targetFile.name.endsWith('.json')) {
            text = await readFileAsText(targetFile.rawFile);
         } else {
            text = "（注意：此处显示示例文本。）\n\n" + getMockText(1) + "\n\n" + getMockText(2) + "\n\n" + getMockText(3);
         }
      }

      if (form.cleanSpaces) text = text.replace(/[ \t]+/g, ' ');
      if (form.cleanUrls) text = text.replace(/https?:\/\/[^\s]+/g, '');

      let chunks = [];
      if (form.segmentMode === 'parent_child') {
        // Simulate Parent-Child chunking preview
        // First split into parent chunks (~1000 chars)
        const parentChunks = chunkText(text, 1000, 0, '\\n\\n');
        chunks = [];
        parentChunks.forEach((parent, idx) => {
          // Each parent can have multiple children (~200 chars)
          const childChunks = chunkText(parent, 200, 0, '\\n');
          childChunks.forEach((child, cIdx) => {
            chunks.push({
              id: `p${idx + 1}-c${cIdx + 1}`,
              content: child,
              length: child.length,
              chunkType: 'child',
              parentIndex: idx + 1
            });
          });
        });
      } else {
        chunks = chunkText(text, form.maxTokens, form.overlap, form.separator);
        chunks = chunks.map((c, i) => ({ id: i + 1, content: c, length: c.length, chunkType: 'chunk' }));
      }
      previewChunks.value = chunks;
      // Default collapse chunks longer than 150 characters
      collapsedChunks.value = new Set(chunks.filter(c => c.length > 150).map(c => c.id));
      showPreview.value = true;
   } catch (e) {
      console.error('Preview error:', e);
      ElMessage.error(e.message); 
   } finally {
      previewLoading.value = false;
   }
};

// Toggle chunk expand/collapse
const toggleChunk = (chunkId) => {
  if (collapsedChunks.value.has(chunkId)) {
    collapsedChunks.value.delete(chunkId);
  } else {
    collapsedChunks.value.add(chunkId);
  }
  // Trigger reactivity
  collapsedChunks.value = new Set(collapsedChunks.value);
};

const handleResetParams = () => {
   form.separator = '\\n\\n';
   form.maxTokens = 500;
   form.overlap = 50;
   showPreview.value = false;
   previewChunks.value = [];
   collapsedChunks.value = new Set();
};

// --- Step 2 -> Step 3: Upload files and trigger parsing ---
const parsingLoading = ref(false);
const parsingComplete = ref(false); // Step 2 parsing completed
const parsingFailed = ref(false); // Has any parsing failure
const uploadedDocIds = ref([]); // Track uploaded document IDs for parsing status check
const docIdToIndex = ref(new Map()); // Map docId to file index for progress tracking

// Step 2: Process files - create KB, upload files, trigger parsing, wait for completion
const processFilesAndParse = async () => {
    if (selectedSource.value !== 'file' || fileList.value.length === 0) {
        return; // No files to parse
    }

    parsingLoading.value = true;
    creatingFiles.value = fileList.value.map(f => ({ name: f.name, progress: 0, status: 'uploading' }));

    try {
        // 1. Create knowledge base (needed for file storage)
        let newKbId = createdKbId.value;
        if (!newKbId) {
            const kb = await request.post('/knowledge', {
                name: kbName.value || '未命名知识库',
                description: kbDescription.value || '',
                status: 'ENABLED',
                parsingEnabled: form.parsingEnabled,
                ocrProvider: form.ocrProvider,
                asrProvider: form.asrProvider,
                ocrModel: form.ocrModel,
                asrModel: form.asrModel,
                richTextEnabled: form.richTextEnabled,
                chunkSize: form.chunkSize || null,
                chunkOverlap: form.chunkOverlap || null,
                topK: form.topK || null,
                similarityThreshold: form.similarityThreshold || null,
                alpha: form.alpha || null,
                enableRerank: form.enableRerank || null,
                rerankModel: form.rerankModel || null
            });
            newKbId = kb.id;
            createdKbId.value = newKbId;
        }

        // 2. Upload files and trigger parsing
        uploadedDocIds.value = [];
        for (let i = 0; i < fileList.value.length; i++) {
            const f = fileList.value[i];
            creatingFiles.value[i].status = 'uploading';

            if (uploadedFiles.value.has(f.name)) {
                creatingFiles.value[i].progress = 100;
                creatingFiles.value[i].status = 'parsed';
                continue;
            }

            const formData = new FormData();
            formData.append('file', f.rawFile);

            try {
                const doc = await request.post(`/knowledge/${newKbId}/documents/upload`, formData, {
                    headers: { 'Content-Type': 'multipart/form-data' }
                });
                uploadedFiles.value.add(f.name);
                uploadedDocIds.value.push(doc.id);
                docIdToIndex.value.set(doc.id, i); // 记录 docId 到文件索引的映射
                creatingFiles.value[i].progress = 50;
                creatingFiles.value[i].status = 'parsing';
            } catch (err) {
                console.error(`Failed to upload ${f.name}:`, err);
                creatingFiles.value[i].status = 'error';
            }
        }

        // 3. Wait for parsing to complete (poll document status)
        let parseSuccess = true;
        if (uploadedDocIds.value.length > 0 && form.parsingEnabled) {
            ElMessage.info('等待解析完成...');
            parseSuccess = await waitForParsingComplete(uploadedDocIds.value);
        }

        // 4. Get parsed text content for step 3 preview
        parsedContent.value = await fetchParsedContent(newKbId);

        if (parseSuccess) {
            ElMessage.success('文件解析完成');
        }
        return parseSuccess;
    } catch (err) {
        console.error('Parsing failed:', err);
        ElMessage.error('解析失败: ' + (err.message || '未知错误'));
        parsingFailed.value = true;
        parsingComplete.value = true;
        return false;
    } finally {
        parsingLoading.value = false;
    }
};

// 重新解析文件
const reparseFiles = async () => {
    // 重置状态
    parsingComplete.value = false;
    parsedContent.value = [];
    uploadedFiles.value.clear();
    uploadedDocIds.value = [];

    // 如果已有知识库，先删除其中的所有文档
    if (createdKbId.value) {
        try {
            // 获取知识库中的所有文档
            const docs = await request.get(`/knowledge/${createdKbId.value}/documents`);
            if (docs && docs.length > 0) {
                // 删除所有文档
                for (const doc of docs) {
                    try {
                        await request.delete(`/knowledge/documents/${doc.id}`);
                    } catch (e) {
                        console.error(`Failed to delete document ${doc.id}:`, e);
                    }
                }
            }
            ElMessage.success('已清理旧文档，开始重新解析');
        } catch (e) {
            console.error('Failed to get documents for cleanup:', e);
        }
    }

    // 重新调用解析逻辑
    await processFilesAndParse();
};

// Wait for parsing to complete
const waitForParsingComplete = async (docIds) => {
    const maxAttempts = 300; // 5 minutes max for OCR/ASR processing
    let attempts = 0;
    parsingFailed.value = false;

    while (attempts < maxAttempts) {
        await new Promise(resolve => setTimeout(resolve, 1000));
        attempts++;

        try {
            let allComplete = true;
            let hasFailed = false;
            for (const docId of docIds) {
                const doc = await request.get(`/knowledge/documents/${docId}`);
                const status = doc.parseStatus || doc.vectorStatus;
                // 使用 docIdToIndex 映射获取文件索引
                const idx = docIdToIndex.value.get(docId);

                // 根据文档状态更新进度条
                if (idx !== undefined && creatingFiles.value[idx]) {
                    if (status === 'PARSED') {
                        // 解析完成
                        creatingFiles.value[idx].progress = 100;
                        creatingFiles.value[idx].status = 'parsed';
                    } else if (status === 'FAILED') {
                        // 解析失败
                        console.warn(`Document ${docId} parsing failed`);
                        hasFailed = true;
                        creatingFiles.value[idx].status = 'error';
                        creatingFiles.value[idx].progress = 100;
                    } else if (status === 'PENDING' || status === 'PARSING' || status === 'PROCESSING' || status === 'RUNNING') {
                        // 解析中 - 动态增长进度 50-90%
                        if (creatingFiles.value[idx].progress < 90) {
                            creatingFiles.value[idx].progress += Math.floor(Math.random() * 10) + 5;
                            if (creatingFiles.value[idx].progress > 90) {
                                creatingFiles.value[idx].progress = 90;
                            }
                        }
                        creatingFiles.value[idx].status = 'parsing';
                        allComplete = false;
                    } else {
                        // 未知状态，继续等待
                        allComplete = false;
                    }
                } else {
                    // 如果找不到对应索引，也视为未完成
                    allComplete = false;
                }
            }

            if (allComplete) {
                parsingFailed.value = hasFailed;
                parsingComplete.value = true;
                return !hasFailed; // Return false if any failed
            }
        } catch (err) {
            console.warn('Error checking parse status:', err);
        }
    }

    console.warn('Parsing timeout');
    parsingComplete.value = true;
    parsingFailed.value = true;
    return false;
};

// Fetch parsed content for preview
const parsedContent = ref([]); // Array of { fileName, text }

const fetchParsedContent = async (kbId) => {
    const content = [];
    try {
        const docs = await request.get(`/knowledge/${kbId}/documents`);
        console.log('[Debug] Fetched documents:', docs);
        for (const doc of docs) {
            try {
                const contentRes = await request.get(`/knowledge/documents/${doc.id}/content`);
                console.log(`[Debug] Content for ${doc.fileName}:`, JSON.stringify(contentRes));
                console.log(`[Debug] Has text property:`, 'text' in contentRes, 'keys:', Object.keys(contentRes));
                if (contentRes.text) {
                    content.push({
                        docId: doc.id,
                        fileName: doc.fileName,
                        text: contentRes.text
                    });
                } else if (contentRes.contentPreview) {
                    console.log(`[Debug] Using contentPreview for ${doc.fileName}`);
                    content.push({
                        docId: doc.id,
                        fileName: doc.fileName,
                        text: contentRes.contentPreview
                    });
                } else {
                    console.warn(`[Debug] No text content for ${doc.fileName}, contentRes:`, contentRes);
                }
            } catch (err) {
                console.warn(`Failed to get content for ${doc.fileName}:`, err);
            }
        }
    } catch (err) {
        console.error('Failed to fetch parsed content:', err);
    }
    console.log('[Debug] Final parsed content:', content);
    return content;
};

// --- Step 3: Processing (vectorization) ---
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

        // Debug: log OCR model selection
        console.log('[Debug] OCR Provider:', form.ocrProvider);
        console.log('[Debug] OCR Model:', form.ocrModel);
        console.log('[Debug] OCR Model Options:', ocrModelOptions.value);
        console.log('[Debug] All Models:', allModels.value.filter(m => {
            const type = m.type?.toUpperCase();
            const isOCR = type === 'OCR';
            const isVision = type === 'VISION' || type === 'VLM' || type === 'MULTIMODAL' || type === 'VISUAL';
            const hasVisionKeywords = (m.modelId + m.name + (m.description || '')).toLowerCase().match(/ocr|vision|vl|multimodal|图片|文字|识别/);
            return (isOCR || isVision || hasVisionKeywords) && m.status === 'ENABLED';
        }));

        const payload = {
            name: kbName.value || '未命名知识库',
            description: kbDescription.value || (form.indexType === 'high_quality' ? 'High Quality Index' : 'Economy Index'),
            status: 'ENABLED',
            // Parsing configuration
            parsingEnabled: form.parsingEnabled,
            ocrProvider: form.ocrProvider,
            asrProvider: form.asrProvider,
            ocrModel: form.ocrModel,
            asrModel: form.asrModel,
            richTextEnabled: form.richTextEnabled,
            chunkSize: form.chunkSize || null,
            chunkOverlap: form.chunkOverlap || null,
            topK: form.topK || null,
            similarityThreshold: form.similarityThreshold || null,
            alpha: form.alpha || null,
            enableRerank: form.enableRerank || null,
            rerankModel: form.rerankModel || null
        };

        console.log('[Debug] Saving KB with payload:', payload);

        // 1. Create Knowledge Base via API (avoid duplicate if already created)
        let newKbId = createdKbId.value;
        if (!newKbId) {
            const kb = await request.post('/knowledge', payload);
            newKbId = kb.id;
            createdKbId.value = newKbId;
        }

        // 2. Handle different source types
        if (selectedSource.value === 'file' && fileList.value.length > 0) {
            // Upload files (skip already uploaded)
            for (let i = 0; i < fileList.value.length; i++) {
                const f = fileList.value[i];

                // Skip if already uploaded (e.g., during generateDescription)
                if (uploadedFiles.value.has(f.name)) {
                    creatingFiles.value[i].progress = 100;
                    continue;
                }

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
        } else if (selectedSource.value === 'web') {
            // Sync from Web URLs
            const urls = webConfig.urlsText.split('\n').filter(u => u.trim());
            creatingFiles.value = urls.map(u => ({ name: u, progress: 0 }));

            try {
                const res = await request.post(`/knowledge/${newKbId}/sync/web`, {
                    urls: urls,
                    maxDepth: webConfig.maxDepth
                });

                if (res.success) {
                    creatingFiles.value.forEach(f => f.progress = 100);
                    ElMessage.success(`成功同步 ${res.documentCount} 个网页`);
                } else {
                    ElMessage.error(res.error || 'Web 同步失败');
                }
            } catch (syncErr) {
                console.error('Failed to sync from web:', syncErr);
                ElNotification({
                    title: '同步失败',
                    message: 'Web 同步失败，请稍后手动重试。',
                    type: 'error'
                });
            }
        } else if (selectedSource.value === 'notion') {
            // Sync from Notion
            creatingFiles.value = [{ name: 'Notion Database', progress: 0 }];

            try {
                const res = await request.post(`/knowledge/${newKbId}/sync/notion`, {
                    integrationToken: notionConfig.integrationToken,
                    databaseId: notionConfig.databaseId
                });

                if (res.success) {
                    creatingFiles.value[0].progress = 100;
                    ElMessage.success(`成功同步 ${res.documentCount} 个 Notion 页面`);
                } else {
                    ElMessage.error(res.error || 'Notion 同步失败');
                }
            } catch (syncErr) {
                console.error('Failed to sync from Notion:', syncErr);
                ElNotification({
                    title: '同步失败',
                    message: 'Notion 同步失败，请稍后手动重试。',
                    type: 'error'
                });
            }
        } else if (selectedSource.value === 'database') {
            // Connect to database
            creatingFiles.value = [{ name: 'Database Connection', progress: 0 }];

            try {
                const res = await request.post(`/knowledge/${newKbId}/sync/database/connect`, dbConfig);

                if (res.success) {
                    creatingFiles.value[0].progress = 100;
                    ElMessage.success('数据库连接成功');
                    // Optionally sync tables here
                } else {
                    ElMessage.error(res.error || '数据库连接失败');
                }
            } catch (syncErr) {
                console.error('Failed to connect database:', syncErr);
                ElNotification({
                    title: '连接失败',
                    message: '数据库连接失败，请检查配置。',
                    type: 'error'
                });
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
.create-header { height: 56px; border-bottom: 1px solid #E5E7EB; display: flex; align-items: center; padding: 0 24px; justify-content: space-between; gap: 24px; }
.header-steps { display: flex; align-items: center; flex: 1; justify-content: center; }
.header-steps .step-item { display: flex; align-items: center; gap: 6px; opacity: 0.5; }
.header-steps .step-item.active, .header-steps .step-item.done { opacity: 1; }
.header-steps .step-num { width: 20px; height: 20px; border-radius: 50%; background: #E5E7EB; color: #6B7280; display: flex; align-items: center; justify-content: center; font-size: 11px; font-weight: 700; }
.header-steps .step-item.active .step-num { background: #2563EB; color: #fff; }
.header-steps .step-item.done .step-num { background: #10B981; color: #fff; }
.header-steps .step-title { font-size: 12px; font-weight: 500; color: #374151; }
.header-steps .step-line { width: 32px; height: 1px; background: #E5E7EB; margin: 0 8px; }
.header-left { display: flex; align-items: center; gap: 16px; cursor: pointer; }
.back-icon { font-size: 20px; color: #6B7280; padding: 8px; border-radius: 8px; border: 1px solid #E5E7EB; }

.create-container { flex: 1; max-width: 1400px; width: 100%; margin: 0 auto; padding: 24px; padding-bottom: 80px; display: flex; flex-direction: column; transition: max-width 0.3s; overflow: hidden; }
.create-container.wide-container { max-width: 1600px; }
.create-container.finish-container { max-width: 1000px; }

.create-footer { position: fixed; bottom: 0; left: 0; right: 0; height: 56px; background: #fff; border-top: 1px solid #E5E7EB; display: flex; align-items: center; justify-content: center; gap: 12px; padding: 0 24px; z-index: 100; }

/* Steps */
.steps-wrapper { display: none; }

.step-item { display: flex; align-items: center; gap: 8px; opacity: 0.5; }
.step-item.active, .step-item.done { opacity: 1; }
.step-num { width: 24px; height: 24px; border-radius: 50%; background: #E5E7EB; color: #6B7280; display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 700; }
.step-item.active .step-num { background: #2563EB; color: #fff; }
.step-item.done .step-num { background: #10B981; color: #fff; }
.step-title { font-size: 14px; font-weight: 500; color: #374151; }
.step-line { width: 60px; height: 1px; background: #E5E7EB; margin: 0 16px; }
.step-content { flex: 1; overflow-y: auto; padding-right: 8px; }
.section-title { font-size: 20px; font-weight: 600; color: #111827; margin-bottom: 20px; text-align: center; }

/* Step 1 Styles */
.source-cards { display: flex; gap: 16px; margin-bottom: 20px; justify-content: center; flex-wrap: wrap; }
.source-card { width: 200px; padding: 16px; border: 1px solid #E5E7EB; border-radius: 12px; cursor: pointer; position: relative; transition: all 0.2s; flex-shrink: 0; }
.source-card.selected { border-color: #2563EB; background: #EFF6FF; }
.source-card.disabled { opacity: 0.5; cursor: not-allowed; background: #F9FAFB; }
.source-icon { font-size: 32px; margin-bottom: 16px; }
.source-icon.blue { color: #2563EB; }
.source-icon.green { color: #10B981; }
.source-icon.purple { color: #9333EA; }
.source-icon.orange { color: #F59E0B; }
.source-info h3 { font-size: 16px; font-weight: 600; margin: 0 0 8px 0; }
.source-info p { font-size: 13px; color: #6B7280; margin: 0; line-height: 1.4; }
.check-mark { position: absolute; top: 12px; right: 12px; color: #2563EB; }
.upload-zone { border: 2px dashed #E5E7EB; border-radius: 12px; padding: 24px; text-align: center; transition: all 0.2s; margin-bottom: 16px; cursor: pointer; }
.upload-zone:hover { border-color: #2563EB; background: #F9FAFB; }
.upload-icon { font-size: 48px; color: #D1D5DB; margin-bottom: 16px; }
.upload-text .link { color: #2563EB; font-weight: 500; }
.file-preview-list { max-width: 600px; margin: 0 auto 32px auto; display: flex; flex-direction: column; gap: 12px; }
.file-item { display: flex; align-items: center; justify-content: space-between; padding: 12px 16px; border: 1px solid #E5E7EB; border-radius: 8px; background: #fff; }
.file-info { display: flex; align-items: center; gap: 10px; overflow: hidden; }
.file-icon { color: #9CA3AF; font-size: 18px; }
.remove-btn:hover { color: #DC2626; background: #FEE2E2; }
.empty-kb-link { text-align: center; }

/* Source Config Styles */
.source-config {
   max-width: 600px;
   margin: 0 auto 32px auto;
   padding: 24px;
   background: #F9FAFB;
   border: 1px solid #E5E7EB;
   border-radius: 12px;
}
.config-title {
   font-size: 16px;
   font-weight: 600;
   margin: 0 0 16px 0;
   color: #111827;
}
.config-form {
   display: flex;
   flex-direction: column;
   gap: 16px;
}
.form-item {
   display: flex;
   flex-direction: column;
   gap: 8px;
}
.form-item label {
   font-size: 14px;
   font-weight: 500;
   color: #374151;
}
.form-row {
   display: flex;
   gap: 16px;
}
.form-row .form-item {
   flex: 1;
}
.form-tip {
   font-size: 12px;
   color: #6B7280;
}
.form-actions {
   display: flex;
   gap: 12px;
   margin-top: 8px;
}

/* Step 2 Styles */
.step-2-layout { display: flex; gap: 24px; height: calc(100vh - 190px); overflow: hidden; }
.step-2-config { flex: 1; overflow-y: auto; padding-right: 12px; }
.step-2-preview { width: 420px; background: #F9FAFB; border: 1px solid #E5E7EB; border-radius: 12px; display: flex; flex-direction: column; overflow: hidden; flex-shrink: 0; }

/* Parsing Options Styles */
.parsing-options { display: flex; flex-direction: column; gap: 12px; }
.parsing-card { border: 1px solid #E5E7EB; border-radius: 8px; padding: 16px; cursor: pointer; transition: all 0.2s; }

/* Provider Cards Styles */
.provider-cards { display: flex; gap: 12px; margin-bottom: 12px; }
.provider-card { flex: 1; border: 1px solid #E5E7EB; border-radius: 8px; padding: 14px; display: flex; align-items: center; gap: 12px; cursor: pointer; transition: all 0.2s; }
.provider-card:hover { border-color: #93C5FD; }
.provider-card.active { border-color: #2563EB; background: #EFF6FF; }
.provider-icon { width: 40px; height: 40px; background: #F3F4F6; border-radius: 8px; display: flex; align-items: center; justify-content: center; color: #6B7280; font-size: 20px; }
.provider-card.active .provider-icon { background: #DBEAFE; color: #2563EB; }
.provider-info { flex: 1; }
.provider-name { font-weight: 600; font-size: 14px; color: #1F2937; }
.provider-desc { font-size: 12px; color: #6B7280; margin-top: 2px; }
.cloud-config { margin-top: 12px; }
.icon-blue { color: #2563EB; }
.parsing-card:hover { border-color: #93C5FD; }
.parsing-card.active { border-color: #2563EB; background: #EFF6FF; }
.parsing-header { display: flex; align-items: center; gap: 12px; }
.parsing-icon { width: 40px; height: 40px; background: #F3F4F6; border-radius: 8px; display: flex; align-items: center; justify-content: center; color: #6B7280; font-size: 20px; }
.parsing-card.active .parsing-icon { background: #DBEAFE; color: #2563EB; }
.parsing-info { flex: 1; }
.parsing-title { font-weight: 600; font-size: 14px; color: #1F2937; }
.parsing-desc { font-size: 12px; color: #6B7280; margin-top: 2px; }
.config-item { margin-bottom: 16px; }
.config-item label { font-size: 12px; font-weight: 500; color: #374151; display: block; margin-bottom: 6px; }
.config-tip { font-size: 11px; color: #9CA3AF; margin-top: 4px; }
.file-type-list { display: flex; flex-direction: column; gap: 8px; }
.file-type-item { display: flex; align-items: center; gap: 8px; padding: 8px 12px; background: #F9FAFB; border-radius: 6px; font-size: 13px; }
.file-type-item .file-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.more-files { text-align: center; color: #6B7280; font-size: 12px; padding: 8px; }
.no-files-tip { display: flex; align-items: center; gap: 8px; color: #9CA3AF; font-size: 13px; padding: 16px; background: #F9FAFB; border-radius: 8px; }
.parsing-info-list { display: flex; flex-direction: column; gap: 16px; }
.info-item { display: flex; gap: 12px; padding: 12px; background: #fff; border-radius: 8px; border: 1px solid #E5E7EB; }
.info-icon { width: 36px; height: 36px; background: #F3F4F6; border-radius: 8px; display: flex; align-items: center; justify-content: center; color: #6B7280; }
.info-content { flex: 1; }
.info-title { font-weight: 600; font-size: 13px; color: #1F2937; }
.info-desc { font-size: 12px; color: #6B7280; margin-top: 2px; }

/* Parsing Loading State */
.parsing-loading { display: flex; flex-direction: column; align-items: center; padding: 20px; }
.parsing-loading .loading-icon { font-size: 32px; color: #2563EB; animation: rotate 1s linear infinite; }
.parsing-loading .loading-text { margin-top: 12px; font-size: 14px; color: #374151; }
.parsing-loading .loading-progress { width: 100%; margin-top: 16px; }
.file-progress-item { margin-bottom: 12px; }
.file-progress-item .file-name { font-size: 12px; color: #6B7280; display: block; margin-bottom: 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
@keyframes rotate { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }

/* Parsed Content Display */
.parsed-content-list { display: flex; flex-direction: column; gap: 12px; }
.parsed-item { background: #fff; border: 1px solid #E5E7EB; border-radius: 8px; padding: 12px; }
.parsed-item-header { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.parsed-item-header .parsed-filename { flex: 1; font-weight: 500; font-size: 13px; color: #374151; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.parsed-item-content { font-size: 12px; color: #6B7280; line-height: 1.5; max-height: 200px; overflow-y: auto; white-space: pre-wrap; word-break: break-all; }

/* Parsing Failed State */
.parsing-failed { display: flex; flex-direction: column; align-items: center; padding: 20px; }
.parsing-failed .failed-icon { font-size: 48px; color: #EF4444; }
.parsing-failed .failed-text { margin-top: 12px; font-size: 14px; color: #EF4444; font-weight: 500; }
.parsing-failed .failed-hint { margin-top: 4px; font-size: 12px; color: #6B7280; }

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

/* Retrieval Config Section */
.retrieval-config-section {
  margin-top: 16px;
  padding: 12px;
  background: #F9FAFB;
  border-radius: 8px;
  border: 1px solid #E5E7EB;
}
.retrieval-config-section .section-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.retrieval-config-section .section-tip {
  font-size: 12px;
  color: #9CA3AF;
}
.retrieval-config-section .config-fields {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.retrieval-config-section .config-row {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}
.retrieval-config-section .config-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 150px;
}
.retrieval-config-section .config-item label {
  font-size: 12px;
  color: #6B7280;
}
.preview-header { padding: 16px 20px; font-weight: 600; color: #1F2937; border-bottom: 1px solid #E5E7EB; background: #fff; min-height: 57px; display: flex; align-items: center; }
.header-content { display: flex; align-items: center; gap: 8px; width: 100%; }
.doc-name { flex: 1; font-size: 13px; font-weight: 500; }
.preview-body { flex: 1; overflow-y: auto; padding: 20px; position: relative; }
.chunk-list { display: flex; flex-direction: column; gap: 16px; }
.chunk-item { background: #fff; border: 1px solid #E5E7EB; border-radius: 8px; padding: 16px; font-size: 13px; }
.chunk-meta { color: #9CA3AF; font-size: 12px; margin-bottom: 8px; display: flex; align-items: center; gap: 8px; }
.chunk-content { color: #374151; line-height: 1.6; word-break: break-word; }
.chunk-content.collapsed { display: -webkit-box; -webkit-line-clamp: 3; line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }
.empty-preview { height: 100%; display: flex; flex-direction: column; align-items: center; justify-content: center; color: #9CA3AF; gap: 16px; }

/* Step 3 Styles - Single Column Layout */
.step-3-layout { max-width: 900px; margin: 0 auto; overflow-y: auto; }
.step-3-main { width: 100%; }

/* Success Header */
.success-header { display: flex; align-items: center; gap: 16px; margin-bottom: 24px; }
.success-icon-wrap {
   width: 56px;
   height: 56px;
   background: linear-gradient(135deg, #10B981 0%, #059669 100%);
   border-radius: 16px;
   display: flex;
   align-items: center;
   justify-content: center;
   flex-shrink: 0;
}
.success-icon-wrap .success-icon { font-size: 28px; color: #fff; }
.header-text h3 { margin: 0 0 4px 0; font-size: 22px; font-weight: 600; color: #111827; }
.header-text p { margin: 0; color: #6B7280; font-size: 14px; }

/* Cards Grid */
.cards-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; margin-bottom: 24px; }
.info-card {
   background: #fff;
   border: 1px solid #E5E7EB;
   border-radius: 12px;
   overflow: hidden;
}
.info-card .card-header {
   display: flex;
   align-items: center;
   gap: 8px;
   padding: 14px 16px;
   background: #F9FAFB;
   border-bottom: 1px solid #E5E7EB;
   font-weight: 500;
   font-size: 14px;
   color: #374151;
}
.info-card .card-header .el-icon { font-size: 16px; color: #6B7280; }
.info-card .card-header .el-tag { margin-left: auto; }
.info-card .card-body { padding: 16px; }

/* KB Info Card - Full Width */
.kb-info-card { grid-column: 1 / -1; }
.form-item { margin-bottom: 16px; }
.form-item:last-child { margin-bottom: 0; }
.form-item label { display: block; font-size: 13px; color: #6B7280; margin-bottom: 8px; font-weight: 500; }
.label-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.label-row label { margin-bottom: 0; }

/* Process Card */
.file-process-list { display: block; }
.process-item { padding: 12px; background: #F9FAFB; border-radius: 8px; margin-bottom: 8px; }
.process-item:last-child { margin-bottom: 0; }
.file-row { display: flex; justify-content: space-between; margin-bottom: 8px; font-size: 13px; font-weight: 500; }
.file-name { display: flex; align-items: center; gap: 6px; color: #374151; }
.file-name .el-icon { color: #6B7280; }
.process-status { color: #6B7280; font-weight: 400; }
.empty-process {
   display: flex;
   align-items: center;
   justify-content: center;
   gap: 8px;
   padding: 24px;
   color: #9CA3AF;
   font-size: 14px;
}
.empty-process .el-icon { font-size: 20px; }

/* Config Card */
.config-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; }
.config-item { display: flex; flex-direction: column; gap: 4px; }
.config-item.full-width { grid-column: 1 / -1; }
.config-label { font-size: 12px; color: #9CA3AF; }
.config-value { font-size: 14px; color: #374151; font-weight: 500; display: flex; align-items: center; gap: 4px; }
.preprocess-tags { display: flex; gap: 4px; }
.no-preprocess { color: #9CA3AF; font-weight: 400; }

/* Actions */
.step-3-actions { display: flex; gap: 12px; padding-top: 8px; border-top: 1px solid #E5E7EB; padding-top: 20px; }
.step-3-actions .api-btn { flex: 1; }
.step-3-actions .go-btn { flex: 2; }

/* Responsive */
@media (max-width: 768px) {
   .cards-grid { grid-template-columns: 1fr; }
   .config-grid { grid-template-columns: 1fr; }
   .step-3-actions { flex-direction: column; }
   .step-3-actions .api-btn, .step-3-actions .go-btn { flex: none; }
}
</style>
