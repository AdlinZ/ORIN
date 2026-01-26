<template>
  <div class="landing-wrapper">
    <!-- Landing Navbar -->
    <nav class="landing-nav animate-fade">
      <div class="nav-content">
        <div class="logo-area">
          <img src="/vite.svg" alt="Logo" class="mini-logo" />
          <span class="logo-text">ORIN Platform</span>
        </div>
        <div class="nav-links">
          <el-button link @click="$router.push('/dashboard/monitor')">监控中心</el-button>
          <el-button link @click="$router.push('/dashboard/agent/list')">智能体管理</el-button>
          
          <template v-if="isLoggedIn">
            <el-dropdown trigger="click" @command="handleCommand">
              <div class="avatar-wrapper shadow-hover">
                <div class="user-info-text">
                  <span class="user-name">{{ userInfo.name || '用户' }}</span>
                  <span class="user-tag">{{ userInfo.role || 'USER' }}</span>
                </div>
                <el-avatar 
                  :size="36" 
                  :src="userInfo.avatar || 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'" 
                />
                <el-icon class="caret-icon"><CaretBottom /></el-icon>
              </div>
              <template #dropdown>
                <el-dropdown-menu class="user-dropdown">
                  <el-dropdown-item command="console">
                    <el-icon><MonitorIcon /></el-icon>进入控制台
                  </el-dropdown-item>
                  <el-dropdown-item command="profile">
                    <el-icon><UserIcon /></el-icon>个人中心
                  </el-dropdown-item>
                  <el-dropdown-item divided command="logout" class="logout-item">
                    <el-icon><SwitchButton /></el-icon>退出登录
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <el-button v-else type="primary" size="small" round @click="$router.push('/login')">
            立即登录
          </el-button>
        </div>
      </div>
    </nav>

    <div class="landing-page">
      <!-- Hero Section -->
    <section class="hero-section">
      <div class="hero-content">
        <h1 class="hero-title animate-up">ORIN 智能体监控与管理系统</h1>
        <p class="hero-subtitle animate-up delay-1">
          下一代企业级 AI 智能体治理平台，连接 Dify 与业务系统的桥梁。
          实现全链路监控、知识库同步与模型智能调度。
        </p>
        <div class="hero-actions animate-up delay-2">
          <el-button type="primary" size="large" class="start-btn" @click="$router.push('/dashboard/monitor')">
             进入监控大屏 <el-icon class="el-icon--right"><ArrowRight /></el-icon>
          </el-button>
          <el-button size="large" class="secondary-btn" @click="$router.push('/dashboard/agent/onboard')">
             接入新智能体
          </el-button>
        </div>
      </div>
      <div class="hero-image animate-fade delay-3">
         <div class="glass-card">
            <div class="card-inner">
               <div class="pulse-dot"></div>
               <div class="line long"></div>
               <div class="line short"></div>
               <div class="line medium"></div>
            </div>
         </div>
         <div class="bg-gradient"></div>
      </div>
    </section>

    <!-- Features Section -->
    <section class="features-section">
      <div class="section-header">
        <h2 class="section-title">核心功能特性</h2>
        <div class="title-underline"></div>
      </div>
      <el-row :gutter="30">
        <el-col :span="8" v-for="(feature, index) in features" :key="index">
          <div class="feature-card shadow-hover">
            <div class="feature-icon" :style="{ backgroundColor: feature.bgColor }">
              <el-icon :style="{ color: feature.color }"><component :is="feature.icon" /></el-icon>
            </div>
            <h3>{{ feature.title }}</h3>
            <p>{{ feature.desc }}</p>
          </div>
        </el-col>
      </el-row>
    </section>

    <!-- Stats Preview -->
    <section class="quick-stats">
       <div class="stat-box">
          <div class="stat-num">99.9%</div>
          <div class="stat-text">服务可用性</div>
       </div>
       <div class="stat-box">
          <div class="stat-num">< 200ms</div>
          <div class="stat-text">平均响应延迟</div>
       </div>
       <div class="stat-box">
          <div class="stat-num">Unlimited</div>
          <div class="stat-text">横向扩展能力</div>
       </div>
    </section>

    <!-- Footer Meta -->
    <footer class="landing-footer">
       <p>© 2024 ORIN - Advanced Agent Management & Monitoring System</p>
       <div class="links">
          <span>Documentation</span>
          <span>Privacy Policy</span>
          <span>API References</span>
       </div>
    </footer>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import Cookies from 'js-cookie';
import { ElMessage } from 'element-plus';
import { useUserStore } from '@/stores/user';
import { 
  ArrowRight, Monitor, Cpu, Connection, Tickets, 
  Lock, Share, CaretBottom, User, SwitchButton,
  Monitor as MonitorIcon, User as UserIcon
} from '@element-plus/icons-vue';

const router = useRouter();
const userStore = useUserStore();
const isLoggedIn = ref(false);
const userInfo = reactive({
  name: '',
  role: '',
  avatar: ''
});

const checkLoginStatus = () => {
  // Use userStore to ensure consistency with Dashboard
  const token = Cookies.get('orin_token');
  isLoggedIn.value = !!token;
  
  if (token) {
    if (userStore.userInfo) {
      // Prioritize username
      userInfo.name = userStore.userInfo.username || userStore.userInfo.nickname || userStore.userInfo.name || '用户';
      userInfo.avatar = userStore.userInfo.avatar || '';
      
      // Handle roles
      if (userStore.roles && userStore.roles.length > 0) {
         const roleMap = { 'ROLE_ADMIN': 'ADMIN', 'ROLE_USER': 'USER' };
         userInfo.role = roleMap[userStore.roles[0]] || userStore.roles[0];
      } else {
         userInfo.role = 'USER';
      }
    } else {
       // Fallback if store is empty roughly (shouldn't happen with restoreFromCookies)
       userStore.restoreFromCookies();
       // Re-check after restore
       if (userStore.userInfo) {
          userInfo.name = userStore.userInfo.username || userStore.userInfo.nickname || '用户';
          userInfo.avatar = userStore.userInfo.avatar || '';
          if (userStore.roles && userStore.roles.length > 0) {
             const roleMap = { 'ROLE_ADMIN': 'ADMIN', 'ROLE_USER': 'USER' };
             userInfo.role = roleMap[userStore.roles[0]] || userStore.roles[0];
          } else {
             userInfo.role = 'USER';
          }
       }
    }
  }
};

const handleLogout = () => {
  Cookies.remove('orin_token');
  localStorage.removeItem('orin_user');
  isLoggedIn.value = false;
  ElMessage.success('已退出登录');
  router.push('/');
};

const handleCommand = (command) => {
  switch (command) {
    case 'console':
      router.push('/dashboard/monitor');
      break;
    case 'profile':
      router.push('/dashboard/profile');
      break;
    case 'logout':
      handleLogout();
      break;
  }
};

onMounted(() => {
  checkLoginStatus();
});

const features = [
  { 
    title: '全链路监控', 
    desc: '实时追踪 CPU、内存利用率及令牌消耗，秒级洞察系统性能瓶颈。', 
    icon: Monitor, 
    color: 'var(--orin-primary)', 
    bgColor: 'rgba(64, 158, 255, 0.1)' 
  },
  { 
    title: '知识库自动同步', 
    desc: '深度集成 Dify 知识库，支持文档版本管理与云端动态资产更新。', 
    icon: Connection, 
    color: '#67C23A', 
    bgColor: 'rgba(103, 194, 58, 0.1)' 
  },
  { 
    title: '分布式生命周期', 
    desc: '从接入到注销，一站式控制不同环境下的智能体运行状态。', 
    icon: Cpu, 
    color: '#E6A23C', 
    bgColor: 'rgba(230, 162, 60, 0.1)' 
  },
  { 
    title: '交互日志审计', 
    desc: '全面保留会话流水与逻辑树，支持全站事件溯源与安全合规审查。', 
    icon: Tickets, 
    color: '#F56C6C', 
    bgColor: 'rgba(245, 108, 108, 0.1)' 
  },
  { 
    title: '模型安全管理', 
    desc: '密钥托管与多租户权限隔离，确保留言资产与模型接口访问安全。', 
    icon: Lock, 
    color: '#909399', 
    bgColor: 'rgba(144, 147, 153, 0.1)' 
  },
  { 
    title: '开放协同生态', 
    desc: '标准 Webhook 接口与 API 扩展，轻松对接现有 DevOps 运维体系。', 
    icon: Share, 
    color: '#722ed1', 
    bgColor: 'rgba(114, 46, 209, 0.1)' 
  }
];
</script>

<style scoped>
.landing-wrapper {
  min-height: 100vh;
  background-color: var(--neutral-bg);
  color: var(--neutral-gray-6);
  overflow-x: hidden;
}

.landing-nav {
  height: 70px;
  border-bottom: 1px solid rgba(0,0,0,0.05);
  display: flex;
  align-items: center;
  position: sticky;
  top: 0;
  background: rgba(255,255,255,0.8);
  backdrop-filter: blur(10px);
  z-index: 100;
}
html.dark .landing-nav {
  background: rgba(20,20,20,0.8);
  border-bottom: 1px solid rgba(255,255,255,0.05);
}

.nav-content {
  max-width: 1400px;
  width: 100%;
  margin: 0 auto;
  padding: 0 40px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.logo-area { 
  display: flex; 
  align-items: center; 
  gap: 10px; 
}

.nav-links {
  display: flex;
  align-items: center;
  gap: 20px;
}
.mini-logo { width: 28px; height: 28px; }
.logo-text { font-weight: 700; font-size: 18px; color: var(--neutral-black); }

/* User Profile Styles */
.user-profile-section {
  display: flex;
  align-items: center;
}

.avatar-wrapper {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 4px 12px;
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: all 0.3s;
  background: rgba(255,255,255,0.5);
  border: 1px solid var(--neutral-gray-100);
}

.avatar-wrapper:hover {
  background: white;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(0,0,0,0.05);
}

.user-info-text {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.user-name {
  font-size: 13px;
  font-weight: 700;
  color: var(--neutral-gray-900);
}

.user-tag {
  font-size: 9px;
  font-weight: 800;
  color: var(--primary-color);
  background: var(--primary-light);
  padding: 0 4px;
  border-radius: var(--radius-xs);
  text-transform: uppercase;
}

.caret-icon {
  font-size: 12px;
  color: var(--neutral-gray-400);
}

.logout-item {
  color: var(--error-color) !important;
}

.landing-page {
  padding: 0 40px;
  max-width: 1400px;
  margin: 0 auto;
}

/* Hero Section */
.hero-section {
  display: flex;
  align-items: center;
  min-height: 500px;
  padding: 60px 0;
  gap: 60px;
}

.hero-content {
  flex: 1;
}

.hero-title {
  font-size: 52px;
  font-weight: 800;
  line-height: 1.1;
  color: var(--neutral-black);
  margin-bottom: 24px;
  letter-spacing: -0.5px;
}

.hero-subtitle {
  font-size: 18px;
  line-height: 1.6;
  color: var(--neutral-gray-5);
  margin-bottom: 40px;
  max-width: 600px;
}

.hero-actions {
  display: flex;
  gap: 16px;
}

.start-btn {
  padding: 15px 35px;
  font-weight: 600;
  border-radius: var(--radius-lg);
  box-shadow: 0 8px 20px var(--primary-glow);
}

.secondary-btn {
  padding: 15px 35px;
  font-weight: 600;
  border-radius: var(--radius-lg);
}

.hero-image {
  flex: 0.8;
  position: relative;
  display: flex;
  justify-content: center;
  align-items: center;
}

.glass-card {
  width: 320px;
  height: 200px;
  background: rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: var(--radius-2xl);
  z-index: 2;
  padding: 30px;
  box-shadow: 0 25px 50px rgba(0,0,0,0.1);
  display: flex;
  align-items: center;
}

.card-inner {
  width: 100%;
}

.pulse-dot {
  width: 12px;
  height: 12px;
  background: var(--primary-color);
  border-radius: 50%;
  margin-bottom: 20px;
  box-shadow: 0 0 0 0 var(--primary-glow);
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0% { transform: scale(0.95); box-shadow: 0 0 0 0 var(--primary-glow); }
  70% { transform: scale(1); box-shadow: 0 0 0 15px rgba(99, 102, 241, 0); }
  100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(99, 102, 241, 0); }
}

.line { height: 8px; border-radius: var(--radius-xs); background: rgba(0,0,0,0.05); margin-bottom: 12px; }
.line.short { width: 40%; }
.line.medium { width: 70%; }
.line.long { width: 90%; }
html.dark .line { background: rgba(255,255,255,0.1); }

.bg-gradient {
  position: absolute;
  width: 400px;
  height: 400px;
  background: radial-gradient(circle, var(--primary-light) 0%, transparent 70%);
  z-index: 1;
}

/* Features Section */
.features-section {
  padding: 80px 0;
}

.section-header {
  text-align: center;
  margin-bottom: 60px;
}

.section-title {
  font-size: 32px;
  font-weight: 700;
  color: var(--neutral-black);
  margin-bottom: 12px;
}

.title-underline {
  width: 60px;
  height: 4px;
  background: var(--primary-color);
  margin: 0 auto;
  border-radius: 2px;
}

.feature-card {
  background: var(--neutral-white);
  padding: 40px;
  border-radius: var(--radius-2xl);
  border: 1px solid var(--neutral-gray-2);
  height: 100%;
  margin-bottom: 30px;
  transition: all 0.3s ease;
}

.feature-card:hover {
  transform: translateY(-10px);
}

.feature-icon {
  width: 56px;
  height: 56px;
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  margin-bottom: 24px;
}

.feature-card h3 {
  font-size: 20px;
  margin-bottom: 12px;
  color: var(--neutral-black);
}

.feature-card p {
  color: var(--neutral-gray-5);
  line-height: 1.6;
}

/* Quick Stats */
.quick-stats {
  display: flex;
  justify-content: space-around;
  padding: 60px 0;
  border-top: 1px solid var(--neutral-gray-2);
  border-bottom: 1px solid var(--neutral-gray-2);
  margin: 40px 0;
}

.stat-box { text-align: center; }
.stat-num { font-size: 36px; font-weight: 800; color: var(--primary-color); margin-bottom: 8px; }
.stat-text { color: var(--neutral-gray-4); font-size: 14px; text-transform: uppercase; letter-spacing: 1px; }

/* Footer */
.landing-footer {
  padding: 60px 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: var(--neutral-gray-4);
  font-size: 14px;
}

.links { display: flex; gap: 30px; }
.links span { cursor: pointer; transition: color 0.3s; }
.links span:hover { color: var(--primary-color); }

/* Animations */
.animate-up { opacity: 0; transform: translateY(20px); animation: fadeUp 0.8s forwards; }
.animate-fade { opacity: 0; animation: fadeIn 1s forwards; }
.delay-1 { animation-delay: 0.2s; }
.delay-2 { animation-delay: 0.4s; }
.delay-3 { animation-delay: 0.6s; }

@keyframes fadeUp { to { opacity: 1; transform: translateY(0); } }
@keyframes fadeIn { to { opacity: 1; } }

@media (max-width: 900px) {
  .hero-section { flex-direction: column; text-align: center; }
  .hero-subtitle { margin: 0 auto 40px; }
  .hero-actions { justify-content: center; }
  .hero-image { display: none; }
}
</style>
