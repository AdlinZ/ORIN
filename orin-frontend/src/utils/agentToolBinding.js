const normalizeId = (value) => (value == null ? '' : String(value).trim())

/**
 * Build an agent binding update that changes one KB membership while preserving
 * every other agent-level binding field returned by the backend.
 */
export function buildAgentKbBindingPayload(binding = {}, kbId, attached) {
  const targetKbId = normalizeId(kbId)
  const existingKbIds = Array.isArray(binding.kbIds)
    ? binding.kbIds.map(normalizeId).filter(Boolean)
    : []
  const nextKbIds = attached
    ? [...new Set([...existingKbIds, targetKbId].filter(Boolean))]
    : existingKbIds.filter(id => id !== targetKbId)

  const payload = {
    toolIds: Array.isArray(binding.toolIds) ? binding.toolIds : [],
    kbIds: nextKbIds,
    skillIds: Array.isArray(binding.skillIds) ? binding.skillIds : [],
    mcpIds: Array.isArray(binding.mcpIds) ? binding.mcpIds : [],
  }

  for (const field of ['enableSuggestions', 'showRetrievedContext', 'autoRenameSession']) {
    if (typeof binding[field] === 'boolean') payload[field] = binding[field]
  }

  return payload
}
