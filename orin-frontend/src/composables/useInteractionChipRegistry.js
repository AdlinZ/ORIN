import { QUICK_CHIP_ACTIONS, createQuickChip } from './useInteractionQuickChips';

const openTabAction = (tab) => ({ type: QUICK_CHIP_ACTIONS.OPEN_TAB, tab });
const openInspectorAction = { type: QUICK_CHIP_ACTIONS.OPEN_INSPECTOR };

export const buildWorkspaceChipSets = (context = {}) => {
  const interactionLabel = context.interactionLabel || '智能助手';
  const normalizedInteraction = String(interactionLabel || '').trim().toUpperCase();
  const isChatMode = normalizedInteraction === 'CHAT';
  const params = context.runtimeParams || {};
  const attachedKbCount = context.attachedKbCount ?? 0;
  const retrievedContextEnabled = Boolean(context.retrievedContextEnabled);
  const filteredDocsCount = context.filteredDocsCount ?? 0;
  const toFixed = (value, digits = 1) => {
    const n = Number(value);
    return Number.isFinite(n) ? n.toFixed(digits).replace(/\.0+$/, '') : value;
  };
  const modelParamChips = [];
  if (normalizedInteraction === 'TEXT_TO_IMAGE' || normalizedInteraction === 'IMAGE_TO_IMAGE') {
    modelParamChips.push(
      createQuickChip({ key: 'param-image-size', label: `尺寸：${params.imageSize || '默认'}`, action: openTabAction('model') }),
      createQuickChip({ key: 'param-image-steps', label: `步数：${params.inferenceSteps ?? 20}`, action: openTabAction('model') })
    );
  } else if (normalizedInteraction === 'TEXT_TO_VIDEO') {
    modelParamChips.push(
      createQuickChip({ key: 'param-video-size', label: `比例：${params.videoSize || '16:9'}`, action: openTabAction('model') }),
      createQuickChip({ key: 'param-video-duration', label: `时长：${params.videoDuration || '5'}s`, action: openTabAction('model') })
    );
  } else if (normalizedInteraction === 'TEXT_TO_SPEECH') {
    modelParamChips.push(
      createQuickChip({ key: 'param-voice', label: `音色：${params.voice || '默认'}`, action: openTabAction('model') }),
      createQuickChip({ key: 'param-speed', label: `语速：${toFixed(params.speed ?? 1.0)}x`, action: openTabAction('model') })
    );
  } else if (isChatMode) {
    modelParamChips.push(
      createQuickChip({ key: 'param-temp', label: `温度：${toFixed(params.temperature ?? 0.7)}`, action: openTabAction('model') }),
      createQuickChip({ key: 'param-max', label: `MaxTokens：${params.maxTokens ?? 2000}`, action: openTabAction('model') })
    );
  }

  const top = [
    createQuickChip({
      key: 'top-mode',
      label: `模式：${interactionLabel}`,
      action: openTabAction('model')
    })
  ];
  if (isChatMode) {
    top.push(
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
    );
  }
  top.push(...modelParamChips);

  const composer = [
    createQuickChip({
      key: 'composer-mode',
      label: `模式：${interactionLabel}`,
      action: openTabAction('model')
    })
  ];
  if (isChatMode) {
    composer.push(
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
    );
  }
  const input = [
    createQuickChip({
      key: 'input-mode',
      label: `模式：${interactionLabel}`,
      action: openTabAction('model')
    })
  ];
  if (isChatMode) {
    input.push(
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
    );
  }
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
