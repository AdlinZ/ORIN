import { QUICK_CHIP_ACTIONS, createQuickChip } from './useInteractionQuickChips';

const openTabAction = (tab) => ({ type: QUICK_CHIP_ACTIONS.OPEN_TAB, tab });
const openInspectorAction = { type: QUICK_CHIP_ACTIONS.OPEN_INSPECTOR };

export const buildWorkspaceChipSets = (context = {}) => {
  const interactionLabel = context.interactionLabel || '智能助手';
  const attachedKbCount = context.attachedKbCount ?? 0;
  const retrievedContextEnabled = Boolean(context.retrievedContextEnabled);
  const filteredDocsCount = context.filteredDocsCount ?? 0;

  const top = [
    createQuickChip({
      key: 'top-mode',
      label: `模式：${interactionLabel}`,
      action: openTabAction('model')
    }),
    createQuickChip({
      key: 'top-kb',
      label: `知识库：${attachedKbCount}`,
      action: openTabAction('tools')
    }),
    createQuickChip({
      key: 'top-retrieved',
      label: `检索：${retrievedContextEnabled ? '开' : '关'}`,
      action: openTabAction('other')
    })
  ];

  const composer = [
    createQuickChip({
      key: 'composer-mode',
      label: `模式：${interactionLabel}`,
      action: openTabAction('model')
    }),
    createQuickChip({
      key: 'composer-kb',
      label: `知识库：${attachedKbCount}`,
      action: openTabAction('tools')
    }),
    createQuickChip({
      key: 'composer-retrieved',
      label: `检索上下文：${retrievedContextEnabled ? '开' : '关'}`,
      action: openTabAction('other')
    })
  ];

  const input = [
    createQuickChip({
      key: 'input-mode',
      label: `模式：${interactionLabel}`,
      action: openTabAction('model')
    }),
    createQuickChip({
      key: 'input-kb',
      label: `知识库：${attachedKbCount}`,
      action: openTabAction('tools')
    }),
    createQuickChip({
      key: 'input-filtered-docs',
      label: `文档过滤：${filteredDocsCount}`,
      action: openTabAction('other')
    })
  ];

  return { top, composer, input };
};

const buildConsoleModeMetaChip = (mode, context = {}) => {
  const params = context.parameters || {};
  const templateCount = context.promptTemplatesCount ?? 0;

  if (mode === 'image') {
    return createQuickChip({
      key: 'meta-size',
      label: `尺寸：${params.imageSize || '默认'}`,
      action: openInspectorAction
    });
  }
  if (mode === 'tts') {
    return createQuickChip({
      key: 'meta-voice',
      label: `音色：${params.voice || '默认'}`,
      action: openInspectorAction
    });
  }
  if (mode === 'stt') {
    return createQuickChip({
      key: 'meta-stt',
      label: '转写：语音输入',
      action: openInspectorAction
    });
  }
  if (mode === 'video') {
    return createQuickChip({
      key: 'meta-duration',
      label: `时长：${params.videoDuration || '5'}s`,
      action: openInspectorAction
    });
  }
  if (mode === 'workflow') {
    return createQuickChip({
      key: 'meta-workflow',
      label: '工作流：已绑定',
      action: openInspectorAction
    });
  }

  return createQuickChip({
    key: 'meta-templates',
    label: `模板：${templateCount}`,
    action: openInspectorAction
  });
};

export const buildConsoleTopChips = (mode, context = {}) => {
  const normalizedMode = String(mode || 'chat').toLowerCase();
  const modeLabel = context.modeLabel || '对话';
  const runtimeStatus = context.runtimeStatus || '已就绪';

  return [
    createQuickChip({
      key: 'mode',
      label: `模式：${modeLabel}`,
      action: openInspectorAction
    }),
    createQuickChip({
      key: 'status',
      label: `状态：${runtimeStatus}`,
      action: openInspectorAction
    }),
    buildConsoleModeMetaChip(normalizedMode, context)
  ];
};

