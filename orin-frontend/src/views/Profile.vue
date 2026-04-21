<template>
  <div class="page-container">
    <PageHeader
      title="个人中心"
      description="查看与管理个人资料、账户安全和近期活动"
      icon="User"
    />
    <!-- Header -->
    <div class="profile-header">
      <div class="header-content">
        <div class="user-info-section">
          <div class="avatar-wrapper" @click="handleAvatarClick">
            <el-avatar
              :size="80"
              :src="userInfo.avatar || defaultAvatar"
              class="user-avatar"
            />
            <div class="avatar-edit-icon">
              <el-icon><Camera /></el-icon>
            </div>
            <input
              ref="avatarInput"
              type="file"
              style="display: none"
              accept="image/*"
              @change="onAvatarFileChange"
            >
          </div>
          <div class="user-details">
            <h1 class="user-name">
              {{ userInfo.nickname || userInfo.username }}
            </h1>
            <p class="user-email">
              {{ userInfo.email }}
            </p>
            <div class="user-meta">
              <el-tag size="small" effect="plain" :type="isAdmin ? 'danger' : 'info'">
                {{ roleDisplay }}
              </el-tag>
              <span class="join-time">
                <el-icon><Calendar /></el-icon>
                加入于 {{ formatJoinDate(userInfo.createTime) }}
              </span>
            </div>
          </div>
        </div>
        <el-button type="primary" @click="activeTab = 'settings'">
          <el-icon><Edit /></el-icon>
          编辑资料
        </el-button>
      </div>
    </div>

    <!-- Stats Cards -->
    <el-row :gutter="20" class="stats-row">
      <el-col v-for="stat in stats" :key="stat.label" :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-card-inner">
            <div class="stat-icon">
              <el-icon><component :is="stat.icon" /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">
                {{ stat.value }}
              </div>
              <div class="stat-label">
                {{ stat.label }}
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Main Content -->
    <el-row :gutter="24">
      <!-- Left Column: Activity -->
      <el-col :span="16">
        <!-- Activity Chart -->
        <el-card shadow="never" class="content-card">
          <template #header>
            <div class="card-header">
              <span><el-icon><TrendCharts /></el-icon> 最近7天活动</span>
            </div>
          </template>
          <div v-if="activityData.length > 0" class="activity-chart">
            <div class="chart-bars">
              <div
                v-for="(day, index) in activityData"
                :key="index"
                class="chart-bar"
                :style="{ height: day.value + '%' }"
                :title="`${day.label}: ${day.count} 次活动`"
              >
                <div class="bar-fill" />
              </div>
            </div>
            <div class="chart-labels">
              <span v-for="(day, index) in activityData" :key="index">{{ day.label }}</span>
            </div>
          </div>
          <el-empty v-else description="暂无活动数据" :image-size="80" />
        </el-card>

        <!-- Recent Activity -->
        <el-card shadow="never" class="content-card" style="margin-top: 20px;">
          <template #header>
            <div class="card-header">
              <span><el-icon><Clock /></el-icon> 最近动态</span>
            </div>
          </template>
          <div v-if="activityLogs.length > 0" class="activity-list">
            <div v-for="(log, index) in activityLogs" :key="index" class="activity-item">
              <div class="activity-dot" :class="log.type" />
              <div class="activity-content">
                <div class="activity-header">
                  <span class="activity-action">{{ log.action }}</span>
                  <span class="activity-time">{{ log.time }}</span>
                </div>
                <div class="activity-detail">
                  {{ log.detail }}
                </div>
              </div>
            </div>
          </div>
          <el-empty v-else description="暂无动态" :image-size="80" />
        </el-card>
      </el-col>

      <!-- Right Column: Settings -->
      <el-col :span="8">
        <el-card shadow="never" class="content-card">
          <el-tabs v-model="activeTab" class="profile-tabs">
            <el-tab-pane name="settings">
              <template #label>
                <span class="tab-label">
                  <el-icon><EditPen /></el-icon>
                  个人资料
                </span>
              </template>
              <el-form :model="userForm" label-position="top" class="profile-form">
                <el-form-item label="用户昵称">
                  <el-input v-model="userForm.nickname" placeholder="请输入昵称">
                    <template #prefix>
                      <el-icon><User /></el-icon>
                    </template>
                  </el-input>
                </el-form-item>
                <el-form-item label="电子邮箱">
                  <el-input v-model="userForm.email" placeholder="请输入邮箱">
                    <template #prefix>
                      <el-icon><Message /></el-icon>
                    </template>
                  </el-input>
                </el-form-item>
                <el-form-item label="个人简介">
                  <el-input
                    v-model="userForm.bio"
                    type="textarea"
                    :rows="3"
                    placeholder="介绍一下自己..."
                    maxlength="200"
                    show-word-limit
                  />
                </el-form-item>
                <el-form-item label="地址">
                  <el-input v-model="userForm.address" placeholder="请输入地址">
                    <template #prefix>
                      <el-icon><Location /></el-icon>
                    </template>
                  </el-input>
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" style="width: 100%;" @click="handleSave">
                    <el-icon><Check /></el-icon>
                    保存修改
                  </el-button>
                </el-form-item>
              </el-form>
            </el-tab-pane>

            <el-tab-pane name="account">
              <template #label>
                <span class="tab-label">
                  <el-icon><Lock /></el-icon>
                  账号安全
                </span>
              </template>
              <div class="security-info">
                <div class="security-item">
                  <div class="security-icon">
                    <el-icon><Key /></el-icon>
                  </div>
                  <div class="security-content">
                    <h4>登录密码</h4>
                    <p>定期更换密码可以提高账户安全性</p>
                  </div>
                  <el-button type="primary" link size="small">
                    修改
                  </el-button>
                </div>
                <div class="security-item">
                  <div class="security-icon phone">
                    <el-icon><Iphone /></el-icon>
                  </div>
                  <div class="security-content">
                    <h4>手机绑定</h4>
                    <p>{{ userInfo.phone ? maskPhone(userInfo.phone) : '未绑定手机' }}</p>
                  </div>
                  <el-button type="primary" link size="small">
                    {{ userInfo.phone ? '修改' : '绑定' }}
                  </el-button>
                </div>
              </div>
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue';
import {
  User, Camera, Message, Location, Calendar, Edit, EditPen,
  Lock, Key, Iphone, Check, TrendCharts, Clock,
  Collection, DataAnalysis, ChatDotRound
} from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { useUserStore } from '@/stores/user';
import { getUserProfile, updateUserProfile, uploadAvatar, updateUserAvatar, getUserDashboard } from '@/api/user';
import PageHeader from '@/components/PageHeader.vue';

const userStore = useUserStore();
const activeTab = ref('settings');
const avatarInput = ref(null);
const defaultAvatar = 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png';

// User data
const defaultUserData = {
  nickname: '',
  username: '',
  email: '',
  bio: '',
  address: '',
  phone: '',
  avatar: '',
  userId: null,
  createTime: null
};

const userInfo = reactive({ ...defaultUserData });
const userForm = reactive({ ...defaultUserData });

// Stats data
const stats = ref([
  { label: '知识库', value: '0', icon: 'Collection' },
  { label: 'AI 智能体', value: '0', icon: 'DataAnalysis' },
  { label: 'Token 消耗', value: '0', icon: 'ChatDotRound' },
  { label: '活跃天数', value: '0', icon: 'Calendar' }
]);

// Activity data
const activityData = ref([]);
const activityLogs = ref([]);

// Role display
const isAdmin = computed(() => {
  return userStore.roles && userStore.roles.includes('ROLE_ADMIN');
});

const roleDisplay = computed(() => {
  if (isAdmin.value) {
    return '超级管理员';
  }
  return '普通用户';
});

// Format functions
const formatJoinDate = (dateStr) => {
  if (!dateStr) return '未知';
  try {
    const date = new Date(dateStr);
    const year = date.getFullYear();
    const month = date.getMonth() + 1;
    return `${year}年${month}月`;
  } catch {
    return '未知';
  }
};

const maskPhone = (phone) => {
  if (!phone) return '未绑定';
  if (phone.length >= 11) {
    return phone.substring(0, 3) + '-' + '****' + '-' + phone.substring(7);
  }
  return phone;
};

// Load data
onMounted(async () => {
  // Sync with store first
  if (userStore.userInfo) {
    Object.assign(userInfo, userStore.userInfo);
    Object.assign(userForm, userInfo);
  }

  // Fetch from backend
  if (userStore.username) {
    try {
      const data = await getUserProfile(userStore.username);
      Object.assign(userInfo, {
        nickname: data.nickname || data.username || '未设置昵称',
        username: data.username || '',
        email: data.email || '',
        bio: data.bio || '',
        address: data.address || '',
        phone: data.phone || '',
        avatar: data.avatar || '',
        userId: data.userId || null,
        createTime: data.createTime || null
      });
      Object.assign(userForm, userInfo);
      userStore.updateUserInfo(data);

      // Fetch dashboard data
      try {
        const dashData = await getUserDashboard(userStore.username);
        if (dashData) {
          if (dashData.stats) stats.value = dashData.stats;
          if (dashData.activityData) activityData.value = dashData.activityData;
          if (dashData.activityLogs) activityLogs.value = dashData.activityLogs;
        }
      } catch (err) {
        console.error('获取监控面板数据失败:', err);
      ElMessage.error('加载失败');
      }
    } catch (e) {
      console.error('获取用户信息失败:', e);
      ElMessage.error('加载失败');
    }
  }
});

// Avatar upload
const handleAvatarClick = () => {
  if (avatarInput.value) {
    avatarInput.value.click();
  }
};

const onAvatarFileChange = async (event) => {
  const file = event.target.files[0];
  if (!file) return;

  if (file.size > 2 * 1024 * 1024) {
    ElMessage.error('头像文件不能超过 2MB');
    return;
  }

  try {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('uploadedBy', userInfo.username);

    const uploadRes = await uploadAvatar(formData);
    const avatarUrl = `/api/v1/multimodal/files/${uploadRes.id}/download`;

    await updateUserAvatar(userInfo.userId, avatarUrl);

    userInfo.avatar = avatarUrl;
    userForm.avatar = avatarUrl;
    userStore.updateUserInfo({ ...userInfo });

    ElMessage.success('头像更新成功');
  } catch (e) {
    console.error('头像上传失败:', e);
    ElMessage.error('上传失败');
    ElMessage.error('头像更新失败');
  } finally {
    if (avatarInput.value) {
      avatarInput.value.value = '';
    }
  }
};

// Save profile
const handleSave = async () => {
  try {
    const resolvedUserId = userInfo.userId || userStore.userId || userStore.userInfo?.id || null;
    if (!resolvedUserId) {
      ElMessage.error('未识别到用户ID，请重新登录后重试');
      return;
    }

    const updateData = {
      userId: resolvedUserId,
      nickname: userForm.nickname,
      email: userForm.email,
      bio: userForm.bio,
      address: userForm.address,
      phone: userForm.phone,
      avatar: userForm.avatar
    };
    const updated = await updateUserProfile(updateData);
    Object.assign(userInfo, updated);
    userStore.updateUserInfo(updated);

    ElMessage.success('资料更新成功');
  } catch (e) {
    console.error('更新资料失败:', e);
    ElMessage.error(e?.response?.data?.message || '更新失败，请重试');
  }
};
</script>

<style scoped>
.page-container {
  padding: 24px;
  min-height: 100vh;
  background: var(--orin-bg);
}

/* Header */
.profile-header {
  background: var(--orin-bg-white);
  border: 1px solid var(--orin-border);
  border-radius: 12px;
  padding: 32px;
  margin-bottom: 24px;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.user-info-section {
  display: flex;
  align-items: center;
  gap: 20px;
}

.avatar-wrapper {
  position: relative;
  cursor: pointer;
}

.user-avatar {
  border: 1px solid var(--el-border-color-light, #e2e8f0);
}

.avatar-edit-icon {
  position: absolute;
  bottom: 0;
  right: 0;
  width: 28px;
  height: 28px;
  background: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--orin-primary, #10b981);
  border: 1px solid var(--el-border-color-light, #e2e8f0);
  transition: all 0.2s;
}

.avatar-wrapper:hover .avatar-edit-icon {
  background: var(--orin-primary, #10b981);
  color: white;
}

.user-details {
  color: var(--text-primary, #1e293b);
}

.user-name {
  font-size: 24px;
  font-weight: 700;
  margin: 0 0 4px 0;
}

.user-email {
  font-size: 14px;
  color: var(--text-secondary, #64748b);
  margin: 0 0 12px 0;
}

.user-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}

.join-time {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--text-secondary, #64748b);
}

/* Stats Cards */
.stats-row {
  margin-bottom: 24px;
}

.stat-card {
  border-radius: 12px !important;
  border: 1px solid var(--orin-border) !important;
  background: var(--orin-bg-white) !important;
  box-shadow: var(--shadow-sm) !important;
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-md) !important;
}

.stat-card-inner {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  background: var(--orin-primary-50);
  color: var(--orin-primary);
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-primary, #1e293b);
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: var(--text-secondary, #64748b);
  margin-top: 2px;
}

/* Content Cards */
.content-card {
  border-radius: 12px !important;
  border: 1px solid var(--orin-border) !important;
  background: var(--orin-bg-white) !important;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 15px;
  color: var(--text-primary, #1e293b);
}

/* Activity Chart */
.activity-chart {
  padding: 20px 0;
}

.chart-bars {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  height: 140px;
  margin-bottom: 8px;
}

.chart-bar {
  flex: 1;
  background: var(--el-fill-color-light, #f1f5f9);
  border-radius: 6px 6px 0 0;
  position: relative;
  min-height: 20px;
  transition: all 0.3s;
}

.chart-bar:hover {
  opacity: 0.8;
}

.bar-fill {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 100%;
  background: var(--orin-primary, #10b981);
  border-radius: 6px 6px 0 0;
}

.chart-labels {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--neutral-gray-500);
}

/* Activity List */
.activity-list {
  display: flex;
  flex-direction: column;
}

.activity-item {
  display: flex;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid var(--orin-border);
}

.activity-item:last-child {
  border-bottom: none;
}

.activity-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-top: 6px;
  flex-shrink: 0;
}

.activity-dot.success {
  background: var(--orin-primary, #10b981);
}

.activity-dot.danger {
  background: #ef4444;
}

.activity-content {
  flex: 1;
}

.activity-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.activity-action {
  font-weight: 600;
  font-size: 14px;
  color: var(--text-primary, #1e293b);
}

.activity-time {
  font-size: 12px;
  color: var(--text-secondary, #64748b);
}

.activity-detail {
  font-size: 13px;
  color: var(--text-secondary, #64748b);
}

/* Tabs */
.profile-tabs :deep(.el-tabs__header) {
  margin-bottom: 20px;
}

.profile-tabs :deep(.el-tabs__item) {
  font-weight: 500;
}

.tab-label {
  display: flex;
  align-items: center;
  gap: 6px;
}

/* Form */
.profile-form :deep(.el-form-item__label) {
  font-weight: 500;
  color: var(--text-primary, #1e293b);
}

/* Security Info */
.security-info {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.security-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px;
  background: var(--orin-bg);
  border-radius: 10px;
}

.security-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  background: var(--el-color-primary-light-9, #ecfdf5);
  color: var(--orin-primary, #10b981);
}

.security-icon.phone {
  background: var(--orin-primary-50);
  color: var(--info-500);
}

.security-content {
  flex: 1;
}

.security-content h4 {
  font-size: 14px;
  font-weight: 600;
  margin: 0 0 4px 0;
  color: var(--text-primary, #1e293b);
}

.security-content p {
  font-size: 12px;
  margin: 0;
  color: var(--text-secondary, #64748b);
}


html.dark .security-content h4 {
  color: #f1f5f9;
}

html.dark .profile-form :deep(.el-form-item__label) {
  color: #e2e8f0;
}
</style>
