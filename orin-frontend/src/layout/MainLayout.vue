<template>
  <div class="main-layout">
    <!-- 顶部导航栏 -->
    <TopNavbar />
    
    <!-- 主内容区域 -->
    <div class="content-area">
      <router-view v-slot="{ Component }">
        <transition name="fade-transform" mode="out-in">
          <component :is="Component" :key="$route.fullPath" />
        </transition>
      </router-view>
    </div>
  </div>
</template>

<script setup>
import TopNavbar from './components/TopNavbar.vue'
import { useRoute } from 'vue-router'

const $route = useRoute()
</script>

<style scoped>
.main-layout {
  min-height: 100vh;
  background: #f5f7fa;
}

.content-area {
  margin-top: 60px; /* 顶部导航栏高度 */
  padding: 20px;
  min-height: calc(100vh - 60px);
}

/* 页面切换动画 */
.fade-transform-enter-active,
.fade-transform-leave-active {
  transition: all 0.3s;
}

.fade-transform-enter-from {
  opacity: 0;
  transform: translateX(-10px);
}

.fade-transform-leave-to {
  opacity: 0;
  transform: translateX(10px);
}
</style>
