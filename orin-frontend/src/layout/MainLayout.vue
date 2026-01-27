<template>
  <div class="app-wrapper">
    <Sidebar class="sidebar-container" />
    <div class="main-container" :class="{ 'collapsed': appStore.isCollapse }">
      <Navbar />
      <div class="app-main">
        <router-view v-slot="{ Component }">
           <component :is="Component" :key="$route.fullPath" />
        </router-view>
      </div>
    </div>
  </div>
</template>

<script setup>
import Sidebar from './components/Sidebar.vue';
import Navbar from './components/Navbar.vue';
import { useAppStore } from '@/stores/app';
import { useRoute } from 'vue-router';

const appStore = useAppStore();
const route = useRoute();
</script>

<style scoped>
.app-wrapper {
  display: flex;
  width: 100%;
  height: 100vh;
  position: relative;
}

.main-container {
  min-height: 100vh;
  width: calc(100% - var(--sidebar-width));
  transition: width 0.3s, margin-left 0.3s;
  margin-left: var(--sidebar-width);
  background-color: var(--neutral-bg);
}

.main-container.collapsed {
  width: calc(100% - var(--sidebar-width-collapsed));
  margin-left: var(--sidebar-width-collapsed);
}

.app-main {
  padding: 20px;
  /* (100vh - header height) */
  min-height: calc(100vh - var(--header-height)); 
  overflow-y: auto;
}

/* Page transition logic */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
