<template>
  <div class="landing-wrapper">
    <nav class="landing-nav">
      <div class="nav-content">
        <button class="logo-button" type="button" aria-label="返回 ORIN 首页" @click="scrollToTop">
          <img class="logo-image" src="/logo.svg" alt="ORIN" />
        </button>

        <div class="nav-center">
          <button type="button" @click="scrollToSection('roles')">角色入口</button>
          <button type="button" @click="scrollToSection('platform')">平台能力</button>
          <button type="button" @click="openApiDocs">API 文档</button>
        </div>

        <div class="nav-actions">
          <template v-if="isLoggedIn">
            <el-dropdown trigger="click" @command="handleCommand">
              <div class="avatar-wrapper">
                <div class="user-info-text">
                  <span class="user-name">{{ userInfo.name || '用户' }}</span>
                  <span class="user-tag">{{ userInfo.role || '普通用户' }}</span>
                </div>
                <el-avatar :size="36" :src="userInfo.avatar || defaultAvatar" />
                <el-icon class="caret-icon"><CaretBottom /></el-icon>
              </div>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="console">
                    <el-icon><MonitorIcon /></el-icon>进入工作区
                  </el-dropdown-item>
                  <el-dropdown-item command="profile">
                    <el-icon><UserIcon /></el-icon>个人中心
                  </el-dropdown-item>
                  <el-dropdown-item divided command="logout">
                    <el-icon><SwitchButton /></el-icon>退出登录
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <el-button v-else class="login-button" round @click="goLogin">登录</el-button>
        </div>
      </div>
    </nav>

    <main class="landing-page">
      <section class="hero-section">
        <a class="release-pill" href="#roles" @click.prevent="scrollToSection('roles')">
          <span>ORIN Platform</span>
          <strong>智能体接入、知识资产、流程协作与运行观测的统一中枢</strong>
          <el-icon><ArrowRight /></el-icon>
        </a>

        <h1 class="hero-title">
          <span>构建企业智能体的</span>
          <span class="title-accent">管理与运行中枢</span>
        </h1>
        <p class="hero-subtitle">
          ORIN 连接智能体、知识库、工作流与观测链路，帮助团队接入 AI 应用、沉淀知识资产、编排协作流程，并逐步形成可治理、可追踪、可运营的平台闭环。
        </p>

        <div class="hero-actions">
          <el-button type="primary" size="large" @click="goUserPortal">
            我是使用者
            <el-icon class="el-icon--right"><ArrowRight /></el-icon>
          </el-button>
          <el-button size="large" class="secondary-button" @click="goAdminConsole">
            我是平台管理者
          </el-button>
        </div>

        <div class="hero-proof">
          <span v-for="item in heroStats" :key="item.label">
            <strong>{{ item.value }}</strong>
            {{ item.label }}
          </span>
        </div>
      </section>

      <section class="product-stage" aria-label="ORIN 产品入口预览">
        <div class="stage-shell">
          <div class="stage-topbar">
            <span />
            <span />
            <span />
            <div>
              <strong>ORIN Console</strong>
            </div>
          </div>

          <div class="product-preview">
            <aside class="preview-sidebar">
              <span class="preview-brand">AI 中枢</span>
              <span>知识资产</span>
              <span>流程编排</span>
              <span>运营观测</span>
            </aside>

            <section class="preview-main">
              <div class="preview-header">
                <div>
                  <span>Overview</span>
                  <h2>智能体管理与运行中枢</h2>
                </div>
                <div class="preview-status">
                  <i />
                  运行中
                </div>
              </div>

              <div class="preview-metrics">
                <article v-for="item in heroStats" :key="item.label">
                  <strong>{{ item.value }}</strong>
                  <span>{{ item.label }}</span>
                </article>
              </div>

              <div class="preview-panel">
                <div class="panel-copy">
                  <span>Workspace</span>
                  <strong>业务使用侧与平台治理侧共享同一套底层能力</strong>
                </div>
                <div class="panel-lines">
                  <i />
                  <i />
                  <i />
                </div>
              </div>

              <div class="preview-tags">
                <span v-for="task in previewTasks" :key="task">{{ task }}</span>
              </div>
            </section>
          </div>
        </div>
      </section>

      <section id="roles" class="roles-section">
        <div class="section-heading">
          <span>Platform entry</span>
          <h2>同一平台，面向不同使用场景</h2>
          <p>业务侧关注可用的智能体和知识服务，治理侧关注应用接入、资源配置、权限边界和运行状态。</p>
        </div>

        <div class="role-row">
          <button type="button" class="role-link" @click="goUserPortal">
            <span>业务使用侧</span>
            <strong>AI 服务工作台</strong>
            <el-icon><ArrowRight /></el-icon>
          </button>

          <button type="button" class="role-link admin" @click="goAdminConsole">
            <span>平台治理侧</span>
            <strong>管理控制台</strong>
            <el-icon><ArrowRight /></el-icon>
          </button>
        </div>
      </section>

      <section id="platform" class="platform-section">
        <div class="section-heading centered">
          <span>Platform capabilities</span>
          <h2>从接入到观测，覆盖智能体平台主链路</h2>
        </div>

        <div class="capability-list">
          <article v-for="item in capabilityGroups" :key="item.title" class="capability-card">
            <h3>{{ item.title }}</h3>
            <p>{{ item.desc }}</p>
          </article>
        </div>
      </section>
    </main>

    <footer class="landing-footer">
      <span>© 2025-2026 ORIN</span>
      <a href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer">蜀ICP备2025125402号-3</a>
      <a href="/unified-docs" target="_blank" rel="noopener noreferrer">API 文档</a>
    </footer>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import Cookies from 'js-cookie'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { ROUTES } from '@/router/routes'
import { getDefaultHomeByRoles } from '@/router/topMenuConfig'
import {
  ArrowRight,
  CaretBottom,
  Monitor as MonitorIcon,
  SwitchButton,
  User as UserIcon,
} from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()
const isLoggedIn = ref(false)
const defaultAvatar = 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'

const roleMap = {
  ROLE_SUPER_ADMIN: '超级管理员',
  ROLE_PLATFORM_ADMIN: '平台管理员',
  ROLE_OPERATOR: '业务运营',
  ROLE_ADMIN: '管理员',
  ROLE_USER: '普通用户',
  ADMIN: '管理员',
  USER: '普通用户',
}

const userInfo = reactive({
  name: '',
  role: '',
  avatar: '',
})

const heroStats = [
  { value: 'Agent', label: '智能体管理' },
  { value: 'RAG', label: '知识资产' },
  { value: 'Trace', label: '运行观测' },
]

const userTasks = ['智能体问答', '知识检索', '协作任务']
const adminTasks = ['智能体接入', '模型资源', '链路观测', '网关策略']
const previewTasks = [...userTasks, ...adminTasks]

const capabilityGroups = [
  {
    title: '智能体接入与管理',
    desc: '统一管理 AI 应用、模型资源、技能绑定和生命周期配置。',
  },
  {
    title: '知识资产与检索',
    desc: '沉淀文档、向量检索和知识同步能力，支撑业务问答。',
  },
  {
    title: '流程编排与协作',
    desc: '面向工作流、多智能体协作和任务执行记录做闭环建设。',
  },
  {
    title: '运行观测与治理',
    desc: '覆盖调用链路、资源健康、网关策略、审计和告警。',
  },
]

const syncUserInfo = () => {
  const token = Cookies.get('orin_token')
  isLoggedIn.value = Boolean(token)

  if (!token) return

  if (!userStore.userInfo) {
    userStore.restoreFromCookies()
  }

  const profile = userStore.userInfo || {}
  const roles = userStore.roles || []
  userInfo.name = profile.username || profile.nickname || profile.name || '用户'
  userInfo.avatar = profile.avatar || ''
  userInfo.role = roleMap[roles[0]] || roles[0] || '普通用户'
}

const getWorkspaceRoute = () => getDefaultHomeByRoles(userStore.roles || [])

const goLogin = () => {
  router.push(ROUTES.LOGIN)
}

const goUserPortal = () => {
  router.push(ROUTES.PORTAL)
}

const goAdminConsole = () => {
  router.push(isLoggedIn.value ? getWorkspaceRoute() : ROUTES.LOGIN)
}

const handleLogout = () => {
  Cookies.remove('orin_token')
  localStorage.removeItem('orin_user')
  isLoggedIn.value = false
  ElMessage.success('已退出登录')
  router.push('/')
}

const handleCommand = (command) => {
  if (command === 'console') {
    router.push(getWorkspaceRoute())
    return
  }

  if (command === 'profile') {
    router.push(ROUTES.CONTROL.PROFILE)
    return
  }

  if (command === 'logout') {
    handleLogout()
  }
}

const openApiDocs = () => {
  window.open('/unified-docs', '_blank')
}

const scrollToTop = () => {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

const scrollToSection = (id) => {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

let revealObserver

const handlePointerMove = (event) => {
  const root = document.querySelector('.landing-wrapper')
  if (!root) return

  root.style.setProperty('--mouse-x', `${event.clientX}px`)
  root.style.setProperty('--mouse-y', `${event.clientY}px`)
}

const initScrollEffects = () => {
  const targets = document.querySelectorAll(
    '.product-stage, .roles-section, .platform-section, .role-link, .capability-card',
  )

  revealObserver = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add('is-visible')
          revealObserver?.unobserve(entry.target)
        }
      })
    },
    { threshold: 0.16 },
  )

  targets.forEach((target) => revealObserver.observe(target))
}

onMounted(() => {
  syncUserInfo()
  initScrollEffects()
  window.addEventListener('pointermove', handlePointerMove, { passive: true })
})

onBeforeUnmount(() => {
  revealObserver?.disconnect()
  window.removeEventListener('pointermove', handlePointerMove)
})
</script>

<style scoped>
:global(html) {
  scroll-behavior: smooth;
}

.landing-wrapper {
  --home-primary: #00bfa5;
  --home-primary-dark: #0f766e;
  --home-blue: #2563eb;
  --home-ink: #101828;
  --home-muted: #667085;
  --home-line: rgba(0, 191, 165, 0.16);
  --mouse-x: 50vw;
  --mouse-y: 18vh;
  position: relative;
  overflow: hidden;
  min-height: 100vh;
  color: var(--home-ink);
  background:
    radial-gradient(circle at var(--mouse-x) var(--mouse-y), rgba(0, 191, 165, 0.16), transparent 18rem),
    radial-gradient(circle at 50% 0%, rgba(0, 191, 165, 0.14), transparent 34%),
    radial-gradient(circle at 84% 24%, rgba(37, 99, 235, 0.06), transparent 28%),
    linear-gradient(180deg, #f7fffd 0%, #ffffff 44%, #f8fafc 100%);
  background-size: 100% 100%, 100% 100%, 100% 100%, 100% 100%;
  animation: ambientGlow 16s ease-in-out infinite alternate;
}

.landing-nav {
  position: sticky;
  top: 0;
  z-index: 20;
  border-bottom: 1px solid rgba(15, 118, 110, 0.12);
  background: rgba(255, 255, 255, 0.78);
  backdrop-filter: blur(20px);
  animation: navDrop 0.7s ease both;
}

.nav-content {
  max-width: 1180px;
  height: 76px;
  margin: 0 auto;
  padding: 0 28px;
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 20px;
}

.logo-button,
.nav-center button,
.role-link {
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font: inherit;
}

.logo-button {
  width: fit-content;
  display: inline-flex;
  align-items: center;
}

.logo-image {
  width: 132px;
  display: block;
}

.nav-center {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 28px;
  padding: 8px 18px;
  border: 1px solid rgba(15, 118, 110, 0.1);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.74);
}

.nav-center button {
  color: #475467;
  font-size: 14px;
  font-weight: 700;
}

.nav-center button:hover {
  color: var(--home-primary-dark);
}

.nav-actions {
  display: flex;
  justify-content: flex-end;
}

.login-button {
  border-color: var(--home-line);
  color: var(--home-primary-dark);
  background: #fff;
}

.avatar-wrapper {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 8px 6px 12px;
  border: 1px solid rgba(15, 118, 110, 0.16);
  border-radius: 999px;
  background: #fff;
  cursor: pointer;
}

.user-info-text {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  line-height: 1.2;
}

.user-name {
  color: var(--home-ink);
  font-size: 13px;
  font-weight: 800;
}

.user-tag {
  color: var(--home-muted);
  font-size: 11px;
}

.caret-icon {
  color: #98a2b3;
}

.landing-page {
  max-width: 1120px;
  margin: 0 auto;
  padding: 78px 28px 64px;
}

.hero-section {
  max-width: 920px;
  margin: 0 auto;
  text-align: center;
}

.release-pill {
  width: fit-content;
  max-width: 100%;
  margin: 0 auto;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px 8px 8px;
  border: 1px solid rgba(0, 191, 165, 0.2);
  border-radius: 999px;
  color: var(--home-primary-dark);
  background: rgba(236, 253, 245, 0.86);
  text-decoration: none;
  box-shadow: 0 12px 38px rgba(15, 118, 110, 0.08);
  animation: riseIn 0.7s ease 0.05s both;
  transition: transform 0.24s ease, box-shadow 0.24s ease, border-color 0.24s ease;
}

.release-pill:hover {
  border-color: rgba(0, 191, 165, 0.36);
  transform: translateY(-2px);
  box-shadow: 0 18px 46px rgba(15, 118, 110, 0.13);
}

.release-pill span {
  padding: 6px 10px;
  border-radius: 999px;
  color: #fff;
  background: var(--home-primary);
  font-size: 12px;
  font-weight: 850;
}

.release-pill strong {
  font-size: 14px;
}

.hero-title {
  margin: 30px 0 18px;
  color: #0f172a;
  font-size: clamp(48px, 7vw, 86px);
  line-height: 1.03;
  font-weight: 900;
  letter-spacing: 0;
  animation: titleReveal 0.9s cubic-bezier(.16, 1, .3, 1) 0.14s both;
}

.hero-title span {
  display: block;
  overflow: hidden;
}

.hero-title span:first-child {
  animation: titleLineIn 0.9s cubic-bezier(.16, 1, .3, 1) 0.18s both;
}

.hero-title .title-accent {
  width: fit-content;
  margin: 0 auto;
  color: var(--home-primary);
  animation: titleLineIn 0.9s cubic-bezier(.16, 1, .3, 1) 0.32s both, titleGlow 2.8s ease-in-out 1.1s infinite alternate;
}

.hero-subtitle {
  max-width: 760px;
  margin: 0 auto;
  color: #475467;
  font-size: 18px;
  line-height: 1.85;
  animation: riseIn 0.8s ease 0.22s both;
}

.hero-actions {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 14px;
  margin-top: 32px;
  animation: riseIn 0.8s ease 0.3s both;
}

.secondary-button {
  border-color: rgba(0, 191, 165, 0.24);
  color: var(--home-primary-dark);
  background: #fff;
}

.hero-proof {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 24px;
  margin-top: 34px;
  color: var(--home-muted);
  animation: riseIn 0.8s ease 0.38s both;
}

.hero-proof span {
  display: inline-flex;
  align-items: baseline;
  gap: 8px;
  font-size: 14px;
}

.hero-proof strong {
  color: var(--home-primary-dark);
  font-size: 22px;
}

.product-stage {
  max-width: 1040px;
  margin: 58px auto 0;
}

.stage-shell {
  overflow: hidden;
  border: 1px solid rgba(0, 191, 165, 0.18);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 30px 90px rgba(15, 118, 110, 0.1);
  animation: stageIn 0.9s cubic-bezier(.2, .8, .2, 1) 0.42s both;
  transform-origin: top center;
}

.stage-topbar {
  height: 74px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  border-bottom: 1px solid rgba(0, 191, 165, 0.14);
  background: rgba(255, 255, 255, 0.82);
}

.stage-topbar {
  justify-content: flex-start;
  gap: 8px;
}

.stage-topbar > span {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #ef4444;
}

.stage-topbar > span:nth-child(2) {
  background: #f59e0b;
}

.stage-topbar > span:nth-child(3) {
  background: var(--home-primary);
}

.stage-topbar strong {
  display: block;
  margin-left: 10px;
  color: var(--home-ink);
  font-size: 14px;
}

.product-preview {
  min-height: 460px;
  display: grid;
  grid-template-columns: 190px 1fr;
  padding: 20px;
  background: linear-gradient(180deg, #ffffff, #f8fafc);
}

.preview-sidebar {
  padding: 20px 12px;
  border-right: 1px solid rgba(0, 191, 165, 0.12);
}

.preview-sidebar span {
  display: block;
  padding: 12px 14px;
  border-radius: 8px;
  color: var(--home-muted);
  font-size: 13px;
  font-weight: 760;
}

.preview-sidebar .preview-brand {
  color: var(--home-primary-dark);
  background: #ccfbf1;
}

.preview-main {
  padding: 24px 26px 20px;
}

.preview-header,
.preview-metrics,
.preview-panel {
  animation: riseIn 0.7s ease both;
}

.preview-header {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: flex-start;
}

.preview-header span,
.panel-copy span {
  color: var(--home-primary-dark);
  font-size: 11px;
  font-weight: 900;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.preview-header h2 {
  margin: 8px 0 0;
  color: var(--home-ink);
  font-size: 30px;
  letter-spacing: 0;
}

.preview-status {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border: 1px solid rgba(0, 191, 165, 0.2);
  border-radius: 999px;
  color: var(--home-primary-dark);
  background: #ecfdf5;
  font-size: 12px;
  font-weight: 850;
}

.preview-status i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--home-primary);
  box-shadow: 0 0 0 5px rgba(0, 191, 165, 0.14);
}

.preview-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-top: 28px;
  animation-delay: 0.08s;
}

.preview-metrics article {
  padding: 18px;
  border: 1px solid rgba(0, 191, 165, 0.12);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.78);
}

.preview-metrics strong,
.preview-metrics span {
  display: block;
}

.preview-metrics strong {
  color: var(--home-primary-dark);
  font-size: 24px;
}

.preview-metrics span {
  margin-top: 8px;
  color: var(--home-muted);
  font-size: 13px;
  font-weight: 760;
}

.preview-panel {
  display: grid;
  grid-template-columns: minmax(0, 0.85fr) minmax(220px, 1fr);
  gap: 26px;
  align-items: center;
  margin-top: 18px;
  padding: 26px;
  border: 1px solid rgba(0, 191, 165, 0.12);
  border-radius: 8px;
  background: linear-gradient(135deg, rgba(236, 253, 245, 0.86), rgba(255, 255, 255, 0.9));
  animation-delay: 0.16s;
}

.panel-copy strong {
  display: block;
  margin-top: 10px;
  color: var(--home-ink);
  font-size: 22px;
  line-height: 1.35;
}

.panel-lines i {
  display: block;
  height: 14px;
  margin: 14px 0;
  border-radius: 999px;
  background: linear-gradient(90deg, rgba(0, 191, 165, 0.2), rgba(37, 99, 235, 0.1));
}

.panel-lines i:nth-child(1) {
  width: 88%;
}

.panel-lines i:nth-child(2) {
  width: 68%;
}

.panel-lines i:nth-child(3) {
  width: 78%;
}

.preview-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.preview-tags span {
  padding: 9px 11px;
  border-radius: 999px;
  color: var(--home-primary-dark);
  background: rgba(204, 251, 241, 0.82);
  font-size: 12px;
  font-weight: 820;
}

.roles-section,
.platform-section {
  padding: 92px 0 0;
  scroll-margin-top: 92px;
}

.section-heading {
  max-width: 680px;
  margin-bottom: 30px;
}

.section-heading.centered {
  margin-right: auto;
  margin-left: auto;
  text-align: center;
}

.section-heading span {
  color: var(--home-primary-dark);
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.section-heading h2 {
  margin: 10px 0;
  color: #0f172a;
  font-size: 36px;
  line-height: 1.2;
  letter-spacing: 0;
}

.section-heading p {
  margin: 0;
  color: var(--home-muted);
  font-size: 16px;
  line-height: 1.75;
}

.role-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.role-link {
  min-height: 112px;
  padding: 24px;
  border: 1px solid rgba(0, 191, 165, 0.16);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.8);
  text-align: left;
  transition: transform 0.24s ease, border-color 0.24s ease, box-shadow 0.24s ease;
}

.role-link:hover,
.capability-card:hover {
  transform: translateY(-4px);
  border-color: rgba(0, 191, 165, 0.3);
  box-shadow: 0 18px 42px rgba(15, 118, 110, 0.1);
}

.role-link span,
.role-link strong {
  display: block;
}

.role-link span {
  color: var(--home-muted);
  font-size: 13px;
  font-weight: 760;
}

.role-link strong {
  margin-top: 8px;
  color: var(--home-primary-dark);
  font-size: 22px;
}

.role-link .el-icon {
  margin-top: 14px;
  color: var(--home-primary-dark);
}

.role-link.admin strong,
.role-link.admin .el-icon {
  color: var(--home-blue);
}

.capability-list {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 1px;
  overflow: hidden;
  border: 1px solid rgba(0, 191, 165, 0.12);
  border-radius: 8px;
  background: rgba(0, 191, 165, 0.12);
}

.capability-card {
  padding: 24px;
  background: rgba(255, 255, 255, 0.82);
  transition: transform 0.24s ease, box-shadow 0.24s ease;
}

.product-stage,
.roles-section,
.platform-section,
.role-link,
.capability-card {
  opacity: 0;
  transform: translateY(34px);
  transition: opacity 0.72s ease, transform 0.72s cubic-bezier(.16, 1, .3, 1);
}

.product-stage.is-visible,
.roles-section.is-visible,
.platform-section.is-visible,
.role-link.is-visible,
.capability-card.is-visible {
  opacity: 1;
  transform: translateY(0);
}

.role-link:nth-child(2),
.capability-card:nth-child(2) {
  transition-delay: 0.08s;
}

.capability-card:nth-child(3) {
  transition-delay: 0.16s;
}

.capability-card:nth-child(4) {
  transition-delay: 0.24s;
}

@keyframes navDrop {
  from {
    opacity: 0;
    transform: translateY(-12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes riseIn {
  from {
    opacity: 0;
    transform: translateY(18px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes titleReveal {
  from {
    opacity: 0;
    filter: blur(10px);
  }
  to {
    opacity: 1;
    filter: blur(0);
  }
}

@keyframes titleLineIn {
  from {
    opacity: 0;
    transform: translateY(38px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes titleGlow {
  from {
    text-shadow: 0 0 0 rgba(0, 191, 165, 0);
  }
  to {
    text-shadow: 0 18px 48px rgba(0, 191, 165, 0.24);
  }
}

@keyframes stageIn {
  from {
    opacity: 0;
    transform: translateY(24px) scale(0.985);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@keyframes ambientGlow {
  from {
    background-position: 0 0, 50% 0%, 88% 28%, 0 0;
  }
  to {
    background-position: 0 0, 46% 2%, 84% 24%, 0 0;
  }
}

.capability-card h3 {
  margin: 0 0 8px;
  color: var(--home-ink);
  font-size: 18px;
}

.capability-card p {
  margin: 0;
  color: var(--home-muted);
  line-height: 1.7;
}

.landing-footer {
  max-width: 1180px;
  margin: 0 auto;
  padding: 42px 28px 48px;
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  color: var(--home-muted);
  font-size: 14px;
}

.landing-footer a {
  color: inherit;
  text-decoration: none;
}

.landing-footer a:hover {
  color: var(--home-primary-dark);
}

@media (max-width: 780px) {
  .nav-content {
    height: auto;
    padding: 16px 20px;
    grid-template-columns: 1fr;
    justify-items: center;
  }

  .nav-center {
    width: 100%;
    justify-content: space-between;
    gap: 12px;
  }

  .landing-page {
    padding: 48px 20px;
  }

  .hero-section h1 {
    font-size: 44px;
  }

  .hero-title .title-accent {
    margin: 0 auto;
  }

  .product-preview,
  .preview-panel {
    grid-template-columns: 1fr;
  }

  .preview-sidebar {
    display: none;
  }

  .preview-main {
    padding: 20px;
  }

  .role-row,
  .capability-list,
  .preview-metrics {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 560px) {
  .release-pill {
    align-items: flex-start;
    border-radius: 8px;
  }

  .release-pill strong {
    text-align: left;
  }

  .hero-actions,
  .hero-actions .el-button {
    width: 100%;
  }

  .preview-header {
    flex-direction: column;
  }
}

@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation-duration: 0.001ms !important;
    animation-iteration-count: 1 !important;
    scroll-behavior: auto !important;
    transition-duration: 0.001ms !important;
  }

  .product-stage,
  .roles-section,
  .platform-section,
  .role-link,
  .capability-card {
    opacity: 1 !important;
    transform: none !important;
  }
}
</style>
