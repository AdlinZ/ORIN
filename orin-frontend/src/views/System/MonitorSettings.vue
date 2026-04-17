<template>
  <div class="page-container">
    <OrinPageShell
      title="系统环境配置"
      description="外部服务连接、AI 能力及知识库参数配置"
      icon="Tools"
      domain="系统管理"
      maturity="available"
    />

    <div class="layout">
      <div class="main-content">

        <!-- ① 存储层 -->
        <section id="sec-storage" class="config-section">

        <el-card id="blk-storage-mysql" shadow="never">
          <template #header>
            <div class="card-head">
              <span>MySQL 数据库</span>
              <div class="card-actions">
                <el-button v-if="!cardEditState['storage-mysql']" size="small" @click="startMysqlEdit">编辑</el-button>
                <template v-else>
                  <el-button size="small" @click="cancelMysqlEdit">取消</el-button>
                  <el-button type="primary" size="small" :loading="dbSaving" :icon="Check" @click="saveMysqlConfig">保存</el-button>
                </template>
              </div>
            </div>
          </template>
          <el-alert title="高危配置：修改后需执行 ./manage.sh restart -b 应用到底层连接池。" type="warning" show-icon :closable="false" style="margin-bottom: 20px" />
          <el-form label-position="left" label-width="180px">
            <el-form-item label="URL">
              <el-input v-model="dbConfig['spring.datasource.url']" :disabled="!cardEditState['storage-mysql']" placeholder="jdbc:mysql://localhost:3306/orindb..." />
            </el-form-item>
            <el-form-item label="Username">
              <el-input v-model="dbConfig['spring.datasource.username']" :disabled="!cardEditState['storage-mysql']" />
            </el-form-item>
            <el-form-item label="Password">
              <el-input v-model="dbConfig['spring.datasource.password']" :disabled="!cardEditState['storage-mysql']" type="password" show-password />
            </el-form-item>
          </el-form>
        </el-card>

        <el-card id="blk-storage-redis" shadow="never" style="margin-top: 16px">
          <template #header>
            <div class="card-head">
              <span>缓存 Redis</span>
              <div class="card-actions">
                <el-button v-if="!cardEditState['storage-redis']" size="small" @click="startRedisEdit">编辑</el-button>
                <template v-else>
                  <el-button size="small" @click="cancelRedisEdit">取消</el-button>
                  <el-button type="primary" size="small" :loading="dbSaving" :icon="Check" @click="saveRedisConfig">保存</el-button>
                </template>
              </div>
            </div>
          </template>
          <el-form label-position="left" label-width="180px">
            <el-form-item label="Host">
              <el-input v-model="dbConfig['spring.data.redis.host']" :disabled="!cardEditState['storage-redis']" />
            </el-form-item>
            <el-form-item label="Port">
              <el-input v-model="dbConfig['spring.data.redis.port']" :disabled="!cardEditState['storage-redis']" />
            </el-form-item>
            <el-form-item label="Password">
              <el-input v-model="dbConfig['spring.data.redis.password']" :disabled="!cardEditState['storage-redis']" type="password" show-password />
            </el-form-item>
          </el-form>
        </el-card>

        <el-card id="blk-storage-milvus" shadow="never" style="margin-top: 16px">
          <template #header>
            <div class="card-head">
              <div class="name-with-badge">
                <span>Milvus 向量引擎</span>
                <el-tag :type="milvusStatus.online ? 'success' : 'danger'" size="small">
                  {{ milvusStatus.online ? '已连接' : '未连接' }}
                </el-tag>
              </div>
              <div class="card-actions">
                <el-button v-if="!cardEditState['storage-milvus']" size="small" @click="startMilvusEdit">编辑</el-button>
                <template v-else>
                  <el-button size="small" @click="cancelMilvusEdit">取消</el-button>
                  <el-button type="primary" size="small" :loading="milvusSaving" :icon="Check" @click="saveMilvusCard">保存</el-button>
                </template>
              </div>
            </div>
          </template>
          <el-form label-position="top">
            <el-row :gutter="16">
              <el-col :span="16">
                <el-form-item label="Host">
                  <el-input v-model="milvusConfig.host" :disabled="!cardEditState['storage-milvus']" placeholder="localhost" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="Port">
                  <el-input-number v-model="milvusConfig.port" :disabled="!cardEditState['storage-milvus']" :min="1" :max="65535" style="width:100%" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-form-item label="Token（可选）">
              <el-input v-model="milvusConfig.token" :disabled="!cardEditState['storage-milvus']" type="password" show-password placeholder="root:Milvus" />
            </el-form-item>
            <el-form-item>
              <el-button type="success" plain :disabled="!cardEditState['storage-milvus']" :loading="testingMilvus" @click="testMilvusConnection">测试连接</el-button>
            </el-form-item>
          </el-form>
          <el-divider style="margin: 12px 0" />
          <div v-if="collectionInfo.exists">
            <el-descriptions :column="3" size="small" border>
              <el-descriptions-item label="Collection">{{ collectionInfo.collectionName }}</el-descriptions-item>
              <el-descriptions-item label="向量维度">{{ collectionInfo.dimension }}</el-descriptions-item>
              <el-descriptions-item label="向量数">{{ collectionInfo.vectorCount || '未知' }}</el-descriptions-item>
            </el-descriptions>
            <div style="margin-top: 10px; display: flex; gap: 8px">
              <el-button size="small" :loading="loadingCollection" @click="loadCollectionInfo">刷新统计</el-button>
              <el-button size="small" type="warning" plain :loading="recreating" @click="recreateCollection">重建 Collection</el-button>
            </div>
          </div>
          <el-empty v-else description="Collection 尚未创建，保存配置并测试连接后自动初始化" :image-size="48" />
        </el-card>

        <el-card id="blk-storage-neo4j" shadow="never" style="margin-top: 16px">
          <template #header>
            <div class="card-head">
              <div class="name-with-badge">
                <el-icon size="15"><Share /></el-icon>
                <span>Neo4j 图数据库</span>
                <span class="card-desc">Graph / RAG 知识图谱</span>
                <el-tag :type="neo4jConfig.enabled ? 'success' : 'info'" size="small">
                  {{ neo4jConfig.enabled ? '已启用' : '未启用' }}
                </el-tag>
              </div>
              <div class="card-actions">
                <el-button v-if="!cardEditState['storage-neo4j']" size="small" @click="startNeo4jEdit">编辑</el-button>
                <template v-else>
                  <el-button size="small" @click="cancelNeo4jEdit">取消</el-button>
                  <el-button type="primary" size="small" :loading="neo4jLoading" @click="saveNeo4jCard">保存</el-button>
                </template>
              </div>
            </div>
          </template>
          <el-form :model="neo4jConfig" label-width="160px">
            <el-form-item label="连接 URI（可选）">
              <el-input v-model="neo4jConfig.uri" :disabled="!cardEditState['storage-neo4j']" placeholder="neo4j+s://xxxx.databases.neo4j.io" />
              <p class="form-tip">优先使用 URI；留空则使用 Host + Port</p>
            </el-form-item>
            <el-form-item label="Host">
              <el-input v-model="neo4jConfig.host" :disabled="!cardEditState['storage-neo4j']" placeholder="localhost" />
            </el-form-item>
            <el-form-item label="Port">
              <el-input-number v-model="neo4jConfig.port" :disabled="!cardEditState['storage-neo4j']" :min="1" :max="65535" />
            </el-form-item>
            <el-form-item label="用户名">
              <el-input v-model="neo4jConfig.username" :disabled="!cardEditState['storage-neo4j']" placeholder="neo4j" />
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="neo4jConfig.password" :disabled="!cardEditState['storage-neo4j']" type="password" show-password />
            </el-form-item>
            <el-form-item label="Database">
              <el-input v-model="neo4jConfig.database" :disabled="!cardEditState['storage-neo4j']" placeholder="neo4j" />
            </el-form-item>
            <el-form-item label="连接池大小">
              <el-input-number v-model="neo4jConfig.maxConnectionPoolSize" :disabled="!cardEditState['storage-neo4j']" :min="1" :max="500" />
            </el-form-item>
            <el-form-item label="获取连接超时 (ms)">
              <el-input-number v-model="neo4jConfig.connectionAcquisitionTimeoutMs" :disabled="!cardEditState['storage-neo4j']" :min="1000" :max="300000" />
            </el-form-item>
            <el-form-item label="启用状态">
              <el-switch v-model="neo4jConfig.enabled" :disabled="!cardEditState['storage-neo4j']" />
            </el-form-item>
            <el-form-item v-if="cardEditState['storage-neo4j']">
              <el-button :disabled="!neo4jConfig.enabled" @click="handleTestNeo4jConnection">测试连接</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        </section>

        <!-- ② AI 服务 -->
        <section id="sec-ai-services" class="config-section">

        <el-card id="blk-ai-capabilities" shadow="never">
          <template #header>
            <div class="card-head">
              <div>
                <div class="card-title">模型能力配置</div>
                <div class="card-subtitle">推理服务接入、嵌入模型、重排序与知识库描述生成统一在一处维护。</div>
              </div>
              <div class="card-actions">
                <el-button v-if="!cardEditState['ai-capabilities']" size="small" @click="startAiCapabilitiesEdit">编辑</el-button>
                <template v-else>
                  <el-button size="small" @click="cancelAiCapabilitiesEdit">取消</el-button>
                  <el-button type="primary" size="small" :loading="aiCapabilitySaving" :icon="Check" @click="saveAiCapabilitiesCard">保存</el-button>
                </template>
              </div>
            </div>
          </template>

          <el-divider content-position="left">推理服务接入</el-divider>
          <p class="section-tip">配置对话推理和网页解析所依赖的外部服务，优先保证上游服务可用性。</p>
          <el-form label-position="top">
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="SiliconFlow API Key">
                  <el-input v-model="aiConfig.siliconFlowApiKey" :disabled="!cardEditState['ai-capabilities']" type="password" show-password />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="SiliconFlow Base URL">
                  <el-input v-model="aiConfig.siliconFlowEndpoint" :disabled="!cardEditState['ai-capabilities']" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="16">
              <el-col :span="6">
                <el-form-item label="Jina Reader">
                  <el-switch v-model="aiConfig.jinaEnabled" :disabled="!cardEditState['ai-capabilities']" />
                </el-form-item>
              </el-col>
              <el-col :span="18">
                <el-form-item label="Jina API Key">
                  <el-input v-model="aiConfig.jinaApiKey" :disabled="!cardEditState['ai-capabilities']" type="password" show-password placeholder="可选，无 Key 时 20 次/分钟" />
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>

          <el-divider content-position="left">向量化与排序</el-divider>
          <p class="section-tip">Embedding 和 Rerank 属于同一条知识处理链路，适合并排调整与验证。</p>
          <el-form label-position="top">
            <el-row :gutter="16">
              <el-col :span="8">
                <el-form-item label="服务提供商">
                  <el-select v-model="embeddingConfig.provider" :disabled="!cardEditState['ai-capabilities']" style="width:100%" @change="onProviderChange">
                    <el-option v-for="p in embeddingProviders" :key="p.value" :label="p.label" :value="p.value" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="API 密钥">
                  <el-select v-model="embeddingConfig.apiKeyId" :disabled="!cardEditState['ai-capabilities']" style="width:100%" clearable @focus="loadApiKeys">
                    <el-option v-for="k in apiKeys" :key="k.id" :label="`${k.provider} - ${k.name || k.id}`" :value="k.id">
                      <span>{{ k.provider }}</span>
                      <span style="float:right; color:#8492a6; font-size:12px">{{ k.enabled ? '启用' : '禁用' }}</span>
                    </el-option>
                  </el-select>
                  <p class="form-tip">
                    已在 API 密钥管理中配置的密钥
                    <el-button type="primary" link size="small" @click="$router.push('/dashboard/control/api-keys')">去配置</el-button>
                  </p>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="模型">
                  <el-select v-model="embeddingConfig.model" :disabled="!cardEditState['ai-capabilities']" style="width:100%" filterable allow-create @focus="loadEmbeddingModels">
                    <el-option v-for="m in embeddingModels" :key="m.id" :label="m.id" :value="m.id" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-form-item>
              <el-button type="success" plain :disabled="!cardEditState['ai-capabilities']" :loading="testingEmbedding" @click="testEmbeddingConnection">测试 Embedding</el-button>
            </el-form-item>
            <el-row :gutter="16">
              <el-col :span="6">
                <el-form-item label="启用 Rerank">
                  <el-switch v-model="embeddingConfig.enableRerank" :disabled="!cardEditState['ai-capabilities']" />
                  <p class="form-tip">粗排后精排，提升检索准确率</p>
                </el-form-item>
              </el-col>
              <el-col :span="18">
                <el-form-item v-if="embeddingConfig.enableRerank" label="Rerank 模型">
                  <el-select v-model="embeddingConfig.rerankModel" :disabled="!cardEditState['ai-capabilities']" style="width:100%" filterable clearable @focus="loadRerankModels">
                    <el-option v-for="m in rerankModels" :key="m.id" :label="m.name || m.id" :value="m.id" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </el-card>

        <el-row :gutter="16" style="margin-top: 16px">
          <el-col :span="12">
            <el-card id="blk-ai-multimodal" shadow="never" style="height: 100%">
              <template #header>
                <div class="card-head">
                  <div>
                    <div class="card-title">多模态解析</div>
                    <div class="card-subtitle">OCR 和 ASR 依赖模型推理能力，属于内容理解链路。</div>
                  </div>
                </div>
              </template>
              <el-form label-position="top">
                <el-row :gutter="20">
                  <el-col :span="12">
                    <el-form-item label="OCR 图片文字识别">
                      <el-select v-model="kbParams.ocrProvider" :disabled="!cardEditState['ai-capabilities']" style="width:100%">
                        <el-option value="local" label="本地（Tesseract）" />
                        <el-option value="cloud" label="云服务 API" />
                      </el-select>
                    </el-form-item>
                    <el-form-item v-if="kbParams.ocrProvider === 'cloud'" label="OCR 模型">
                      <el-input v-model="kbParams.ocrModel" :disabled="!cardEditState['ai-capabilities']" placeholder="ocr-general-v1" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="ASR 语音识别">
                      <el-select v-model="kbParams.asrProvider" :disabled="!cardEditState['ai-capabilities']" style="width:100%">
                        <el-option value="local" label="本地（Whisper）" />
                        <el-option value="cloud" label="云服务 API" />
                      </el-select>
                    </el-form-item>
                    <el-form-item label="Whisper 模型">
                      <el-select v-model="kbParams.asrModel" :disabled="!cardEditState['ai-capabilities']" style="width:100%">
                        <el-option value="tiny" label="tiny — 最快" />
                        <el-option value="base" label="base — 平衡" />
                        <el-option value="small" label="small — 较好精度" />
                        <el-option value="medium" label="medium — 高精度" />
                        <el-option value="large" label="large — 最高精度" />
                      </el-select>
                    </el-form-item>
                  </el-col>
                </el-row>
              </el-form>
            </el-card>
          </el-col>

          <el-col :span="12">
            <el-card id="blk-ai-kb-description" shadow="never" style="height: 100%">
              <template #header>
                <div class="card-head">
                  <div>
                    <div class="card-title">AI 生成知识库描述</div>
                    <div class="card-subtitle">根据知识库文档内容自动生成名称和描述。</div>
                  </div>
                </div>
              </template>
              <el-row :gutter="24">
                <el-col :span="14">
                  <el-form label-position="top" size="small">
                    <el-row :gutter="12">
                      <el-col :span="12">
                        <el-form-item label="选择知识库">
                          <el-select v-model="selectedKB" :disabled="!cardEditState['ai-capabilities']" placeholder="选择知识库" style="width:100%" filterable>
                            <el-option v-for="kb in knowledgeBases" :key="kb.kbId" :label="kb.name" :value="kb.kbId">
                              <span>{{ kb.name }}</span>
                              <span style="float:right; color:#8492a6; font-size:12px">{{ kb.docCount || 0 }} 文档</span>
                            </el-option>
                          </el-select>
                        </el-form-item>
                      </el-col>
                      <el-col :span="12">
                        <el-form-item label="选择 AI 模型">
                          <el-select v-model="selectedKBModel" :disabled="!cardEditState['ai-capabilities']" placeholder="选择模型" style="width:100%" filterable>
                            <el-option v-for="m in kbModels" :key="m.id" :label="m.name" :value="m.id" />
                          </el-select>
                        </el-form-item>
                      </el-col>
                    </el-row>
                    <el-button type="primary" :loading="generatingDesc" :disabled="!cardEditState['ai-capabilities'] || !selectedKB || !selectedKBModel" @click="generateKBDescription">
                      生成名称和描述
                    </el-button>
                    <p class="form-tip" style="margin-top: 8px">AI 将根据知识库文档内容自动生成名称和描述</p>
                  </el-form>
                </el-col>
                <el-col :span="10">
                  <div class="stats-list">
                    <div v-for="s in kbStats" :key="s.label" class="stat-item">
                      <span class="stat-label">{{ s.label }}</span>
                      <span class="stat-value">{{ knowledgeStats[s.key] }}</span>
                    </div>
                  </div>
                  <el-button size="small" :loading="loadingStats" style="width:100%; margin-top:10px" @click="loadKnowledgeStats">
                    刷新统计
                  </el-button>
                </el-col>
              </el-row>
            </el-card>
          </el-col>
        </el-row>

        </section>

        <!-- ③ 外部集成 -->
        <section id="sec-integrations" class="config-section">

        <el-card
          v-for="(card, idx) in integrationCards"
          :key="card.key"
          :id="card.anchor"
          shadow="never"
          :style="idx > 0 ? 'margin-top: 16px' : ''"
        >
          <template #header>
            <div class="card-head">
              <div class="name-with-badge">
                <el-icon size="15"><component :is="card.icon" /></el-icon>
                <span>{{ card.title }}</span>
                <span class="card-desc">{{ card.description }}</span>
                <el-tag :type="card.config.enabled ? 'success' : 'info'" size="small">
                  {{ card.config.enabled ? '已启用' : '未启用' }}
                </el-tag>
              </div>
              <div class="card-actions">
                <el-button v-if="!cardEditState[card.key]" size="small" @click="startIntegrationEdit(card.key)">编辑</el-button>
                <template v-else>
                  <el-button size="small" @click="cancelIntegrationEdit(card.key)">取消</el-button>
                  <el-button type="primary" size="small" :loading="card.loading" @click="saveIntegrationCard(card)">保存</el-button>
                </template>
              </div>
            </div>
          </template>
          <el-form :model="card.config" label-width="100px">
            <el-form-item label="API 地址">
              <el-input v-model="card.config.apiUrl" :disabled="!cardEditState[card.key]" :placeholder="card.apiUrlPlaceholder" />
            </el-form-item>
            <el-form-item label="API Key">
              <el-input v-model="card.config.apiKey" :disabled="!cardEditState[card.key]" type="password" show-password />
            </el-form-item>
            <el-form-item label="启用状态">
              <el-switch v-model="card.config.enabled" :disabled="!cardEditState[card.key]" />
            </el-form-item>
            <el-form-item v-if="cardEditState[card.key]">
              <el-button :disabled="!card.config.enabled" @click="card.onTest">测试连接</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        </section>

        <!-- ④ 知识库参数 -->
        <section id="sec-kb-params" class="config-section">

        <el-card id="blk-kb-retrieval" shadow="never">
          <template #header>
            <div class="card-head">
              <span>检索参数</span>
              <div class="card-actions">
                <el-button v-if="!cardEditState['kb-retrieval']" size="small" @click="startKbRetrievalEdit">编辑</el-button>
                <template v-else>
                  <el-button size="small" @click="cancelKbRetrievalEdit">取消</el-button>
                  <el-button type="primary" size="small" :loading="kbParamsSaving" :icon="Check" @click="saveKbRetrievalCard">保存</el-button>
                </template>
              </div>
            </div>
          </template>
          <el-form label-position="top">
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="默认 Collection 名称">
                  <el-input v-model="kbParams.defaultCollection" :disabled="!cardEditState['kb-retrieval']" placeholder="orin_knowledge_base" />
                  <p class="form-tip">全局唯一，存储所有知识库向量数据</p>
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="Chunk 最大字符数">
                  <el-input-number v-model="kbParams.chunkSize" :disabled="!cardEditState['kb-retrieval']" :min="100" :max="2000" :step="100" style="width:100%" />
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="Chunk 重叠字符数">
                  <el-input-number v-model="kbParams.chunkOverlap" :disabled="!cardEditState['kb-retrieval']" :min="0" :max="500" :step="50" style="width:100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="默认返回结果数（Top K）">
                  <el-input-number v-model="kbParams.defaultTopK" :disabled="!cardEditState['kb-retrieval']" :min="1" :max="20" style="width:200px" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="相似度阈值">
                  <el-slider v-model="kbParams.similarityThreshold" :disabled="!cardEditState['kb-retrieval']" :min="0" :max="1" :step="0.05"
                    :format-tooltip="v => (v * 100).toFixed(0) + '%'" show-stops style="padding: 0 8px" />
                  <p class="form-tip">仅返回相似度高于此阈值的结果，默认 70%</p>
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </el-card>

        <el-card id="blk-kb-vectors" shadow="never" style="margin-top: 16px">
          <template #header>
            <div class="card-head">
              <span>向量数据详情</span>
              <el-button size="small" :loading="loadingDetail" @click="loadCollectionDetail">刷新</el-button>
            </div>
          </template>
          <div v-if="collectionDetail.exists">
            <el-descriptions :column="3" border size="small">
              <el-descriptions-item label="Collection">{{ collectionDetail.collectionName }}</el-descriptions-item>
              <el-descriptions-item label="向量维度">{{ collectionDetail.dimension }}</el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag :type="collectionDetail.status === 'connected' ? 'success' : 'warning'" size="small">{{ collectionDetail.status }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="向量总数">{{ collectionDetail.totalVectors || 0 }}</el-descriptions-item>
              <el-descriptions-item label="文档总数">{{ collectionDetail.totalDocs || 0 }}</el-descriptions-item>
              <el-descriptions-item label="分区数">{{ collectionDetail.partitionCount || 0 }}</el-descriptions-item>
            </el-descriptions>
            <el-table v-if="collectionDetail.knowledgeBases?.length" :data="collectionDetail.knowledgeBases" size="small" border stripe style="margin-top:16px">
              <el-table-column prop="name" label="知识库名称">
                <template #default="{ row }">
                  <el-button type="primary" link @click="viewKnowledgeBaseVectors(row)">{{ row.name }}</el-button>
                </template>
              </el-table-column>
              <el-table-column prop="docCount" label="文档数" width="100" />
              <el-table-column prop="vectorCount" label="向量数" width="100" />
            </el-table>
          </div>
          <el-empty v-else description="Collection 不存在或未连接" :image-size="60" />
        </el-card>

        </section>

      </div>

      <!-- 右侧目录 -->
      <aside class="toc">
        <p class="toc-title">在此页面</p>
        <ul class="toc-list">
          <li v-for="section in tocSections" :key="section.id" class="toc-group">
            <button
              class="toc-item toc-item-primary"
              :class="{ active: isTocItemActive(section) }"
              @click="scrollToAnchor(section)"
            >
              {{ section.label }}
            </button>
            <ul class="toc-sublist">
              <li v-for="child in section.children" :key="child.id">
                <button
                  class="toc-item toc-item-secondary"
                  :class="{ active: activeAnchor === child.id }"
                  @click="scrollToAnchor(child)"
                >
                  {{ child.label }}
                </button>
              </li>
            </ul>
          </li>
        </ul>
      </aside>
    </div>

    <!-- 向量详情弹窗 -->
    <el-dialog v-model="vectorDialogVisible" :title="`向量详情 — ${vectorDialogData.kb?.name || ''}`" width="900px" destroy-on-close>
      <div v-loading="vectorDialogData.loading">
        <el-alert :title="`共 ${vectorDialogData.totalChunks} 个向量，${vectorDialogData.totalDocs} 个文档`" type="info" :closable="false" show-icon style="margin-bottom:16px" />
        <el-table :data="vectorDialogData.chunks" size="small" border max-height="500">
          <el-table-column prop="fileName" label="文件名" width="180" show-overflow-tooltip />
          <el-table-column prop="chunkIndex" label="索引" width="60" />
          <el-table-column prop="title" label="标题" width="150" show-overflow-tooltip />
          <el-table-column prop="content" label="内容" min-width="250" show-overflow-tooltip />
          <el-table-column prop="charCount" label="字符数" width="80" />
          <el-table-column prop="vectorId" label="Vector ID" width="100" show-overflow-tooltip />
        </el-table>
        <div style="margin-top:16px; text-align:center">
          <el-button v-if="vectorDialogData.chunks.length < vectorDialogData.totalChunks" type="primary" link :loading="vectorDialogData.loading" @click="loadMoreVectors">加载更多</el-button>
          <span v-else-if="vectorDialogData.chunks.length > 0" style="color:#999; font-size:13px">已加载全部</span>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, markRaw } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import request from '@/utils/request';
import OrinPageShell from '@/components/orin/OrinPageShell.vue';
import { Check, Connection, Reading, Share } from '@element-plus/icons-vue';
import { diagnoseMilvus } from '@/api/knowledge';
import {
  getDifyConfig, saveDifyConfig, testDifyConnection,
  getRagflowConfig, saveRagflowConfig, testRagflowConnection,
  getNeo4jConfig, saveNeo4jConfig, testNeo4jConnection
} from '@/api/integrations';

const tocSections = [
  {
    id: 'storage', label: '存储层', anchor: 'sec-storage',
    children: [
      { id: 'storage-mysql',  label: 'MySQL 数据库',  anchor: 'blk-storage-mysql' },
      { id: 'storage-redis',  label: '缓存 Redis',    anchor: 'blk-storage-redis' },
      { id: 'storage-milvus', label: 'Milvus 向量引擎', anchor: 'blk-storage-milvus' },
      { id: 'storage-neo4j',  label: 'Neo4j 图数据库', anchor: 'blk-storage-neo4j' },
    ],
  },
  {
    id: 'ai-services', label: 'AI 服务', anchor: 'sec-ai-services',
    children: [
      { id: 'ai-capabilities',   label: '模型能力配置',      anchor: 'blk-ai-capabilities' },
      { id: 'ai-multimodal',     label: '多模态解析',         anchor: 'blk-ai-multimodal' },
      { id: 'ai-kb-description', label: 'AI 生成知识库描述', anchor: 'blk-ai-kb-description' },
    ],
  },
  {
    id: 'integrations', label: '外部集成', anchor: 'sec-integrations',
    children: [
      { id: 'integration-dify',    label: 'Dify',    anchor: 'blk-integration-dify' },
      { id: 'integration-ragflow', label: 'RAGFlow', anchor: 'blk-integration-ragflow' },
    ],
  },
  {
    id: 'kb-params', label: '知识库参数', anchor: 'sec-kb-params',
    children: [
      { id: 'kb-retrieval', label: '检索参数',     anchor: 'blk-kb-retrieval' },
      { id: 'kb-vectors',   label: '向量数据详情', anchor: 'blk-kb-vectors' },
    ],
  },
];

const tocEntries = tocSections.flatMap(s => [s, ...s.children]);
const activeAnchor = ref('storage');

const scrollToAnchor = (item) => {
  activeAnchor.value = item.id;
  document.getElementById(item.anchor)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
};

const isTocItemActive = (item) => (
  activeAnchor.value === item.id || item.children?.some(c => c.id === activeAnchor.value)
);

const cardEditState = reactive({
  'storage-mysql': false,
  'storage-redis': false,
  'storage-milvus': false,
  'storage-neo4j': false,
  'ai-capabilities': false,
  dify: false,
  ragflow: false,
  'kb-retrieval': false,
});
const cardSnapshots = reactive({});

const cloneState = value => JSON.parse(JSON.stringify(value));

const startCardEdit = (cardId, snapshot) => {
  cardSnapshots[cardId] = cloneState(snapshot);
  cardEditState[cardId] = true;
};

const cancelCardEdit = (cardId, restore) => {
  if (cardSnapshots[cardId] !== undefined)
    restore(cloneState(cardSnapshots[cardId]));
  cardEditState[cardId] = false;
  delete cardSnapshots[cardId];
};

const finishCardEdit = (cardId) => {
  cardEditState[cardId] = false;
  delete cardSnapshots[cardId];
};

// ==================== 存储层 ====================
const dbConfig = ref({});
const dbSaving = ref(false);

const SYSTEM_PROPERTY_FALLBACKS = {
  db: {
    'spring.datasource.url': '',
    'spring.datasource.username': '',
    'spring.datasource.password': '',
    'spring.data.redis.host': '',
    'spring.data.redis.port': '',
    'spring.data.redis.password': '',
  },
  milvus: { host: 'localhost', port: 19530, token: '' },
  ai: { jinaEnabled: false, jinaApiKey: '' },
  embedding: { enableRerank: false, rerankModel: '' },
  kb: {
    defaultCollection: 'orin_knowledge_base',
    chunkSize: 500,
    chunkOverlap: 50,
    defaultTopK: 5,
    similarityThreshold: 0.7,
    ocrProvider: 'local',
    ocrModel: '',
    asrProvider: 'local',
    asrModel: 'base',
  },
};

const loadSystemProperties = async () => {
  try { return await request.get('/monitor/system/properties'); } catch (e) { return null; }
};

const saveSystemProperties = payload => request.post('/monitor/system/properties', payload);

const applySystemProperties = (props = {}) => {
  dbConfig.value = { ...SYSTEM_PROPERTY_FALLBACKS.db, ...props };

  milvusConfig.host = props['milvus.host'] || SYSTEM_PROPERTY_FALLBACKS.milvus.host;
  milvusConfig.port = parseInt(props['milvus.port']) || SYSTEM_PROPERTY_FALLBACKS.milvus.port;
  milvusConfig.token = props['milvus.token'] || SYSTEM_PROPERTY_FALLBACKS.milvus.token;

  aiConfig.jinaEnabled = props['jina.reader.enabled'] === true || props['jina.reader.enabled'] === 'true';
  aiConfig.jinaApiKey = props['jina.reader.api-key'] || SYSTEM_PROPERTY_FALLBACKS.ai.jinaApiKey;

  embeddingConfig.enableRerank = props['knowledge.rerank.enabled'] === 'true';
  embeddingConfig.rerankModel = props['knowledge.rerank.model'] || SYSTEM_PROPERTY_FALLBACKS.embedding.rerankModel;

  kbParams.defaultCollection = props['knowledge.default-collection'] || SYSTEM_PROPERTY_FALLBACKS.kb.defaultCollection;
  kbParams.chunkSize = parseInt(props['knowledge.chunk-size']) || SYSTEM_PROPERTY_FALLBACKS.kb.chunkSize;
  kbParams.chunkOverlap = parseInt(props['knowledge.chunk-overlap']) || SYSTEM_PROPERTY_FALLBACKS.kb.chunkOverlap;
  kbParams.defaultTopK = parseInt(props['knowledge.default-top-k']) || SYSTEM_PROPERTY_FALLBACKS.kb.defaultTopK;
  kbParams.similarityThreshold = parseFloat(props['knowledge.similarity-threshold']) || SYSTEM_PROPERTY_FALLBACKS.kb.similarityThreshold;
  kbParams.ocrProvider = props['knowledge.ocr.provider'] || SYSTEM_PROPERTY_FALLBACKS.kb.ocrProvider;
  kbParams.ocrModel = props['knowledge.ocr.model'] || SYSTEM_PROPERTY_FALLBACKS.kb.ocrModel;
  kbParams.asrProvider = props['knowledge.asr.provider'] || SYSTEM_PROPERTY_FALLBACKS.kb.asrProvider;
  kbParams.asrModel = props['knowledge.asr.model'] || SYSTEM_PROPERTY_FALLBACKS.kb.asrModel;
};

const saveDbConfig = async () => {
  dbSaving.value = true;
  try {
    await saveSystemProperties({
      'spring.datasource.url':      dbConfig.value['spring.datasource.url'] || '',
      'spring.datasource.username': dbConfig.value['spring.datasource.username'] || '',
      'spring.datasource.password': dbConfig.value['spring.datasource.password'] || '',
      'spring.data.redis.host':     dbConfig.value['spring.data.redis.host'] || '',
      'spring.data.redis.port':     dbConfig.value['spring.data.redis.port'] || '',
      'spring.data.redis.password': dbConfig.value['spring.data.redis.password'] || '',
    });
    ElMessage.success('数据库配置已保存，重启服务后生效');
    return true;
  } catch (e) { ElMessage.error('保存失败: ' + e.message); }
  finally { dbSaving.value = false; }
  return false;
};

const MYSQL_DB_KEYS = ['spring.datasource.url', 'spring.datasource.username', 'spring.datasource.password'];
const REDIS_DB_KEYS = ['spring.data.redis.host', 'spring.data.redis.port', 'spring.data.redis.password'];

const pickDbConfig = keys => keys.reduce((result, key) => { result[key] = dbConfig.value[key]; return result; }, {});
const restoreDbConfig = snapshot => { Object.entries(snapshot).forEach(([key, value]) => { dbConfig.value[key] = value; }); };

const startMysqlEdit = () => startCardEdit('storage-mysql', pickDbConfig(MYSQL_DB_KEYS));
const cancelMysqlEdit = () => cancelCardEdit('storage-mysql', restoreDbConfig);
const saveMysqlConfig = async () => { if (await saveDbConfig()) finishCardEdit('storage-mysql'); };

const startRedisEdit = () => startCardEdit('storage-redis', pickDbConfig(REDIS_DB_KEYS));
const cancelRedisEdit = () => cancelCardEdit('storage-redis', restoreDbConfig);
const saveRedisConfig = async () => { if (await saveDbConfig()) finishCardEdit('storage-redis'); };

// Milvus
const milvusConfig = reactive({ host: 'localhost', port: 19530, token: '' });
const milvusStatus = ref({ online: false });
const collectionInfo = ref({ exists: false });
const milvusSaving = ref(false);
const testingMilvus = ref(false);
const loadingCollection = ref(false);
const recreating = ref(false);

const saveMilvusConfig = async () => {
  milvusSaving.value = true;
  try {
    await saveSystemProperties({ 'milvus.host': milvusConfig.host, 'milvus.port': milvusConfig.port.toString(), 'milvus.token': milvusConfig.token });
    ElMessage.success('Milvus 配置已保存');
    return true;
  } catch (e) { ElMessage.error('保存失败: ' + e.message); }
  finally { milvusSaving.value = false; }
  return false;
};

const startMilvusEdit = () => startCardEdit('storage-milvus', milvusConfig);
const cancelMilvusEdit = () => cancelCardEdit('storage-milvus', snapshot => Object.assign(milvusConfig, snapshot));
const saveMilvusCard = async () => { if (await saveMilvusConfig()) finishCardEdit('storage-milvus'); };

const testMilvusConnection = async () => {
  if (testingMilvus.value) return;
  testingMilvus.value = true;
  milvusStatus.value.online = false;
  const t = setTimeout(() => { testingMilvus.value = false; ElMessage.warning('连接超时'); }, 10000);
  try {
    const res = await request.get('/monitor/milvus/test', { params: { host: milvusConfig.host, port: milvusConfig.port, token: milvusConfig.token } });
    clearTimeout(t);
    milvusStatus.value.online = res.online;
    if (res.online) { ElMessage.success('Milvus 连接成功'); await loadCollectionInfo(); }
    else ElMessage.warning('Milvus 连接失败: ' + (res.error || '未知'));
  } catch (e) { clearTimeout(t); milvusStatus.value.online = false; ElMessage.error('测试失败: ' + (e.response?.data?.message || e.message)); }
  finally { testingMilvus.value = false; }
};

const loadCollectionInfo = async () => {
  loadingCollection.value = true;
  try { const res = await request.get('/knowledge/collection/info'); if (res) collectionInfo.value = res; }
  catch (e) { collectionInfo.value = { exists: false }; }
  finally { loadingCollection.value = false; }
};

const recreateCollection = async () => {
  try {
    await ElMessageBox.confirm('此操作将删除现有 Collection 和所有向量数据，是否继续？', '警告', {
      confirmButtonText: '确认重建', cancelButtonText: '取消', type: 'warning'
    });
    recreating.value = true;
    await request.post('/knowledge/collection/recreate');
    ElMessage.success('Collection 重建成功');
    await loadCollectionInfo();
  } catch (e) { if (e !== 'cancel') ElMessage.error('重建失败: ' + e.message); }
  finally { recreating.value = false; }
};

// Neo4j
const neo4jConfig = reactive({
  uri: '', host: 'localhost', port: 7687, username: 'neo4j', password: '',
  database: 'neo4j', maxConnectionPoolSize: 50, connectionAcquisitionTimeoutMs: 60000, enabled: false
});
const neo4jLoading = ref(false);

const loadNeo4jConfig = async () => {
  try {
    const res = await getNeo4jConfig();
    if (res) {
      neo4jConfig.uri = res.uri || '';
      neo4jConfig.host = res.host || 'localhost';
      neo4jConfig.port = Number(res.port) || 7687;
      neo4jConfig.username = res.username || 'neo4j';
      neo4jConfig.password = res.password || '';
      neo4jConfig.database = res.database || 'neo4j';
      neo4jConfig.maxConnectionPoolSize = Number(res.maxConnectionPoolSize) || 50;
      neo4jConfig.connectionAcquisitionTimeoutMs = Number(res.connectionAcquisitionTimeoutMs) || 60000;
      neo4jConfig.enabled = !!res.enabled;
    }
  } catch (e) { /* ignore */ }
};

const handleSaveNeo4jConfig = async () => {
  neo4jLoading.value = true;
  try { await saveNeo4jConfig({ ...neo4jConfig }); ElMessage.success('Neo4j 配置已保存'); return true; }
  catch (e) { ElMessage.error('保存失败: ' + (e.message || e)); }
  finally { neo4jLoading.value = false; }
  return false;
};

const handleTestNeo4jConnection = async () => {
  try {
    const res = await testNeo4jConnection();
    res.success ? ElMessage.success('Neo4j 连接成功') : ElMessage.error(res.message || '连接失败');
  } catch (e) { ElMessage.error('测试失败: ' + (e.message || e)); }
};

const startNeo4jEdit = () => startCardEdit('storage-neo4j', neo4jConfig);
const cancelNeo4jEdit = () => cancelCardEdit('storage-neo4j', snapshot => Object.assign(neo4jConfig, snapshot));
const saveNeo4jCard = async () => { if (await handleSaveNeo4jConfig()) finishCardEdit('storage-neo4j'); };

// ==================== AI 服务 ====================
const aiConfig = reactive({
  siliconFlowApiKey: '',
  siliconFlowEndpoint: 'https://api.siliconflow.cn/v1',
  jinaEnabled: SYSTEM_PROPERTY_FALLBACKS.ai.jinaEnabled,
  jinaApiKey: '',
});
const aiCapabilitySaving = ref(false);

const loadAiConfig = async () => {
  try {
    const mc = await request.get('/model-config');
    if (mc) {
      aiConfig.siliconFlowApiKey  = mc.siliconFlowApiKey || '';
      aiConfig.siliconFlowEndpoint = mc.siliconFlowEndpoint || 'https://api.siliconflow.cn/v1';
    }
  } catch (e) { /* ignore */ }
};

const persistExternalAiConfig = () => Promise.all([
  saveSystemProperties({ 'jina.reader.enabled': aiConfig.jinaEnabled, 'jina.reader.api-key': aiConfig.jinaApiKey }),
  request.put('/model-config', { siliconFlowApiKey: aiConfig.siliconFlowApiKey, siliconFlowEndpoint: aiConfig.siliconFlowEndpoint }),
]);

const embeddingConfig = reactive({
  provider: 'SiliconFlow',
  model: 'Qwen/Qwen3-Embedding-8B',
  apiKeyId: '',
  enableRerank: SYSTEM_PROPERTY_FALLBACKS.embedding.enableRerank,
  rerankModel: '',
  descGenerationModel: '',
});
const testingEmbedding = ref(false);
const embeddingModels = ref([]);
const rerankModels = ref([]);
const apiKeys = ref([]);
const loadingModels = ref(false);

const embeddingProviders = [
  { value: 'SiliconFlow', label: 'SiliconFlow' },
  { value: 'Ollama', label: 'Ollama（本地）' },
];

const loadEmbeddingConfig = async () => {
  try {
    const mc = await request.get('/model-config');
    if (mc) {
      embeddingConfig.provider = mc.embeddingProvider || 'SiliconFlow';
      embeddingConfig.model = mc.embeddingModel || 'Qwen/Qwen3-Embedding-8B';
      embeddingConfig.apiKeyId = mc.embeddingApiKeyId || '';
      embeddingConfig.descGenerationModel = mc.descGenerationModel || '';
    }
  } catch (e) { /* ignore */ }
};

const persistEmbeddingConfig = () => Promise.all([
  request.put('/model-config', {
    embeddingProvider:   embeddingConfig.provider,
    embeddingModel:      embeddingConfig.model,
    embeddingApiKeyId:   embeddingConfig.apiKeyId || null,
    descGenerationModel: embeddingConfig.descGenerationModel,
  }),
  saveSystemProperties({
    'knowledge.rerank.enabled': embeddingConfig.enableRerank.toString(),
    'knowledge.rerank.model':   embeddingConfig.rerankModel || '',
  }),
]);

const persistMultimodalConfig = () => saveSystemProperties({
  'knowledge.ocr.provider': kbParams.ocrProvider,
  'knowledge.ocr.model': kbParams.ocrModel,
  'knowledge.asr.provider': kbParams.asrProvider,
  'knowledge.asr.model': kbParams.asrModel,
});

const saveAiCapabilityConfig = async () => {
  aiCapabilitySaving.value = true;
  try {
    await Promise.all([persistExternalAiConfig(), persistEmbeddingConfig(), persistMultimodalConfig()]);
    ElMessage.success('AI 能力配置已保存');
    return true;
  } catch (e) { ElMessage.error('保存失败: ' + e.message); }
  finally { aiCapabilitySaving.value = false; }
  return false;
};

const getAiCapabilitySnapshot = () => ({
  aiConfig: cloneState(aiConfig),
  embeddingConfig: cloneState(embeddingConfig),
  kbParams: { ocrProvider: kbParams.ocrProvider, ocrModel: kbParams.ocrModel, asrProvider: kbParams.asrProvider, asrModel: kbParams.asrModel },
  selectedKB: selectedKB.value,
  selectedKBModel: selectedKBModel.value,
});

const restoreAiCapabilitySnapshot = (snapshot) => {
  Object.assign(aiConfig, snapshot.aiConfig);
  Object.assign(embeddingConfig, snapshot.embeddingConfig);
  Object.assign(kbParams, snapshot.kbParams);
  selectedKB.value = snapshot.selectedKB;
  selectedKBModel.value = snapshot.selectedKBModel;
};

const startAiCapabilitiesEdit = () => startCardEdit('ai-capabilities', getAiCapabilitySnapshot());
const cancelAiCapabilitiesEdit = () => cancelCardEdit('ai-capabilities', restoreAiCapabilitySnapshot);
const saveAiCapabilitiesCard = async () => { if (await saveAiCapabilityConfig()) finishCardEdit('ai-capabilities'); };

const onProviderChange = () => { embeddingConfig.model = ''; };

const loadApiKeys = async () => {
  try { const res = await request.get('/api-keys/external'); apiKeys.value = res || []; } catch (e) { /* ignore */ }
};

const loadEmbeddingModels = async () => {
  if (loadingModels.value) return;
  loadingModels.value = true;
  try {
    const res = await request.get('/models');
    if (res) embeddingModels.value = res.filter(m => m.type === 'EMBEDDING').map(m => ({ id: m.modelName || m.name || m.modelId }));
  } catch (e) { /* ignore */ }
  finally { loadingModels.value = false; }
};

const loadRerankModels = async () => {
  if (rerankModels.value.length) return;
  try {
    const res = await request.get('/models');
    if (res) {
      const filtered = res.filter(m => ['RERANK', 'RERANKER'].includes(m.type?.toUpperCase()));
      rerankModels.value = filtered.length
        ? filtered.map(m => ({ id: m.modelId || m.name, name: m.name || m.modelId }))
        : [{ id: 'BAAI/bge-reranker-v2-m3', name: 'BAAI/bge-reranker-v2-m3' }, { id: 'BAAI/bge-reranker-base', name: 'BAAI/bge-reranker-base' }];
    }
  } catch (e) { /* ignore */ }
};

const testEmbeddingConnection = async () => {
  testingEmbedding.value = true;
  try {
    const res = await diagnoseMilvus();
    if (res?.embedding?.status === 'ok') ElMessage.success('Embedding 可用，维度: ' + res.embedding.dimension);
    else if (res?.embedding?.status === 'error') ElMessage.error('Embedding 失败: ' + res.embedding.error);
    else ElMessage.warning('诊断结果未知');
  } catch (e) { ElMessage.error('测试失败: ' + e.message); }
  finally { testingEmbedding.value = false; }
};

// ==================== 外部集成 ====================
const difyConfig = reactive({ apiUrl: '', apiKey: '', enabled: false });
const difyLoading = ref(false);

const loadDifyConfig = async () => {
  try { const res = await getDifyConfig(); if (res) Object.assign(difyConfig, res); } catch (e) { /* ignore */ }
};

const handleSaveDifyConfig = async () => {
  difyLoading.value = true;
  try { await saveDifyConfig(difyConfig); ElMessage.success('Dify 配置已保存'); return true; }
  catch (e) { ElMessage.error('保存失败: ' + (e.message || e)); }
  finally { difyLoading.value = false; }
  return false;
};

const handleTestDifyConnection = async () => {
  try {
    const res = await testDifyConnection();
    res.success ? ElMessage.success('Dify 连接成功') : ElMessage.error(res.message || '连接失败');
  } catch (e) { ElMessage.error('测试失败: ' + (e.message || e)); }
};

const ragflowConfig = reactive({ apiUrl: '', apiKey: '', enabled: false });
const ragflowLoading = ref(false);

const loadRagflowConfig = async () => {
  try { const res = await getRagflowConfig(); if (res) Object.assign(ragflowConfig, res); } catch (e) { /* ignore */ }
};

const handleSaveRagflowConfig = async () => {
  ragflowLoading.value = true;
  try { await saveRagflowConfig(ragflowConfig); ElMessage.success('RAGFlow 配置已保存'); return true; }
  catch (e) { ElMessage.error('保存失败: ' + (e.message || e)); }
  finally { ragflowLoading.value = false; }
  return false;
};

const handleTestRagflowConnection = async () => {
  try {
    const res = await testRagflowConnection();
    res.success ? ElMessage.success('RAGFlow 连接成功') : ElMessage.error(res.message || '连接失败');
  } catch (e) { ElMessage.error('测试失败: ' + (e.message || e)); }
};

const integrationCards = [
  { key: 'dify', anchor: 'blk-integration-dify', title: 'Dify', description: '工作流与应用集成', icon: markRaw(Connection), config: difyConfig, loading: difyLoading, onSave: handleSaveDifyConfig, onTest: handleTestDifyConnection, apiUrlPlaceholder: 'https://api.dify.ai/v1' },
  { key: 'ragflow', anchor: 'blk-integration-ragflow', title: 'RAGFlow', description: '知识库检索增强', icon: markRaw(Reading), config: ragflowConfig, loading: ragflowLoading, onSave: handleSaveRagflowConfig, onTest: handleTestRagflowConnection, apiUrlPlaceholder: 'https://ragflow.example.com/api/v1' },
];

const getIntegrationCard = key => integrationCards.find(card => card.key === key);
const startIntegrationEdit = (key) => { const card = getIntegrationCard(key); if (card) startCardEdit(key, card.config); };
const cancelIntegrationEdit = (key) => { const card = getIntegrationCard(key); if (card) cancelCardEdit(key, snapshot => Object.assign(card.config, snapshot)); };
const saveIntegrationCard = async (card) => { if (await card.onSave()) finishCardEdit(card.key); };

// ==================== 知识库参数 ====================
const kbParams = reactive({
  defaultCollection: SYSTEM_PROPERTY_FALLBACKS.kb.defaultCollection,
  chunkSize: SYSTEM_PROPERTY_FALLBACKS.kb.chunkSize,
  chunkOverlap: SYSTEM_PROPERTY_FALLBACKS.kb.chunkOverlap,
  defaultTopK: SYSTEM_PROPERTY_FALLBACKS.kb.defaultTopK,
  similarityThreshold: SYSTEM_PROPERTY_FALLBACKS.kb.similarityThreshold,
  ocrProvider: SYSTEM_PROPERTY_FALLBACKS.kb.ocrProvider,
  ocrModel: SYSTEM_PROPERTY_FALLBACKS.kb.ocrModel,
  asrProvider: SYSTEM_PROPERTY_FALLBACKS.kb.asrProvider,
  asrModel: SYSTEM_PROPERTY_FALLBACKS.kb.asrModel,
});
const kbParamsSaving = ref(false);

const kbStats = [
  { label: '知识库总数', key: 'totalKBs' },
  { label: '文档总数',   key: 'totalDocs' },
  { label: '向量总数',   key: 'totalVectors' },
];
const knowledgeStats = ref({ totalKBs: '—', totalDocs: '—', totalVectors: '—' });
const loadingStats = ref(false);
const knowledgeBases = ref([]);
const kbModels = ref([]);
const selectedKB = ref(null);
const selectedKBModel = ref('');
const generatingDesc = ref(false);

const collectionDetail = ref({ exists: false, knowledgeBases: [], partitions: [] });
const loadingDetail = ref(false);
const vectorDialogVisible = ref(false);
const vectorDialogData = ref({ kb: null, chunks: [], totalChunks: 0, totalDocs: 0, page: 0, size: 20, loading: false });

const saveKbParams = async () => {
  kbParamsSaving.value = true;
  try {
    await saveSystemProperties({
      'knowledge.default-collection':   kbParams.defaultCollection,
      'knowledge.chunk-size':           kbParams.chunkSize.toString(),
      'knowledge.chunk-overlap':        kbParams.chunkOverlap.toString(),
      'knowledge.default-top-k':        kbParams.defaultTopK.toString(),
      'knowledge.similarity-threshold': kbParams.similarityThreshold.toString(),
    });
    ElMessage.success('知识库参数已保存');
    return true;
  } catch (e) { ElMessage.error('保存失败: ' + e.message); }
  finally { kbParamsSaving.value = false; }
  return false;
};

const getKbRetrievalSnapshot = () => ({
  defaultCollection: kbParams.defaultCollection,
  chunkSize: kbParams.chunkSize,
  chunkOverlap: kbParams.chunkOverlap,
  defaultTopK: kbParams.defaultTopK,
  similarityThreshold: kbParams.similarityThreshold,
});

const startKbRetrievalEdit = () => startCardEdit('kb-retrieval', getKbRetrievalSnapshot());
const cancelKbRetrievalEdit = () => cancelCardEdit('kb-retrieval', snapshot => Object.assign(kbParams, snapshot));
const saveKbRetrievalCard = async () => { if (await saveKbParams()) finishCardEdit('kb-retrieval'); };

const loadCollectionDetail = async () => {
  loadingDetail.value = true;
  try { const res = await request.get('/knowledge/collection/detail'); if (res) collectionDetail.value = res; }
  catch (e) { collectionDetail.value = { exists: false }; }
  finally { loadingDetail.value = false; }
};

const loadKnowledgeStats = async () => {
  loadingStats.value = true;
  try {
    const res = await diagnoseMilvus();
    if (res?.documents) knowledgeStats.value = { totalKBs: 'N/A', totalDocs: res.documents.total, totalVectors: res.collection?.vectorCount || 'N/A' };
    else knowledgeStats.value = { totalKBs: '—', totalDocs: '—', totalVectors: '—' };
  } catch (e) { knowledgeStats.value = { totalKBs: '—', totalDocs: '—', totalVectors: '—' }; }
  finally { loadingStats.value = false; }
};

const loadKnowledgeBases = async () => {
  try { const res = await request.get('/knowledge/list'); knowledgeBases.value = res || []; } catch (e) { /* ignore */ }
};

const loadKBModels = async () => {
  try {
    const res = await request.get('/models');
    if (res) {
      kbModels.value = res
        .filter(m => ['CHAT', 'LLM', 'chat', 'llm'].includes(m.type) || !m.type)
        .map(m => ({ id: m.modelId || m.modelName || m.name, name: m.name || m.modelName || m.modelId, provider: m.provider || '' }));
      if (kbModels.value.length && !selectedKBModel.value) selectedKBModel.value = kbModels.value[0].id;
    }
  } catch (e) { /* ignore */ }
};

const generateKBDescription = async () => {
  if (!selectedKB.value || !selectedKBModel.value) return;
  generatingDesc.value = true;
  try {
    const res = await request.post(`/knowledge/${selectedKB.value}/generate-description`, { model: selectedKBModel.value });
    if (res.title || res.description) {
      await request.put(`/knowledge/${selectedKB.value}`, {
        name: res.title || knowledgeBases.value.find(kb => kb.kbId === selectedKB.value)?.name,
        description: res.description || ''
      });
      ElMessage.success('描述生成成功');
      await loadKnowledgeBases();
    } else ElMessage.warning('AI 未返回内容');
  } catch (e) { ElMessage.error('生成失败: ' + (e.message || '未知')); }
  finally { generatingDesc.value = false; }
};

const viewKnowledgeBaseVectors = async (kb) => {
  vectorDialogData.value = { kb, chunks: [], totalChunks: 0, totalDocs: 0, page: 0, size: 20, loading: true };
  vectorDialogVisible.value = true;
  try {
    const res = await request.get(`/knowledge/kb/${kb.id}/vectors`, { params: { page: 0, size: 20 } });
    vectorDialogData.value.chunks = res.chunks || [];
    vectorDialogData.value.totalChunks = res.totalChunks || 0;
    vectorDialogData.value.totalDocs = res.totalDocs || 0;
  } catch (e) { ElMessage.error('加载向量详情失败: ' + e.message); }
  finally { vectorDialogData.value.loading = false; }
};

const loadMoreVectors = async () => {
  if (vectorDialogData.value.loading) return;
  vectorDialogData.value.loading = true;
  try {
    const nextPage = vectorDialogData.value.page + 1;
    const res = await request.get(`/knowledge/kb/${vectorDialogData.value.kb.id}/vectors`, { params: { page: nextPage, size: vectorDialogData.value.size } });
    vectorDialogData.value.chunks = [...vectorDialogData.value.chunks, ...(res.chunks || [])];
    vectorDialogData.value.page = nextPage;
  } catch (e) { ElMessage.error('加载更多失败: ' + e.message); }
  finally { vectorDialogData.value.loading = false; }
};

onMounted(async () => {
  const [systemProperties] = await Promise.all([
    loadSystemProperties(),
    loadNeo4jConfig(),
    loadAiConfig(),
    loadEmbeddingConfig(),
    loadDifyConfig(),
    loadRagflowConfig(),
    loadKnowledgeBases(),
    loadKBModels(),
  ]);
  if (systemProperties) applySystemProperties(systemProperties);
  setTimeout(() => {
    testMilvusConnection();
    loadKnowledgeStats();
    setupObserver();
  }, 400);
});

onUnmounted(() => observer?.disconnect());

let observer = null;

const setupObserver = () => {
  observer = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        if (!entry.isIntersecting) continue;
        const matched = tocEntries.find(item => item.anchor === entry.target.id);
        if (matched) activeAnchor.value = matched.id;
      }
    },
    { threshold: 0.2, rootMargin: '-10% 0px -65% 0px' }
  );
  tocEntries.forEach(({ anchor }) => {
    const el = document.getElementById(anchor);
    if (el) observer.observe(el);
  });
};
</script>

<style scoped>
.page-container {
  padding: 0;
  animation: fadeIn 0.3s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(4px); }
  to   { opacity: 1; transform: translateY(0); }
}

.layout {
  display: flex;
  gap: 32px;
  align-items: flex-start;
  margin-top: 20px;
}

.main-content {
  flex: 1;
  min-width: 0;
}

.config-section {
  margin-bottom: 32px;
  scroll-margin-top: 12px;
}

/* 右侧目录 */
.toc {
  width: 160px;
  flex-shrink: 0;
  position: sticky;
  top: 0;
}

.toc-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--neutral-gray-400);
  margin: 0 0 10px;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.toc-list {
  list-style: none;
  padding: 0;
  margin: 0;
  border-left: 2px solid var(--el-border-color-lighter);
}

.toc-group { list-style: none; }

.toc-item {
  width: 100%;
  padding: 5px 0 5px 14px;
  border: 0;
  background: transparent;
  text-align: left;
  font-size: 13px;
  color: var(--neutral-gray-500);
  cursor: pointer;
  transition: color 0.15s;
  line-height: 1.4;
}

.toc-item:hover { color: var(--neutral-gray-700); }
.toc-item-primary { font-weight: 600; }

.toc-sublist {
  list-style: none;
  padding: 0 0 4px;
  margin: 2px 0 6px;
}

.toc-item-secondary {
  padding-left: 28px;
  font-size: 12px;
  color: var(--neutral-gray-400);
}

.toc-item.active {
  color: var(--el-color-primary);
  font-weight: 600;
  border-left: 2px solid var(--el-color-primary);
  margin-left: -2px;
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  font-size: 14px;
  color: var(--neutral-gray-800);
}

.card-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--neutral-gray-800);
}

.card-subtitle {
  margin-top: 2px;
  font-size: 12px;
  font-weight: 400;
  color: var(--neutral-gray-500);
  line-height: 1.5;
}

.card-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.name-with-badge {
  display: flex;
  align-items: center;
  gap: 8px;
}

.card-desc {
  font-size: 12px;
  font-weight: 400;
  color: var(--neutral-gray-500);
}

.section-tip {
  font-size: 12px;
  color: var(--neutral-gray-500);
  margin: -8px 0 12px;
  line-height: 1.5;
}

.form-tip {
  font-size: 12px;
  color: var(--neutral-gray-500);
  margin: 5px 0 0;
  line-height: 1.5;
}

.stats-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.stat-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  background: var(--neutral-gray-50);
  border-radius: 8px;
}

.stat-label { font-size: 13px; color: var(--neutral-gray-600); }
.stat-value { font-weight: 700; font-size: 16px; color: var(--el-color-primary); }
</style>
