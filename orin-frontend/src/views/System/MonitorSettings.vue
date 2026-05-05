<template>
  <div class="page-container">
    <OrinEntityHeader
      domain="系统设置"
      title="环境配置"
      description="维护数据库、缓存、队列、向量引擎与知识服务等运行环境参数"
      :summary="systemHeaderSummary"
    />

    <div class="layout">
      <div class="main-content">

        <!-- ① 存储层 -->
        <section id="sec-storage" class="config-section">

        <OrinArcoConfigSection id="blk-storage-mysql">
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
        </OrinArcoConfigSection>

        <OrinArcoConfigSection id="blk-storage-redis" style="margin-top: 16px">
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
        </OrinArcoConfigSection>

        <OrinArcoConfigSection id="blk-storage-rabbitmq" style="margin-top: 16px">
          <template #header>
            <div class="card-head">
              <span>RabbitMQ 队列</span>
              <div class="card-actions">
                <el-button v-if="!cardEditState['storage-rabbitmq']" size="small" @click="startRabbitmqEdit">编辑</el-button>
                <template v-else>
                  <el-button size="small" @click="cancelRabbitmqEdit">取消</el-button>
                  <el-button type="primary" size="small" :loading="dbSaving" :icon="Check" @click="saveRabbitmqConfig">保存</el-button>
                </template>
              </div>
            </div>
          </template>
          <el-form label-position="left" label-width="180px">
            <el-form-item label="Host">
              <el-input v-model="dbConfig['spring.rabbitmq.host']" :disabled="!cardEditState['storage-rabbitmq']" />
            </el-form-item>
            <el-form-item label="Port">
              <el-input v-model="dbConfig['spring.rabbitmq.port']" :disabled="!cardEditState['storage-rabbitmq']" />
            </el-form-item>
            <el-form-item label="Username">
              <el-input v-model="dbConfig['spring.rabbitmq.username']" :disabled="!cardEditState['storage-rabbitmq']" />
            </el-form-item>
            <el-form-item label="Password">
              <el-input v-model="dbConfig['spring.rabbitmq.password']" :disabled="!cardEditState['storage-rabbitmq']" type="password" show-password />
            </el-form-item>
            <el-form-item label="Virtual Host">
              <el-input v-model="dbConfig['spring.rabbitmq.virtual-host']" :disabled="!cardEditState['storage-rabbitmq']" />
            </el-form-item>
          </el-form>
        </OrinArcoConfigSection>

        <OrinArcoConfigSection id="blk-collaboration-orchestration" style="margin-top: 16px">
          <template #header>
            <div class="card-head">
              <span>协作编舞（LangGraph + MQ）</span>
              <div class="card-actions">
                <el-button v-if="!cardEditState['collaboration-orchestration']" size="small" @click="startCollabOrchestrationEdit">编辑</el-button>
                <template v-else>
                  <el-button size="small" @click="cancelCollabOrchestrationEdit">取消</el-button>
                  <el-button type="primary" size="small" :loading="dbSaving" :icon="Check" @click="saveCollabOrchestrationConfig">保存</el-button>
                </template>
              </div>
            </div>
          </template>
          <el-form label-position="left" label-width="220px">
            <el-form-item label="编舞模式">
              <el-select v-model="dbConfig['orin.collaboration.mode']" :disabled="!cardEditState['collaboration-orchestration']" style="width: 100%">
                <el-option label="LANGGRAPH_MQ（分布式）" value="LANGGRAPH_MQ" />
                <el-option label="JAVA_NATIVE（本地编排）" value="JAVA_NATIVE" />
              </el-select>
            </el-form-item>
            <el-form-item label="PARALLEL 使用 MQ">
              <el-switch
                v-model="dbConfig['orin.collaboration.mq-for-parallel']"
                :disabled="!cardEditState['collaboration-orchestration']"
                active-value="true"
                inactive-value="false"
              />
            </el-form-item>
            <el-form-item label="SEQUENTIAL 使用 MQ">
              <el-switch
                v-model="dbConfig['orin.collaboration.mq-for-sequential']"
                :disabled="!cardEditState['collaboration-orchestration']"
                active-value="true"
                inactive-value="false"
              />
            </el-form-item>
            <el-form-item label="CONSENSUS 使用 MQ">
              <el-switch
                v-model="dbConfig['orin.collaboration.mq-for-consensus']"
                :disabled="!cardEditState['collaboration-orchestration']"
                active-value="true"
                inactive-value="false"
              />
            </el-form-item>
          </el-form>
        </OrinArcoConfigSection>

        <OrinArcoConfigSection id="blk-storage-milvus" style="margin-top: 16px">
          <template #header>
            <div class="card-head">
              <span>Milvus 向量引擎</span>
              <div class="card-actions card-actions-right">
                <el-tag :type="milvusStatus.online ? 'success' : 'danger'" size="small">
                  {{ milvusStatus.online ? '已连接' : '未连接' }}
                </el-tag>
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
          <el-divider style="margin: 16px 0 12px" />
          <div class="card-head">
            <span>向量数据详情</span>
            <el-button size="small" :loading="loadingDetail" @click="loadCollectionDetail">刷新</el-button>
          </div>
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
        </OrinArcoConfigSection>

        <OrinArcoConfigSection id="blk-storage-neo4j" style="margin-top: 16px">
          <template #header>
            <div class="card-head">
              <div class="card-head-main">
                <el-icon size="15"><Share /></el-icon>
                <span>Neo4j 图数据库</span>
                <span class="card-desc">Graph / RAG 知识图谱</span>
              </div>
              <div class="card-actions card-actions-right">
                <el-tag :type="neo4jConfig.enabled ? 'success' : 'info'" size="small">
                  {{ neo4jConfig.enabled ? '已启用' : '未启用' }}
                </el-tag>
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
        </OrinArcoConfigSection>

        <OrinArcoConfigSection id="blk-storage-minio" style="margin-top: 16px">
          <template #header>
            <div class="card-head">
              <div class="card-head-main">
                <span>MinIO 对象存储</span>
              </div>
              <div class="card-actions card-actions-right">
                <el-tag :type="minioStatus.up ? 'success' : 'danger'" size="small">
                  {{ minioStatus.up ? '已连接' : '未连接' }}
                </el-tag>
                <el-button v-if="!cardEditState['storage-minio']" size="small" @click="startMinioEdit">编辑</el-button>
                <template v-else>
                  <el-button size="small" @click="cancelMinioEdit">取消</el-button>
                  <el-button type="primary" size="small" :loading="minioSaving" :icon="Check" @click="saveMinioCard">保存</el-button>
                </template>
              </div>
            </div>
          </template>
          <el-form label-position="top">
            <el-row :gutter="16">
              <el-col :span="8">
                <el-form-item label="存储模式">
                  <el-select v-model="minioConfig.mode" :disabled="!cardEditState['storage-minio']" style="width:100%">
                    <el-option value="single" label="single（单存储）" />
                    <el-option value="dual" label="dual（双线）" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="主存储">
                  <el-select v-model="minioConfig.primary" :disabled="!cardEditState['storage-minio']" style="width:100%">
                    <el-option value="local" label="local" />
                    <el-option value="minio" label="minio" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="次存储">
                  <el-select v-model="minioConfig.secondary" :disabled="!cardEditState['storage-minio']" style="width:100%">
                    <el-option value="local" label="local" />
                    <el-option value="minio" label="minio" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>

            <el-row :gutter="16">
              <el-col :span="8">
                <el-form-item label="读回退">
                  <el-switch v-model="minioConfig.readFallback" :disabled="!cardEditState['storage-minio']" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="异步补偿">
                  <el-switch v-model="minioConfig.writeAsyncRepair" :disabled="!cardEditState['storage-minio']" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="预签名 TTL（秒）">
                  <el-input-number v-model="minioConfig.presignTtlSeconds" :disabled="!cardEditState['storage-minio']" :min="60" :max="604800" style="width:100%" />
                </el-form-item>
              </el-col>
            </el-row>

            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="Endpoint">
                  <el-input v-model="minioConfig.endpoint" :disabled="!cardEditState['storage-minio']" placeholder="http://192.168.1.164:9000" />
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="Bucket">
                  <el-input v-model="minioConfig.bucket" :disabled="!cardEditState['storage-minio']" placeholder="orin-files" />
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="HTTPS">
                  <el-switch v-model="minioConfig.secure" :disabled="!cardEditState['storage-minio']" />
                </el-form-item>
              </el-col>
            </el-row>

            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="Access Key">
                  <el-input v-model="minioConfig.accessKey" :disabled="!cardEditState['storage-minio']" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="Secret Key">
                  <el-input v-model="minioConfig.secretKey" :disabled="!cardEditState['storage-minio']" type="password" show-password />
                </el-form-item>
              </el-col>
            </el-row>

            <el-form-item>
              <el-button type="success" plain :disabled="!cardEditState['storage-minio']" :loading="testingMinio" @click="testMinioConnection">
                测试连接
              </el-button>
            </el-form-item>
          </el-form>
        </OrinArcoConfigSection>

        </section>

        <!-- ② AI 服务 -->
        <section id="sec-ai-services" class="config-section">

        <OrinArcoConfigSection id="blk-ai-capabilities">
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
          <div id="blk-ai-multimodal"></div>
          <el-divider content-position="left">多模态解析</el-divider>
          <p class="section-tip">直接选择 OCR / ASR 使用的模型，统一由当前推理链路执行。</p>
          <el-form label-position="top">
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="OCR 模型">
                  <el-select
                    v-model="kbParams.ocrModel"
                    :disabled="!cardEditState['ai-capabilities']"
                    style="width:100%"
                    filterable
                    allow-create
                    default-first-option
                    placeholder="选择 OCR 模型"
                  >
                    <el-option v-for="model in ocrModelOptions" :key="model.id" :label="model.name" :value="model.id" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="ASR 模型">
                  <el-select v-model="kbParams.asrModel" :disabled="!cardEditState['ai-capabilities']" style="width:100%" placeholder="选择 ASR 模型">
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

          <div id="blk-ai-kb-description"></div>
          <el-divider content-position="left">AI 生成知识库描述</el-divider>
          <p class="section-tip">根据知识库文档内容自动生成名称和描述。</p>
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
                <el-form-item label="选择描述生成模型">
                  <el-select v-model="selectedKBModel" :disabled="!cardEditState['ai-capabilities']" placeholder="选择描述生成模型" style="width:100%" filterable>
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
        </OrinArcoConfigSection>

        </section>

        <!-- ③ 外部集成 -->
        <section id="sec-integrations" class="config-section">

        <OrinArcoConfigSection id="blk-integration-jina">
          <template #header>
            <div class="card-head">
              <div class="card-head-main">
                <el-icon size="15"><Connection /></el-icon>
                <span>Jina Reader</span>
                <span class="card-desc">网页 URL 转 Markdown 解析</span>
              </div>
              <div class="card-actions card-actions-right">
                <el-tag :type="aiConfig.jinaEnabled ? 'success' : 'info'" size="small">
                  {{ aiConfig.jinaEnabled ? '已启用' : '未启用' }}
                </el-tag>
                <el-button v-if="!cardEditState['jina-reader']" size="small" @click="startJinaReaderEdit">编辑</el-button>
                <template v-else>
                  <el-button size="small" @click="cancelJinaReaderEdit">取消</el-button>
                  <el-button type="primary" size="small" :loading="jinaSaving" @click="saveJinaReaderCard">保存</el-button>
                </template>
              </div>
            </div>
          </template>
          <el-form label-width="100px">
            <el-form-item label="启用状态">
              <el-switch v-model="aiConfig.jinaEnabled" :disabled="!cardEditState['jina-reader']" />
            </el-form-item>
            <el-form-item label="API Key">
              <el-input
                v-model="aiConfig.jinaApiKey"
                :disabled="!cardEditState['jina-reader']"
                type="password"
                show-password
                placeholder="可选，无 Key 时 20 次/分钟"
              />
            </el-form-item>
          </el-form>
        </OrinArcoConfigSection>

        <OrinArcoConfigSection
          v-for="(card, idx) in integrationCards"
          :key="card.key"
          :id="card.anchor"
          shadow="never"
          :style="idx > 0 ? 'margin-top: 16px' : ''"
        >
          <template #header>
            <div class="card-head">
              <div class="card-head-main">
                <el-icon size="15"><component :is="card.icon" /></el-icon>
                <span>{{ card.title }}</span>
                <span class="card-desc">{{ card.description }}</span>
              </div>
              <div class="card-actions card-actions-right">
                <el-tag :type="card.config.enabled ? 'success' : 'info'" size="small">
                  {{ card.config.enabled ? '已启用' : '未启用' }}
                </el-tag>
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
        </OrinArcoConfigSection>

        </section>

        <!-- ④ 知识库参数 -->
        <section id="sec-kb-params" class="config-section">

        <OrinArcoConfigSection id="blk-kb-retrieval">
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
        </OrinArcoConfigSection>

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
import OrinEntityHeader from '@/components/orin/OrinEntityHeader.vue';
import OrinArcoConfigSection from '@/ui/arco/OrinArcoConfigSection.vue';
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
      { id: 'storage-rabbitmq', label: 'RabbitMQ 队列', anchor: 'blk-storage-rabbitmq' },
      { id: 'collaboration-orchestration', label: '协作编舞', anchor: 'blk-collaboration-orchestration' },
      { id: 'storage-milvus', label: 'Milvus 向量引擎', anchor: 'blk-storage-milvus' },
      { id: 'storage-neo4j',  label: 'Neo4j 图数据库', anchor: 'blk-storage-neo4j' },
      { id: 'storage-minio',  label: 'MinIO 对象存储', anchor: 'blk-storage-minio' },
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
      { id: 'integration-jina',    label: 'Jina Reader', anchor: 'blk-integration-jina' },
      { id: 'integration-dify',    label: 'Dify',    anchor: 'blk-integration-dify' },
      { id: 'integration-ragflow', label: 'RAGFlow', anchor: 'blk-integration-ragflow' },
    ],
  },
  {
    id: 'kb-params', label: '知识库参数', anchor: 'sec-kb-params',
    children: [
      { id: 'kb-retrieval', label: '检索参数',     anchor: 'blk-kb-retrieval' },
    ],
  },
];

const systemHeaderSummary = [
  { label: '存储层', value: '7 项' },
  { label: 'AI 能力', value: '1 组' },
  { label: '外部集成', value: '3 项' },
  { label: '检索策略', value: '1 组' }
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
  'storage-rabbitmq': false,
  'collaboration-orchestration': false,
  'storage-milvus': false,
  'storage-neo4j': false,
  'storage-minio': false,
  'ai-capabilities': false,
  'jina-reader': false,
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
    'spring.rabbitmq.host': 'localhost',
    'spring.rabbitmq.port': '5672',
    'spring.rabbitmq.username': 'guest',
    'spring.rabbitmq.password': 'guest',
    'spring.rabbitmq.virtual-host': '/',
    'orin.collaboration.mode': 'LANGGRAPH_MQ',
    'orin.collaboration.mq-for-parallel': 'true',
    'orin.collaboration.mq-for-sequential': 'true',
    'orin.collaboration.mq-for-consensus': 'true',
  },
  milvus: { host: '192.168.1.164', port: 19530, token: '' },
  ai: { jinaEnabled: false, jinaApiKey: '' },
  embedding: { enableRerank: false, rerankModel: '' },
  kb: {
    defaultCollection: 'orin_knowledge_base',
    chunkSize: 500,
    chunkOverlap: 50,
    defaultTopK: 5,
    similarityThreshold: 0.7,
    ocrProvider: 'local',
    ocrModel: 'tesseract',
    asrProvider: 'local',
    asrModel: 'base',
  },
  minio: {
    mode: 'dual',
    primary: 'local',
    secondary: 'minio',
    readFallback: true,
    writeAsyncRepair: true,
    presignTtlSeconds: 600,
    endpoint: 'http://localhost:9000',
    accessKey: '',
    secretKey: '',
    bucket: 'orin-files',
    secure: false,
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

  minioConfig.mode = props['storage.mode'] || SYSTEM_PROPERTY_FALLBACKS.minio.mode;
  minioConfig.primary = props['storage.primary'] || SYSTEM_PROPERTY_FALLBACKS.minio.primary;
  minioConfig.secondary = props['storage.secondary'] || SYSTEM_PROPERTY_FALLBACKS.minio.secondary;
  minioConfig.readFallback = props['storage.read-fallback'] === true || props['storage.read-fallback'] === 'true';
  minioConfig.writeAsyncRepair = props['storage.write-async-repair'] === true || props['storage.write-async-repair'] === 'true';
  minioConfig.presignTtlSeconds = parseInt(props['storage.presign.ttl-seconds']) || SYSTEM_PROPERTY_FALLBACKS.minio.presignTtlSeconds;
  minioConfig.endpoint = props['storage.minio.endpoint'] || SYSTEM_PROPERTY_FALLBACKS.minio.endpoint;
  minioConfig.accessKey = props['storage.minio.access-key'] || SYSTEM_PROPERTY_FALLBACKS.minio.accessKey;
  minioConfig.secretKey = props['storage.minio.secret-key'] || SYSTEM_PROPERTY_FALLBACKS.minio.secretKey;
  minioConfig.bucket = props['storage.minio.bucket'] || SYSTEM_PROPERTY_FALLBACKS.minio.bucket;
  minioConfig.secure = props['storage.minio.secure'] === true || props['storage.minio.secure'] === 'true';

  aiConfig.jinaEnabled = props['jina.reader.enabled'] === true || props['jina.reader.enabled'] === 'true';
  aiConfig.jinaApiKey = props['jina.reader.api-key'] || SYSTEM_PROPERTY_FALLBACKS.ai.jinaApiKey;

  embeddingConfig.enableRerank = props['knowledge.rerank.enabled'] === 'true';
  embeddingConfig.rerankModel = props['knowledge.rerank.model'] || SYSTEM_PROPERTY_FALLBACKS.embedding.rerankModel;

  kbParams.defaultCollection = props['knowledge.default-collection'] || SYSTEM_PROPERTY_FALLBACKS.kb.defaultCollection;
  kbParams.chunkSize = parseInt(props['knowledge.chunk-size']) || SYSTEM_PROPERTY_FALLBACKS.kb.chunkSize;
  kbParams.chunkOverlap = parseInt(props['knowledge.chunk-overlap']) || SYSTEM_PROPERTY_FALLBACKS.kb.chunkOverlap;
  kbParams.defaultTopK = parseInt(props['knowledge.default-top-k']) || SYSTEM_PROPERTY_FALLBACKS.kb.defaultTopK;
  kbParams.similarityThreshold = parseFloat(props['knowledge.similarity-threshold']) || SYSTEM_PROPERTY_FALLBACKS.kb.similarityThreshold;
  kbParams.ocrProvider = 'local';
  kbParams.ocrModel = props['knowledge.ocr.model'] || SYSTEM_PROPERTY_FALLBACKS.kb.ocrModel;
  kbParams.asrProvider = 'local';
  kbParams.asrModel = props['knowledge.asr.model'] || SYSTEM_PROPERTY_FALLBACKS.kb.asrModel;
};

const saveDbKeys = async (keys, successMessage = '配置已保存，重启服务后生效') => {
  dbSaving.value = true;
  try {
    const payload = keys.reduce((acc, key) => {
      acc[key] = dbConfig.value[key] ?? '';
      return acc;
    }, {});
    await saveSystemProperties(payload);
    ElMessage.success(successMessage);
    return true;
  } catch (e) { ElMessage.error('保存失败: ' + e.message); }
  finally { dbSaving.value = false; }
  return false;
};

const MYSQL_DB_KEYS = ['spring.datasource.url', 'spring.datasource.username', 'spring.datasource.password'];
const REDIS_DB_KEYS = ['spring.data.redis.host', 'spring.data.redis.port', 'spring.data.redis.password'];
const RABBITMQ_DB_KEYS = [
  'spring.rabbitmq.host',
  'spring.rabbitmq.port',
  'spring.rabbitmq.username',
  'spring.rabbitmq.password',
  'spring.rabbitmq.virtual-host',
];
const COLLAB_ORCHESTRATION_KEYS = [
  'orin.collaboration.mode',
  'orin.collaboration.mq-for-parallel',
  'orin.collaboration.mq-for-sequential',
  'orin.collaboration.mq-for-consensus',
];

const pickDbConfig = keys => keys.reduce((result, key) => { result[key] = dbConfig.value[key]; return result; }, {});
const restoreDbConfig = snapshot => { Object.entries(snapshot).forEach(([key, value]) => { dbConfig.value[key] = value; }); };

const startMysqlEdit = () => startCardEdit('storage-mysql', pickDbConfig(MYSQL_DB_KEYS));
const cancelMysqlEdit = () => cancelCardEdit('storage-mysql', restoreDbConfig);
const saveMysqlConfig = async () => { if (await saveDbKeys(MYSQL_DB_KEYS, 'MySQL 配置已保存，重启服务后生效')) finishCardEdit('storage-mysql'); };

const startRedisEdit = () => startCardEdit('storage-redis', pickDbConfig(REDIS_DB_KEYS));
const cancelRedisEdit = () => cancelCardEdit('storage-redis', restoreDbConfig);
const saveRedisConfig = async () => { if (await saveDbKeys(REDIS_DB_KEYS, 'Redis 配置已保存，重启服务后生效')) finishCardEdit('storage-redis'); };

const startRabbitmqEdit = () => startCardEdit('storage-rabbitmq', pickDbConfig(RABBITMQ_DB_KEYS));
const cancelRabbitmqEdit = () => cancelCardEdit('storage-rabbitmq', restoreDbConfig);
const saveRabbitmqConfig = async () => { if (await saveDbKeys(RABBITMQ_DB_KEYS, 'RabbitMQ 配置已保存，重启服务后生效')) finishCardEdit('storage-rabbitmq'); };

const startCollabOrchestrationEdit = () => startCardEdit('collaboration-orchestration', pickDbConfig(COLLAB_ORCHESTRATION_KEYS));
const cancelCollabOrchestrationEdit = () => cancelCardEdit('collaboration-orchestration', restoreDbConfig);
const saveCollabOrchestrationConfig = async () => { if (await saveDbKeys(COLLAB_ORCHESTRATION_KEYS, '协作编舞配置已保存，重启服务后生效')) finishCardEdit('collaboration-orchestration'); };

// Milvus
const milvusConfig = reactive({ host: '192.168.1.164', port: 19530, token: '' });
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
    if (res.online) {
      ElMessage.success('Milvus 连接成功');
      await Promise.allSettled([loadCollectionInfo(), loadCollectionDetail()]);
    }
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

// MinIO / 双线对象存储
const minioConfig = reactive({
  mode: SYSTEM_PROPERTY_FALLBACKS.minio.mode,
  primary: SYSTEM_PROPERTY_FALLBACKS.minio.primary,
  secondary: SYSTEM_PROPERTY_FALLBACKS.minio.secondary,
  readFallback: SYSTEM_PROPERTY_FALLBACKS.minio.readFallback,
  writeAsyncRepair: SYSTEM_PROPERTY_FALLBACKS.minio.writeAsyncRepair,
  presignTtlSeconds: SYSTEM_PROPERTY_FALLBACKS.minio.presignTtlSeconds,
  endpoint: SYSTEM_PROPERTY_FALLBACKS.minio.endpoint,
  accessKey: SYSTEM_PROPERTY_FALLBACKS.minio.accessKey,
  secretKey: SYSTEM_PROPERTY_FALLBACKS.minio.secretKey,
  bucket: SYSTEM_PROPERTY_FALLBACKS.minio.bucket,
  secure: SYSTEM_PROPERTY_FALLBACKS.minio.secure,
});
const minioSaving = ref(false);
const testingMinio = ref(false);
const minioStatus = ref({ up: false, backend: 'minio', error: '' });

const saveMinioConfig = async () => {
  minioSaving.value = true;
  try {
    await saveSystemProperties({
      'storage.mode': minioConfig.mode,
      'storage.primary': minioConfig.primary,
      'storage.secondary': minioConfig.secondary,
      'storage.read-fallback': String(minioConfig.readFallback),
      'storage.write-async-repair': String(minioConfig.writeAsyncRepair),
      'storage.presign.ttl-seconds': String(minioConfig.presignTtlSeconds),
      'storage.minio.endpoint': minioConfig.endpoint,
      'storage.minio.access-key': minioConfig.accessKey,
      'storage.minio.secret-key': minioConfig.secretKey,
      'storage.minio.bucket': minioConfig.bucket,
      'storage.minio.secure': String(minioConfig.secure),
    });
    ElMessage.success('MinIO 存储配置已保存');
    return true;
  } catch (e) {
    ElMessage.error('保存失败: ' + e.message);
  } finally {
    minioSaving.value = false;
  }
  return false;
};

const testMinioConnection = async () => {
  if (testingMinio.value) return;
  testingMinio.value = true;
  try {
    const candidate = await request.post('/storage/health/minio/test', {
      endpoint: minioConfig.endpoint,
      accessKey: minioConfig.accessKey,
      secretKey: minioConfig.secretKey,
      bucket: minioConfig.bucket,
      secure: minioConfig.secure,
    });
    minioStatus.value = {
      up: candidate.up === true,
      backend: candidate.backend || 'minio',
      error: candidate.error || '',
    };
    if (minioStatus.value.up) ElMessage.success('MinIO 连接成功');
    else ElMessage.error('MinIO 连接失败' + (minioStatus.value.error ? `: ${minioStatus.value.error}` : ''));
  } catch (e) {
    minioStatus.value = { up: false, backend: 'minio', error: e.message || '未知错误' };
    ElMessage.error('测试失败: ' + (e.message || '未知错误'));
  } finally {
    testingMinio.value = false;
  }
};

const startMinioEdit = () => startCardEdit('storage-minio', minioConfig);
const cancelMinioEdit = () => cancelCardEdit('storage-minio', snapshot => Object.assign(minioConfig, snapshot));
const saveMinioCard = async () => { if (await saveMinioConfig()) finishCardEdit('storage-minio'); };

// ==================== AI 服务 ====================
const aiConfig = reactive({
  siliconFlowApiKey: '',
  siliconFlowEndpoint: 'https://api.siliconflow.cn/v1',
  jinaEnabled: SYSTEM_PROPERTY_FALLBACKS.ai.jinaEnabled,
  jinaApiKey: '',
});
const aiCapabilitySaving = ref(false);
const jinaSaving = ref(false);

const loadAiConfig = async () => {
  try {
    const mc = await request.get('/model-config');
    if (mc) {
      aiConfig.siliconFlowApiKey  = mc.siliconFlowApiKey || '';
      aiConfig.siliconFlowEndpoint = mc.siliconFlowEndpoint || 'https://api.siliconflow.cn/v1';
    }
  } catch (e) { /* ignore */ }
};

const persistExternalAiConfig = () => request.put('/model-config', {
  siliconFlowApiKey: aiConfig.siliconFlowApiKey,
  siliconFlowEndpoint: aiConfig.siliconFlowEndpoint
});

const saveJinaReaderConfig = async () => {
  jinaSaving.value = true;
  try {
    await saveSystemProperties({
      'jina.reader.enabled': aiConfig.jinaEnabled,
      'jina.reader.api-key': aiConfig.jinaApiKey
    });
    ElMessage.success('Jina Reader 配置已保存');
    return true;
  } catch (e) {
    ElMessage.error('保存失败: ' + e.message);
  } finally {
    jinaSaving.value = false;
  }
  return false;
};

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

const ocrModelOptions = [
  { id: 'tesseract', name: 'tesseract' },
  { id: 'paddleocr', name: 'paddleocr' },
  { id: 'ocr-general-v1', name: 'ocr-general-v1' },
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
    kbParams.ocrProvider = 'local';
    kbParams.asrProvider = 'local';
    if (selectedKBModel.value) {
      embeddingConfig.descGenerationModel = selectedKBModel.value;
    }
    await Promise.all([persistExternalAiConfig(), persistEmbeddingConfig(), persistMultimodalConfig()]);
    ElMessage.success('AI 能力配置已保存');
    return true;
  } catch (e) { ElMessage.error('保存失败: ' + e.message); }
  finally { aiCapabilitySaving.value = false; }
  return false;
};

const getAiCapabilitySnapshot = () => ({
  aiConfig: {
    siliconFlowApiKey: aiConfig.siliconFlowApiKey,
    siliconFlowEndpoint: aiConfig.siliconFlowEndpoint,
  },
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
const startJinaReaderEdit = () => startCardEdit('jina-reader', { jinaEnabled: aiConfig.jinaEnabled, jinaApiKey: aiConfig.jinaApiKey });
const cancelJinaReaderEdit = () => cancelCardEdit('jina-reader', snapshot => Object.assign(aiConfig, snapshot));
const saveJinaReaderCard = async () => { if (await saveJinaReaderConfig()) finishCardEdit('jina-reader'); };

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
  const apiUrl = String(difyConfig.apiUrl || '').trim();
  const apiKey = String(difyConfig.apiKey || '').trim();
  if (!apiUrl || !apiKey) {
    ElMessage.warning('请先填写 Dify API 地址和 API Key');
    return false;
  }
  difyLoading.value = true;
  try {
    await saveDifyConfig({ ...difyConfig, apiUrl, apiKey });
    difyConfig.apiUrl = apiUrl;
    difyConfig.apiKey = apiKey;
    ElMessage.success('Dify 配置已保存');
    return true;
  }
  catch (e) { ElMessage.error('保存失败: ' + (e.message || e)); }
  finally { difyLoading.value = false; }
  return false;
};

const handleTestDifyConnection = async () => {
  try {
    const apiUrl = String(difyConfig.apiUrl || '').trim();
    const apiKey = String(difyConfig.apiKey || '').trim();
    if (!apiUrl || !apiKey) {
      ElMessage.warning('请先填写 Dify API 地址和 API Key');
      return;
    }
    const res = await testDifyConnection({ apiUrl, apiKey });
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
      const configuredDescModel = embeddingConfig.descGenerationModel;
      const hasConfiguredDescModel = configuredDescModel && kbModels.value.some(m => m.id === configuredDescModel);
      if (hasConfiguredDescModel) {
        selectedKBModel.value = configuredDescModel;
      } else if (kbModels.value.length && !selectedKBModel.value) {
        selectedKBModel.value = kbModels.value[0].id;
      }
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
    testMinioConnection();
    loadCollectionInfo();
    loadCollectionDetail();
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
  color: #243244;
}

.layout {
  display: flex;
  gap: 20px;
  align-items: flex-start;
  margin-top: 14px;
}

.main-content {
  flex: 1;
  min-width: 0;
}

.config-section {
  margin-bottom: 20px;
  scroll-margin-top: 12px;
}

.page-container :deep(.orin-config-section + .orin-config-section) {
  margin-top: 12px !important;
}

.page-container :deep(.section-header) {
  padding: 13px 16px;
}

.page-container :deep(.section-body) {
  padding: 16px;
}

.page-container :deep(.el-form-item__label) {
  color: #526178;
  font-weight: 520;
}

.page-container :deep(.el-input__wrapper),
.page-container :deep(.el-select__wrapper),
.page-container :deep(.el-input-number) {
  box-shadow: 0 0 0 1px #dbe4ee inset;
}

.page-container :deep(.el-table) {
  border: 1px solid #e3e9ef;
  border-radius: 8px;
  overflow: hidden;
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
  width: 100%;
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
  margin-left: auto;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.card-actions-right {
  margin-left: auto;
}

.card-head-main {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
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

@media (max-width: 900px) {
  .card-head {
    align-items: flex-start;
    gap: 10px;
  }

  .card-actions {
    flex-wrap: wrap;
    justify-content: flex-end;
  }
}

</style>
