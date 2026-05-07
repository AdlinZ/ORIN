export const DEFAULT_SUPPORTED_WORKFLOW_NODE_TYPES = [
  'start',
  'end',
  'llm',
  'agent',
  'code',
  'if_else',
  'knowledge_retrieval',
  'iteration',
  'loop',
  'variable_assigner',
  'skill'
]

export function normalizeWorkflowNodeType(type) {
  return String(type || '').replace(/-/g, '_').toLowerCase()
}

export function isSupportedWorkflowNodeType(type, supportedTypes = DEFAULT_SUPPORTED_WORKFLOW_NODE_TYPES) {
  const supported = new Set(supportedTypes.map(normalizeWorkflowNodeType))
  return supported.has(normalizeWorkflowNodeType(type))
}

export function findUnsupportedWorkflowNodes(nodes, supportedTypes = DEFAULT_SUPPORTED_WORKFLOW_NODE_TYPES) {
  return (Array.isArray(nodes) ? nodes : [])
    .filter((node) => !node.source)
    .filter((node) => !isSupportedWorkflowNodeType(node.type, supportedTypes))
    .map((node) => ({
      id: node.id,
      type: node.type,
      label: node.data?.label || node.data?.title || node.type
    }))
}
