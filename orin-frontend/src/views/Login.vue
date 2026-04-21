<template>
  <div class="login-container">
    <div class="login-box animate-scale">
      <div class="login-left">
        <div class="app-info">
          <BrandingLogo :height="80" class="login-logo" />
          <h1>Platform</h1>
          <p>智能体管理与全链路监控系统</p>
          <div class="feature-list">
            <div class="feature-item">
              <el-icon><CircleCheckFilled /></el-icon> 实时性能反馈
            </div>
            <div class="feature-item">
              <el-icon><CircleCheckFilled /></el-icon> 分布式知识库同步
            </div>
            <div class="feature-item">
              <el-icon><CircleCheckFilled /></el-icon> 自动化运维审计
            </div>
          </div>
        </div>
      </div>
      <div class="login-right">
        <div class="login-form-wrapper">
          <h2 class="form-title">
            用户登录
          </h2>
          <p class="form-subtitle">
            欢迎回来,请使用您的账号登录
          </p>

          <el-form
            ref="formRef"
            :model="loginForm"
            :rules="loginRules"
            label-position="top"
          >
            <el-form-item label="用户名" prop="username">
              <el-input
                v-model="loginForm.username"
                placeholder="请输入用户名"
                :prefix-icon="User"
              />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input
                v-model="loginForm.password"
                type="password"
                show-password
                placeholder="请输入密码"
                :prefix-icon="Lock"
                @keyup.enter="handleLogin"
              />
            </el-form-item>
            
            <div class="extra-actions">
              <el-checkbox v-model="rememberMe">
                记住我
              </el-checkbox>
              <el-button link type="primary">
                忘记密码?
              </el-button>
            </div>

            <!-- Custom Slide Verify -->
            <div class="slide-verify-wrapper">
              <div 
                ref="trackRef" 
                class="slide-track"
                :class="{ 'is-success': isVerified }"
              >
                <div class="slide-bg" :style="{ width: slideWidth + 'px' }" />
                <div class="slide-text">
                  {{ verifyText }}
                </div>
                <div 
                  class="slide-handler" 
                  :style="{ left: slideWidth + 'px' }"
                  @mousedown="startDrag"
                  @touchstart="startDrag"
                >
                  <el-icon v-if="isVerified">
                    <Check />
                  </el-icon>
                  <el-icon v-else>
                    <ArrowRight />
                  </el-icon>
                </div>
              </div>
            </div>

            <el-button 
              type="primary" 
              size="large" 
              class="login-btn" 
              :loading="loading" 
              :disabled="!isVerified"
              @click="handleLogin"
            >
              登 录
            </el-button>
          </el-form>

          <div class="social-login">
            <el-divider><span class="divider-text">其他登录方式</span></el-divider>
            <div class="icons">
              <el-button circle>
                <el-icon><Share /></el-icon>
              </el-button>
              <el-button circle>
                <el-icon><Link /></el-icon>
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </div>
    

     
    
    <div class="login-footer">
      © 2025-2026 ORIN - Advanced Agent Management & Monitoring System | 
      <a href="https://beian.miit.gov.cn/" target="_blank" style="color: inherit; text-decoration: none;">蜀ICP备2025125402号-3</a>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import { User, Lock, CircleCheckFilled, Share, Link, ArrowRight, Check } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import Cookies from 'js-cookie';
import BrandingLogo from '@/components/BrandingLogo.vue';
import { useUserStore } from '@/stores/user';
import { getDefaultHomeByRoles } from '@/router/topMenuConfig';
import { login } from '../api/auth';

const router = useRouter();
const userStore = useUserStore();
const loading = ref(false);
const rememberMe = ref(false);
const formRef = ref(null);

const loginForm = reactive({
  username: '',
  password: ''
});

const loginRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
};

// Slide Verify Logic
const trackRef = ref(null);
const isVerified = ref(false);
const slideWidth = ref(0);
const startX = ref(0);
const isDragging = ref(false);
const verifyText = ref('按住滑块拖动到最右边');

const startDrag = (e) => {
  if (isVerified.value) return;
  isDragging.value = true;
  startX.value = e.pageX || e.touches[0].pageX;
};

const onDrag = (e) => {
  if (!isDragging.value || isVerified.value) return;
  const currentX = e.pageX || e.touches[0].pageX;
  const diff = currentX - startX.value;
  const max = trackRef.value.offsetWidth - 40; // 40 is handler width

  if (diff > 0 && diff <= max) {
    slideWidth.value = diff;
  } else if (diff > max) {
    slideWidth.value = max;
    isVerified.value = true;
    verifyText.value = '验证通过';
    isDragging.value = false;
  }
};

const stopDrag = () => {
  if (isVerified.value) return;
  isDragging.value = false;
  // Reset with animation
  const step = () => {
    if (slideWidth.value > 0) {
      slideWidth.value -= 10;
      requestAnimationFrame(step);
    } else {
      slideWidth.value = 0;
    }
  };
  requestAnimationFrame(step);
};

onMounted(() => {
  document.addEventListener('mousemove', onDrag);
  document.addEventListener('mouseup', stopDrag);
  document.addEventListener('touchmove', onDrag);
  document.addEventListener('touchend', stopDrag);
});

onUnmounted(() => {
  document.removeEventListener('mousemove', onDrag);
  document.removeEventListener('mouseup', stopDrag);
  document.removeEventListener('touchmove', onDrag);
  document.removeEventListener('touchend', stopDrag);
});

const handleLogin = async () => {
  if (!isVerified.value) {
    ElMessage.warning('请先完成滑动验证');
    return;
  }
  if (!formRef.value) return;

  await formRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true;
      try {
        const loginData = {
          ...loginForm,
          rememberMe: rememberMe.value
        };
        const res = await login(loginData);
        const token = res.token || (res.data && res.data.token);
        const user = res.user || (res.data && res.data.user);
        const roles = res.roles || (res.data && res.data.roles);

        if (!token) throw new Error('Invalid login response');

        ElMessage.success('登录成功');

        // 根据 rememberMe 选择存储方式
        // 如果记住我：存储到 localStorage 并设置 Cookie 7天
        // 如果不记住我：仅存储到 sessionStorage（浏览器关闭后清除）
        if (rememberMe.value) {
          localStorage.setItem('orin_token', token);
          localStorage.setItem('orin_user', JSON.stringify(user));
        } else {
          sessionStorage.setItem('orin_token', token);
          sessionStorage.setItem('orin_user', JSON.stringify(user));
        }

        userStore.login(token, user, roles || ['ROLE_USER']);

        // 设置 Cookie（用于跨页面恢复），记住我时过期时间更长
        const cookieExpires = rememberMe.value ? 7 : 1;
        Cookies.set('orin_token', token, { expires: cookieExpires });
        Cookies.set('orin_userInfo', JSON.stringify(user), { expires: cookieExpires });
        Cookies.set('orin_roles', JSON.stringify(roles || ['ROLE_USER']), { expires: cookieExpires });

        const targetRoute = getDefaultHomeByRoles(roles || ['ROLE_USER']);
        setTimeout(() => router.push(targetRoute), 500);
      } catch (error) {
        ElMessage.error('登录失败: ' + (error.response?.data?.message || '请检查账号密码'));
      } finally {
        loading.value = false;
      }
    }
  });
};
</script>

<style scoped>
.login-container {
  height: 100vh;
  width: 100vw;
  background: linear-gradient(135deg, var(--orin-bg) 0%, var(--orin-primary-fade) 100%);
  display: flex;
  justify-content: center;
  align-items: center;
  position: relative;
  overflow: hidden;
}

.login-box {
  width: 1000px;
  max-width: 90%;
  height: 600px;
  background: var(--orin-bg-white);
  border-radius: var(--radius-2xl);
  box-shadow: 0 40px 100px rgba(0,0,0,0.08);
  display: flex;
  overflow: hidden;
}

.login-left {
  flex: 1.2;
  background: linear-gradient(135deg, #1d2129 0%, #313742 100%);
  padding: 60px;
  color: #fff;
  display: flex;
  align-items: center;
}

.login-logo { height: 80px; width: auto; margin-bottom: 32px; }
.login-left h1 { font-size: 32px; font-weight: 800; margin-bottom: 12px; }
.login-left p { color: rgba(255,255,255,0.6); margin-bottom: 40px; }

.feature-list { display: flex; flex-direction: column; gap: 20px; }
.feature-item { display: flex; align-items: center; gap: 12px; font-size: 15px; color: rgba(255,255,255,0.9); }
.feature-item .el-icon { color: var(--primary-color); font-size: 20px; }

.login-right {
  flex: 1.5;
  padding: 60px;
  background: var(--orin-bg-white);
  display: flex;
  justify-content: center;
  align-items: center;
}

.login-form-wrapper { width: 100%; max-width: 400px; }
.form-title { font-size: 28px; font-weight: 700; color: var(--neutral-gray-900); margin-bottom: 8px; }
.form-subtitle { color: var(--neutral-gray-400); margin-bottom: 35px; font-size: 14px; }

.login-btn { width: 100%; height: 48px; font-size: 16px; font-weight: 600; border-radius: var(--radius-lg); margin-top: 20px; }
.extra-actions { display: flex; justify-content: space-between; align-items: center; margin-top: -10px; margin-bottom: 10px; }

.social-login { margin-top: 40px; text-align: center; }
.divider-text { font-size: 12px; color: var(--neutral-gray-400); }
.icons { margin-top: 15px; display: flex; justify-content: center; gap: 20px; }

.login-footer {
  position: absolute;
  bottom: 24px;
  left: 50%;
  transform: translateX(-50%);
  color: var(--neutral-gray-400);
  font-size: 13px;
  white-space: nowrap;
}

.animate-scale { animation: scaleIn 0.6s ease; }
@keyframes scaleIn { from { opacity: 0; transform: scale(0.95); } to { opacity: 1; transform: scale(1); } }

html.dark .login-box { background: var(--neutral-gray-800); box-shadow: 0 40px 100px rgba(0,0,0,0.3); }
html.dark .login-left { background: var(--neutral-gray-900); }

/* Slide Verify Styles */
.slide-verify-wrapper {
  margin: 20px 0;
  user-select: none;
}
.slide-track {
  position: relative;
  width: 100%;
  height: 40px;
  background: #f2f3f5;
  border-radius: 4px;
  overflow: hidden;
  border: 1px solid #e5e6eb;
}
.slide-bg {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  background-color: var(--success-500);
  z-index: 1;
}
.slide-text {
  position: absolute;
  width: 100%;
  height: 100%;
  line-height: 40px;
  text-align: center;
  font-size: 14px;
  color: #86909c;
  z-index: 2;
  pointer-events: none;
}
.slide-track.is-success .slide-text {
  color: #fff;
}
.slide-handler {
  position: absolute;
  top: 0;
  left: 0;
  width: 40px;
  height: 40px;
  background: #fff;
  border: 1px solid #e5e6eb;
  border-radius: 4px;
  cursor: grab;
  z-index: 3;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.3s;
  box-shadow: 0 2px 4px rgba(0,0,0,0.05);
}
.slide-handler:hover {
  background: #f7f8fa;
}
.slide-handler:active {
  cursor: grabbing;
}
.slide-track.is-success .slide-handler {
  color: var(--success-500);
  cursor: default;
}

</style>
