<template>
  <el-dialog
    v-model="visible"
    title="批量操作"
    width="500px"
    @close="handleClose"
  >
    <el-form :model="form" label-width="100px">
      <el-form-item label="操作类型">
        <el-select v-model="form.operation" placeholder="选择操作">
          <el-option label="批量删除" value="delete" />
          <el-option label="批量启用" value="enable" />
          <el-option label="批量禁用" value="disable" />
          <el-option label="批量移动" value="move" />
        </el-select>
      </el-form-item>
      
      <el-form-item label="选择项" v-if="selectedItems.length > 0">
        <el-tag v-for="item in selectedItems.slice(0, 5)" :key="item.id" style="margin: 4px">
          {{ item.name || item.id }}
        </el-tag>
        <el-tag v-if="selectedItems.length > 5">
          +{{ selectedItems.length - 5 }} 更多
        </el-tag>
      </el-form-item>
      
      <el-form-item label="目标位置" v-if="form.operation === 'move'">
        <el-input v-model="form.targetPath" placeholder="输入目标路径" />
      </el-form-item>
    </el-form>
    
    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" @click="handleConfirm" :loading="loading">
        确认
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps({
  modelValue: Boolean,
  selectedItems: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['update:modelValue', 'confirm'])

const visible = ref(props.modelValue)
const loading = ref(false)

const form = reactive({
  operation: 'delete',
  targetPath: ''
})

watch(() => props.modelValue, (val) => {
  visible.value = val
})

const handleClose = () => {
  visible.value = false
  emit('update:modelValue', false)
}

const handleConfirm = async () => {
  if (props.selectedItems.length === 0) {
    ElMessage.warning('请先选择操作项')
    return
  }
  
  loading.value = true
  try {
    emit('confirm', {
      operation: form.operation,
      items: props.selectedItems,
      targetPath: form.targetPath
    })
    handleClose()
  } finally {
    loading.value = false
  }
}
</script>
