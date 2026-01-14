<template>
  <div class="page-header-wrapper">
    <div class="page-header-container">
      <div class="header-main">
        <div class="header-content">
          <div class="title-section">
            <div v-if="icon" class="header-icon">
              <el-icon><component :is="icon" /></el-icon>
            </div>
            <h1 class="page-title">{{ title }}</h1>
            <el-tag v-if="tagText" :type="tagType" effect="plain" round class="header-tag">
              <slot name="tag-content">
                {{ tagText }}
              </slot>
            </el-tag>
          </div>
          <p v-if="description" class="header-description">{{ description }}</p>
        </div>
        <div class="header-actions">
          <slot name="actions"></slot>
        </div>
      </div>
      <div v-if="$slots.filters" class="header-filters">
        <slot name="filters"></slot>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: {
    type: String,
    required: true
  },
  description: {
    type: String,
    default: ''
  },
  icon: {
    type: [Object, String],
    default: null
  },
  tagText: {
    type: String,
    default: ''
  },
  tagType: {
    type: String,
    default: 'success'
  }
});
</script>

<style scoped>
.page-header-wrapper {
  margin-bottom: var(--spacing-xl);
  perspective: 1000px;
}

.page-header-container {
  background: rgba(var(--neutral-white-rgb, 255, 255, 255), 0.7);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid rgba(var(--primary-500-rgb, 99, 102, 241), 0.1);
  border-radius: var(--radius-xl);
  padding: var(--spacing-lg) var(--spacing-xl);
  box-shadow: 
    0 4px 6px -1px rgba(0, 0, 0, 0.05),
    0 10px 15px -3px rgba(0, 0, 0, 0.03),
    inset 0 0 0 1px rgba(255, 255, 255, 0.5);
  transition: all var(--transition-base);
}

html.dark .page-header-container {
  background: rgba(17, 24, 39, 0.6);
  border-color: rgba(99, 102, 241, 0.2);
  box-shadow: 
    0 10px 30px -10px rgba(0, 0, 0, 0.5),
    inset 0 0 0 1px rgba(255, 255, 255, 0.05);
}

.header-main {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--spacing-xl);
}

.header-content {
  flex: 1;
}

.title-section {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}

.header-icon {
  width: 40px;
  height: 40px;
  background: linear-gradient(135deg, var(--primary-500) 0%, var(--primary-600) 100%);
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 20px;
  box-shadow: 0 4px 12px var(--primary-glow);
}

.page-title {
  font-size: var(--text-2xl);
  font-weight: var(--font-extrabold);
  background: linear-gradient(135deg, var(--neutral-gray-900) 0%, var(--primary-600) 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  margin: 0;
  letter-spacing: -0.02em;
}

html.dark .page-title {
  background: linear-gradient(135deg, #fff 0%, var(--primary-300) 100%);
  -webkit-background-clip: text;
  background-clip: text;
}

.header-tag {
  font-family: var(--font-heading);
  font-weight: var(--font-bold);
  border: none;
  background: var(--primary-light);
  color: var(--primary-600);
}

.header-description {
  margin-top: var(--spacing-xs);
  color: var(--neutral-gray-500);
  font-size: var(--text-sm);
  max-width: 600px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.header-filters {
  margin-top: var(--spacing-lg);
  padding-top: var(--spacing-lg);
  border-top: 1px solid rgba(0, 0, 0, 0.05);
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--spacing-md);
}

html.dark .header-filters {
  border-top-color: rgba(255, 255, 255, 0.05);
}

/* Animations */
.page-header-container {
  animation: slideInDown 0.6s cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes slideInDown {
  from {
    transform: translateY(-20px);
    opacity: 0;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}
</style>
