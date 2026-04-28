<template>
  <div class="page-header-wrapper" :class="{ 'is-flat': flat }">
    <div class="page-header-container">
      <div class="header-main">
        <div class="header-content">
          <div class="title-section">
            <div v-if="icon" class="header-icon">
              <el-icon><component :is="icon" /></el-icon>
            </div>
            <h1 class="page-title">
              {{ title }}
            </h1>
            <el-tag
              v-if="tagText"
              :type="tagType"
              effect="plain"
              round
              class="header-tag"
            >
              <slot name="tag-content">
                {{ tagText }}
              </slot>
            </el-tag>
          </div>
          <p v-if="description" class="header-description">
            {{ description }}
          </p>
        </div>
        <div class="header-actions">
          <slot name="actions" />
        </div>
      </div>
      <div v-if="$slots.filters" class="header-filters">
        <slot name="filters" />
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
  },
  flat: {
    type: Boolean,
    default: false
  }
});
</script>

<style scoped>
.page-header-wrapper {
  margin-bottom: var(--orin-page-gap, 18px);
}

.page-header-container {
  background: rgba(255, 255, 255, 0.84);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid var(--orin-border-strong, #d8e0e8);
  border-radius: var(--radius-base, 8px);
  padding: 18px 20px;
  box-shadow: var(--shadow-sm, 0 1px 3px rgba(15, 23, 42, 0.08));
  transition: all var(--transition-base);
}

html.dark .page-header-container {
  background: rgba(15, 23, 42, 0.8);
  border-color: rgba(148, 163, 184, 0.22);
  box-shadow: none;
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
  width: 36px;
  height: 36px;
  background: var(--neutral-gray-100);
  border: 1px solid var(--orin-border-strong, #d8e0e8);
  border-radius: var(--radius-base, 8px);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--orin-primary);
  font-size: 18px;
}

.page-title {
  font-size: 22px;
  font-weight: var(--font-bold);
  color: var(--neutral-gray-900);
  margin: 0;
  letter-spacing: 0;
}

html.dark .page-title {
  color: #f8fafc;
}

.header-tag {
  font-family: var(--font-heading);
  font-weight: var(--font-bold);
  border: none;
  background: var(--primary-light);
  color: var(--primary-600);
}

.header-description {
  margin: var(--spacing-sm) 0 0;
  color: var(--neutral-gray-500);
  font-size: var(--text-sm);
  max-width: 760px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.header-filters {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--orin-border-strong, #d8e0e8);
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--spacing-md);
}

html.dark .header-filters {
  border-top-color: rgba(255, 255, 255, 0.05);
}

/* flat 模式：去掉独立卡片外壳，用于嵌入其他卡片内 */
.is-flat {
  margin-bottom: 0;
}

.is-flat .page-header-container {
  background: transparent !important;
  backdrop-filter: none !important;
  -webkit-backdrop-filter: none !important;
  border: none !important;
  border-radius: 0 !important;
  box-shadow: none !important;
  animation: none !important;
  padding: var(--spacing-lg) var(--spacing-xl);
}

</style>
