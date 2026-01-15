<template>
  <div v-if="showWarning" class="token-expiry-warning">
    <el-alert
      :title="warningMessage"
      type="warning"
      :closable="false"
      show-icon
    >
      <template #default>
        <div class="warning-content">
          <span>{{ warningMessage }}</span>
          <el-button type="warning" size="small" @click="handleRelogin" class="relogin-btn">
            立即重新登录
          </el-button>
        </div>
      </template>
    </el-alert>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue';
import { useUserStore } from '@/stores/user';
import { useRouter } from 'vue-router';

const userStore = useUserStore();
const router = useRouter();

const showWarning = ref(false);
const warningMessage = ref('');
let checkInterval = null;

// 检查 Token 状态
function checkTokenStatus() {
  if (!userStore.isLoggedIn) {
    showWarning.value = false;
    return;
  }

  const tokenInfo = userStore.getTokenInfo();
  
  if (!tokenInfo.valid) {
    // Token 已过期
    showWarning.value = false; // 不显示警告，直接跳转
    handleRelogin();
  } else if (tokenInfo.remaining < 5 * 60 * 1000) {
    // 剩余时间少于 5 分钟
    showWarning.value = true;
    warningMessage.value = `登录即将过期（剩余 ${tokenInfo.formatted}），请及时保存数据`;
  } else {
    showWarning.value = false;
  }
}

// 重新登录
function handleRelogin() {
  userStore.logout();
  router.push('/login');
}

onMounted(() => {
  // 立即检查一次
  checkTokenStatus();
  
  // 每分钟检查一次
  checkInterval = setInterval(checkTokenStatus, 60 * 1000);
});

onUnmounted(() => {
  if (checkInterval) {
    clearInterval(checkInterval);
  }
});
</script>

<style scoped>
.token-expiry-warning {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 9999;
  padding: 0;
}

.token-expiry-warning :deep(.el-alert) {
  border-radius: 0;
  margin: 0;
}

.warning-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.relogin-btn {
  flex-shrink: 0;
}
</style>
