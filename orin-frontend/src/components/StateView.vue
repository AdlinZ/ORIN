<template>
  <div class="state-view" :class="[`state-${state}`, { 'is-fullscreen': fullscreen }]">
    <!-- Loading 状态 -->
    <div v-if="state === 'loading'" class="state-loading">
      <div class="loading-spinner">
        <el-icon class="is-loading" :size="loadingSize">
          <Loading />
        </el-icon>
      </div>
      <p v-if="loadingText" class="state-text">{{ loadingText }}</p>
      <el-skeleton v-if="showSkeleton" :rows="skeletonRows" animated />
    </div>

    <!-- Empty 状态 -->
    <div v-else-if="state === 'empty'" class="state-empty">
      <div class="state-icon">
        <el-icon :size="iconSize">
          <component :is="emptyIcon" />
        </el-icon>
      </div>
      <p class="state-title">{{ emptyTitle }}</p>
      <p v-if="emptyDescription" class="state-description">{{ emptyDescription }}</p>
      <div v-if="$slots.emptyAction" class="state-action">
        <slot name="emptyAction"></slot>
      </div>
    </div>

    <!-- Error 状态 -->
    <div v-else-if="state === 'error'" class="state-error">
      <div class="state-icon state-icon-error">
        <el-icon :size="iconSize">
          <CircleCloseFilled />
        </el-icon>
      </div>
      <p class="state-title">{{ errorTitle }}</p>
      <p v-if="errorDescription" class="state-description">{{ errorDescription }}</p>
      <div v-if="showErrorAction" class="state-action">
        <el-button type="primary" :icon="RefreshRight" @click="handleRetry">
          重试
        </el-button>
        <el-button v-if="showGoBack" @click="handleGoBack">
          返回
        </el-button>
      </div>
    </div>

    <!-- Forbidden 状态 -->
    <div v-else-if="state === 'forbidden'" class="state-forbidden">
      <div class="state-icon state-icon-warning">
        <el-icon :size="iconSize">
          <Lock />
        </el-icon>
      </div>
      <p class="state-title">{{ forbiddenTitle }}</p>
      <p v-if="forbiddenDescription" class="state-description">{{ forbiddenDescription }}</p>
      <div v-if="$slots.forbiddenAction" class="state-action">
        <slot name="forbiddenAction"></slot>
      </div>
    </div>

    <!-- Success 状态 -->
    <div v-else-if="state === 'success'" class="state-success">
      <div class="state-icon state-icon-success">
        <el-icon :size="iconSize">
          <CircleCheckFilled />
        </el-icon>
      </div>
      <p v-if="successMessage" class="state-title">{{ successMessage }}</p>
      <div v-if="$slots.successAction" class="state-action">
        <slot name="successAction"></slot>
      </div>
    </div>

    <!-- 自定义内容插槽 -->
    <slot v-else></slot>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  Loading,
  RefreshRight,
  FolderOpened,
  CircleCloseFilled,
  Lock,
  CircleCheckFilled
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

// 状态类型
type StateType = 'idle' | 'loading' | 'success' | 'error' | 'empty' | 'forbidden'

interface Props {
  // 状态: idle | loading | success | error | empty | forbidden
  state?: StateType
  // 是否全屏显示
  fullscreen?: boolean
  // Loading 相关
  loadingText?: string
  loadingSize?: number
  showSkeleton?: boolean
  skeletonRows?: number
  // Empty 相关
  emptyTitle?: string
  emptyDescription?: string
  emptyIcon?: any
  // Error 相关
  errorTitle?: string
  errorDescription?: string
  errorDetails?: string
  showErrorAction?: boolean
  showGoBack?: boolean
  // Forbidden 相关
  forbiddenTitle?: string
  forbiddenDescription?: string
  // Success 相关
  successMessage?: string
  // 重试回调
  onRetry?: () => void
}

const props = withDefaults(defineProps<Props>(), {
  state: 'idle',
  fullscreen: false,
  loadingText: '加载中...',
  loadingSize: 32,
  showSkeleton: false,
  skeletonRows: 3,
  emptyTitle: '暂无数据',
  emptyDescription: '',
  emptyIcon: FolderOpened,
  errorTitle: '加载失败',
  errorDescription: '',
  showErrorAction: true,
  showGoBack: false,
  forbiddenTitle: '无权限访问',
  forbiddenDescription: '您没有权限查看此内容',
  successMessage: ''
})

const emit = defineEmits<{
  retry: []
}>()

const router = useRouter()
const retryCount = ref(0)
const MAX_RETRY = 3

const iconSize = computed(() => 64)

// 自动重试
const autoRetry = ref(false)
let retryTimer: ReturnType<typeof setTimeout> | null = null

watch(() => props.state, (newState) => {
  if (newState === 'error' && autoRetry.value && retryCount.value < MAX_RETRY) {
    // 延迟重试
    if (retryTimer) clearTimeout(retryTimer)
    retryTimer = setTimeout(() => {
      retryCount.value++
      if (props.onRetry) {
        props.onRetry()
      }
      emit('retry')
    }, 2000)
  }
})

onMounted(() => {
  // 清理定时器
  return () => {
    if (retryTimer) clearTimeout(retryTimer)
  }
})

const handleRetry = () => {
  retryCount.value++
  if (props.onRetry) {
    props.onRetry()
  }
  emit('retry')
  ElMessage.info('正在重试...')
}

const handleGoBack = () => {
  router.back()
}

// 导出状态常量供外部使用
defineExpose({
  StateType
})
</script>

<style scoped>
.state-view {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  text-align: center;
  min-height: 200px;
}

.state-view.is-fullscreen {
  min-height: calc(100vh - 200px);
}

/* Loading 状态 */
.state-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.loading-spinner {
  color: var(--el-color-primary);
}

.state-text {
  color: var(--el-text-color-secondary);
  font-size: 14px;
  margin: 0;
}

/* 通用图标 */
.state-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 80px;
  height: 80px;
  border-radius: 50%;
  margin-bottom: 16px;
}

.state-icon-error {
  background: var(--el-color-error-light-9);
  color: var(--el-color-error);
}

.state-icon-success {
  background: var(--el-color-success-light-9);
  color: var(--el-color-success);
}

.state-icon-warning {
  background: var(--el-color-warning-light-9);
  color: var(--el-color-warning);
}

/* 通用文本 */
.state-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin: 0 0 8px 0;
}

.state-description {
  font-size: 14px;
  color: var(--el-text-color-secondary);
  margin: 0 0 16px 0;
  max-width: 360px;
  line-height: 1.6;
}

.state-action {
  display: flex;
  gap: 12px;
  justify-content: center;
  flex-wrap: wrap;
}

/* Empty 状态 */
.state-empty .state-icon {
  background: var(--el-fill-color-lighter);
  color: var(--el-text-color-placeholder);
}

/* Error 状态 */
.state-error .state-description {
  color: var(--el-color-error);
}

/* Forbidden 状态 */
.state-forbidden .state-icon {
  background: var(--el-color-warning-light-9);
  color: var(--el-color-warning);
}

/* Success 状态 */
.state-success .state-icon {
  background: var(--el-color-success-light-9);
  color: var(--el-color-success);
}

.state-success .state-title {
  color: var(--el-color-success);
}
</style>
