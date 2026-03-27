<template>
  <div class="page-container">
    <PageHeader
      title="知识图谱"
      description="图谱构建、浏览与检索能力正在建设中，后续会基于 LightRAG 与 Neo4j 提供统一的图谱工作台。"
      icon="Connection"
    />

    <el-card class="placeholder-card" shadow="never">
      <el-empty description="知识图谱功能建设中">
        <template #image>
          <div class="placeholder-icon">
            <el-icon><Connection /></el-icon>
          </div>
        </template>
        <div class="placeholder-copy">
          <p>当前版本已预留知识图谱入口，后续将逐步接入图谱构建、图谱浏览与图谱检索能力。</p>
          <p>如需继续使用知识资产管理，可先前往知识库与同步管理页面完成日常操作。</p>
        </div>
      </el-empty>
    </el-card>
  </div>
</template>

<script setup>
import { Connection } from '@element-plus/icons-vue'
</script>

<style scoped>
.placeholder-card {
  min-height: 420px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.placeholder-icon {
  width: 88px;
  height: 88px;
  margin: 0 auto 16px;
  border-radius: 24px;
  background: linear-gradient(135deg, #f3e8ff 0%, #ede9fe 100%);
  color: #7c3aed;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40px;
}

.placeholder-copy {
  max-width: 520px;
  margin: 8px auto 0;
  color: var(--el-text-color-regular);
  line-height: 1.8;
}
</style>
