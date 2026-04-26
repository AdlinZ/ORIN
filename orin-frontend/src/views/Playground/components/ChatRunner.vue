<script setup>
import { MessageSquare, PanelLeftClose, PanelLeftOpen, PanelRightClose, PanelRightOpen, Send, Square } from "lucide-vue-next";
import { inject, nextTick, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { I18N_KEY } from "../i18n";
import CollaborationMessageList from "./CollaborationMessageList.vue";

const props = defineProps({
  selectedWorkflowId: {
    type: String,
    default: "",
  },
  selectedWorkflow: {
    type: Object,
    default: null,
  },
  loading: {
    type: Boolean,
    default: false,
  },
  leftVisible: {
    type: Boolean,
    default: true,
  },
  rightVisible: {
    type: Boolean,
    default: true,
  },
  showRightToggle: {
    type: Boolean,
    default: true,
  },
  messages: {
    type: Array,
    default: () => [],
  },
});

const emit = defineEmits(["run", "clear", "stop", "toggle-left", "toggle-right"]);
const i18n = inject(I18N_KEY, null);
const t = i18n?.t || ((key) => key);
const router = useRouter();

const form = reactive({
  user_input: "",
});
const inputRef = ref(null);

function resizeInput() {
  const el = inputRef.value;
  if (!el) return;
  el.style.height = "auto";
  const maxHeight = 180;
  const nextHeight = Math.min(el.scrollHeight, maxHeight);
  el.style.height = `${nextHeight}px`;
  el.style.overflowY = el.scrollHeight > maxHeight ? "auto" : "hidden";
}

function handleInput() {
  nextTick(resizeInput);
}

function handleKeydown(event) {
  if (event.isComposing) return;
  if (event.key === "Enter" && !event.shiftKey) {
    event.preventDefault();
    submit();
  }
}

async function submit() {
  if (props.loading || !props.selectedWorkflowId || !form.user_input.trim()) return;
  const nextInput = form.user_input;
  form.user_input = "";
  await nextTick();
  resizeInput();
  await emit("run", {
    workflow_id: props.selectedWorkflowId,
    user_input: nextInput,
  });
}

function editCurrentWorkflow() {
  if (!props.selectedWorkflowId) return;
  router.push({
    path: "/dashboard/applications/playground/workflows",
    query: { workflowId: props.selectedWorkflowId },
  });
}

onMounted(() => {
  resizeInput();
});
</script>

<template>
  <section class="glass-panel chat-shell">
    <header class="run-panel-header">
      <div class="chat-head-main">
        <button class="chat-panel-button" type="button" @click="$emit('toggle-left')">
          <component :is="props.leftVisible ? PanelLeftClose : PanelLeftOpen" :size="15" />
        </button>
        <div class="chat-icon">
          <MessageSquare :size="16" />
        </div>
        <div>
          <h3 class="run-panel-title">{{ t("chat.title") }}</h3>
          <p class="chat-active-text">{{ t("chat.active") }}: {{ selectedWorkflow?.name || t("chat.noneSelected") }}</p>
        </div>
      </div>
      <div class="chat-header-actions">
        <button
          v-if="selectedWorkflowId"
          class="text-button text-xs"
          @click="editCurrentWorkflow"
        >
          {{ t("chat.editWorkflow") }}
        </button>
        <button class="text-button text-xs" @click="$emit('clear')">
          {{ t("chat.clear") }}
        </button>
        <div v-if="props.showRightToggle" class="chat-header-divider"></div>
        <button
          v-if="props.showRightToggle"
          class="chat-panel-button"
          type="button"
          @click="$emit('toggle-right')"
        >
          <component :is="props.rightVisible ? PanelRightClose : PanelRightOpen" :size="15" />
        </button>
      </div>
    </header>

    <CollaborationMessageList
      class="chat-scroll"
      :messages="messages"
      :loading="loading"
      :empty-title="t('chat.startRun')"
      :empty-description="t('chat.startRunDesc')"
      :thinking-text="t('chat.thinking')"
    />

    <footer class="chat-input-wrap">
      <div class="chat-input-shell">
        <textarea
          ref="inputRef"
          v-model="form.user_input"
          rows="1"
          :placeholder="t('chat.inputPlaceholder')"
          @input="handleInput"
          @keydown="handleKeydown"
        />
        <button
          v-if="loading"
          class="stop-mini-button"
          type="button"
          @click="$emit('stop')"
        >
          <Square :size="12" />
        </button>
        <button class="send-mini-button" :disabled="!selectedWorkflowId || loading" @click="submit">
          <Send :size="14" />
        </button>
      </div>
    </footer>
  </section>
</template>
