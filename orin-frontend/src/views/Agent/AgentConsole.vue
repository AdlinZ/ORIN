<template>
  <AgentWorkspace :preset-agent-id="agentId" :lock-agent="true" />
</template>

<script setup>
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import AgentWorkspace from './AgentWorkspace.vue';

const route = useRoute();
const agentId = computed(() => String(route.params.id || ''));
</script>
