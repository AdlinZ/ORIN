<template>
  <el-card class="box-card">
    <el-table :data="data" style="width: 100%">
      <el-table-column prop="name" label="名称" min-width="180">
        <template #default="scope">
          <span style="font-weight: 600">{{ scope.row.name }}</span>
          <div style="font-size: 12px; color: #999;">ID: {{ scope.row.id }}</div>
        </template>
      </el-table-column>
      
      <el-table-column prop="type" label="类型" width="120" v-if="props.type === 'structured'">
         <template #default="{ row }">
             <el-tag effect="plain">{{ row.type }}</el-tag>
         </template>
      </el-table-column>

      <el-table-column prop="docCount" label="资源数量" width="120" align="center">
        <template #default="scope">
          <el-tag size="small" type="info">{{ scope.row.docCount }} items</el-tag>
        </template>
      </el-table-column>
      
      <el-table-column prop="syncTime" label="最后更新" width="180">
          <template #default="scope">
              {{ formatTime(scope.row.syncTime) }}
          </template>
      </el-table-column>
      
      <el-table-column prop="status" label="状态" width="100" align="center">
        <template #default="scope">
          <el-tag :type="scope.row.status === 'ENABLED' ? 'success' : 'info'" effect="dark">
            {{ scope.row.status === 'ENABLED' ? '已启用' : '已禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      
      <el-table-column label="操作" width="250" fixed="right">
        <template #default="scope">
          <el-button link type="primary" @click="$emit('view-chunks', scope.row)" v-if="props.type !== 'api'">
            管理
          </el-button>
           <el-button link type="warning" @click="$emit('test-retrieve', scope.row)" v-if="props.type === 'default'">
            检索测试
          </el-button>
          
          <el-switch
            v-model="scope.row.status"
            :active-value="'ENABLED'"
            :inactive-value="'DISABLED'"
            inline-prompt
            active-text="ON"
            inactive-text="OFF"
            @change="(val) => $emit('status-change', scope.row, val)"
            style="margin-left: 10px;"
          />
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
const props = defineProps({
  data: {
    type: Array,
    default: () => []
  },
  type: {
      type: String, // 'default' (rag), 'structured', 'api'
      default: 'default'
  }
})

const emit = defineEmits(['status-change', 'view-chunks', 'test-retrieve'])

const formatTime = (time) => {
    if (!time) return '-'
    return new Date(time).toLocaleString()
}
</script>
