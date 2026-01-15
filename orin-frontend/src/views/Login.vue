<template>
  <div class="login-container">
    <div class="login-box animate-scale">
      <div class="login-left">
        <div class="app-info">
          <img src="/vite.svg" alt="Logo" class="login-logo" />
          <h1>ORIN Platform</h1>
          <p>智能体管理与全链路监控系统</p>
          <div class="feature-list">
            <div class="feature-item"><el-icon><CircleCheckFilled /></el-icon> 实时性能反馈</div>
            <div class="feature-item"><el-icon><CircleCheckFilled /></el-icon> 分布式知识库同步</div>
            <div class="feature-item"><el-icon><CircleCheckFilled /></el-icon> 自动化运维审计</div>
          </div>
        </div>
      </div>
      <div class="login-right">
        <div class="login-form-wrapper">
          <h2 class="form-title">用户登录</h2>
          <p class="form-subtitle">欢迎回来,请使用您的账号登录</p>

          <el-form ref="formRef" :model="loginForm" :rules="loginRules" label-position="top">
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
              <el-checkbox v-model="rememberMe">记住我</el-checkbox>
              <el-button link type="primary">忘记密码?</el-button>
            </div>

            <el-button 
              type="primary" 
              size="large" 
              class="login-btn" 
              :loading="loading" 
              @click="handleLogin"
            >
              登 录
            </el-button>
          </el-form>

          <div class="social-login">
             <el-divider><span class="divider-text">其他登录方式</span></el-divider>
             <div class="icons">
                <el-button circle><el-icon><Share /></el-icon></el-button>
                <el-button circle><el-icon><Link /></el-icon></el-button>
             </div>
          </div>
        </div>
      </div>
    </div>
    
    <div class="login-footer">
      © 2024 ORIN Monitoring System. All rights reserved.
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue';
import { useRouter } from 'vue-router';
import { User, Lock, CircleCheckFilled, Share, Link } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { useUserStore } from '@/stores/user';

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

const handleLogin = async () => {
  if (!formRef.value) return;
  
  await formRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true;
      try {
        const res = await login(loginForm);
        const { token, user, roles } = res.data;
        
        ElMessage.success('登录成功,欢迎回来!');
        
        // 使用userStore保存登录信息和角色
        userStore.login(token, user, roles || ['ROLE_USER']);
        
        // 同时保存到 localStorage 供 Navbar 使用
        localStorage.setItem('orin_user', JSON.stringify(user));
        
        setTimeout(() => {
          router.push('/dashboard/monitor');
        }, 500);
      } catch (error) {
        console.error('Login failed:', error);
        ElMessage.error('登录失败,请检查用户名和密码');
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
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  background: linear-gradient(135deg, var(--neutral-bg) 0%, var(--primary-light) 100%);
  overflow: hidden;
}

.login-box {
  width: 1000px;
  max-width: 90%;
  height: 600px;
  background: var(--neutral-white);
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

.login-logo { width: 64px; height: 64px; margin-bottom: 24px; }
.login-left h1 { font-size: 32px; font-weight: 800; margin-bottom: 12px; }
.login-left p { color: rgba(255,255,255,0.6); margin-bottom: 40px; }

.feature-list { display: flex; flex-direction: column; gap: 20px; }
.feature-item { display: flex; align-items: center; gap: 12px; font-size: 15px; color: rgba(255,255,255,0.9); }
.feature-item .el-icon { color: var(--primary-color); font-size: 20px; }

.login-right {
  flex: 1.5;
  padding: 60px;
  background: var(--neutral-white);
  display: flex;
  justify-content: center;
  align-items: center;
}

.login-form-wrapper { width: 100%; max-width: 400px; }
.form-title { font-size: 28px; font-weight: 700; color: var(--neutral-black); margin-bottom: 8px; }
.form-subtitle { color: var(--neutral-gray-4); margin-bottom: 35px; font-size: 14px; }

.login-btn { width: 100%; height: 48px; font-size: 16px; font-weight: 600; border-radius: var(--radius-lg); margin-top: 20px; }
.extra-actions { display: flex; justify-content: space-between; align-items: center; margin-top: -10px; margin-bottom: 10px; }

.social-login { margin-top: 40px; text-align: center; }
.divider-text { font-size: 12px; color: var(--neutral-gray-4); }
.icons { margin-top: 15px; display: flex; justify-content: center; gap: 20px; }

.login-footer { margin-top: 40px; color: var(--neutral-gray-4); font-size: 13px; }

.animate-scale { animation: scaleIn 0.6s ease; }
@keyframes scaleIn { from { opacity: 0; transform: scale(0.95); } to { opacity: 1; transform: scale(1); } }

html.dark .login-box { background: #1f1f1f; box-shadow: 0 40px 100px rgba(0,0,0,0.3); }
html.dark .login-left { background: #111; }
</style>
