<template>
  <div class="workflow-edge" :class="{ 'is-active': isActive, 'has-error': hasError }">
    <svg
      v-if="!isSimple"
      viewBox="0 0 100 40"
      class="edge-path"
      :class="{ 'is-animated': animated }"
    >
      <!-- Animated flow dots -->
      <circle
        v-if="animated"
        r="3"
        fill="#10b981"
        class="flow-dot"
      >
        <animateMotion
          dur="2s"
          repeatCount="indefinite"
          path="M5,20 Q50,5 95,20"
        />
      </circle>

      <!-- Main path -->
      <path
        :d="path"
        :stroke="edgeColor"
        stroke-width="2"
        fill="none"
        :stroke-dasharray="hasError ? '5,5' : ''"
      />
      
      <!-- Data label on edge -->
      <rect
        v-if="dataLabel"
        :x="labelX"
        y="15"
        width="60"
        height="14"
        rx="3"
        fill="#f8fafc"
        stroke="#e2e8f0"
      />
      <text
        v-if="dataLabel"
        :x="labelX + 30"
        y="25"
        text-anchor="middle"
        font-size="8"
        fill="#64748b"
      >
        {{ dataLabel }}
      </text>
    </svg>
    
    <!-- Simple straight line -->
    <svg v-else viewBox="0 0 100 20" class="edge-path">
      <path
        :d="simplePath"
        :stroke="edgeColor"
        stroke-width="2"
        fill="none"
        marker-end="url(#arrowhead)"
      />
    </svg>
    
    <!-- Arrow marker -->
    <svg style="position: absolute; width: 0; height: 0;">
      <defs>
        <marker
          id="arrowhead"
          markerWidth="10"
          markerHeight="7"
          refX="9"
          refY="3.5"
          orient="auto"
        >
          <polygon
            points="0 0, 10 3.5, 0 7"
            :fill="edgeColor"
          />
        </marker>
      </defs>
    </svg>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  id: String,
  sourceX: Number,
  sourceY: Number,
  targetX: Number,
  targetY: Number,
  sourcePosition: String,
  targetPosition: String,
  data: Object,
  animated: {
    type: Boolean,
    default: false
  },
  selected: Boolean
})

const isSimple = computed(() => {
  return Math.abs(props.targetX - props.sourceX) < 50
})

const path = computed(() => {
  const sx = props.sourceX
  const sy = props.sourceY
  const tx = props.targetX
  const ty = props.targetY
  
  // Bezier curve for smooth flow
  const dx = Math.abs(tx - sx) / 2
  return `M5,20 Q50,5 95,20`
})

const simplePath = computed(() => {
  return `M0,10 L90,10`
})

const labelX = computed(() => {
  return 20
})

const dataLabel = computed(() => {
  return props.data?.label || props.data?.variable || ''
})

const isActive = computed(() => {
  return props.data?.active || false
})

const hasError = computed(() => {
  return props.data?.error || false
})

const animated = computed(() => {
  return props.animated || props.data?.animated || false
})

const edgeColor = computed(() => {
  if (hasError.value) return '#ef4444'
  if (isActive.value) return '#10b981'
  if (props.selected) return '#3b82f6'
  return '#94a3b8'
})
</script>

<style scoped>
.workflow-edge {
  width: 100px;
  height: 40px;
}

.edge-path {
  width: 100%;
  height: 100%;
}

.edge-path path {
  transition: stroke 0.3s ease;
}

.flow-dot {
  filter: drop-shadow(0 0 3px #10b981);
}

.is-animated .flow-dot {
  opacity: 1;
}

.is-active path {
  stroke: #10b981;
  stroke-width: 3;
}

.has-error path {
  stroke: #ef4444;
  stroke-dasharray: 5, 5;
}
</style>
