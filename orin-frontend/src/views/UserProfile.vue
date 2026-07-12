<template>
  <main class="user-profile-page">
    <header class="profile-header">
      <button class="brand" type="button" aria-label="返回 ORIN Chat" @click="goToChat">
        <img src="/logo.svg" alt="ORIN" />
        <span>Chat</span>
      </button>
      <button class="back-button" type="button" @click="goToChat">
        <el-icon><ArrowLeft /></el-icon>
        返回对话
      </button>
    </header>

    <section class="profile-shell" aria-labelledby="profile-title">
      <div class="profile-intro">
        <p class="eyebrow">账户</p>
        <h1 id="profile-title">个人中心</h1>
        <p>管理你的个人资料与账户信息。</p>
      </div>

      <section class="identity-card">
        <div class="avatar-control" @click="openAvatarPicker">
          <el-avatar :size="76" :src="profile.avatar" class="avatar">
            {{ avatarText }}
          </el-avatar>
          <span class="avatar-edit" aria-hidden="true"><el-icon><Camera /></el-icon></span>
          <input ref="avatarInput" type="file" accept="image/*" hidden @change="onAvatarFileChange" />
        </div>
        <div>
          <strong>{{ profile.nickname || profile.username || 'ORIN 用户' }}</strong>
          <span>{{ profile.email || '尚未填写邮箱' }}</span>
          <small>{{ roleLabel }}<template v-if="profile.createTime"> · 加入于 {{ formatJoinDate(profile.createTime) }}</template></small>
        </div>
      </section>

      <div class="profile-grid">
        <section class="profile-panel">
          <div class="panel-heading">
            <div>
              <h2>个人资料</h2>
              <p>这些信息仅用于你的 ORIN 账户。</p>
            </div>
          </div>

          <el-form label-position="top" class="profile-form" @submit.prevent="saveProfile">
            <div class="form-row">
              <el-form-item label="昵称">
                <el-input v-model="form.nickname" maxlength="40" placeholder="输入昵称" />
              </el-form-item>
              <el-form-item label="电子邮箱">
                <el-input v-model="form.email" type="email" placeholder="name@example.com" />
              </el-form-item>
            </div>
            <div class="form-row">
              <el-form-item label="手机号码">
                <el-input v-model="form.phone" placeholder="选填" />
              </el-form-item>
              <el-form-item label="所在地">
                <el-input v-model="form.address" placeholder="选填" />
              </el-form-item>
            </div>
            <el-form-item label="个人简介">
              <el-input v-model="form.bio" type="textarea" :rows="4" maxlength="200" show-word-limit placeholder="介绍一下自己..." />
            </el-form-item>
            <div class="panel-actions">
              <el-button type="primary" :loading="saving" @click="saveProfile">
                保存资料
              </el-button>
            </div>
          </el-form>
        </section>

        <aside class="account-panel">
          <h2>账户安全</h2>
          <div class="account-item">
            <el-icon><Lock /></el-icon>
            <div>
              <strong>登录状态</strong>
              <span>当前会话受保护</span>
            </div>
          </div>
          <div class="account-item">
            <el-icon><User /></el-icon>
            <div>
              <strong>账户角色</strong>
              <span>{{ roleLabel }}</span>
            </div>
          </div>
          <div class="account-footer">
            <button class="sign-out" type="button" @click="signOut">
              <el-icon><SwitchButton /></el-icon>
              退出登录
            </button>
          </div>
        </aside>
      </div>
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { ArrowLeft, Camera, Lock, SwitchButton, User } from '@element-plus/icons-vue';
import { getUserProfile, updateUserAvatar, updateUserProfile, uploadAvatar } from '@/api/user';
import { ROUTES } from '@/router/routes';
import { useUserStore } from '@/stores/user';

const router = useRouter();
const userStore = useUserStore();
const avatarInput = ref(null);
const saving = ref(false);
const profile = reactive({
  userId: null,
  username: '',
  nickname: '',
  email: '',
  bio: '',
  address: '',
  phone: '',
  avatar: '',
  createTime: null
});
const form = reactive({ nickname: '', email: '', bio: '', address: '', phone: '' });

const avatarText = computed(() => (profile.nickname || profile.username || 'O').slice(0, 1).toUpperCase());
const roleLabel = computed(() => userStore.isAdmin ? '管理员' : '普通用户');

const applyProfile = (data = {}) => {
  Object.assign(profile, {
    userId: data.userId ?? profile.userId,
    username: data.username ?? profile.username,
    nickname: data.nickname ?? data.username ?? profile.nickname,
    email: data.email ?? profile.email,
    bio: data.bio ?? profile.bio,
    address: data.address ?? profile.address,
    phone: data.phone ?? profile.phone,
    avatar: data.avatar ?? profile.avatar,
    createTime: data.createTime ?? profile.createTime
  });
  Object.assign(form, {
    nickname: profile.nickname,
    email: profile.email,
    bio: profile.bio,
    address: profile.address,
    phone: profile.phone
  });
};

const formatJoinDate = (value) => {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '未知时间';
  return date.toLocaleDateString('zh-CN', { year: 'numeric', month: 'long' });
};

const goToChat = () => router.push(ROUTES.CHAT);

const openAvatarPicker = () => avatarInput.value?.click();

const onAvatarFileChange = async (event) => {
  const file = event.target.files?.[0];
  if (!file) return;
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.error('头像文件不能超过 2MB');
    return;
  }

  try {
    const payload = new FormData();
    payload.append('file', file);
    payload.append('uploadedBy', profile.username);
    const uploaded = await uploadAvatar(payload);
    const fileId = uploaded?.id || uploaded?.data?.id;
    if (!fileId || !profile.userId) throw new Error('头像上传未返回可用信息');

    const avatarUrl = `/api/v1/multimodal/files/${fileId}/download`;
    await updateUserAvatar(profile.userId, avatarUrl);
    profile.avatar = avatarUrl;
    userStore.updateUserInfo({ ...userStore.userInfo, avatar: avatarUrl });
    ElMessage.success('头像已更新');
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '头像更新失败');
  } finally {
    event.target.value = '';
  }
};

const saveProfile = async () => {
  if (saving.value || !profile.userId) return;
  saving.value = true;
  try {
    const updated = await updateUserProfile({ userId: profile.userId, ...form, avatar: profile.avatar });
    applyProfile(updated);
    userStore.updateUserInfo({ ...userStore.userInfo, ...updated });
    ElMessage.success('资料已保存');
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '保存失败，请稍后重试');
  } finally {
    saving.value = false;
  }
};

const signOut = () => {
  userStore.logout();
  router.replace(ROUTES.LOGIN);
};

onMounted(async () => {
  applyProfile(userStore.userInfo || {});
  if (!userStore.username) return;
  try {
    applyProfile(await getUserProfile(userStore.username));
  } catch {
    ElMessage.warning('未能同步最新资料，当前显示本地账户信息');
  }
});
</script>

<style scoped>
.user-profile-page { min-height: 100vh; background: #f7f9f9; color: #0f172a; }
.profile-header { height: 64px; padding: 0 28px; background: #fff; border-bottom: 1px solid #e4ecea; display: flex; align-items: center; justify-content: space-between; }
.brand, .back-button, .sign-out { border: 0; background: transparent; cursor: pointer; }
.brand { display: inline-flex; align-items: center; gap: 9px; color: #111827; font-size: 17px; font-weight: 740; }
.brand img { display: block; width: 64px; height: 25px; object-fit: contain; }
.back-button { display: inline-flex; align-items: center; gap: 6px; color: #475569; font-size: 14px; padding: 8px; border-radius: 8px; }
.back-button:hover { background: #edf5f3; color: #0f766e; }
.profile-shell { width: min(920px, calc(100% - 40px)); margin: 0 auto; padding: 64px 0; }
.eyebrow { margin: 0 0 8px; color: #0d9488; font-size: 13px; font-weight: 700; }
.profile-intro h1 { margin: 0; font-size: 30px; line-height: 1.2; }
.profile-intro > p:last-child { margin: 10px 0 28px; color: #64748b; }
.identity-card, .profile-panel, .account-panel { background: #fff; border: 1px solid #e3ecea; border-radius: 12px; }
.identity-card { display: flex; align-items: center; gap: 18px; padding: 24px; margin-bottom: 18px; }
.avatar-control { position: relative; cursor: pointer; }
.avatar { background: #0f766e; color: #fff; font-weight: 700; }
.avatar-edit { position: absolute; right: -2px; bottom: -2px; width: 24px; height: 24px; display: grid; place-items: center; background: #fff; color: #0d9488; border: 1px solid #cfe7e2; border-radius: 50%; }
.identity-card strong, .identity-card span, .identity-card small { display: block; }
.identity-card strong { font-size: 19px; }
.identity-card span { margin-top: 5px; color: #64748b; font-size: 14px; }
.identity-card small { margin-top: 9px; color: #0f766e; font-size: 12px; }
.profile-grid { display: grid; grid-template-columns: minmax(0, 1fr) 260px; gap: 18px; align-items: start; }
.profile-panel, .account-panel { padding: 26px; }
.panel-heading h2, .account-panel h2 { margin: 0; font-size: 17px; }
.panel-heading p { margin: 7px 0 22px; color: #64748b; font-size: 13px; }
.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
.profile-form :deep(.el-form-item) { margin-bottom: 17px; }
.profile-form :deep(.el-form-item__label) { color: #334155; font-weight: 600; }
.panel-actions { display: flex; justify-content: flex-end; margin-top: 4px; }
.account-item { display: flex; gap: 10px; padding: 17px 0; border-bottom: 1px solid #edf1f0; color: #0d9488; }
.account-item > div { min-width: 0; }
.account-item strong, .account-item span { display: block; }
.account-item strong { color: #334155; font-size: 14px; }
.account-item span { margin-top: 4px; color: #64748b; font-size: 12px; }
.account-footer { padding-top: 18px; }
.sign-out { display: inline-flex; align-items: center; gap: 7px; color: #b42318; font-size: 14px; padding: 0; }
.sign-out:hover { color: #7f1d1d; }
@media (max-width: 720px) { .profile-header { padding: 0 18px; } .profile-shell { width: min(100% - 32px, 560px); padding: 36px 0; } .profile-grid { grid-template-columns: 1fr; } .form-row { grid-template-columns: 1fr; gap: 0; } .account-panel { order: -1; } }
</style>
