<template>
  <div class="version-manager">
    <el-card class="premium-card">
      <template #header>
        <div class="card-header">
          <div>
            <el-icon><Clock /></el-icon>
            <span>版本历史</span>
            <el-tag size="small" type="info" class="ml-2">{{ versions.length }} 个版本</el-tag>
          </div>
          <el-button type="primary" :icon="Plus" @click="createVersionDialog = true">
            创建新版本
          </el-button>
        </div>
      </template>

      <el-timeline v-loading="loading">
        <el-timeline-item
          v-for="version in versions"
          :key="version.id"
          :timestamp="formatTime(version.createdAt)"
          :type="version.isActive ? 'primary' : 'info'"
          :hollow="!version.isActive"
          placement="top"
        >
          <el-card class="version-card" :class="{ active: version.isActive }">
            <div class="version-header">
              <div class="version-info">
                <h4>
                  版本 {{ version.versionNumber }}
                  <el-tag v-if="version.isActive" type="success" size="small" class="ml-2">
                    当前版本
                  </el-tag>
                  <el-tag v-if="version.versionTag" size="small" class="ml-2">
                    {{ version.versionTag }}
                  </el-tag>
                </h4>
                <p class="description">{{ version.changeDescription || '无变更说明' }}</p>
                <p class="meta">
                  <el-icon><User /></el-icon>
                  {{ version.createdBy }} · {{ formatTime(version.createdAt) }}
                </p>
              </div>
              <div class="version-actions">
                <el-button
                  size="small"
                  :icon="View"
                  @click="viewVersion(version)"
                >
                  查看
                </el-button>
                <el-button
                  v-if="!version.isActive"
                  size="small"
                  type="warning"
                  :icon="RefreshLeft"
                  @click="rollbackVersion(version)"
                >
                  回滚
                </el-button>
                <el-button
                  size="small"
                  :icon="DocumentCopy"
                  @click="compareDialog = true; selectedVersion = version"
                >
                  对比
                </el-button>
              </div>
            </div>
          </el-card>
        </el-timeline-item>
      </el-timeline>

      <el-empty v-if="!loading && versions.length === 0" description="暂无版本记录" />
    </el-card>

    <!-- 创建版本对话框 -->
    <el-dialog v-model="createVersionDialog" title="创建新版本" width="500px">
      <el-form :model="newVersion" label-width="100px">
        <el-form-item label="变更说明">
          <el-input
            v-model="newVersion.description"
            type="textarea"
            :rows="4"
            placeholder="请描述本次配置变更的内容..."
          />
        </el-form-item>
        <el-form-item label="版本标签">
          <el-input
            v-model="newVersion.tag"
            placeholder="例如: v1.0-stable (可选)"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVersionDialog = false">取消</el-button>
        <el-button type="primary" @click="createVersion" :loading="creating">
          创建版本
        </el-button>
      </template>
    </el-dialog>

    <!-- 版本详情对话框 -->
    <el-dialog v-model="detailDialog" title="版本详情" width="600px">
      <div v-if="selectedVersion">
        <el-descriptions border :column="1">
          <el-descriptions-item label="版本号">
            {{ selectedVersion.versionNumber }}
          </el-descriptions-item>
          <el-descriptions-item label="版本标签">
            {{ selectedVersion.versionTag || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">
            {{ formatTime(selectedVersion.createdAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="创建者">
            {{ selectedVersion.createdBy }}
          </el-descriptions-item>
          <el-descriptions-item label="变更说明">
            {{ selectedVersion.changeDescription || '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <h4 style="margin-top: 20px">配置快照</h4>
        <pre class="config-snapshot">{{ formatConfig(selectedVersion.configSnapshot) }}</pre>
      </div>
    </el-dialog>

    <!-- 版本对比对话框 -->
    <el-dialog v-model="compareDialog" title="版本对比" width="800px">
      <el-form inline>
        <el-form-item label="对比版本">
          <el-select v-model="compareVersion1" placeholder="选择版本1">
            <el-option
              v-for="v in versions"
              :key="v.id"
              :label="`版本 ${v.versionNumber}`"
              :value="v.versionNumber"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-select v-model="compareVersion2" placeholder="选择版本2">
            <el-option
              v-for="v in versions"
              :key="v.id"
              :label="`版本 ${v.versionNumber}`"
              :value="v.versionNumber"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="compareVersions">对比</el-button>
        </el-form-item>
      </el-form>

      <div v-if="comparisonResult" class="comparison-result">
        <h4>差异列表</h4>
        <el-table :data="comparisonResult.differences" stripe>
          <el-table-column prop="field" label="字段" width="150" />
          <el-table-column prop="oldValue" label="旧值" />
          <el-table-column prop="newValue" label="新值" />
        </el-table>
        <el-empty v-if="comparisonResult.differences.length === 0" description="两个版本配置相同" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import {
  Clock, Plus, User, View, RefreshLeft, DocumentCopy
} from '@element-plus/icons-vue'

const props = defineProps({
  agentId: {
    type: String,
    required: true
  }
})

const loading = ref(false)
const creating = ref(false)
const versions = ref([])
const createVersionDialog = ref(false)
const detailDialog = ref(false)
const compareDialog = ref(false)
const selectedVersion = ref(null)
const comparisonResult = ref(null)

const newVersion = ref({
  description: '',
  tag: ''
})

const compareVersion1 = ref(null)
const compareVersion2 = ref(null)

const loadVersions = async () => {
  loading.value = true
  try {
    const res = await request.get(`/agents/${props.agentId}/versions`)
    versions.value = res.data || []
  } catch (error) {
    ElMessage.error('加载版本列表失败')
  } finally {
    loading.value = false
  }
}

const createVersion = async () => {
  creating.value = true
  try {
    await request.post(`/agents/${props.agentId}/versions`, {
      description: newVersion.value.description,
      createdBy: 'admin' // TODO: 从用户上下文获取
    })
    ElMessage.success('版本创建成功')
    createVersionDialog.value = false
    newVersion.value = { description: '', tag: '' }
    await loadVersions()
  } catch (error) {
    ElMessage.error('创建版本失败')
  } finally {
    creating.value = false
  }
}

const viewVersion = (version) => {
  selectedVersion.value = version
  detailDialog.value = true
}

const rollbackVersion = async (version) => {
  try {
    await ElMessageBox.confirm(
      `确定要回滚到版本 ${version.versionNumber} 吗？这将恢复该版本的配置。`,
      '回滚确认',
      {
        confirmButtonText: '确定回滚',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    await request.post(`/agents/${props.agentId}/versions/${version.id}/rollback`)
    ElMessage.success('回滚成功')
    await loadVersions()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('回滚失败')
    }
  }
}

const compareVersions = async () => {
  if (!compareVersion1.value || !compareVersion2.value) {
    ElMessage.warning('请选择两个版本进行对比')
    return
  }

  try {
    const res = await request.get(`/agents/${props.agentId}/versions/compare`, {
      params: {
        version1: compareVersion1.value,
        version2: compareVersion2.value
      }
    })
    comparisonResult.value = res.data
  } catch (error) {
    ElMessage.error('版本对比失败')
  }
}

const formatTime = (time) => {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN')
}

const formatConfig = (configJson) => {
  try {
    return JSON.stringify(JSON.parse(configJson), null, 2)
  } catch {
    return configJson
  }
}

onMounted(() => {
  loadVersions()
})
</script>

<style scoped>
.version-manager {
  padding: 20px;
}

.premium-card {
  border-radius: var(--radius-xl);
  border: 1px solid var(--neutral-gray-100);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}

.card-header > div {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ml-2 {
  margin-left: 8px;
}

.version-card {
  margin-top: 10px;
  border-left: 3px solid var(--neutral-gray-200);
  transition: all 0.3s;
}

.version-card.active {
  border-left-color: var(--primary-color);
  background: var(--primary-color-light-9);
}

.version-card:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.version-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.version-info h4 {
  margin: 0 0 8px 0;
  display: flex;
  align-items: center;
}

.version-info .description {
  color: var(--neutral-gray-700);
  margin: 8px 0;
  line-height: 1.6;
}

.version-info .meta {
  font-size: 12px;
  color: var(--neutral-gray-500);
  display: flex;
  align-items: center;
  gap: 4px;
  margin: 4px 0 0 0;
}

.version-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.config-snapshot {
  background: var(--neutral-gray-50);
  padding: 15px;
  border-radius: 8px;
  overflow-x: auto;
  font-size: 13px;
  line-height: 1.6;
}

.comparison-result {
  margin-top: 20px;
}

.comparison-result h4 {
  margin-bottom: 12px;
}
</style>
