<template>
  <div class="home-dashboard">
    <div class="dashboard-header">
      <h1>系统总览</h1>
      <p>ORIN 智能体运行监控中心</p>
    </div>

    <div class="dashboard-grid">
      <!-- 系统统计 -->
      <el-card class="stat-card">
        <template #header>
          <div class="card-header">
            <el-icon><Box /></el-icon>
            <span>活跃应用</span>
          </div>
        </template>
        <div class="stat-content">
          <div class="stat-number">12</div>
          <div class="stat-label">个应用正在运行</div>
        </div>
      </el-card>

      <el-card class="stat-card">
        <template #header>
          <div class="card-header">
            <el-icon><ChatDotRound /></el-icon>
            <span>今日调用</span>
          </div>
        </template>
        <div class="stat-content">
          <div class="stat-number">2,847</div>
          <div class="stat-label">次API调用</div>
        </div>
      </el-card>

      <el-card class="stat-card">
        <template #header>
          <div class="card-header">
            <el-icon><Coin /></el-icon>
            <span>Token 用量</span>
          </div>
        </template>
        <div class="stat-content">
          <div class="stat-number">1.2M</div>
          <div class="stat-label">Token 已使用</div>
        </div>
      </el-card>

      <el-card class="stat-card">
        <template #header>
          <div class="card-header">
            <el-icon><Timer /></el-icon>
            <span>平均延迟</span>
          </div>
        </template>
        <div class="stat-content">
          <div class="stat-number">234ms</div>
          <div class="stat-label">响应时间</div>
        </div>
      </el-card>
    </div>

    <!-- 快捷入口 -->
    <el-card class="quick-access">
      <template #header>
        <div class="card-header">
          <el-icon><Grid /></el-icon>
          <span>快捷入口</span>
        </div>
      </template>
      <div class="quick-links">
        <router-link :to="ROUTES.APPLICATIONS.AGENTS" class="quick-link">
          <el-icon><Box /></el-icon>
          <span>应用管理</span>
        </router-link>
        <router-link :to="ROUTES.RUNTIME.OVERVIEW" class="quick-link">
          <el-icon><Monitor /></el-icon>
          <span>运行监控</span>
        </router-link>
        <router-link :to="ROUTES.RESOURCES.KNOWLEDGE" class="quick-link">
          <el-icon><Document /></el-icon>
          <span>知识库</span>
        </router-link>
        <router-link :to="ROUTES.CONTROL.USERS" class="quick-link">
          <el-icon><Setting /></el-icon>
          <span>系统设置</span>
        </router-link>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { Box, ChatDotRound, Coin, Timer, Grid, Monitor, Document, Setting } from '@element-plus/icons-vue'
import { ROUTES } from '@/router/routes'
</script>

<style scoped>
.home-dashboard {
  padding: 24px;
}

.dashboard-header {
  margin-bottom: 32px;
}

.dashboard-header h1 {
  font-size: 28px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  margin: 0 0 8px 0;
}

.dashboard-header p {
  font-size: 14px;
  color: var(--el-text-color-secondary);
  margin: 0;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 20px;
  margin-bottom: 24px;
}

.stat-card {
  border-radius: 12px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.stat-content {
  text-align: center;
  padding: 20px 0;
}

.stat-number {
  font-size: 36px;
  font-weight: 700;
  color: var(--el-color-primary);
  margin-bottom: 8px;
}

.stat-label {
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.quick-access {
  border-radius: 12px;
}

.quick-links {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 16px;
}

.quick-link {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 24px;
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
  text-decoration: none;
  color: var(--el-text-color-primary);
  transition: all 0.3s;
}

.quick-link:hover {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  transform: translateY(-2px);
}

.quick-link .el-icon {
  font-size: 32px;
}

.quick-link span {
  font-size: 14px;
  font-weight: 500;
}
</style>
