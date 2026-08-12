<template>
  <div class="page-header-wrapper" :class="{ 'is-flat': flat }">
    <div class="page-header-container">
      <div class="header-main">
        <div class="header-content">
          <div class="title-section">
            <div v-if="icon" class="header-icon">
              <el-icon><component :is="icon" /></el-icon>
            </div>
            <div class="title-copy">
              <h1 class="page-title">
                {{ title }}
              </h1>
              <p v-if="description" class="header-description">
                {{ description }}
              </p>
            </div>
            <div v-if="$slots['tag-content']" class="header-meta">
              <slot name="tag-content" />
            </div>
            <el-tag
              v-else-if="tagText"
              :type="tagType"
              effect="plain"
              class="header-tag"
            >
              {{ tagText }}
            </el-tag>
          </div>
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
  margin-bottom: var(--orin-section-gap, 16px);
}

.page-header-container {
  background: var(--orin-surface, #ffffff);
  border: 1px solid var(--orin-border, #dfe6e4);
  border-radius: var(--orin-radius-lg, 12px);
  padding: 22px 24px;
  box-shadow: var(--orin-shadow-xs, 0 1px 2px rgba(15, 23, 42, 0.04));
}

html.dark .page-header-container {
  background: rgba(15, 23, 42, 0.8);
  border-color: rgba(148, 163, 184, 0.22);
  box-shadow: none;
}

.header-main {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 24px;
}

.header-content {
  flex: 1;
}

.title-section {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}

.header-icon {
  width: 40px;
  height: 40px;
  flex: 0 0 40px;
  background: var(--orin-primary-soft, #eef8f6);
  border: 1px solid var(--orin-primary-border, #cfe6e1);
  border-radius: var(--orin-radius-md, 10px);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--orin-primary);
  font-size: 19px;
}

.title-copy {
  min-width: 0;
}

.page-title {
  font-size: 23px;
  font-weight: 650;
  line-height: 1.25;
  color: var(--orin-ink, #17211f);
  margin: 0;
  letter-spacing: -0.01em;
}

html.dark .page-title {
  color: #f8fafc;
}

.header-tag {
  margin-top: 3px;
  font-weight: 600;
}

.header-meta {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding-top: 3px;
}

.header-description {
  margin: 6px 0 0;
  color: var(--orin-muted, #66736f);
  font-size: 13px;
  line-height: 1.55;
  max-width: 760px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.header-filters {
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid var(--orin-border-soft, #edf1f0);
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
  padding: 20px 22px;
}

@media (max-width: 760px) {
  .page-header-container {
    padding: 18px;
  }

  .header-main,
  .title-section {
    align-items: flex-start;
  }

  .header-main {
    flex-direction: column;
  }

  .header-actions {
    width: 100%;
    flex-wrap: wrap;
  }

  .header-meta {
    display: none;
  }
}

</style>
