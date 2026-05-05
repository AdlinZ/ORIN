<template>
  <div class="login-container">
    <div class="login-shell">
      <section class="login-brand-panel">
        <BrandingLogo :height="68" class="login-logo" />
        <div class="brand-copy">
          <span class="brand-kicker">ORIN Enterprise AI Hub</span>
          <h1>企业 AI 中枢</h1>
          <p>统一接入智能体、知识资产、流程编排与运营观测，支撑企业级 AI 服务的治理与交付。</p>
        </div>
        <div class="trust-grid">
          <div class="trust-item">
            <span class="trust-label">服务治理</span>
            <strong>统一路由与权限</strong>
          </div>
          <div class="trust-item">
            <span class="trust-label">知识资产</span>
            <strong>可追踪可审计</strong>
          </div>
          <div class="trust-item">
            <span class="trust-label">运营观测</span>
            <strong>成本与链路透明</strong>
          </div>
        </div>
      </section>
      <section class="login-form-panel">
        <div class="login-form-wrapper">
          <h2 class="form-title">
            登录工作台
          </h2>
          <p class="form-subtitle">
            使用组织账号进入 ORIN 企业 AI 中枢
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

            <el-button 
              type="primary" 
              size="large" 
              class="login-btn" 
              :loading="loading" 
              @click="handleLogin"
            >
              登录
            </el-button>
          </el-form>
          <div class="security-note">
            <span>组织级访问控制</span>
            <span>会话有效期由管理员统一配置</span>
          </div>
        </div>
      </section>
    </div>
    <div class="login-footer">
      © 2025-2026 ORIN 企业 AI 中枢 |
      <a href="https://beian.miit.gov.cn/" target="_blank" style="color: inherit; text-decoration: none;">蜀ICP备2025125402号-3</a>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue';
import { useRouter } from 'vue-router';
import { User, Lock } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import Cookies from 'js-cookie';
import BrandingLogo from '@/components/BrandingLogo.vue';
import { useUserStore } from '@/stores/user';
import { useAppStore } from '@/stores/app';
import { ADMIN_MENU_ROLES, getDefaultHomeByRoles } from '@/router/topMenuConfig';
import { login } from '../api/auth';

const router = useRouter();
const userStore = useUserStore();
const appStore = useAppStore();
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

const isAdminLike = (roles = []) => roles.some((role) => ADMIN_MENU_ROLES.includes(role));

const handleLogin = async () => {
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

        const userRoles = roles || ['ROLE_USER'];
        userStore.login(token, user, userRoles);

        if (isAdminLike(userRoles)) {
          appStore.setMenuMode('sidebar');
        }

        // 设置 Cookie（用于跨页面恢复），记住我时过期时间更长
        const cookieExpires = rememberMe.value ? 7 : 1;
        Cookies.set('orin_token', token, { expires: cookieExpires });
        Cookies.set('orin_userInfo', JSON.stringify(user), { expires: cookieExpires });
        Cookies.set('orin_roles', JSON.stringify(userRoles), { expires: cookieExpires });

        const targetRoute = getDefaultHomeByRoles(userRoles);
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
  min-height: 100vh;
  width: 100%;
  box-sizing: border-box;
  background: #ffffff;
  display: flex;
  justify-content: center;
  align-items: center;
  position: relative;
  overflow-x: hidden;
  overflow-y: auto;
  padding: 32px;
}

.login-shell {
  width: 1080px;
  max-width: 90%;
  min-height: 600px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 460px;
  overflow: hidden;
  border: 1px solid var(--orin-border-strong, #d8e0e8);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.82);
  -webkit-backdrop-filter: blur(12px);
  backdrop-filter: blur(12px);
  box-shadow: 0 18px 44px rgba(15, 23, 42, 0.08);
}

.login-brand-panel {
  padding: 56px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 42px;
  border-right: 1px solid var(--orin-border-strong, #d8e0e8);
  background: #ffffff;
}

.login-logo {
  height: 68px;
  width: auto;
  align-self: flex-start;
}

.brand-copy {
  max-width: 560px;
}

.brand-kicker {
  display: block;
  margin-bottom: 16px;
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.brand-copy h1 {
  margin: 0 0 18px;
  color: #0f172a;
  font-size: 40px;
  line-height: 1.15;
  letter-spacing: 0;
}

.brand-copy p {
  margin: 0;
  color: #475569;
  font-size: 16px;
  line-height: 1.8;
}

.trust-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.trust-item {
  padding: 16px;
  border: 1px solid var(--orin-border-strong, #d8e0e8);
  border-radius: 8px;
  background: #ffffff;
}

.trust-label {
  display: block;
  margin-bottom: 8px;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.trust-item strong {
  color: #0f172a;
  font-size: 14px;
}

.login-form-panel {
  padding: 56px;
  background: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
}

.login-form-wrapper { width: 100%; max-width: 400px; }
.form-title { font-size: 28px; font-weight: 700; color: var(--neutral-gray-900); margin-bottom: 8px; }
.form-subtitle { color: var(--neutral-gray-400); margin-bottom: 35px; font-size: 14px; }

.login-btn { width: 100%; height: 48px; font-size: 16px; font-weight: 600; border-radius: var(--radius-lg); margin-top: 20px; }
.extra-actions { display: flex; justify-content: space-between; align-items: center; margin-top: -10px; margin-bottom: 10px; }

.security-note {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-top: 22px;
  padding-top: 18px;
  border-top: 1px solid var(--orin-border-strong, #d8e0e8);
  color: #64748b;
  font-size: 12px;
}

.login-footer {
  position: absolute;
  bottom: 24px;
  left: 50%;
  transform: translateX(-50%);
  color: var(--neutral-gray-400);
  font-size: 13px;
  white-space: nowrap;
}

@media (max-width: 960px) {
  .login-container {
    height: auto;
    min-height: 100vh;
    flex-direction: column;
    align-items: center;
    justify-content: flex-start;
    padding: 20px;
  }

  .login-shell {
    width: min(100%, 720px);
    max-width: none;
    min-height: 0;
    grid-template-columns: 1fr;
  }

  .login-brand-panel {
    padding: 28px 32px;
    border-right: none;
    border-bottom: 1px solid var(--orin-border-strong, #d8e0e8);
    gap: 18px;
    justify-content: flex-start;
  }

  .login-logo {
    height: 48px;
  }

  .brand-kicker {
    margin-bottom: 10px;
    font-size: 11px;
  }

  .brand-copy h1 {
    margin-bottom: 10px;
    font-size: 28px;
  }

  .brand-copy p {
    font-size: 14px;
    line-height: 1.65;
  }

  .login-form-panel {
    padding: 32px;
  }

  .trust-grid {
    display: none;
  }

  .login-footer {
    position: static;
    transform: none;
    margin-top: 18px;
    white-space: normal;
    text-align: center;
  }
}

@media (max-width: 640px) {
  .login-container {
    padding: 12px;
  }

  .login-shell {
    border-radius: 10px;
  }

  .login-brand-panel {
    padding: 22px 20px;
  }

  .login-logo {
    height: 40px;
  }

  .brand-copy h1 {
    font-size: 24px;
  }

  .brand-copy p {
    font-size: 13px;
  }

  .login-form-panel {
    padding: 26px 20px 24px;
  }

  .form-title {
    font-size: 24px;
  }

  .form-subtitle {
    margin-bottom: 24px;
    font-size: 13px;
  }

  .extra-actions,
  .security-note {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
  }

  .login-footer {
    font-size: 12px;
  }
}

html.dark .login-container {
  background: #0b1118;
}

html.dark .login-shell,
html.dark .login-brand-panel,
html.dark .login-form-panel {
  background: #111827;
  border-color: rgba(148, 163, 184, 0.22);
}

html.dark .brand-copy h1,
html.dark .trust-item strong {
  color: #f8fafc;
}

html.dark .brand-copy p,
html.dark .security-note {
  color: #94a3b8;
}

html.dark .trust-item {
  background: #0f172a;
  border-color: rgba(148, 163, 184, 0.22);
}

</style>
