// DSL v2 协作节点配置组件
// 用于可视化工作流编辑器中配置协作节点

export const collaborationNodes = {
  // 规划节点
  planner: {
    label: '任务规划',
    icon: 'Planning',
    category: 'collaboration',
    color: '#409EFF',
    description: '将用户意图分解为可执行的子任务计划',
    config: [
      {
        name: 'intent',
        label: '任务意图',
        type: 'input',
        placeholder: '输入任务意图描述'
      },
      {
        name: 'taskType',
        label: '任务类型',
        type: 'select',
        options: [
          { value: 'general', label: '通用' },
          { value: 'analysis', label: '分析' },
          { value: 'research', label: '研究' },
          { value: 'generation', label: '生成' },
          { value: 'coding', label: '编码' },
          { value: 'testing', label: '测试' }
        ]
      },
      {
        name: 'maxSubtasks',
        label: '最大子任务数',
        type: 'number',
        default: 5
      }
    ],
    outputs: [
      { id: 'next', label: '下一步' }
    ]
  },

  // 委托节点
  delegate: {
    label: '任务委托',
    icon: 'Share',
    category: 'collaboration',
    color: '#67C23A',
    description: '将任务委托给指定角色的智能体执行',
    config: [
      {
        name: 'role',
        label: '执行角色',
        type: 'select',
        options: [
          { value: 'planner', label: '规划师' },
          { value: 'specialist', label: '专家' },
          { value: 'reviewer', label: '审查员' },
          { value: 'critic', label: '批评家' }
        ]
      },
      {
        name: 'agentId',
        label: 'Agent ID',
        type: 'input',
        placeholder: '指定 Agent（可选）'
      },
      {
        name: 'timeout',
        label: '超时时间(秒)',
        type: 'number',
        default: 60
      }
    ],
    outputs: [
      { id: 'next', label: '完成' },
      { id: 'fallback', label: '回退' }
    ]
  },

  // 并行分支节点
  parallel_fork: {
    label: '并行分支',
    icon: 'Connection',
    category: 'collaboration',
    color: '#E6A23C',
    description: '启动多个并行子任务',
    config: [
      {
        name: 'branches',
        label: '分支配置',
        type: 'branch-list',
        children: [
          { name: 'id', label: '分支ID', type: 'input' },
          { name: 'type', label: '类型', type: 'select', options: [
            { value: 'llm', label: 'LLM调用' },
            { value: 'tool', label: '工具调用' },
            { value: 'subgraph', label: '子图' }
          ]},
          { name: 'config', label: '配置', type: 'json' }
        ]
      },
      {
        name: 'maxParallel',
        label: '最大并行数',
        type: 'number',
        default: 3
      },
      {
        name: 'timeout',
        label: '超时时间(秒)',
        type: 'number',
        default: 60
      }
    ],
    outputs: [
      { id: 'merge', label: '合并' }
    ]
  },

  // 共识节点
  consensus: {
    label: '共识裁决',
    icon: 'Check',
    category: 'collaboration',
    color: '#909399',
    description: '收集多方意见并达成共识',
    config: [
      {
        name: 'strategy',
        label: '共识策略',
        type: 'select',
        options: [
          { value: 'majority', label: '多数投票' },
          { value: 'unanimous', label: '全体一致' },
          { value: 'weighted', label: '加权投票' },
          { value: 'human', label: '人工介入' }
        ]
      },
      {
        name: 'threshold',
        label: '阈值',
        type: 'number',
        default: 0.5,
        step: 0.1,
        min: 0,
        max: 1
      },
      {
        name: 'timeout',
        label: '超时时间(秒)',
        type: 'number',
        default: 30
      }
    ],
    outputs: [
      { id: 'agreed', label: '达成共识' },
      { id: 'disagree', label: '未达成' },
      { id: 'fallback', label: '回退' }
    ]
  },

  // 审查节点
  critic: {
    label: '审查批评',
    icon: 'View',
    category: 'collaboration',
    color: '#F56C6C',
    description: '对执行结果进行审查和批评',
    config: [
      {
        name: 'criteria',
        label: '审查标准',
        type: 'multi-select',
        options: [
          { value: 'accuracy', label: '准确性' },
          { value: 'completeness', label: '完整性' },
          { value: 'consistency', label: '一致性' },
          { value: 'efficiency', label: '效率' }
        ]
      },
      {
        name: 'threshold',
        label: '通过阈值',
        type: 'number',
        default: 0.7,
        step: 0.1
      },
      {
        name: 'autoRetry',
        label: '自动重试',
        type: 'switch',
        default: true
      }
    ],
    outputs: [
      { id: 'approve', label: '通过' },
      { id: 'retry', label: '重试' }
    ]
  },

  // 记忆读取节点
  memory_read: {
    label: '记忆读取',
    icon: 'Reading',
    category: 'collaboration',
    color: '#9C27B0',
    description: '从共享记忆存储中读取数据',
    config: [
      {
        name: 'key',
        label: '记忆键',
        type: 'input',
        placeholder: '输入记忆键名'
      },
      {
        name: 'default',
        label: '默认值',
        type: 'input',
        placeholder: '未找到时的默认值'
      }
    ],
    outputs: [
      { id: 'found', label: '找到' },
      { id: 'not_found', label: '未找到' }
    ]
  },

  // 记忆写入节点
  memory_write: {
    label: '记忆写入',
    icon: 'Edit',
    category: 'collaboration',
    color: '#9C27B0',
    description: '将数据写入共享记忆存储',
    config: [
      {
        name: 'key',
        label: '记忆键',
        type: 'input',
        placeholder: '输入记忆键名'
      },
      {
        name: 'value',
        label: '值',
        type: 'input',
        placeholder: '输入要存储的值'
      },
      {
        name: 'merge',
        label: '合并模式',
        type: 'switch',
        default: false,
        description: '与现有值合并'
      }
    ],
    outputs: [
      { id: 'done', label: '完成' }
    ]
  },

  // 事件发射节点
  event_emit: {
    label: '事件发射',
    icon: 'Promotion',
    category: 'collaboration',
    color: '#00BCD4',
    description: '发送协作事件',
    config: [
      {
        name: 'eventType',
        label: '事件类型',
        type: 'select',
        options: [
          { value: 'task_created', label: '任务创建' },
          { value: 'task_assigned', label: '任务分配' },
          { value: 'task_completed', label: '任务完成' },
          { value: 'consensus_reached', label: '共识达成' },
          { value: 'fallback_triggered', label: '回退触发' },
          { value: 'custom', label: '自定义' }
        ]
      },
      {
        name: 'eventData',
        label: '事件数据',
        type: 'json',
        placeholder: '{"key": "value"}'
      }
    ],
    outputs: [
      { id: 'done', label: '完成' }
    ]
  },

  // 事件监听节点
  event_listen: {
    label: '事件监听',
    icon: 'Bell',
    category: 'collaboration',
    color: '#00BCD4',
    description: '等待特定事件触发',
    config: [
      {
        name: 'eventType',
        label: '监听事件',
        type: 'select',
        options: [
          { value: 'task_created', label: '任务创建' },
          { value: 'task_completed', label: '任务完成' },
          { value: 'consensus_reached', label: '共识达成' },
          { value: 'error_occurred', label: '错误发生' }
        ]
      },
      {
        name: 'timeout',
        label: '超时时间(秒)',
        type: 'number',
        default: 30
      }
    ],
    outputs: [
      { id: 'triggered', label: '触发' },
      { id: 'timeout', label: '超时' }
    ]
  },

  // 重试策略节点
  retry_policy: {
    label: '重试策略',
    icon: 'Refresh',
    category: 'collaboration',
    color: '#FF9800',
    description: '实现协作级重试机制',
    config: [
      {
        name: 'maxRetries',
        label: '最大重试次数',
        type: 'number',
        default: 3
      },
      {
        name: 'strategy',
        label: '重试策略',
        type: 'select',
        options: [
          { value: 'exponential', label: '指数退避' },
          { value: 'linear', label: '线性退避' },
          { value: 'fixed', label: '固定间隔' }
        ]
      },
      {
        name: 'initialDelay',
        label: '初始延迟(秒)',
        type: 'number',
        default: 1
      }
    ],
    outputs: [
      { id: 'retry', label: '重试' },
      { id: 'exhausted', label: '耗尽' }
    ]
  }
}

// 注册所有协作节点
export function registerCollaborationNodes(editor) {
  Object.entries(collaborationNodes).forEach(([type, config]) => {
    editor.registerNode(type, {
      ...config,
      component: CollaborationNodeConfig
    })
  })
}

// 协作节点配置组件
const CollaborationNodeConfig = {
  props: ['node'],
  template: `
    <div class="collaboration-node-config">
      <el-form label-width="100px">
        <el-form-item
          v-for="field in nodeConfig"
          :key="field.name"
          :label="field.label"
        >
          <el-input
            v-if="field.type === 'input'"
            v-model="node.data[field.name]"
            :placeholder="field.placeholder"
          />
          <el-select
            v-else-if="field.type === 'select'"
            v-model="node.data[field.name]"
            style="width: 100%"
          >
            <el-option
              v-for="opt in field.options"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
          <el-switch
            v-else-if="field.type === 'switch'"
            v-model="node.data[field.name]"
          />
          <el-input-number
            v-else-if="field.type === 'number'"
            v-model="node.data[field.name]"
            :min="field.min || 0"
            :max="field.max"
            :step="field.step || 1"
          />
        </el-form-item>
      </el-form>
    </div>
  `,
  computed: {
    nodeConfig() {
      const type = this.node?.type
      return collaborationNodes[type]?.config || []
    }
  }
}

export default collaborationNodes