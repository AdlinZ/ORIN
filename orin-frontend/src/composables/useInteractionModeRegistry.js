const MODE_LABEL_MAP = Object.freeze({
  chat: '对话模式',
  completion: '文本补全',
  workflow: '工作流',
  image: '图像生成',
  tts: '语音合成',
  stt: '语音转写',
  video: '视频生成'
});

const TAB_MODE_MAP = Object.freeze({
  image: 'image',
  stt: 'stt',
  audio: 'stt',
  tts: 'tts',
  video: 'video',
  workflow: 'workflow',
  completion: 'completion',
  chat: 'chat'
});

const VIEW_TYPE_MODE_MAP = Object.freeze({
  TEXT_TO_IMAGE: 'image',
  IMAGE_TO_IMAGE: 'image',
  TTI: 'image',
  SPEECH_TO_TEXT: 'stt',
  STT: 'stt',
  TEXT_TO_SPEECH: 'tts',
  TTS: 'tts',
  TEXT_TO_VIDEO: 'video',
  VIDEO: 'video',
  TTV: 'video',
  WORKFLOW: 'workflow',
  CHAT: 'chat',
  COMPLETION: 'completion'
});

const normalizeMode = (mode, fallback = 'chat') => {
  const normalized = String(mode || '').toLowerCase();
  return MODE_LABEL_MAP[normalized] ? normalized : fallback;
};

export const resolveModeFromTab = (tab) => {
  if (!tab) return '';
  const normalized = String(tab).toLowerCase();
  return TAB_MODE_MAP[normalized] || '';
};

export const resolveModeFromViewType = (viewType) => {
  const normalized = String(viewType || '').toUpperCase();
  return VIEW_TYPE_MODE_MAP[normalized] || '';
};

export const resolveModeFromMeta = ({ tab, metaMode, viewType, fallback = 'chat' } = {}) => {
  const fromTab = resolveModeFromTab(tab);
  const fromMetaMode = normalizeMode(metaMode || '', '');
  const fromViewType = resolveModeFromViewType(viewType);
  return fromTab || fromMetaMode || fromViewType || fallback;
};

export const getModeLabel = (mode) => {
  const normalized = normalizeMode(mode, 'chat');
  return MODE_LABEL_MAP[normalized];
};

export const isMode = (mode, target) => normalizeMode(mode, '') === normalizeMode(target, '');

export const isViewTypeInMode = (viewType, targetMode) => {
  const modeFromViewType = resolveModeFromViewType(viewType);
  return isMode(modeFromViewType, targetMode);
};

