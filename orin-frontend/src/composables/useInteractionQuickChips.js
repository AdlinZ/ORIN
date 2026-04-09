export const QUICK_CHIP_ACTIONS = Object.freeze({
  OPEN_INSPECTOR: 'open_inspector',
  TOGGLE_INSPECTOR: 'toggle_inspector',
  OPEN_TAB: 'open_tab',
  CUSTOM: 'custom'
});

export const createQuickChip = ({
  key,
  label,
  action,
  disabled = false
}) => ({
  key,
  label,
  action,
  disabled
});

export const runQuickChipAction = (chip, handlers = {}) => {
  const action = chip?.action;
  if (!action || typeof action !== 'object') return;

  switch (action.type) {
    case QUICK_CHIP_ACTIONS.OPEN_INSPECTOR:
      handlers.openInspector?.(action);
      break;
    case QUICK_CHIP_ACTIONS.TOGGLE_INSPECTOR:
      handlers.toggleInspector?.(action);
      break;
    case QUICK_CHIP_ACTIONS.OPEN_TAB:
      handlers.openTab?.(action.tab, action);
      break;
    case QUICK_CHIP_ACTIONS.CUSTOM:
      handlers.custom?.(action);
      break;
    default:
      break;
  }
};

