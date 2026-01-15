<template>
  <TokenExpiryWarning />
  <router-view />
</template>

<script setup>
import TokenExpiryWarning from '@/components/TokenExpiryWarning.vue';
</script>

<style>
/* Reset and global overrides if any */
html, body, #app {
  height: 100%;
  margin: 0;
  padding: 0;
}
</style>
