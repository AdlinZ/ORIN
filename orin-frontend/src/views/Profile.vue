<template>
  <div class="profile-page">
    <!-- Hero Section with Cover -->
    <div class="profile-hero">
      <div class="cover-gradient"></div>
      <div class="hero-content">
        <div class="avatar-section">
          <div class="avatar-wrapper">
            <el-avatar 
              :size="120" 
              :src="userInfo.avatar || 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'" 
              class="main-avatar"
            />
            <div class="avatar-edit-badge" @click="handleAvatarClick">
              <el-icon v-loading="avatarLoading"><Camera /></el-icon>
            </div>
            <input 
              type="file" 
              ref="avatarInput" 
              style="display: none" 
              accept="image/*"
              @change="onAvatarFileChange"
            />
            <div class="online-indicator"></div>
          </div>
          <div class="user-info">
            <h1 class="user-name">{{ userInfo.username || userInfo.nickname }}</h1>
            <p class="user-role">
              <el-icon><Star /></el-icon>
              {{ roleDisplay }}
            </p>
            <div class="user-meta">
              <span class="meta-badge">
                <el-icon><Calendar /></el-icon>
                加入于 2024年1月
              </span>
              <span class="meta-badge">
                <el-icon><Location /></el-icon>
                中国 · 北京
              </span>
            </div>
          </div>
        </div>
        
        <div class="quick-actions">
          <el-button type="primary" :icon="Edit" round>编辑资料</el-button>
          <el-button :icon="Share" round plain>分享主页</el-button>
          <el-button :icon="Setting" circle plain></el-button>
        </div>
      </div>
    </div>

    <!-- Stats Cards -->
    <div class="stats-section">
      <div class="stat-card" v-for="stat in stats" :key="stat.label">
        <div class="stat-icon" :style="{ background: stat.color }">
          <component :is="stat.icon" />
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ stat.value }}</div>
          <div class="stat-label">{{ stat.label }}</div>
          <div class="stat-trend" :class="stat.trend > 0 ? 'up' : 'down'">
            <el-icon><CaretTop v-if="stat.trend > 0" /><CaretBottom v-else /></el-icon>
            {{ Math.abs(stat.trend) }}%
          </div>
        </div>
      </div>
    </div>

    <!-- Main Content Grid -->
    <div class="content-grid">
      <!-- Left Column -->
      <div class="left-column">
        <!-- About Card -->
        <div class="content-card about-card">
          <div class="card-header">
            <h3><el-icon><User /></el-icon> 关于我</h3>
          </div>
          <div class="card-body">
            <div class="info-item">
              <span class="info-label">邮箱</span>
              <span class="info-value">{{ userInfo.email }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">手机</span>
              <span class="info-value">+86 138-0000-0000</span>
            </div>
            <div class="info-item">
              <span class="info-label">个人简介</span>
              <span class="info-value bio">{{ userInfo.bio || '这个人很懒，还没有填写个人简介' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">地址</span>
              <span class="info-value">{{ userInfo.address || '中国 · 北京 · 中关村' }}</span>
            </div>
          </div>
        </div>

        <!-- Activity Chart -->
        <div class="content-card activity-chart-card">
          <div class="card-header">
            <h3><el-icon><TrendCharts /></el-icon> 活动趋势</h3>
            <el-select v-model="activityPeriod" size="small" style="width: 100px">
              <el-option label="7天" value="7d" />
              <el-option label="30天" value="30d" />
              <el-option label="90天" value="90d" />
            </el-select>
          </div>
          <div class="card-body">
            <div class="activity-chart">
              <div class="chart-bars">
                <div 
                  v-for="(day, index) in activityData" 
                  :key="index"
                  class="chart-bar"
                  :style="{ height: day.value + '%' }"
                  :title="`${day.label}: ${day.count} 次活动`"
                >
                  <div class="bar-fill"></div>
                </div>
              </div>
              <div class="chart-labels">
                <span v-for="(day, index) in activityData" :key="index">{{ day.label }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- Skills & Tags -->
        <div class="content-card skills-card">
          <div class="card-header">
            <h3><el-icon><Medal /></el-icon> 技能标签</h3>
          </div>
          <div class="card-body">
            <div class="skills-grid">
              <el-tag v-for="skill in skills" :key="skill" :type="getRandomTagType()" effect="plain" round>
                {{ skill }}
              </el-tag>
            </div>
          </div>
        </div>
      </div>

      <!-- Right Column -->
      <div class="right-column">
        <!-- Settings Tabs -->
        <div class="content-card settings-card">
          <el-tabs v-model="activeTab" class="profile-tabs">
            <!-- Personal Settings -->
            <el-tab-pane name="settings">
              <template #label>
                <span class="tab-label">
                  <el-icon><EditPen /></el-icon>
                  个人设置
                </span>
              </template>
              <el-form :model="userForm" label-position="top" class="profile-form">
                <el-row :gutter="16">
                  <el-col :span="12">
                    <el-form-item label="用户昵称">
                      <el-input v-model="userForm.nickname" placeholder="输入您的常用昵称">
                        <template #prefix>
                          <el-icon><User /></el-icon>
                        </template>
                      </el-input>
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="电子邮箱">
                      <el-input v-model="userForm.email" placeholder="official@example.com">
                        <template #prefix>
                          <el-icon><Message /></el-icon>
                        </template>
                      </el-input>
                    </el-form-item>
                  </el-col>
                </el-row>
                <el-form-item label="个人简介">
                  <el-input 
                    v-model="userForm.bio" 
                    type="textarea" 
                    :rows="4" 
                    placeholder="分享一下您的专业方向或兴趣爱好..."
                    maxlength="200"
                    show-word-limit
                  />
                </el-form-item>
                <el-form-item label="通讯地址">
                  <el-input v-model="userForm.address" placeholder="详细的联系地址">
                    <template #prefix>
                      <el-icon><Location /></el-icon>
                    </template>
                  </el-input>
                </el-form-item>
                <div class="form-actions">
                  <el-button @click="resetForm">重置</el-button>
                  <el-button type="primary" @click="handleSave">
                    <el-icon><Check /></el-icon>
                    保存修改
                  </el-button>
                </div>
              </el-form>
            </el-tab-pane>

            <!-- Security Settings -->
            <el-tab-pane name="security">
              <template #label>
                <span class="tab-label">
                  <el-icon><Lock /></el-icon>
                  安全设置
                </span>
              </template>
              <div class="security-section">
                <div class="security-item">
                  <div class="security-icon password">
                    <el-icon><Key /></el-icon>
                  </div>
                  <div class="security-content">
                    <h4>账户密码</h4>
                    <p>定期更换密码可以提高账户安全性</p>
                    <div class="security-status">
                      <span class="status-label">当前强度：</span>
                      <el-progress :percentage="85" :stroke-width="8" :show-text="false" />
                      <span class="status-text strong">极高</span>
                    </div>
                  </div>
                  <el-button type="primary" link>修改密码</el-button>
                </div>

                <div class="security-item">
                  <div class="security-icon phone">
                    <el-icon><Iphone /></el-icon>
                  </div>
                  <div class="security-content">
                    <h4>密保手机</h4>
                    <p>已绑定手机号：+86 138-****-0000</p>
                  </div>
                  <el-button type="primary" link>更换手机</el-button>
                </div>

                <div class="security-item">
                  <div class="security-icon shield">
                    <el-icon><CircleCheck /></el-icon>
                  </div>
                  <div class="security-content">
                    <h4>双因子认证 (2FA)</h4>
                    <p>启用双因子认证，在登录时需要额外的动态验证码</p>
                  </div>
                  <el-switch v-model="twoFA" size="large" />
                </div>

                <div class="security-item">
                  <div class="security-icon sessions">
                    <el-icon><Monitor /></el-icon>
                  </div>
                  <div class="security-content">
                    <h4>活跃会话</h4>
                    <p>当前有 3 个活跃登录会话</p>
                  </div>
                  <el-button type="danger" link>管理会话</el-button>
                </div>
              </div>
            </el-tab-pane>

            <!-- Activity Log -->
            <el-tab-pane name="activity">
              <template #label>
                <span class="tab-label">
                  <el-icon><Clock /></el-icon>
                  最近动态
                </span>
              </template>
              <div class="activity-timeline">
                <div class="timeline-item" v-for="(log, index) in activityLogs" :key="index">
                  <div class="timeline-dot" :class="log.type"></div>
                  <div class="timeline-content">
                    <div class="timeline-header">
                      <span class="timeline-action">{{ log.action }}</span>
                      <span class="timeline-time">{{ log.time }}</span>
                    </div>
                    <p class="timeline-detail">{{ log.detail }}</p>
                  </div>
                </div>
              </div>
            </el-tab-pane>

            <!-- Notifications -->
            <el-tab-pane name="notifications">
              <template #label>
                <span class="tab-label">
                  <el-icon><Bell /></el-icon>
                  通知设置
                </span>
              </template>
              <div class="notification-settings">
                <div class="notification-item" v-for="notif in notifications" :key="notif.id">
                  <div class="notif-info">
                    <h4>{{ notif.title }}</h4>
                    <p>{{ notif.description }}</p>
                  </div>
                  <el-switch v-model="notif.enabled" />
                </div>
              </div>
            </el-tab-pane>
          </el-tabs>
        </div>

        <!-- Premium Upgrade Banner -->
        <div class="premium-banner">
          <div class="premium-icon">
            <el-icon><Trophy /></el-icon>
          </div>
          <div class="premium-content">
            <h3>升级到 ORIN Enterprise</h3>
            <p>获得更多训练额度、独占计算资源和 24/7 技术支持</p>
            <ul class="premium-features">
              <li><el-icon><Check /></el-icon> 无限知识库存储</li>
              <li><el-icon><Check /></el-icon> 优先模型训练</li>
              <li><el-icon><Check /></el-icon> 专属技术支持</li>
            </ul>
          </div>
          <el-button type="warning" size="large" round>
            <el-icon><Star /></el-icon>
            立即升级
          </el-button>
        </div>

        <!-- Danger Zone -->
        <div class="content-card danger-zone">
          <div class="card-header">
            <h3><el-icon><WarningFilled /></el-icon> 危险操作</h3>
          </div>
          <div class="card-body">
            <div class="danger-item">
              <div class="danger-info">
                <h4>退出登录</h4>
                <p>退出当前账号，需要重新登录</p>
              </div>
              <el-button type="danger" plain @click="handleLogout">退出账号</el-button>
            </div>
            <div class="danger-item">
              <div class="danger-info">
                <h4>删除账户</h4>
                <p>永久删除您的账户和所有数据，此操作不可恢复</p>
              </div>
              <el-button type="danger">删除账户</el-button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue';
import { 
  User, Camera, Message, Phone, Location, Calendar, Star,
  Edit, Share, Setting, EditPen, Lock, Clock, Bell,
  Key, Iphone, CircleCheck, Monitor, Check, Trophy,
  WarningFilled, Medal, TrendCharts,
  CaretTop, CaretBottom, Collection, DataAnalysis, ChatDotRound
} from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useRouter } from 'vue-router';
import { useUserStore } from '@/stores/user';
import { getUserProfile, updateUserProfile, uploadAvatar, updateUserAvatar } from '@/api/user';

const router = useRouter();
const userStore = useUserStore();
const activeTab = ref('settings');
const twoFA = ref(true);
const activityPeriod = ref('7d');
const avatarLoading = ref(false);
const avatarInput = ref(null);

// User data
const defaultUserData = {
  nickname: '',
  username: '',
  email: '',
  bio: '',
  address: '',
  avatar: '',
  userId: null
};

const userInfo = reactive({ ...defaultUserData });
const userForm = reactive({ ...defaultUserData });

// Stats data
const stats = ref([
  { 
    label: '知识库', 
    value: '12', 
    trend: 12.5,
    icon: 'Collection',
    color: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)'
  },
  { 
    label: '已训模型', 
    value: '48', 
    trend: 8.2,
    icon: 'DataAnalysis',
    color: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)'
  },
  { 
    label: '总会话', 
    value: '1.2k', 
    trend: -3.1,
    icon: 'ChatDotRound',
    color: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)'
  },
  { 
    label: '活跃天数', 
    value: '89', 
    trend: 5.7,
    icon: 'Calendar',
    color: 'linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)'
  }
]);

// Activity data for chart
const activityData = ref([
  { label: '周一', value: 65, count: 12 },
  { label: '周二', value: 85, count: 18 },
  { label: '周三', value: 45, count: 9 },
  { label: '周四', value: 95, count: 21 },
  { label: '周五', value: 75, count: 15 },
  { label: '周六', value: 55, count: 11 },
  { label: '周日', value: 40, count: 8 }
]);

// Skills
const skills = ref([
  'AI/ML', 'Python', 'Vue.js', 'React', 'Node.js', 
  'Docker', 'Kubernetes', 'AWS', 'RAG', 'LLM'
]);

// Activity logs
const activityLogs = ref([
  {
    action: '登录系统',
    detail: '登录成功 (IP: 192.168.1.182)',
    time: '2小时前',
    type: 'success'
  },
  {
    action: '更新智能体',
    detail: '修改了 "Code Assistant" 的检索阈值参数',
    time: '5小时前',
    type: 'info'
  },
  {
    action: '敏感操作',
    detail: '异常尝试停止节点 "Runtime-Server-02"',
    time: '1天前',
    type: 'warning'
  },
  {
    action: '创建知识库',
    detail: '成功创建新库 "ORIN 核心技术文档"',
    time: '2天前',
    type: 'success'
  },
  {
    action: '模型训练完成',
    detail: '模型 "Customer-Service-v2" 训练完成',
    time: '3天前',
    type: 'success'
  }
]);

// Notifications
const notifications = ref([
  {
    id: 1,
    title: '系统通知',
    description: '接收系统更新、维护通知等重要消息',
    enabled: true
  },
  {
    id: 2,
    title: '邮件通知',
    description: '通过邮件接收重要活动和更新',
    enabled: true
  },
  {
    id: 3,
    title: '会话提醒',
    description: '当有新的会话消息时通知您',
    enabled: false
  },
  {
    id: 4,
    title: '训练完成',
    description: '模型训练完成后发送通知',
    enabled: true
  }
]);

// Role display
const roleDisplay = computed(() => {
  if (userStore.roles && userStore.roles.length > 0) {
    if (userStore.roles.includes('ROLE_ADMIN')) {
      return '超级管理员';
    } else if (userStore.roles.includes('ROLE_USER')) {
      return '普通用户';
    }
  }
  return '用户';
});

// Random tag type for skills
const getRandomTagType = () => {
  const types = ['', 'success', 'info', 'warning', 'danger'];
  return types[Math.floor(Math.random() * types.length)];
};

onMounted(async () => {
  // Sync with store first
  if (userStore.userInfo) {
    Object.assign(userInfo, userStore.userInfo);
    Object.assign(userForm, userInfo);
  }

  // Then fetch latest from backend
  if (userStore.username) {
    try {
      const data = await getUserProfile(userStore.username);
      Object.assign(userInfo, {
        nickname: data.nickname || data.username || '未设置昵称',
        username: data.username || '',
        email: data.email || '未设置邮箱',
        bio: data.bio || '这个人很懒，还没有填写个人简介',
        address: data.address || '未设置地址',
        avatar: data.avatar || '',
        userId: data.userId || null
      });
      Object.assign(userForm, userInfo);
      // Update store as well
      userStore.updateUserInfo(data);
    } catch (e) {
      console.error('获取用户信息失败:', e);
    }
  }
});

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

  avatarLoading.value = true;
  try {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('uploadedBy', userInfo.username);
    
    // 1. Upload file
    const uploadRes = await uploadAvatar(formData);
    const avatarUrl = `/api/v1/multimodal/files/${uploadRes.id}/download`;
    
    // 2. Update user avatar URL
    await updateUserAvatar(userInfo.userId, avatarUrl);
    
    // 3. Update local state
    userInfo.avatar = avatarUrl;
    userForm.avatar = avatarUrl;
    
    // 4. Update store
    const updatedUserInfo = { ...userInfo };
    userStore.updateUserInfo(updatedUserInfo);
    
    ElMessage.success('头像更新成功');
  } catch (e) {
    console.error('头像上传失败:', e);
    ElMessage.error('头像更新失败');
  } finally {
    avatarLoading.value = false;
    if (avatarInput.value) {
      avatarInput.value.value = ''; // Reset input
    }
  }
};

const handleLogout = () => {
  ElMessageBox.confirm('确定要退出当前账号吗？', '安全退出', {
    confirmButtonText: '确定退出',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    userStore.logout();
    localStorage.removeItem('orin_user');
    ElMessage.success('已安全退出，正在跳转登录页...');
    setTimeout(() => {
      router.push('/login');
    }, 800);
  });
};

const handleSave = async () => {
  try {
    const updateData = {
      ...userForm,
      userId: userInfo.userId
    };
    const updated = await updateUserProfile(updateData);
    Object.assign(userInfo, updated);
    userStore.updateUserInfo(updated);
    
    ElMessage({
      message: '个人资料已成功同步到服务器',
      type: 'success',
      duration: 3000
    });
  } catch (e) {
    console.error('更新资料失败:', e);
  }
};

const resetForm = () => {
  Object.assign(userForm, userInfo);
  ElMessage.info('已重置为上次保存的内容');
};
</script>

<style scoped>
.profile-page {
  min-height: 100vh;
  padding-bottom: 40px;
}

/* Hero Section */
.profile-hero {
  position: relative;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 0 0 32px 32px;
  padding: 60px 40px 40px;
  margin-bottom: 40px;
  overflow: hidden;
}

.cover-gradient {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: 
    radial-gradient(circle at 20% 50%, rgba(255, 255, 255, 0.1) 0%, transparent 50%),
    radial-gradient(circle at 80% 80%, rgba(255, 255, 255, 0.1) 0%, transparent 50%);
  pointer-events: none;
}

.hero-content {
  position: relative;
  display: flex;
  justify-content: space-between;
  align-items: center;
  max-width: 1400px;
  margin: 0 auto;
}

.avatar-section {
  display: flex;
  align-items: center;
  gap: 24px;
}

.avatar-wrapper {
  position: relative;
}

.main-avatar {
  border: 5px solid rgba(255, 255, 255, 0.3);
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.3);
  transition: transform 0.3s ease;
}

.main-avatar:hover {
  transform: scale(1.05);
}

.avatar-edit-badge {
  position: absolute;
  bottom: 5px;
  right: 5px;
  width: 36px;
  height: 36px;
  background: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #667eea;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
  transition: all 0.3s ease;
}

.avatar-edit-badge:hover {
  background: #667eea;
  color: white;
  transform: scale(1.1);
}

.online-indicator {
  position: absolute;
  top: 5px;
  right: 5px;
  width: 20px;
  height: 20px;
  background: #22c55e;
  border: 3px solid white;
  border-radius: 50%;
  box-shadow: 0 0 0 3px rgba(34, 197, 94, 0.3);
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% {
    box-shadow: 0 0 0 3px rgba(34, 197, 94, 0.3);
  }
  50% {
    box-shadow: 0 0 0 6px rgba(34, 197, 94, 0.1);
  }
}

.user-info {
  color: white;
}

.user-name {
  font-size: 32px;
  font-weight: 800;
  margin: 0 0 8px 0;
  color: white;
}

.user-role {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  margin: 0 0 12px 0;
  opacity: 0.9;
}

.user-meta {
  display: flex;
  gap: 20px;
}

.meta-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  opacity: 0.85;
}

.quick-actions {
  display: flex;
  gap: 12px;
}

/* Stats Section */
.stats-section {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 20px;
  max-width: 1400px;
  margin: 0 auto 40px;
  padding: 0 40px;
}

.stat-card {
  background: white;
  border: 1px solid var(--neutral-gray-100);
  border-radius: 16px;
  padding: 24px;
  display: flex;
  align-items: center;
  gap: 20px;
  transition: all 0.3s ease;
  cursor: pointer;
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 24px rgba(0, 0, 0, 0.1);
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 24px;
  flex-shrink: 0;
}

.stat-content {
  flex: 1;
}

.stat-value {
  font-size: 28px;
  font-weight: 800;
  color: var(--neutral-gray-900);
  line-height: 1;
  margin-bottom: 4px;
}

.stat-label {
  font-size: 14px;
  color: var(--neutral-gray-500);
  margin-bottom: 8px;
}

.stat-trend {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  font-weight: 600;
  padding: 4px 8px;
  border-radius: 6px;
}

.stat-trend.up {
  color: #22c55e;
  background: rgba(34, 197, 94, 0.1);
}

.stat-trend.down {
  color: #ef4444;
  background: rgba(239, 68, 68, 0.1);
}

/* Content Grid */
.content-grid {
  display: grid;
  grid-template-columns: 400px 1fr;
  gap: 24px;
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 40px;
}

.left-column,
.right-column {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* Content Card */
.content-card {
  background: white;
  border: 1px solid var(--neutral-gray-100);
  border-radius: 16px;
  overflow: hidden;
}

.card-header {
  padding: 20px 24px;
  border-bottom: 1px solid var(--neutral-gray-100);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header h3 {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  font-weight: 700;
  color: var(--neutral-gray-900);
  margin: 0;
}

.card-body {
  padding: 24px;
}

/* About Card */
.info-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 20px;
}

.info-item:last-child {
  margin-bottom: 0;
}

.info-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--neutral-gray-400);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.info-value {
  font-size: 14px;
  color: var(--neutral-gray-700);
}

.info-value.bio {
  line-height: 1.6;
}

/* Activity Chart */
.activity-chart {
  margin-top: 20px;
}

.chart-bars {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  height: 180px;
  margin-bottom: 12px;
}

.chart-bar {
  flex: 1;
  background: var(--neutral-gray-100);
  border-radius: 6px 6px 0 0;
  position: relative;
  cursor: pointer;
  transition: all 0.3s ease;
  min-height: 20px;
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
  background: linear-gradient(180deg, #667eea 0%, #764ba2 100%);
  border-radius: 6px 6px 0 0;
}

.chart-labels {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--neutral-gray-400);
}

/* Skills Card */
.skills-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

/* Settings Card */
.profile-tabs :deep(.el-tabs__header) {
  padding: 0 24px;
  margin: 0;
  background: var(--neutral-gray-50);
}

.profile-tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.profile-tabs :deep(.el-tabs__content) {
  padding: 24px;
}

.tab-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  padding: 16px 0;
}

/* Profile Form */
.profile-form :deep(.el-form-item__label) {
  font-weight: 600;
  color: var(--neutral-gray-700);
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px solid var(--neutral-gray-100);
}

/* Security Section */
.security-section {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.security-item {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 20px;
  background: var(--neutral-gray-50);
  border-radius: 12px;
  transition: all 0.3s ease;
}

.security-item:hover {
  background: var(--neutral-gray-100);
}

.security-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}

.security-icon.password {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.security-icon.phone {
  background: linear-gradient(135deg, #22c55e 0%, #16a34a 100%);
  color: white;
}

.security-icon.shield {
  background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
  color: white;
}

.security-icon.sessions {
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
  color: white;
}

.security-content {
  flex: 1;
}

.security-content h4 {
  font-size: 15px;
  font-weight: 700;
  color: var(--neutral-gray-900);
  margin: 0 0 6px 0;
}

.security-content p {
  font-size: 13px;
  color: var(--neutral-gray-500);
  margin: 0;
}

.security-status {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
}

.status-label {
  font-size: 13px;
  color: var(--neutral-gray-600);
}

.security-status :deep(.el-progress) {
  flex: 1;
  max-width: 200px;
}

.status-text {
  font-size: 13px;
  font-weight: 600;
}

.status-text.strong {
  color: #22c55e;
}

/* Activity Timeline */
.activity-timeline {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.timeline-item {
  display: flex;
  gap: 16px;
  position: relative;
}

.timeline-item:not(:last-child)::before {
  content: '';
  position: absolute;
  left: 11px;
  top: 32px;
  bottom: -20px;
  width: 2px;
  background: var(--neutral-gray-200);
}

.timeline-dot {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  flex-shrink: 0;
  margin-top: 4px;
  position: relative;
  z-index: 1;
}

.timeline-dot.success {
  background: linear-gradient(135deg, #22c55e 0%, #16a34a 100%);
  box-shadow: 0 0 0 4px rgba(34, 197, 94, 0.1);
}

.timeline-dot.info {
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
  box-shadow: 0 0 0 4px rgba(59, 130, 246, 0.1);
}

.timeline-dot.warning {
  background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
  box-shadow: 0 0 0 4px rgba(245, 158, 11, 0.1);
}

.timeline-content {
  flex: 1;
}

.timeline-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.timeline-action {
  font-size: 15px;
  font-weight: 700;
  color: var(--neutral-gray-900);
}

.timeline-time {
  font-size: 13px;
  color: var(--neutral-gray-400);
}

.timeline-detail {
  font-size: 14px;
  color: var(--neutral-gray-600);
  margin: 0;
  line-height: 1.5;
}

/* Notification Settings */
.notification-settings {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.notification-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px;
  background: var(--neutral-gray-50);
  border-radius: 12px;
}

.notif-info h4 {
  font-size: 15px;
  font-weight: 700;
  color: var(--neutral-gray-900);
  margin: 0 0 6px 0;
}

.notif-info p {
  font-size: 13px;
  color: var(--neutral-gray-500);
  margin: 0;
}

/* Premium Banner */
.premium-banner {
  background: linear-gradient(135deg, #fbbf24 0%, #f59e0b 100%);
  border-radius: 16px;
  padding: 32px;
  color: white;
  display: flex;
  align-items: center;
  gap: 24px;
  position: relative;
  overflow: hidden;
}

.premium-banner::before {
  content: '';
  position: absolute;
  top: -50%;
  right: -10%;
  width: 300px;
  height: 300px;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.2) 0%, transparent 70%);
  pointer-events: none;
}

.premium-icon {
  width: 64px;
  height: 64px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  flex-shrink: 0;
}

.premium-content {
  flex: 1;
}

.premium-content h3 {
  font-size: 20px;
  font-weight: 800;
  margin: 0 0 8px 0;
}

.premium-content p {
  font-size: 14px;
  margin: 0 0 16px 0;
  opacity: 0.9;
}

.premium-features {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.premium-features li {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

/* Danger Zone */
.danger-zone .card-header h3 {
  color: var(--error-color);
}

.danger-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px;
  background: rgba(239, 68, 68, 0.05);
  border: 1px solid rgba(239, 68, 68, 0.2);
  border-radius: 12px;
  margin-bottom: 16px;
}

.danger-item:last-child {
  margin-bottom: 0;
}

.danger-info h4 {
  font-size: 15px;
  font-weight: 700;
  color: var(--neutral-gray-900);
  margin: 0 0 6px 0;
}

.danger-info p {
  font-size: 13px;
  color: var(--neutral-gray-500);
  margin: 0;
}

/* Responsive */
@media (max-width: 1200px) {
  .content-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .profile-hero {
    padding: 40px 20px 30px;
  }

  .hero-content {
    flex-direction: column;
    gap: 24px;
  }

  .avatar-section {
    flex-direction: column;
    text-align: center;
  }

  .user-meta {
    justify-content: center;
  }

  .quick-actions {
    width: 100%;
    justify-content: center;
  }

  .stats-section {
    padding: 0 20px;
    grid-template-columns: 1fr;
  }

  .content-grid {
    padding: 0 20px;
  }
}

/* Dark Mode Styles */
html.dark .profile-page {
  background: #0f172a;
}

/* Hero Section - Dark Mode */
html.dark .profile-hero {
  background: linear-gradient(135deg, #312e81 0%, #1e1b4b 100%);
}

html.dark .cover-gradient {
  background: 
    radial-gradient(circle at 20% 50%, rgba(139, 92, 246, 0.1) 0%, transparent 50%),
    radial-gradient(circle at 80% 80%, rgba(139, 92, 246, 0.1) 0%, transparent 50%);
}

html.dark .avatar-edit-badge {
  background: #1f2937;
  color: #a78bfa;
  border: 1px solid #374151;
}

html.dark .avatar-edit-badge:hover {
  background: #7c3aed;
  color: white;
}

html.dark .online-indicator {
  border-color: #1f2937;
}

/* Stats Cards - Dark Mode */
html.dark .stat-card {
  background: #1f2937;
  border-color: #374151;
}

html.dark .stat-card:hover {
  background: #374151;
  box-shadow: 0 12px 24px rgba(0, 0, 0, 0.5);
}

html.dark .stat-value {
  color: #f9fafb;
}

html.dark .stat-label {
  color: #9ca3af;
}

/* Content Cards - Dark Mode */
html.dark .content-card {
  background: #1f2937;
  border-color: #374151;
}

html.dark .card-header {
  border-bottom-color: #374151;
}

html.dark .card-header h3 {
  color: #f9fafb;
}

html.dark .card-body {
  color: #d1d5db;
}

/* About Card - Dark Mode */
html.dark .info-label {
  color: #6b7280;
}

html.dark .info-value {
  color: #e5e7eb;
}

/* Activity Chart - Dark Mode */
html.dark .chart-bar {
  background: #374151;
}

html.dark .bar-fill {
  background: linear-gradient(180deg, #7c3aed 0%, #6d28d9 100%);
}

html.dark .chart-labels {
  color: #6b7280;
}

/* Tabs - Dark Mode */
html.dark .profile-tabs :deep(.el-tabs__header) {
  background: #111827;
}

html.dark .profile-tabs :deep(.el-tabs__item) {
  color: #9ca3af;
}

html.dark .profile-tabs :deep(.el-tabs__item.is-active) {
  color: #a78bfa;
}

html.dark .profile-tabs :deep(.el-tabs__active-bar) {
  background-color: #a78bfa;
}

/* Form - Dark Mode */
html.dark .profile-form :deep(.el-form-item__label) {
  color: #d1d5db;
}

html.dark .profile-form :deep(.el-input__wrapper) {
  background-color: #111827;
  border-color: #374151;
  box-shadow: none;
}

html.dark .profile-form :deep(.el-input__wrapper:hover) {
  border-color: #4b5563;
}

html.dark .profile-form :deep(.el-input__wrapper.is-focus) {
  border-color: #a78bfa;
  box-shadow: 0 0 0 1px #a78bfa inset;
}

html.dark .profile-form :deep(.el-input__inner) {
  color: #f3f4f6;
}

html.dark .profile-form :deep(.el-textarea__inner) {
  background-color: #111827;
  border-color: #374151;
  color: #f3f4f6;
}

html.dark .profile-form :deep(.el-textarea__inner:hover) {
  border-color: #4b5563;
}

html.dark .profile-form :deep(.el-textarea__inner:focus) {
  border-color: #a78bfa;
}

html.dark .form-actions {
  border-top-color: #374151;
}

/* Security Section - Dark Mode */
html.dark .security-item {
  background: #111827;
}

html.dark .security-item:hover {
  background: #1f2937;
}

html.dark .security-content h4 {
  color: #f9fafb;
}

html.dark .security-content p {
  color: #9ca3af;
}

html.dark .status-label {
  color: #9ca3af;
}

html.dark .security-status :deep(.el-progress__text) {
  color: #d1d5db;
}

/* Timeline - Dark Mode */
html.dark .timeline-item::before {
  background: #374151;
}

html.dark .timeline-action {
  color: #f9fafb;
}

html.dark .timeline-time {
  color: #6b7280;
}

html.dark .timeline-detail {
  color: #9ca3af;
}

/* Notifications - Dark Mode */
html.dark .notification-item {
  background: #111827;
}

html.dark .notif-info h4 {
  color: #f9fafb;
}

html.dark .notif-info p {
  color: #9ca3af;
}

/* Premium Banner - Dark Mode */
html.dark .premium-banner {
  background: linear-gradient(135deg, #d97706 0%, #b45309 100%);
}

html.dark .premium-icon {
  background: rgba(0, 0, 0, 0.2);
}

/* Danger Zone - Dark Mode */
html.dark .danger-zone .card-header h3 {
  color: #f87171;
}

html.dark .danger-item {
  background: rgba(239, 68, 68, 0.1);
  border-color: rgba(239, 68, 68, 0.3);
}

html.dark .danger-info h4 {
  color: #f9fafb;
}

html.dark .danger-info p {
  color: #9ca3af;
}

/* Element Plus Components Dark Mode Overrides */
html.dark :deep(.el-button) {
  border-color: #374151;
}

html.dark :deep(.el-button--default) {
  background-color: #1f2937;
  color: #e5e7eb;
  border-color: #374151;
}

html.dark :deep(.el-button--default:hover) {
  background-color: #374151;
  border-color: #4b5563;
}

html.dark :deep(.el-button--primary) {
  background-color: #7c3aed;
  border-color: #7c3aed;
}

html.dark :deep(.el-button--primary:hover) {
  background-color: #6d28d9;
  border-color: #6d28d9;
}

html.dark :deep(.el-button.is-plain) {
  background-color: transparent;
  border-color: #374151;
  color: #d1d5db;
}

html.dark :deep(.el-button.is-plain:hover) {
  background-color: #1f2937;
  border-color: #4b5563;
  color: #f3f4f6;
}

html.dark :deep(.el-switch.is-checked .el-switch__core) {
  background-color: #7c3aed;
}

html.dark :deep(.el-tag) {
  background-color: #1f2937;
  border-color: #374151;
  color: #e5e7eb;
}

html.dark :deep(.el-tag.el-tag--success) {
  background-color: rgba(34, 197, 94, 0.2);
  border-color: rgba(34, 197, 94, 0.4);
  color: #4ade80;
}

html.dark :deep(.el-tag.el-tag--info) {
  background-color: rgba(59, 130, 246, 0.2);
  border-color: rgba(59, 130, 246, 0.4);
  color: #60a5fa;
}

html.dark :deep(.el-tag.el-tag--warning) {
  background-color: rgba(245, 158, 11, 0.2);
  border-color: rgba(245, 158, 11, 0.4);
  color: #fbbf24;
}

html.dark :deep(.el-tag.el-tag--danger) {
  background-color: rgba(239, 68, 68, 0.2);
  border-color: rgba(239, 68, 68, 0.4);
  color: #f87171;
}

html.dark :deep(.el-select .el-input__wrapper) {
  background-color: #111827;
  border-color: #374151;
}

html.dark :deep(.el-progress__text) {
  color: #d1d5db !important;
}

html.dark :deep(.el-select-dropdown) {
  background-color: #1f2937;
  border-color: #374151;
}

html.dark :deep(.el-select-dropdown__item) {
  color: #e5e7eb;
}

html.dark :deep(.el-select-dropdown__item:hover) {
  background-color: #374151;
}

html.dark :deep(.el-select-dropdown__item.selected) {
  color: #a78bfa;
}

</style>
