<template>
  <div class="gateway-acl-tab">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>访问控制规则</span>
          <el-button type="primary" @click="openDialog(null)">
            <el-icon><Plus /></el-icon>
            添加规则
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="rules" stripe>
        <el-table-column prop="name" label="规则名称" width="150" />
        <el-table-column prop="type" label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.type === 'WHITELIST' ? 'success' : 'danger'" size="small">
              {{ row.type === 'WHITELIST' ? '白名单' : '黑名单' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ipPattern" label="IP 模式" min-width="180" show-overflow-tooltip />
        <el-table-column prop="pathPattern" label="路径模式" min-width="150" show-overflow-tooltip />
        <el-table-column prop="apiKeyRequired" label="需要API Key" width="120">
          <template #default="{ row }">
            <el-tag :type="row.apiKeyRequired ? 'warning' : 'info'" size="small">
              {{ row.apiKeyRequired ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="80" />
        <el-table-column prop="enabled" label="状态" width="80">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" @change="toggleRule(row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openDialog(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="deleteRule(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Test Dialog -->
    <el-card style="margin-top: 20px;">
      <template #header>
        <span>IP 测试</span>
      </template>
      <el-form inline>
        <el-form-item label="IP 地址">
          <el-input v-model="testIp" placeholder="192.168.1.1" style="width: 200px;" />
        </el-form-item>
        <el-form-item label="路径">
          <el-input v-model="testPath" placeholder="/api/v1/agents" style="width: 200px;" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="testIpMatch">测试</el-button>
        </el-form-item>
      </el-form>
      <div v-if="testResult" style="margin-top: 10px;">
        <el-alert :type="testResult.action === 'ALLOW' ? 'success' : 'error'" :title="`${testResult.action === 'ALLOW' ? '允许' : '拒绝'} - ${testResult.ruleName || '无匹配规则'}`" show-icon />
      </div>
    </el-card>

    <!-- Create/Edit Dialog -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑规则' : '添加规则'" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="规则名称" required>
          <el-input v-model="form.name" placeholder="生产环境 IP" />
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="form.type">
            <el-radio value="WHITELIST">白名单</el-radio>
            <el-radio value="BLACKLIST">黑名单</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="IP 模式" required>
          <el-input v-model="form.ipPattern" placeholder="192.168.1.0/24,10.0.0.1" />
        </el-form-item>
        <el-form-item label="路径模式">
          <el-input v-model="form.pathPattern" placeholder="/api/v1/**" />
        </el-form-item>
        <el-form-item label="需要API Key">
          <el-switch v-model="form.apiKeyRequired" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="form.priority" :min="0" :max="1000" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveRule" :loading="submitting">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAclRules, createAclRule, updateAclRule, deleteAclRule, testAclRule } from '@/api/gateway'

const loading = ref(false)
const submitting = ref(false)
const rules = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const testIp = ref('')
const testPath = ref('')
const testResult = ref(null)

const form = reactive({
  id: null, name: '', type: 'WHITELIST', ipPattern: '', pathPattern: '', apiKeyRequired: false, priority: 0, description: ''
})

const loadRules = async () => {
  loading.value = true
  try {
    rules.value = await getAclRules()
  } catch (e) {
    ElMessage.error('加载规则失败')
  } finally {
    loading.value = false
  }
}

const openDialog = (row) => {
  isEdit.value = !!row
  Object.assign(form, row ? { ...row } : { id: null, name: '', type: 'WHITELIST', ipPattern: '', pathPattern: '', apiKeyRequired: false, priority: 0, description: '' })
  dialogVisible.value = true
}

const saveRule = async () => {
  submitting.value = true
  try {
    if (isEdit.value) {
      await updateAclRule(form.id, form)
      ElMessage.success('规则已更新')
    } else {
      await createAclRule(form)
      ElMessage.success('规则已添加')
    }
    dialogVisible.value = false
    loadRules()
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    submitting.value = false
  }
}

const deleteRule = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要删除规则 "${row.name}" 吗?`, '提示', { type: 'warning' })
    await deleteAclRule(row.id)
    ElMessage.success('规则已删除')
    loadRules()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

const toggleRule = async (row) => {
  try {
    await updateAclRule(row.id, { enabled: row.enabled })
    ElMessage.success(row.enabled ? '规则已启用' : '规则已禁用')
  } catch (e) {
    row.enabled = !row.enabled
    ElMessage.error('操作失败')
  }
}

const testIpMatch = async () => {
  if (!testIp.value) {
    ElMessage.warning('请输入 IP 地址')
    return
  }
  try {
    const res = await testAclRule({ ip: testIp.value, path: testPath.value })
    testResult.value = res
  } catch (e) {
    ElMessage.error('测试失败')
  }
}

onMounted(() => {
  loadRules()
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
