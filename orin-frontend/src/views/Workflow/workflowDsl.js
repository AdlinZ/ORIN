export const ORIN_WORKFLOW_VERSION = 'orin.workflow.v1'

export const NODE_TYPES = [
  { type: 'start', label: '开始 / 输入', description: '声明工作流入口与输入' },
  { type: 'end', label: '结束', description: '结构化映射最终输出' },
  { type: 'answer', label: '直接回复', description: '聊天型快捷输出' },
  { type: 'llm', label: 'LLM', description: '调用模型生成或推理' },
  { type: 'agent', label: '智能体', description: '调用 ORIN 智能体' },
  { type: 'knowledge_retrieval', label: '知识检索', description: '检索知识库上下文' },
  { type: 'tool', label: '工具', description: '调用技能或外部工具' },
  { type: 'http_request', label: 'HTTP 请求', description: '访问外部 HTTP API' },
  { type: 'if_else', label: '条件分支', description: '按条件选择路径' },
  { type: 'variable_assigner', label: '变量赋值', description: '写入上下文变量' },
  { type: 'code', label: '代码', description: '运行代码片段' }
]

export const NODE_TYPE_LABELS = NODE_TYPES.reduce((acc, item) => {
  acc[item.type] = item.label
  return acc
}, {})

export function createDefaultWorkflowDsl() {
  return normalizeWorkflowDsl({
    version: ORIN_WORKFLOW_VERSION,
    kind: 'workflow',
    metadata: {
      source: 'ORIN',
      compatibility: {}
    },
    graph: {
      nodes: [
        {
          id: 'start-1',
          type: 'start',
          title: '开始 / 输入',
          position: { x: 120, y: 160 },
          data: { label: '开始 / 输入' }
        },
        {
          id: 'end-1',
          type: 'end',
          title: '结束',
          position: { x: 380, y: 160 },
          data: { label: '结束', outputs: [{ name: 'answer', value: '{{ inputs.query }}' }] }
        }
      ],
      edges: [
        createEdge('start-1', 'end-1')
      ]
    },
    inputs: [],
    outputs: [],
    variables: []
  })
}

export function createNode(type, x = 160, y = 160) {
  const id = `${type}-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 6)}`
  return {
    id,
    type,
    title: NODE_TYPE_LABELS[type] || type,
    position: { x, y },
    data: {
      label: NODE_TYPE_LABELS[type] || type
    }
  }
}

export function createEdge(source, target) {
  return {
    id: `edge-${source}-${target}-${Date.now().toString(36)}`,
    source,
    target
  }
}

export function normalizeWorkflowDsl(definition = {}) {
  const graph = definition.graph || {
    nodes: definition.nodes || [],
    edges: definition.edges || []
  }
  const nodes = Array.isArray(graph.nodes) ? graph.nodes.map((node, index) => ({
    id: String(node.id || `${node.type || 'node'}-${index + 1}`),
    type: normalizeNodeType(node.type),
    title: node.title || node.data?.title || node.data?.label || NODE_TYPE_LABELS[normalizeNodeType(node.type)] || normalizeNodeType(node.type),
    position: node.position || { x: 120 + index * 220, y: 160 },
    data: {
      ...(node.data || {}),
      label: node.data?.label || node.title || NODE_TYPE_LABELS[normalizeNodeType(node.type)] || normalizeNodeType(node.type)
    }
  })) : []

  const edges = Array.isArray(graph.edges) ? graph.edges
    .filter(edge => edge?.source && edge?.target)
    .map((edge, index) => ({
      id: String(edge.id || `edge-${index + 1}`),
      source: String(edge.source),
      target: String(edge.target),
      ...(edge.sourceHandle ? { sourceHandle: edge.sourceHandle } : {}),
      ...(edge.targetHandle ? { targetHandle: edge.targetHandle } : {}),
      ...(edge.condition ? { condition: edge.condition } : {})
    })) : []

  const normalized = {
    version: ORIN_WORKFLOW_VERSION,
    kind: 'workflow',
    metadata: {
      ...(definition.metadata || {}),
      source: definition.metadata?.source || 'ORIN'
    },
    graph: { nodes, edges },
    inputs: Array.isArray(definition.inputs) ? definition.inputs : [],
    outputs: Array.isArray(definition.outputs) ? definition.outputs : [],
    variables: Array.isArray(definition.variables) ? definition.variables : []
  }
  normalized.metadata.compatibility = buildCompatibilityReport(normalized)
  return normalized
}

export function buildCompatibilityReport(definition) {
  const nodes = definition?.graph?.nodes || []
  const supported = new Set(NODE_TYPES.map(item => item.type))
  const unsupportedNodes = nodes
    .filter(node => !supported.has(node.type))
    .map(node => ({ id: node.id, type: node.type, title: node.title || node.type }))
  return {
    format: definition?.metadata?.source === 'DIFY' ? 'DIFY' : 'ORIN',
    level: unsupportedNodes.length === 0 ? 'FULL' : unsupportedNodes.length === nodes.length ? 'BLOCKED' : 'PARTIAL',
    publishability: unsupportedNodes.length === 0 ? 'PUBLISHABLE' : 'BLOCKED',
    unsupportedTypes: [...new Set(unsupportedNodes.map(node => node.type))],
    unsupportedNodes,
    warnings: definition?.metadata?.compatibility?.warnings || []
  }
}

export function validateWorkflowDsl(definition) {
  const errors = []
  const nodes = definition?.graph?.nodes || []
  const edges = definition?.graph?.edges || []
  const nodeIds = new Set(nodes.map(node => node.id))
  if (definition?.version !== ORIN_WORKFLOW_VERSION) errors.push('必须使用 ORIN Workflow DSL v1')
  if (!nodes.some(node => node.type === 'start')) errors.push('缺少 start 节点')
  const hasTerminal = nodes.some(node => ['end', 'answer'].includes(node.type))
  if (!hasTerminal) errors.push('缺少 end 或 answer 节点')
  if (!nodes.some(node => !['start', 'end', 'answer'].includes(node.type))) errors.push('至少需要一个业务节点')
  edges.forEach(edge => {
    if (!nodeIds.has(edge.source) || !nodeIds.has(edge.target)) {
      errors.push(`连线 ${edge.id} 指向不存在的节点`)
    }
    const sourceNode = nodes.find(node => node.id === edge.source)
    const targetNode = nodes.find(node => node.id === edge.target)
    if (['end', 'answer'].includes(sourceNode?.type)) {
      errors.push(`${sourceNode.title || sourceNode.id}: 终止节点不能连接后继节点`)
    }
    if (targetNode?.type === 'start') {
      errors.push(`${targetNode.title || targetNode.id}: 开始 / 输入节点不能接入前置节点`)
    }
  })
  const compatibility = definition?.metadata?.compatibility || buildCompatibilityReport(definition)
  if (compatibility.unsupportedNodes?.length) errors.push('存在不支持的兼容节点')
  nodes.filter(node => node.type === 'end').forEach(node => {
    if (!Array.isArray(node.data?.outputs) || node.data.outputs.length === 0) {
      errors.push(`${node.title || node.id}: 至少配置一个输出映射`)
    }
  })
  nodes.filter(node => node.type === 'answer').forEach(node => {
    if (!node.data?.answer && !node.data?.text && !node.data?.value && !node.data?.sourceExpression) {
      errors.push(`${node.title || node.id}: 请配置回复内容来源`)
    }
  })
  return errors
}

export function compatibilityLabel(report = {}) {
  if (report.publishability === 'BLOCKED' || report.level === 'BLOCKED') return '不可发布'
  if (report.level === 'PARTIAL') return '部分兼容'
  return '完全兼容'
}

export function normalizeNodeType(type = 'unknown') {
  const normalized = String(type || 'unknown').replaceAll('-', '_').toLowerCase()
  if (normalized === 'input') return 'start'
  if (normalized === 'skill') return 'tool'
  return normalized
}
