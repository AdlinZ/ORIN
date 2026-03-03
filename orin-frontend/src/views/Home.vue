<template>
  <div class="landing-wrapper">
    <!-- Landing Navbar -->
    <nav class="landing-nav animate-fade">
      <div class="nav-content">
        <div class="logo-area">
          <svg id="ORIN_Logo" xmlns="http://www.w3.org/2000/svg" viewBox="198 154 746 284" class="logo-svg">
            <g id="ORIN">
              <g id="N" class="text-part">
                <path d="M824.43,214.21v106.91c-27.86-33.61-54.54-68.23-82.49-101.68-1.28-1.53-2.88-3.33-3.51-5.23h-32.5v155.37h-36.75c1.04,2.3,1.64,4.84,1.64,7.52,0,.16-.02.32-.02.48h42.64v-156.87h19.75c1.59,0,16.24,18.77,18.48,21.51,24.04,29.45,47.41,59.56,71.78,89.13,2.19,2.66,4.57,5.57,6.84,8.11.3.33-.04.96,1.14.64v-118.9h23.5v164.36h-20c-2.99-5.21-7.27-9.54-11-14.24-24.31-30.55-47.95-61.43-72.51-91.91-4.69-5.83-9.51-11.73-14.49-17.25v130.89h6.5v-111.41c.99.37,1.79.96,2.5,1.75,19.96,22.1,37.07,49.51,56.52,72.3l28.98,36.1c.33.31,1.48,1.25,1.75,1.25h28.75v-178.85h-37.5Z"/>
                <path d="M840.17,377.08h6.25v-147.88h-7v136.39l-109.15-136.23c-1.15-.82-7.95-1.19-8.35.1v156.12h-47.25l-4.75,7.49h59v-153.87l25.49,30.37,85.76,107.53Z"/>
              </g>
              <g id="I" class="primary-part">
                <path d="M656.43,359.27l.07-111.09c-1.16.23-2.36.36-3.59.36-1.37,0-2.7-.16-3.98-.45v111.14c1.18-.24,2.4-.37,3.65-.37,1.32,0,2.61.15,3.85.42Z"/>
                <polygon points="663.9 354.86 670.93 361.58 670.93 245.68 663.89 251.41 663.9 354.86"/>
                <polygon points="641.46 251.35 634.42 245.62 634.42 361.53 641.45 354.81 641.46 251.35"/>
                <path d="M669.18,369.58c-2.36-5.18-7.06-9.08-12.75-10.31-1.24-.27-2.53-.42-3.85-.42-1.25,0-2.46.13-3.64.37-8.32,1.69-14.6,9.06-14.6,17.87,0,10.06,8.18,18.24,18.24,18.24s17.96-7.93,18.22-17.77c0-.16.02-.32.02-.48,0-2.68-.6-5.22-1.64-7.52ZM659.54,382.59c-1.44,1.75-3.52,2.94-5.88,3.22-.35.04-.71.07-1.08.07-2.57,0-4.89-1.09-6.53-2.82-1.53-1.62-2.48-3.79-2.48-6.19,0-.82.12-1.62.33-2.38.58-2.11,1.89-3.92,3.67-5.11,1.13-.76,2.44-1.26,3.85-1.44.08-.01.16-.03.24-.04.3-.03.61-.05.92-.05s.62.02.92.05c2.73.28,5.1,1.77,6.56,3.94.67.99,1.14,2.12,1.37,3.34.1.55.16,1.11.16,1.69,0,2.17-.77,4.17-2.05,5.72Z"/>
                <path d="M656.49,248.18c8.35-1.67,14.66-9.05,14.66-17.88,0-10.06-8.18-18.24-18.24-18.24s-18.24,8.18-18.24,18.24c0,8.69,6.11,15.97,14.26,17.79,1.28.29,2.61.45,3.98.45,1.23,0,2.43-.13,3.59-.36ZM643.83,232.01c-.13-.61-.2-1.24-.2-1.89,0-4.36,3.09-7.99,7.2-8.83.59-.12,1.19-.18,1.81-.18,4.98,0,9.01,4.03,9.01,9.01s-4.03,9.01-9.01,9.01c-4.33,0-7.94-3.05-8.81-7.12Z"/>
              </g>
              <g id="R" class="text-part">
                <path d="M574.58,327.01c49.51-30.97,34.74-106.44-23.97-112.74l-98.69-.06v65.2c0,.91-4.74,5.81-5.75,6.74h-61.25v6h65.75l8.75-10.24v-61.2h91.25c3.91,0,17.05,5.44,20.91,7.59,37.65,20.95,32.76,80.96-7.17,95.57-1.01.37-6.46,1.42-6.48,2.01l42.99,59.7h-9.75c-2.47-3.21-4.88-6.48-7.33-9.68-1.9-2.49-3.77-5.2-5.7-7.77-11.81-15.7-23.57-31.46-35.21-47.25q-.18-.62.73-.74c2.41-.31,4.65-.32,7.36-.64,53.03-6.31,49-87.06-5.33-90.3l-77.27.02v55.2l-12.5,14.24h-71v6.49h73.33l17.14-20.26c.17-1.04-.03-2.17.03-3.22h24.25c2.07,3.44,4.17,6.89,8.54,7.45,13.4,1.73,17.29-15.78,6.66-20.14-6.49-2.66-12.31.86-15.2,6.69h-23.75v-39.97l68.27-.02c50.13,2.21,50.81,74.45-.03,77.95-14.03.97-29.62.09-43.25,0v6.5h32.75c1.43,1.7,1.76,2.41,2.74,3.62,10.59,13.04,20.5,27.05,30.35,40.72l15.65,21.11h-10.75l-41.85-56.34-33.04-.11c-4.17,3.57-9.64,8.05-13.36,11.5v44.46h-24.5v-59.45h-7.5v67.44h39v-47.71l10.85-9.64c8.23.1,16.59.29,24.85.45l42.55,56.9h47.25l-46.31-62.7c-.13-1.46,3.69-2.55,4.96-3.35ZM510.4,268.24c5.74,0,10.42,4.67,10.42,10.42s-4.67,10.42-10.42,10.42-10.42-4.67-10.42-10.42,4.67-10.42,10.42-10.42Z"/>
                <path d="M538.7,299.16c-1.27.06-24.79-.12-35.24-.16-3.19-.01-6.37.02-9.53.14,1.76,1.84,4.32,4.79,5.28,5.95.7-.04,3.63-.03,7.72,0,5.4.04,12.84.11,19.88.16,9.51.07,18.3.09,20.39-.09,33.75-2.88,36.13-56.63.48-61.47h-63.75v24.48h7v-17.49h55.25c11.46,0,20.75,12.9,20.75,23.73,0,17.64-12.58,24.01-28.23,24.75Z"/>
                <path d="M491.92,314.5c-.11,5.65-4.73,10.21-10.41,10.21s-10.42-4.67-10.42-10.42c0-.75.08-1.47.23-2.17h-86.4v6h86.5c4.39,11.52,20.87,7.44,20.49-3.62Z"/>
                <rect x="468.43" y="328.61" width="6.5" height="48.46"/>
                <path d="M481.52,324.72c5.68,0,10.3-4.56,10.41-10.21,0-.07.01-.13.01-.2,0-5.74-4.67-10.42-10.42-10.42-2.19,0-4.22.68-5.9,1.84-1.57,1.08-2.82,2.59-3.61,4.35-.29.65-.52,1.34-.67,2.05-.15.7-.23,1.43-.23,2.17,0,5.74,4.67,10.42,10.42,10.42ZM476.74,310.82c1.08-1.47,2.81-2.44,4.78-2.44.06,0,.11,0,.17,0,.59.02,1.16.12,1.7.3,2.35.78,4.04,3,4.04,5.61,0,.06,0,.11,0,.17-.09,3.19-2.7,5.74-5.91,5.74s-5.92-2.65-5.92-5.92c0-1.3.43-2.5,1.14-3.48Z"/>
                <path d="M510.4,289.07c5.74,0,10.42-4.67,10.42-10.42s-4.67-10.42-10.42-10.42-10.42,4.67-10.42,10.42,4.67,10.42,10.42,10.42ZM510.39,272.74c3.27,0,5.92,2.65,5.92,5.92s-2.65,5.92-5.92,5.92-5.92-2.65-5.92-5.92,2.65-5.92,5.92-5.92Z"/>
              </g>
              <g id="O" class="primary-part">
                <path d="M296.17,390.07c-52.59-19.23-75.89-78.21-50.58-128.5,21.99-43.71,73.29-63.5,119.18-44.95,27.05,10.93,47.24,34.14,54.16,62.54h7.5c-6.39-27.05-23.35-50.48-47.66-64.03-73.55-40.98-160.31,20.53-149.77,102.69,7.85,61.22,71.93,99.43,130.32,81.38,33.77-10.44,60.21-38.17,67.12-73.07h-7.5c-12.33,54.26-70.92,82.92-122.76,63.96Z"/>
                <path d="M402.18,279.15h7.25c-11.98-46.64-65.34-71.79-110.12-55.33-44.8,16.47-67.21,68.93-48.31,112.96,27.66,64.42,123.21,67.65,153.79,3.92,2.23-4.64,3.9-9.6,5.14-14.59h-7.25c-1.87,4.52-3.23,9.12-5.49,13.5-32.93,63.68-133.54,47.72-145.11-23.39-12.91-79.3,88.98-122.69,137.84-60.29,5.46,6.98,9.17,14.95,12.26,23.22Z"/>
                <g>
                  <g>
                    <path d="M275.99,349.23h105.22v-26.49h-105.22v26.49ZM279.99,326.73h97.22v18.49h-97.22v-18.49Z"/>
                    <polygon points="287.23 330.59 287.23 341.6 290.43 341.6 291.23 341.6 291.23 330.36 287.23 330.36 287.23 330.59"/>
                    <polygon points="299.67 341.6 299.67 331 299.67 330.36 295.67 330.36 295.67 330.99 295.67 341.6 299.67 341.6"/>
                    <polygon points="308.1 330.6 308.1 330.36 304.1 330.36 304.1 341.6 304.93 341.6 308.1 341.6 308.1 330.6"/>
                    <path d="M370.16,336.42c0-1.09-.36-2.09-.96-2.91-.9-1.24-2.36-2.05-4.01-2.05-2.74,0-4.97,2.22-4.97,4.97,0,1.74.9,3.26,2.25,4.15.78.51,1.71.82,2.72.82,2.74,0,4.97-2.22,4.97-4.97Z"/>
                  </g>
                  <g>
                    <path d="M381.2,288.03h-105.22v26.49h105.22v-26.49ZM377.2,310.52h-97.22v-18.49h97.22v18.49Z"/>
                    <rect x="287.23" y="295.65" width="4" height="11.25"/>
                    <rect x="295.67" y="295.65" width="4" height="11.25"/>
                    <rect x="304.1" y="295.65" width="4" height="11.25"/>
                    <ellipse cx="365.19" cy="301.72" rx="4.97" ry="4.97" transform="translate(-.56 .68) rotate(-.11)"/>
                  </g>
                  <g>
                    <path d="M381.2,253.03h-105.22v26.49h105.22v-26.49ZM377.2,275.52h-97.22v-18.49h97.22v18.49Z"/>
                    <path d="M287.65,260.65h-.41v11.25h4v-11.25h-2.73c-.28-.02-.57-.02-.85,0Z"/>
                    <path d="M299.67,271.9v-11.25h-1.17c-.28-.02-.57-.02-.85,0h-1.98v11.25h4Z"/>
                    <polygon points="308.1 260.65 307.65 260.65 304.1 260.65 304.1 271.9 305.43 271.9 308.1 271.9 308.1 260.65"/>
                    <path d="M370.16,266.72c0-1.49-.67-2.81-1.71-3.72-.87-.77-2.01-1.25-3.26-1.25-2.74,0-4.97,2.22-4.97,4.97,0,2.23,1.48,4.09,3.5,4.72.47.15.96.25,1.47.25,2.74,0,4.97-2.22,4.97-4.97Z"/>
                  </g>
                </g>
              </g>
            </g>
          </svg>
        </div>
        <div class="nav-links">
          <el-button link @click="$router.push(ROUTES.HOME)">监控中心</el-button>
          <el-button link @click="$router.push(ROUTES.APPLICATIONS.AGENTS)">智能体管理</el-button>

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
          <div class="hero-badge animate-up">
            <span class="badge-dot"></span>
            智能体管理与全链路监控系统
          </div>
          <h1 class="hero-title animate-up delay-1">
            ORIN <span class="highlight">智能体监控</span>与管理系统
          </h1>
          <p class="hero-subtitle animate-up delay-2">
            下一代企业级 AI 智能体治理平台，连接 Dify、RAGFlow 与业务系统的桥梁。
            实现全链路监控、知识库同步与模型智能调度。
          </p>
          <div class="hero-actions animate-up delay-3">
            <el-button type="primary" size="large" class="start-btn" @click="$router.push(ROUTES.HOME)">
               进入监控大屏 <el-icon class="el-icon--right"><ArrowRight /></el-icon>
            </el-button>
            <el-button size="large" class="secondary-btn" @click="$router.push(ROUTES.APPLICATIONS.AGENT_ONBOARD)">
               接入新智能体
            </el-button>
          </div>

          <div class="hero-stats animate-up delay-4">
            <div class="stat-item">
              <span class="stat-value">99.9%</span>
              <span class="stat-label">服务可用性</span>
            </div>
            <div class="stat-divider"></div>
            <div class="stat-item">
              <span class="stat-value">&lt; 200ms</span>
              <span class="stat-label">平均响应延迟</span>
            </div>
            <div class="stat-divider"></div>
            <div class="stat-item">
              <span class="stat-value">Unlimited</span>
              <span class="stat-label">横向扩展能力</span>
            </div>
          </div>
        </div>
        <div class="hero-visual animate-fade delay-3">
          <div class="visual-card">
            <div class="card-header">
              <span class="dot red"></span>
              <span class="dot yellow"></span>
              <span class="dot green"></span>
            </div>
            <div class="card-body">
              <div class="metric-row">
                <div class="metric-label">CPU 使用率</div>
                <div class="metric-bar"><div class="metric-fill" style="width: 45%"></div></div>
                <div class="metric-value">45%</div>
              </div>
              <div class="metric-row">
                <div class="metric-label">内存占用</div>
                <div class="metric-bar"><div class="metric-fill" style="width: 62%"></div></div>
                <div class="metric-value">62%</div>
              </div>
              <div class="metric-row">
                <div class="metric-label">活跃请求</div>
                <div class="metric-bar"><div class="metric-fill" style="width: 78%"></div></div>
                <div class="metric-value">1,234</div>
              </div>
              <div class="pulse-ring"></div>
            </div>
          </div>
          <div class="floating-badge badge-1">
            <el-icon><Connection /></el-icon>
            <span>知识库已同步</span>
          </div>
          <div class="floating-badge badge-2">
            <el-icon><Monitor /></el-icon>
            <span>监控中</span>
          </div>
        </div>
      </section>

      <!-- Features Section -->
      <section class="features-section">
        <div class="section-header">
          <h2 class="section-title">核心功能特性</h2>
          <div class="title-underline"></div>
        </div>

        <!-- 分类标签 -->
        <div class="category-tabs">
          <button
            v-for="cat in featureCategories"
            :key="cat.key"
            :class="['tab-item', { active: activeFeatureCategory === cat.key }]"
            @click="activeFeatureCategory = cat.key"
          >
            {{ cat.name }}
          </button>
        </div>

        <!-- 功能卡片 -->
        <el-row :gutter="24">
          <el-col
            :span="8"
            v-for="(feature, index) in paginatedFeatures"
            :key="feature.title"
          >
            <div class="feature-card shadow-hover" :style="{ animationDelay: (index * 0.05) + 's' }">
              <div class="feature-icon" :style="{ backgroundColor: feature.bgColor }">
                <el-icon :style="{ color: feature.color }"><component :is="feature.icon" /></el-icon>
              </div>
              <h3>{{ feature.title }}</h3>
              <p>{{ feature.desc }}</p>
            </div>
          </el-col>
        </el-row>

        <!-- 分页器 -->
        <div class="pagination-wrapper" v-if="filteredFeatures.length > pageSize">
          <el-pagination
            v-model:current-page="featurePage"
            :page-size="pageSize"
            :total="filteredFeatures.length"
            layout="prev, pager, next"
            background
          />
        </div>
      </section>

      <!-- Integrations Section -->
      <section class="integrations-section">
        <div class="section-header">
          <h2 class="section-title">支持的平台与服务</h2>
          <div class="title-underline"></div>
        </div>

        <!-- 分类标签 -->
        <div class="category-tabs">
          <button
            v-for="cat in integrationCategories"
            :key="cat.key"
            :class="['tab-item', { active: activeIntegrationCategory === cat.key }]"
            @click="activeIntegrationCategory = cat.key"
          >
            {{ cat.name }}
          </button>
        </div>

        <!-- 集成卡片 -->
        <div class="integration-grid">
          <div
            class="integration-item"
            v-for="(item, index) in paginatedIntegrations"
            :key="item.name"
            :style="{ animationDelay: (index * 0.05) + 's' }"
          >
            <div class="integration-icon">{{ item.icon }}</div>
            <span>{{ item.name }}</span>
          </div>
        </div>

        <!-- 分页器 -->
        <div class="pagination-wrapper" v-if="filteredIntegrations.length > integrationPageSize">
          <el-pagination
            v-model:current-page="integrationPage"
            :page-size="integrationPageSize"
            :total="filteredIntegrations.length"
            layout="prev, pager, next"
            background
          />
        </div>
      </section>

      <!-- Footer Meta -->
      <footer class="landing-footer">
         <p>
           © 2025-2026 ORIN
           <a href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer" style="color: inherit; text-decoration: none; margin-left: 10px; transition: color 0.3s;" onmouseover="this.style.color='var(--orin-primary)'" onmouseout="this.style.color='inherit'">
             蜀ICP备2025125402号-3
           </a>
         </p>
         <div class="links">
            <a href="https://github.com/AdlinZ/ORIN/" target="_blank">GitHub</a>
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
import { ROUTES } from '@/router/routes';
import Cookies from 'js-cookie';
import { ElMessage } from 'element-plus';
import { useUserStore } from '@/stores/user';
import BrandingLogo from '@/components/BrandingLogo.vue';
import {
  ArrowRight, Monitor, Cpu, Connection, Tickets,
  Lock, Share, CaretBottom, User, SwitchButton,
  Monitor as MonitorIcon, User as UserIcon, DataAnalysis, Clock, Bell, Key
} from '@element-plus/icons-vue';
import { computed, watch } from 'vue';

const router = useRouter();
const userStore = useUserStore();
const isLoggedIn = ref(false);

// 分页相关状态
const pageSize = 6;
const featurePage = ref(1);
const integrationPageSize = 8;
const integrationPage = ref(1);
const activeFeatureCategory = ref('all');
const activeIntegrationCategory = ref('all');

// Features 分类数据
const featureCategories = [
  { key: 'all', name: '全部' },
  { key: 'monitor', name: '监控' },
  { key: 'knowledge', name: '知识库' },
  { key: 'security', name: '安全' },
  { key: 'ecosystem', name: '生态' }
];

// Integrations 分类数据
const integrationCategories = [
  { key: 'all', name: '全部' },
  { key: 'ai', name: 'AI 模型' },
  { key: 'vector', name: '向量库' },
  { key: 'db', name: '数据库' }
];

// 带分类的 features 数据
const featuresByCategory = {
  monitor: [
    { title: '全链路监控', desc: '实时追踪 CPU、内存利用率及令牌消耗，秒级洞察系统性能瓶颈。', icon: Monitor, color: '#00BFA5', bgColor: 'rgba(0, 191, 165, 0.1)' },
    { title: '交互日志审计', desc: '全面保留会话流水与逻辑树，支持全站事件溯源与安全合规审查。', icon: Tickets, color: '#EF4444', bgColor: 'rgba(239, 68, 68, 0.1)' },
    { title: '实时告警', desc: '多通道通知机制，及时响应系统异常与性能瓶颈。', icon: Bell, color: '#F59E0B', bgColor: 'rgba(245, 158, 11, 0.1)' }
  ],
  knowledge: [
    { title: '知识库自动同步', desc: '深度集成 Dify、RAGFlow 知识库，支持文档版本管理与云端动态资产更新。', icon: Connection, color: '#10B981', bgColor: 'rgba(16, 185, 129, 0.1)' }
  ],
  security: [
    { title: '模型安全管理', desc: '密钥托管与多租户权限隔离，确保留言资产与模型接口访问安全。', icon: Key, color: '#8B5CF6', bgColor: 'rgba(139, 92, 246, 0.1)' }
  ],
  ecosystem: [
    { title: '分布式生命周期', desc: '从接入到注销，一站式控制不同环境下的智能体运行状态。', icon: Cpu, color: '#F59E0B', bgColor: 'rgba(245, 158, 11, 0.1)' },
    { title: '开放协同生态', desc: '标准 Webhook 接口与 API 扩展，轻松对接现有 DevOps 运维体系。', icon: Share, color: '#06B6D4', bgColor: 'rgba(6, 182, 212, 0.1)' }
  ]
};

// 带分类的 integrations 数据
const integrationsByCategory = {
  ai: [
    { name: 'Dify', icon: '🔗' },
    { name: 'RAGFlow', icon: '📚' },
    { name: 'Kimi', icon: '🧠' },
    { name: 'SiliconFlow', icon: '⚡' },
    { name: 'OpenAI', icon: '🤖' }
  ],
  vector: [
    { name: 'Milvus', icon: '🔍' }
  ],
  db: [
    { name: 'MySQL', icon: '🗄️' },
    { name: 'Redis', icon: '⚡' }
  ]
};

// 计算过滤后的数据
const filteredFeatures = computed(() => {
  if (activeFeatureCategory.value === 'all') {
    return Object.values(featuresByCategory).flat();
  }
  return featuresByCategory[activeFeatureCategory.value] || [];
});

const filteredIntegrations = computed(() => {
  if (activeIntegrationCategory.value === 'all') {
    return Object.values(integrationsByCategory).flat();
  }
  return integrationsByCategory[activeIntegrationCategory.value] || [];
});

// 分页后的数据
const paginatedFeatures = computed(() => {
  const start = (featurePage.value - 1) * pageSize;
  return filteredFeatures.value.slice(start, start + pageSize);
});

const paginatedIntegrations = computed(() => {
  const start = (integrationPage.value - 1) * integrationPageSize;
  return filteredIntegrations.value.slice(start, start + integrationPageSize);
});

// 切换分类时重置页码
watch(activeFeatureCategory, () => {
  featurePage.value = 1;
});

watch(activeIntegrationCategory, () => {
  integrationPage.value = 1;
});

const userInfo = reactive({
  name: '',
  role: '',
  avatar: ''
});

const checkLoginStatus = () => {
  const token = Cookies.get('orin_token');
  isLoggedIn.value = !!token;

  if (token) {
    if (userStore.userInfo) {
      userInfo.name = userStore.userInfo.username || userStore.userInfo.nickname || userStore.userInfo.name || '用户';
      userInfo.avatar = userStore.userInfo.avatar || '';

      if (userStore.roles && userStore.roles.length > 0) {
         const roleMap = { 'ROLE_ADMIN': 'ADMIN', 'ROLE_USER': 'USER' };
         userInfo.role = roleMap[userStore.roles[0]] || userStore.roles[0];
      } else {
         userInfo.role = 'USER';
      }
    } else {
       userStore.restoreFromCookies();
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
      router.push(ROUTES.HOME);
      break;
    case 'profile':
      router.push(ROUTES.CONTROL.PROFILE);
      break;
    case 'logout':
      handleLogout();
      break;
  }
};

onMounted(() => {
  checkLoginStatus();
});
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

.logo-svg {
  height: 32px;
  width: auto;
  display: block;
}

.logo-svg .text-part {
  fill: var(--orin-primary);
}

.logo-svg .primary-part {
  fill: var(--orin-primary);
}

.nav-links {
  display: flex;
  align-items: center;
  gap: 20px;
}
.mini-logo { width: 28px; height: 28px; }
.logo-text { font-weight: 700; font-size: 18px; color: var(--orin-primary); }

.avatar-wrapper {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 4px 12px;
  border-radius: 12px;
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
  color: var(--orin-primary);
  background: rgba(0, 191, 165, 0.1);
  padding: 0 4px;
  border-radius: 4px;
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
  min-height: 550px;
  padding: 80px 0;
  gap: 80px;
}

.hero-content {
  flex: 1;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: rgba(0, 191, 165, 0.1);
  border: 1px solid rgba(0, 191, 165, 0.2);
  border-radius: 50px;
  font-size: 14px;
  color: var(--orin-primary);
  margin-bottom: 24px;
}

.badge-dot {
  width: 8px;
  height: 8px;
  background: var(--orin-primary);
  border-radius: 50%;
  animation: pulse 2s infinite;
}

.hero-title {
  font-size: 56px;
  font-weight: 800;
  line-height: 1.15;
  color: var(--neutral-black);
  margin-bottom: 24px;
  letter-spacing: -1px;
}

.hero-title .highlight {
  color: var(--orin-primary);
  position: relative;
}

.hero-title .highlight::after {
  content: '';
  position: absolute;
  bottom: 8px;
  left: 0;
  width: 100%;
  height: 8px;
  background: rgba(0, 191, 165, 0.2);
  z-index: -1;
  border-radius: 4px;
}

.hero-subtitle {
  font-size: 18px;
  line-height: 1.7;
  color: var(--neutral-gray-5);
  margin-bottom: 40px;
  max-width: 580px;
}

.hero-actions {
  display: flex;
  gap: 16px;
  margin-bottom: 60px;
}

.start-btn {
  padding: 16px 36px;
  font-weight: 600;
  border-radius: 12px;
  background: var(--orin-primary);
  border-color: var(--orin-primary);
  box-shadow: 0 8px 20px rgba(0, 191, 165, 0.3);
}

.start-btn:hover {
  background: var(--orin-primary-hover);
  border-color: var(--orin-primary-hover);
  box-shadow: 0 12px 28px rgba(0, 191, 165, 0.4);
}

.secondary-btn {
  padding: 16px 36px;
  font-weight: 600;
  border-radius: 12px;
}

.hero-stats {
  display: flex;
  align-items: center;
  gap: 32px;
}

.stat-item {
  display: flex;
  flex-direction: column;
}

.stat-value {
  font-size: 28px;
  font-weight: 800;
  color: var(--orin-primary);
}

.stat-label {
  font-size: 13px;
  color: var(--neutral-gray-4);
}

.stat-divider {
  width: 1px;
  height: 40px;
  background: var(--neutral-gray-3);
}

/* Hero Visual */
.hero-visual {
  flex: 0.9;
  position: relative;
  display: flex;
  justify-content: center;
  align-items: center;
}

.visual-card {
  width: 360px;
  background: white;
  border-radius: 16px;
  box-shadow: 0 25px 50px rgba(0, 191, 165, 0.15);
  overflow: hidden;
  position: relative;
  z-index: 2;
}

html.dark .visual-card {
  background: #1a1a1a;
}

.card-header {
  padding: 16px 20px;
  display: flex;
  gap: 8px;
  border-bottom: 1px solid var(--neutral-gray-2);
}

.dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
}
.dot.red { background: #EF4444; }
.dot.yellow { background: #F59E0B; }
.dot.green { background: #10B981; }

.card-body {
  padding: 24px;
  position: relative;
}

.metric-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.metric-label {
  width: 80px;
  font-size: 13px;
  color: var(--neutral-gray-5);
}

.metric-bar {
  flex: 1;
  height: 8px;
  background: var(--neutral-gray-2);
  border-radius: 4px;
  overflow: hidden;
}

.metric-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--orin-primary), #10B981);
  border-radius: 4px;
  transition: width 1s ease;
}

.metric-value {
  width: 50px;
  text-align: right;
  font-size: 14px;
  font-weight: 600;
  color: var(--orin-primary);
}

.pulse-ring {
  position: absolute;
  bottom: 20px;
  right: 20px;
  width: 80px;
  height: 80px;
  border: 3px solid var(--orin-primary);
  border-radius: 50%;
  opacity: 0.3;
  animation: pulse-ring 2s infinite;
}

@keyframes pulse-ring {
  0% { transform: scale(0.8); opacity: 0.3; }
  50% { transform: scale(1.1); opacity: 0.1; }
  100% { transform: scale(0.8); opacity: 0.3; }
}

.floating-badge {
  position: absolute;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: white;
  border-radius: 50px;
  box-shadow: 0 8px 20px rgba(0,0,0,0.1);
  font-size: 13px;
  font-weight: 500;
  color: var(--neutral-gray-7);
  z-index: 3;
  animation: float 3s ease-in-out infinite;
}

.floating-badge .el-icon {
  color: var(--orin-primary);
}

.badge-1 {
  top: 40px;
  right: 0;
  animation-delay: 0s;
}

.badge-2 {
  bottom: 60px;
  left: -20px;
  animation-delay: 1.5s;
}

@keyframes float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-10px); }
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
  background: var(--orin-primary);
  margin: 0 auto;
  border-radius: 2px;
}

/* 分类标签 */
.category-tabs {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-bottom: 40px;
  flex-wrap: wrap;
}

.tab-item {
  padding: 10px 24px;
  border: 1px solid var(--neutral-gray-2);
  border-radius: 50px;
  background: white;
  color: var(--neutral-gray-5);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
}

html.dark .tab-item {
  background: #1a1a1a;
  border-color: #2a2a2a;
}

.tab-item:hover {
  border-color: var(--orin-primary);
  color: var(--orin-primary);
}

.tab-item.active {
  background: var(--orin-primary);
  border-color: var(--orin-primary);
  color: white;
}

/* 分页器 */
.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 40px;
}

.pagination-wrapper :deep(.el-pagination) {
  --el-pagination-button-bg-color: white;
  --el-pagination-button-color: var(--neutral-gray-5);
  --el-pagination-hover-color: var(--orin-primary);
}

html.dark .pagination-wrapper :deep(.el-pagination) {
  --el-pagination-button-bg-color: #1a1a1a;
}

.pagination-wrapper :deep(.el-pagination.is-background .el-pager li.is-active) {
  background-color: var(--orin-primary);
}

.feature-card {
  background: var(--neutral-white);
  padding: 36px;
  border-radius: 16px;
  border: 1px solid var(--neutral-gray-2);
  height: 100%;
  margin-bottom: 24px;
  transition: all 0.3s ease;
}

html.dark .feature-card {
  background: #1a1a1a;
  border-color: #2a2a2a;
}

.feature-card:hover {
  transform: translateY(-8px);
  border-color: var(--orin-primary);
  box-shadow: 0 20px 40px rgba(0, 191, 165, 0.1);
}

.feature-icon {
  width: 52px;
  height: 52px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  margin-bottom: 20px;
}

.feature-card h3 {
  font-size: 18px;
  margin-bottom: 10px;
  color: var(--neutral-black);
}

.feature-card p {
  color: var(--neutral-gray-5);
  line-height: 1.6;
  font-size: 14px;
}

/* Integrations Section */
.integrations-section {
  padding: 60px 0 100px;
}

.integration-grid {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 20px;
}

.integration-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 24px;
  background: white;
  border: 1px solid var(--neutral-gray-2);
  border-radius: 12px;
  font-size: 14px;
  font-weight: 500;
  color: var(--neutral-gray-6);
  transition: all 0.3s;
}

html.dark .integration-item {
  background: #1a1a1a;
  border-color: #2a2a2a;
}

.integration-item:hover {
  border-color: var(--orin-primary);
  color: var(--orin-primary);
  transform: translateY(-2px);
}

.integration-icon {
  font-size: 18px;
}

/* Footer */
.landing-footer {
  padding: 40px 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: var(--neutral-gray-4);
  font-size: 14px;
  border-top: 1px solid var(--neutral-gray-2);
}

.links { display: flex; gap: 30px; }
.links span, .links a { cursor: pointer; transition: color 0.3s; color: inherit; text-decoration: none; }
.links span:hover, .links a:hover { color: var(--orin-primary); }

/* Animations */
.animate-up { opacity: 0; transform: translateY(20px); animation: fadeUp 0.6s forwards; }
.animate-fade { opacity: 0; animation: fadeIn 0.8s forwards; }
.delay-1 { animation-delay: 0.15s; }
.delay-2 { animation-delay: 0.3s; }
.delay-3 { animation-delay: 0.45s; }
.delay-4 { animation-delay: 0.6s; }

@keyframes fadeUp { to { opacity: 1; transform: translateY(0); } }
@keyframes fadeIn { to { opacity: 1; } }
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

@media (max-width: 1100px) {
  .hero-section { flex-direction: column; text-align: center; }
  .hero-subtitle { margin: 0 auto 40px; }
  .hero-actions { justify-content: center; }
  .hero-stats { justify-content: center; }
  .hero-visual { display: none; }
}
</style>
