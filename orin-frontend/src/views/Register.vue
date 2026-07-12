<template>
  <div class="register-container">
    <div class="register-shell">
      <section class="register-brand-panel">
        <BrandingLogo :height="68" class="register-logo" />
        <div class="brand-copy">
          <span class="brand-kicker">ORIN Personal Workspace</span>
          <h1>创建个人账号</h1>
          <p>进入 ORIN Chat 和开发者平台，管理自己的对话、文件和 API Key。</p>
        </div>
        <div class="trust-grid">
          <div class="trust-item">
            <span class="trust-label">默认角色</span>
            <strong>普通用户</strong>
          </div>
          <div class="trust-item">
            <span class="trust-label">个人入口</span>
            <strong>Chat 与 Platform</strong>
          </div>
          <div class="trust-item">
            <span class="trust-label">权限边界</span>
            <strong>仅管理本人资源</strong>
          </div>
        </div>
      </section>

      <section class="register-form-panel">
        <div class="register-form-wrapper">
          <h2 class="form-title">注册账号</h2>
          <p class="form-subtitle">创建后自动进入个人工作台</p>

          <el-alert
            v-if="registrationEnabled === false"
            type="warning"
            show-icon
            :closable="false"
            class="status-alert"
            title="当前环境未开放自助注册"
          />

          <el-form
            ref="formRef"
            :model="registerForm"
            :rules="registerRules"
            label-position="top"
          >
            <el-form-item label="用户名" prop="username">
              <el-input
                v-model="registerForm.username"
                placeholder="3-32 位字母、数字或 _.-"
                :prefix-icon="User"
                :disabled="registrationEnabled === false"
              />
            </el-form-item>
            <el-form-item label="邮箱" prop="email">
              <el-input
                v-model="registerForm.email"
                placeholder="可选"
                :prefix-icon="Message"
                :disabled="registrationEnabled === false"
              />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input
                v-model="registerForm.password"
                type="password"
                show-password
                placeholder="至少 8 位"
                :prefix-icon="Lock"
                :disabled="registrationEnabled === false"
              />
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input
                v-model="registerForm.confirmPassword"
                type="password"
                show-password
                placeholder="再次输入密码"
                :prefix-icon="Lock"
                :disabled="registrationEnabled === false"
                @keyup.enter="handleRegister"
              />
            </el-form-item>

            <el-checkbox v-model="rememberMe" :disabled="registrationEnabled === false">
              记住我
            </el-checkbox>

            <el-button
              type="primary"
              size="large"
              class="register-btn"
              :loading="loading"
              :disabled="registrationEnabled === false"
              @click="handleRegister"
            >
              注册并进入
            </el-button>
          </el-form>

          <div class="login-entry">
            <span>已有账号?</span>
            <el-button link type="primary" @click="router.push('/login')">
              返回登录
            </el-button>
          </div>
        </div>
      </section>
    </div>
    <div class="register-footer">
      © 2025-2026 ORIN 企业 AI 中枢
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { Lock, Message, User } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import Cookies from 'js-cookie';
import BrandingLogo from '@/components/BrandingLogo.vue';
import { register, getRegistrationStatus } from '@/api/auth';
import { useUserStore } from '@/stores/user';
import { getDefaultHomeByRoles } from '@/router/topMenuConfig';

const router = useRouter();
const userStore = useUserStore();
const loading = ref(false);
const rememberMe = ref(true);
const formRef = ref(null);
const registrationEnabled = ref(null);

const registerForm = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: ''
});

const validateConfirmPassword = (_rule, value, callback) => {
  if (value !== registerForm.password) {
    callback(new Error('两次输入的密码不一致'));
    return;
  }
  callback();
};

const registerRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_.-]{3,32}$/, message: '用户名格式不正确', trigger: 'blur' }
  ],
  email: [
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 128, message: '密码长度需为 8-128 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
};

onMounted(async () => {
  try {
    const res = await getRegistrationStatus();
    registrationEnabled.value = Boolean(res?.enabled ?? res?.data?.enabled);
  } catch (_error) {
    registrationEnabled.value = true;
  }
});

const persistSession = (token, user, roles) => {
  if (rememberMe.value) {
    localStorage.setItem('orin_token', token);
    localStorage.setItem('orin_user', JSON.stringify(user));
  } else {
    sessionStorage.setItem('orin_token', token);
    sessionStorage.setItem('orin_user', JSON.stringify(user));
  }

  userStore.login(token, user, roles);

  const cookieExpires = rememberMe.value ? 7 : 1;
  Cookies.set('orin_token', token, { expires: cookieExpires });
  Cookies.set('orin_userInfo', JSON.stringify(user), { expires: cookieExpires });
  Cookies.set('orin_roles', JSON.stringify(roles), { expires: cookieExpires });
};

const handleRegister = async () => {
  if (!formRef.value || registrationEnabled.value === false) return;

  await formRef.value.validate(async (valid) => {
    if (!valid) return;
    loading.value = true;
    try {
      const payload = {
        username: registerForm.username,
        email: registerForm.email || null,
        password: registerForm.password,
        rememberMe: rememberMe.value
      };
      const res = await register(payload);
      const token = res.token || res.data?.token;
      const user = res.user || res.data?.user;
      const roles = res.roles || res.data?.roles || ['ROLE_USER'];

      if (!token) throw new Error('Invalid register response');

      persistSession(token, user, roles);
      ElMessage.success('注册成功');
      router.push(getDefaultHomeByRoles(roles));
    } catch (error) {
      const message = error.response?.data?.message || error.message || '请稍后重试';
      ElMessage.error('注册失败: ' + message);
    } finally {
      loading.value = false;
    }
  });
};
</script>

<style scoped>
.register-container {
  min-height: 100svh;
  width: 100%;
  box-sizing: border-box;
  background: #ffffff;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  position: relative;
  overflow-x: hidden;
  overflow-y: auto;
  padding: clamp(16px, 3vh, 32px);
}

.register-shell {
  width: min(1180px, calc(100vw - 48px));
  max-width: none;
  min-height: min(640px, calc(100svh - 72px));
  display: grid;
  grid-template-columns: minmax(420px, 1fr) minmax(390px, 460px);
  overflow: hidden;
  border: 1px solid var(--orin-border-strong, #d8e0e8);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.82);
  -webkit-backdrop-filter: blur(12px);
  backdrop-filter: blur(12px);
  box-shadow: 0 18px 44px rgba(15, 23, 42, 0.08);
}

.register-brand-panel {
  padding: clamp(34px, 4vw, 56px);
  background: var(--surface-secondary, #f8fafc);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 34px;
}

.register-logo {
  align-self: flex-start;
}

.brand-copy {
  max-width: 520px;
}

.brand-kicker {
  display: inline-block;
  margin-bottom: 18px;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0;
  text-transform: uppercase;
  color: var(--primary-color, #2563eb);
}

.brand-copy h1 {
  margin: 0 0 18px;
  color: var(--text-primary, #0f172a);
  font-size: clamp(30px, 3vw, 40px);
  line-height: 1.1;
  letter-spacing: 0;
}

.brand-copy p {
  margin: 0;
  color: var(--text-secondary, #475569);
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
  border: 1px solid var(--orin-border, #e2e8f0);
  border-radius: 8px;
  background: #ffffff;
}

.trust-label {
  display: block;
  margin-bottom: 8px;
  color: var(--text-secondary, #64748b);
  font-size: 12px;
}

.trust-item strong {
  color: var(--text-primary, #0f172a);
  font-size: 14px;
}

.register-form-panel {
  padding: clamp(30px, 4vw, 48px);
  display: flex;
  align-items: center;
  justify-content: center;
  background: #ffffff;
}

.register-form-wrapper {
  width: 100%;
  max-width: 420px;
}

.form-title {
  margin: 0 0 10px;
  color: var(--text-primary, #0f172a);
  font-size: 28px;
  letter-spacing: 0;
}

.form-subtitle {
  margin: 0 0 28px;
  color: var(--text-secondary, #64748b);
  font-size: 14px;
}

.status-alert {
  margin-bottom: 18px;
}

.register-btn {
  width: 100%;
  height: 48px;
  margin-top: 20px;
  border-radius: var(--radius-lg, 8px);
  font-size: 16px;
  font-weight: 600;
}

.login-entry {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 6px;
  margin-top: 14px;
  color: var(--text-secondary, #64748b);
  font-size: 14px;
}

.register-footer {
  position: absolute;
  bottom: 10px;
  left: 50%;
  transform: translateX(-50%);
  color: var(--text-tertiary, #94a3b8);
  font-size: 12px;
}

@media (max-width: 1180px) {
  .register-shell {
    width: min(960px, calc(100vw - 40px));
    grid-template-columns: minmax(360px, 1fr) minmax(360px, 430px);
  }

  .register-brand-panel,
  .register-form-panel {
    padding: 32px;
  }

  .trust-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 860px) {
  .register-container {
    align-items: flex-start;
    padding: 18px;
  }

  .register-shell {
    width: min(720px, calc(100vw - 36px));
    max-width: 100%;
    min-height: 0;
    grid-template-columns: 1fr;
  }

  .register-brand-panel {
    padding: 32px;
    gap: 28px;
    justify-content: flex-start;
  }

  .register-form-panel {
    padding: 32px;
  }

  .trust-grid {
    grid-template-columns: 1fr;
  }

  .register-footer {
    position: static;
    transform: none;
    margin-top: 16px;
  }
}

@media (max-width: 640px) {
  .register-container {
    padding: 12px;
  }

  .register-shell {
    width: 100%;
    border-radius: 10px;
  }

  .register-brand-panel {
    padding: 22px 20px;
  }

  .register-logo {
    height: 44px;
  }

  .brand-kicker {
    margin-bottom: 10px;
    font-size: 11px;
  }

  .brand-copy h1 {
    margin-bottom: 10px;
    font-size: 24px;
  }

  .brand-copy p {
    font-size: 13px;
    line-height: 1.65;
  }

  .register-form-panel {
    padding: 26px 20px 24px;
  }

  .form-title {
    font-size: 24px;
  }

  .form-subtitle {
    margin-bottom: 22px;
  }
}

@media (max-height: 760px) and (min-width: 861px) {
  .register-container {
    align-items: flex-start;
    padding-top: 18px;
    padding-bottom: 28px;
  }

  .register-shell {
    min-height: 0;
  }

  .register-brand-panel,
  .register-form-panel {
    padding-top: 28px;
    padding-bottom: 28px;
  }

  .register-logo {
    height: 54px;
  }

  .trust-item {
    padding: 12px;
  }

  .form-subtitle {
    margin-bottom: 20px;
  }

  .register-btn {
    margin-top: 14px;
  }
}
</style>
