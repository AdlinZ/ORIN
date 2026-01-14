<template>
  <div class="page-container">
    <PageHeader 
      title="个人中心" 
      description="管理您的个人资料、安全设置及最近的系统活动"
      icon="User"
    >
      <template #actions>
        <div class="user-meta-header">
          <div class="meta-item">
            <span class="label">账户状态</span>
            <el-tag type="success" size="small" effect="dark">Active</el-tag>
          </div>
          <div class="meta-item">
            <span class="label">角色级别</span>
            <span class="value">{{ roleDisplay }}</span>
          </div>
        </div>
      </template>
    </PageHeader>


    <el-row :gutter="24">
      <!-- Left: User Intro Card -->
      <el-col :lg="8" :md="24">
        <el-card shadow="never" class="premium-card user-main-card">
          <div class="user-header">
            <div class="avatar-uploader-box">
              <el-avatar :size="100" src="https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png" class="master-avatar" />
              <div class="avatar-badge"><el-icon><Camera /></el-icon></div>
            </div>
            <h2 class="user-display-name">{{ userInfo.username || userInfo.nickname }}</h2>
            <p class="user-sub-desc">{{ roleDisplay }} / ORIN Platform</p>
          </div>

          <div class="stat-grid">
            <div class="stat-box">
              <div class="num">12</div>
              <div class="label">知识库</div>
            </div>
            <div class="stat-box">
              <div class="num">48</div>
              <div class="label">已训模型</div>
            </div>
            <div class="stat-box">
              <div class="num">1.2k</div>
              <div class="label">总会话</div>
            </div>
          </div>

          <div class="info-details">
            <div class="info-row">
              <el-icon><Message /></el-icon>
              <span>{{ userInfo.email }}</span>
            </div>
            <div class="info-row">
              <el-icon><Phone /></el-icon>
              <span>+86 138-0000-0000</span>
            </div>
            <div class="info-row">
              <el-icon><Location /></el-icon>
              <span>中国 · 北京 · 中关村</span>
            </div>
          </div>

          <div class="card-footer-actions">
            <el-button type="danger" plain class="logout-wide-btn" @click="handleLogout">
              退出当前账号登录
            </el-button>
          </div>
        </el-card>
      </el-col>

      <!-- Right: Detailed Configuration -->
      <el-col :lg="16" :md="24">
        <el-card shadow="never" class="premium-card tabs-card">
          <el-tabs v-model="activeTab" class="custom-tabs">
            <!-- Account Settings -->
            <el-tab-pane name="account">
              <template #label>
                <div class="tab-label">
                  <el-icon><EditPen /></el-icon><span>个人设置</span>
                </div>
              </template>
              <el-form :model="userForm" label-position="top" class="premium-form">
                <el-row :gutter="20">
                  <el-col :span="12">
                    <el-form-item label="用户昵称">
                      <el-input v-model="userForm.nickname" placeholder="输入您的常用昵称" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="电子邮箱">
                      <el-input v-model="userForm.email" placeholder="official@example.com" />
                    </el-form-item>
                  </el-col>
                </el-row>
                <el-form-item label="个人简介">
                  <el-input 
                    v-model="userForm.bio" 
                    type="textarea" 
                    :rows="4" 
                    placeholder="分享一下您的专业方向或兴趣爱好..." 
                    resize="none"
                  />
                </el-form-item>
                <el-form-item label="通讯地址">
                  <el-input v-model="userForm.address" placeholder="详细的联系地址" />
                </el-form-item>
                <div class="form-footer">
                  <el-button type="primary" size="large" @click="handleSave" class="save-btn">
                    保存修改
                  </el-button>
                </div>
              </el-form>
            </el-tab-pane>

            <!-- Security Info -->
            <el-tab-pane name="security">
              <template #label>
                <div class="tab-label">
                  <el-icon><Lock /></el-icon><span>安全设置</span>
                </div>
              </template>
              <div class="security-list">
                <div class="security-item">
                  <div class="item-icon security"><el-icon><Key /></el-icon></div>
                  <div class="item-content">
                    <div class="title">账户密码</div>
                    <div class="desc">定期更换密码可以提高账户安全性。当前强度：<span class="text-success">极高</span></div>
                  </div>
                  <el-button link type="primary">修改资料</el-button>
                </div>
                
                <div class="security-item">
                  <div class="item-icon phone"><el-icon><Iphone /></el-icon></div>
                  <div class="item-content">
                    <div class="title">密保手机</div>
                    <div class="desc">已绑定手机号：+86 138-****-0000</div>
                  </div>
                  <el-button link type="primary">更换手机</el-button>
                </div>

                <div class="security-item">
                  <div class="item-icon shield"><el-icon><ShieldBadge /></el-icon></div>
                  <div class="item-content">
                    <div class="title">双因子认证 (2FA)</div>
                    <div class="desc">启用双因子认证，在登录时需要额外的动态验证码。</div>
                  </div>
                  <el-switch v-model="twoFA" active-color="#4f46e5" />
                </div>
              </div>
            </el-tab-pane>

            <!-- Personal Logs -->
            <el-tab-pane name="logs">
              <template #label>
                <div class="tab-label">
                  <el-icon><Histogram /></el-icon><span>最近动态</span>
                </div>
              </template>
              <div class="logs-container">
                <el-timeline>
                  <el-timeline-item timestamp="2024-03-24 18:00" type="primary" hollow>
                    <div class="log-entry">
                      <span class="action">登录系统</span>
                      <span class="detail">登录成功 (IP: 192.168.1.182)</span>
                    </div>
                  </el-timeline-item>
                  <el-timeline-item timestamp="2024-03-24 15:30" color="#4f46e5">
                    <div class="log-entry">
                      <span class="action">更新智能体</span>
                      <span class="detail">修改了 "Code Assistant" 的检索阈值参数</span>
                    </div>
                  </el-timeline-item>
                  <el-timeline-item timestamp="2024-03-23 10:15" color="#f59e0b">
                    <div class="log-entry alert">
                      <span class="action">敏感操作</span>
                      <span class="detail">异常尝试停止节点 "Runtime-Server-02"</span>
                    </div>
                  </el-timeline-item>
                  <el-timeline-item timestamp="2024-03-22 09:00">
                    <div class="log-entry">
                      <span class="action">创建知识库</span>
                      <span class="detail">成功创建新库 "ORIN 核心技术文档"</span>
                    </div>
                  </el-timeline-item>
                </el-timeline>
              </div>
            </el-tab-pane>
          </el-tabs>
        </el-card>

        <el-card shadow="none" class="promo-card">
          <div class="promo-content">
             <div class="promo-text">
                <h3>升级到 ORIN Enterprise</h3>
                <p>获得更多训练额度、独占计算资源和 24/7 技术支持。</p>
             </div>
             <el-button type="primary" round>立即升级</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue';
import PageHeader from '@/components/PageHeader.vue';
import { 
  User, Camera, Message, Phone, Location, 
  EditPen, Lock, Histogram, Key, Iphone, 
  SwitchButton as ShieldBadge 
} from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useRouter } from 'vue-router';
import { useUserStore } from '@/stores/user';
import Cookies from 'js-cookie';

const router = useRouter();
const userStore = useUserStore();
const activeTab = ref('account');
const twoFA = ref(true);

// 默认值，如果用户没有设置这些字段
const defaultUserData = {
  nickname: '',
  username: '',
  email: '',
  bio: '',
  address: ''
};

const userInfo = reactive({ ...defaultUserData });
const userForm = reactive({ ...defaultUserData });

// 角色显示名称
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

onMounted(() => {
  // 从 localStorage 获取用户信息
  const storedUser = localStorage.getItem('orin_user');
  if (storedUser) {
    try {
      const data = JSON.parse(storedUser);
      // 合并用户数据，保留默认值
      Object.assign(userInfo, {
        nickname: data.nickname || data.username || '未设置昵称',
        username: data.username || '',
        email: data.email || '未设置邮箱',
        bio: data.bio || '这个人很懒，还没有填写个人简介',
        address: data.address || '未设置地址'
      });
      Object.assign(userForm, userInfo);
    } catch (e) {
      console.error('解析用户信息失败:', e);
    }
  }
});

const handleLogout = () => {
  ElMessageBox.confirm('确定要退出当前账号吗？', '安全退出', {
    confirmButtonText: '确定退出',
    cancelButtonText: '取消',
    confirmButtonClass: 'danger-confirm-btn',
    type: 'warning'
  }).then(() => {
    // 使用 userStore 的 logout 方法
    userStore.logout();
    // 清除 localStorage
    localStorage.removeItem('orin_user');
    ElMessage.success('已安全退出，正在跳转登录页...');
    setTimeout(() => {
      router.push('/login');
    }, 800);
  });
};

const handleSave = () => {
  Object.assign(userInfo, userForm);
  localStorage.setItem('orin_user', JSON.stringify(userInfo));
  ElMessage({
    message: '个人资料已更新存档',
    type: 'success',
    duration: 3000
  });
};
</script>

<style scoped>
.page-container {
  padding: 0;
}

/* Header Banner - Common ORIN Style */
.header-banner {
  background: linear-gradient(135deg, var(--neutral-white) 0%, var(--neutral-gray-50) 100%);
  border: 1px solid var(--neutral-gray-100);
  border-radius: var(--border-radius-xl);
  padding: 32px;
  margin-bottom: 24px;
  box-shadow: var(--shadow-sm);
  position: relative;
  overflow: hidden;
}

.header-banner::before {
  content: '';
  position: absolute;
  top: -40%;
  right: -5%;
  width: 280px;
  height: 280px;
  background: radial-gradient(circle, var(--primary-glow) 0%, transparent 60%);
  opacity: 0.4;
  pointer-events: none;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.title-area {
  display: flex;
  align-items: center;
  gap: 20px;
}

.icon-box {
  width: 56px;
  height: 56px;
  background: linear-gradient(135deg, var(--primary-color), #4f46e5);
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 28px;
  box-shadow: 0 8px 20px var(--primary-glow);
}

.page-title {
  font-size: 24px;
  margin: 0;
  font-weight: 800;
  color: var(--neutral-gray-900);
}

.subtitle {
  margin: 4px 0 0;
  color: var(--neutral-gray-500);
  font-size: 14px;
}

.user-meta-header {
  display: flex;
  gap: 32px;
}

.meta-item {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.meta-item .label {
  font-size: 12px;
  color: var(--neutral-gray-400);
  margin-bottom: 4px;
}

.meta-item .value {
  font-size: 15px;
  font-weight: 700;
  color: var(--neutral-gray-800);
}

/* Premium Card Overrides */
.premium-card {
  border: 1px solid var(--neutral-gray-100) !important;
  border-radius: var(--border-radius-lg) !important;
  background: var(--neutral-white) !important;
}

.user-main-card {
  height: fit-content;
}

.user-header {
  text-align: center;
  padding: 10px 0 24px;
}

.avatar-uploader-box {
  position: relative;
  display: inline-block;
}

.master-avatar {
  border: 4px solid var(--neutral-white);
  box-shadow: 0 10px 25px rgba(0,0,0,0.1);
}

.avatar-badge {
  position: absolute;
  bottom: 5px;
  right: 5px;
  width: 32px;
  height: 32px;
  background: var(--neutral-white);
  border: 1px solid var(--neutral-gray-200);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--neutral-gray-600);
  cursor: pointer;
  box-shadow: 0 4px 8px rgba(0,0,0,0.05);
  transition: all 0.2s;
}

.avatar-badge:hover {
  background: var(--primary-color);
  color: white;
  transform: scale(1.1);
}

.user-display-name {
  font-size: 22px;
  font-weight: 800;
  margin: 16px 0 4px;
  color: var(--neutral-gray-900);
}

.user-sub-desc {
  font-size: 13px;
  color: var(--neutral-gray-400);
  margin: 0;
}

/* Stat Grid */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  padding: 24px 0;
  border-top: 1px solid var(--neutral-gray-100);
  border-bottom: 1px solid var(--neutral-gray-100);
}

.stat-box {
  text-align: center;
}

.stat-box .num {
  font-size: 20px;
  font-weight: 800;
  color: var(--primary-color);
}

.stat-box .label {
  font-size: 12px;
  color: var(--neutral-gray-400);
  margin-top: 2px;
}

/* Info Details */
.info-details {
  padding: 24px 10px;
}

.info-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  color: var(--neutral-gray-600);
  font-size: 14px;
}

.info-row .el-icon {
  font-size: 16px;
  color: var(--neutral-gray-400);
}

.card-footer-actions {
  padding-top: 10px;
}

.logout-wide-btn {
  width: 100%;
  height: 46px;
  font-weight: 600;
  border-radius: 12px;
}

/* Tabs Styling */
.tabs-card {
  padding: 8px;
}

.custom-tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.custom-tabs :deep(.el-tabs__header) {
  margin-bottom: 24px;
}

.tab-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
  font-size: 15px;
}

.premium-form {
  padding: 10px 10px 0;
}

.premium-form :deep(.el-form-item__label) {
  font-weight: 700;
  color: var(--neutral-gray-600);
  padding-bottom: 8px;
}

.form-footer {
  margin-top: 32px;
  display: flex;
  justify-content: flex-end;
}

.save-btn {
  padding: 12px 40px;
  height: 50px;
  font-weight: 700;
  box-shadow: 0 8px 16px var(--primary-glow);
}

/* Security List */
.security-list {
  padding: 0 10px;
}

.security-item {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 24px 0;
  border-bottom: 1px solid var(--neutral-gray-50);
}

.security-item:last-child {
  border-bottom: none;
}

.item-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
}

.item-icon.security { background: rgba(79, 70, 229, 0.1); color: #4f46e5; }
.item-icon.phone { background: rgba(34, 197, 94, 0.1); color: #22c55e; }
.item-icon.shield { background: rgba(245, 158, 11, 0.1); color: #f59e0b; }

.item-content {
  flex: 1;
}

.item-content .title {
  font-size: 15px;
  font-weight: 700;
  color: var(--neutral-gray-900);
  margin-bottom: 4px;
}

.item-content .desc {
  font-size: 13px;
  color: var(--neutral-gray-500);
}

/* Promo Card */
.promo-card {
  margin-top: 24px;
  background: linear-gradient(135deg, #1e1b4b 0%, #312e81 100%);
  border: none;
  border-radius: 20px;
  padding: 24px;
  color: white;
}

.promo-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.promo-text h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 800;
}

.promo-text p {
  margin: 6px 0 0;
  font-size: 13px;
  opacity: 0.8;
}

.logs-container {
  padding: 10px 20px;
}

.log-entry {
  display: flex;
  justify-content: space-between;
  font-size: 14px;
}

.log-entry .action {
  font-weight: 700;
  color: var(--neutral-gray-800);
}

.log-entry .detail {
  color: var(--neutral-gray-500);
}

.log-entry.alert .action {
  color: var(--error-color);
}

/* Dark Mode Specific Tweaks */
html.dark .header-banner {
  background: linear-gradient(135deg, #111827 0%, #030712 100%);
}

html.dark .security-item {
  border-bottom-color: var(--neutral-gray-100);
}

html.dark .info-row {
  color: var(--neutral-gray-400);
}

html.dark .promo-card {
  background: linear-gradient(135deg, #312e81 0%, #1e1b4b 100%);
}
</style>
