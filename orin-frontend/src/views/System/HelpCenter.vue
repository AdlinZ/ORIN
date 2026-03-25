<template>
  <div class="help-center-container">
    <PageHeader
      title="帮助中心"
      description="获取系统使用文档、常见问题解答和操作指南"
      icon="QuestionFilled"
    />

    <el-row :gutter="20">
      <!-- 左侧导航 -->
      <el-col :span="6">
        <el-card class="nav-card">
          <el-menu :default-active="activeMenu" @select="handleMenuSelect">
            <el-menu-item index="guide">
              <el-icon><Guide /></el-icon>
              <span>使用指南</span>
            </el-menu-item>
            <el-menu-item index="faq">
              <el-icon><QuestionFilled /></el-icon>
              <span>常见问题</span>
            </el-menu-item>
            <el-menu-item index="api">
              <el-icon><Document /></el-icon>
              <span>API 文档</span>
            </el-menu-item>
            <el-menu-item index="feedback">
              <el-icon><ChatDotRound /></el-icon>
              <span>反馈建议</span>
            </el-menu-item>
          </el-menu>
        </el-card>
      </el-col>

      <!-- 右侧内容 -->
      <el-col :span="18">
        <!-- 使用指南 -->
        <div v-if="activeMenu === 'guide'">
          <el-card>
            <template #header>
              <span>系统使用指南</span>
            </template>

            <el-timeline>
              <el-timeline-item timestamp="快速开始" placement="top">
                <h4>1. 快速开始</h4>
                <ul>
                  <li>完成系统登录后，进入首页仪表盘</li>
                  <li>点击「智能体管理」创建您的第一个智能体</li>
                  <li>配置智能体的模型、能力、技能</li>
                  <li>开始与智能体对话</li>
                </ul>
              </el-timeline-item>

              <el-timeline-item timestamp="智能体管理" placement="top">
                <h4>2. 智能体管理</h4>
                <ul>
                  <li>支持创建、编辑、删除智能体</li>
                  <li>可以为智能体绑定技能、MCP 工具</li>
                  <li>支持配置智能体的温度、上下文长度等参数</li>
                  <li>查看智能体的使用统计和对话历史</li>
                </ul>
              </el-timeline-item>

              <el-timeline-item timestamp="知识库" placement="top">
                <h4>3. 知识库使用</h4>
                <ul>
                  <li>创建知识库并上传文档</li>
                  <li>系统自动进行文档解析和向量化</li>
                  <li>智能体可引用知识库内容进行回答</li>
                  <li>支持 RAG 检索效果测试</li>
                </ul>
              </el-timeline-item>

              <el-timeline-item timestamp="工作流编排" placement="top">
                <h4>4. 工作流编排</h4>
                <ul>
                  <li>使用可视化编辑器创建工作流</li>
                  <li>拖拽节点设计智能体协作流程</li>
                  <li>支持条件分支、循环等复杂逻辑</li>
                  <li>发布工作流并在智能体中调用</li>
                </ul>
              </el-timeline-item>

              <el-timeline-item timestamp="监控运维" placement="top">
                <h4>5. 监控运维</h4>
                <ul>
                  <li>查看 Token 消耗和成本统计</li>
                  <li>分析智能体响应时延</li>
                  <li>配置告警规则和通知渠道</li>
                  <li>追踪调用链路和调试问题</li>
                </ul>
              </el-timeline-item>
            </el-timeline>
          </el-card>
        </div>

        <!-- 常见问题 -->
        <div v-if="activeMenu === 'faq'">
          <el-card>
            <template #header>
              <span>常见问题</span>
            </template>

            <el-collapse v-model="activeFaq">
              <el-collapse-item title="如何创建智能体?" name="faq1">
                <div>点击「智能体管理」→「新建智能体」，填写名称、描述，选择模型后保存即可。您还可以为智能体添加技能和知识库。</div>
              </el-collapse-item>

              <el-collapse-item title="如何上传知识库文档?" name="faq2">
                <div>进入「知识库管理」，选择或创建知识库，点击「上传文档」按钮，支持 PDF、Word、txt 等格式。系统会自动解析文档内容。</div>
              </el-collapse-item>

              <el-collapse-item title="智能体回复很慢怎么办?" name="faq3">
                <div>1. 检查网络连接是否正常；2. 更换响应更快的模型；3. 在「监控运维」→「时延统计」中分析耗时节点；4. 减少上下文长度。</div>
              </el-collapse-item>

              <el-collapse-item title="如何配置告警通知?" name="faq4">
                <div>进入「监控运维」→「通知渠道」，可以配置邮件、钉钉、企业微信等通知方式，然后在「告警规则」中创建触发条件。</div>
              </el-collapse-item>

              <el-collapse-item title="API Key 如何获取?" name="faq5">
                <div>在「系统管理」→「API Key」页面，点击「创建 API Key」，设置名称和权限后即可获取。记得妥善保存，密钥只会显示一次。</div>
              </el-collapse-item>

              <el-collapse-item title="如何进行端侧同步?" name="faq6">
                <div>进入「知识中心」→「端侧同步」，配置同步策略和目标设备。客户端安装对应 SDK 后即可接收知识库更新。</div>
              </el-collapse-item>
            </el-collapse>
          </el-card>
        </div>

        <!-- API 文档 -->
        <div v-if="activeMenu === 'api'">
          <el-card>
            <template #header>
              <span>API 接口文档</span>
            </template>

            <el-alert type="info" :closable="false" show-icon>
              <template #title>
                <span>API 文档迁移中</span>
              </template>
              <template #default>
                完整 API 文档请访问 <el-link type="primary">/api/docs</el-link> 或使用 Swagger UI
              </template>
            </el-alert>

            <el-divider>基础接口</el-divider>

            <h4>智能体对话</h4>
            <el-table :data="apiList" size="small">
              <el-table-column prop="method" label="方法" width="80">
                <template #default="{ row }">
                  <el-tag :type="row.method === 'GET' ? 'success' : row.method === 'POST' ? 'primary' : 'warning'" size="small">
                    {{ row.method }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="path" label="路径" min-width="200" />
              <el-table-column prop="desc" label="描述" />
            </el-table>
          </el-card>
        </div>

        <!-- 反馈建议 -->
        <div v-if="activeMenu === 'feedback'">
          <el-card>
            <template #header>
              <span>反馈与建议</span>
            </template>

            <el-form :model="feedbackForm" label-width="80px">
              <el-form-item label="反馈类型">
                <el-radio-group v-model="feedbackForm.type">
                  <el-radio value="bug">Bug 报告</el-radio>
                  <el-radio value="feature">功能建议</el-radio>
                  <el-radio value="other">其他</el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="标题">
                <el-input v-model="feedbackForm.title" placeholder="请简要描述问题" />
              </el-form-item>
              <el-form-item label="详细内容">
                <el-input v-model="feedbackForm.content" type="textarea" :rows="6" placeholder="请详细描述您遇到的问题或建议" />
              </el-form-item>
              <el-form-item label="联系方式">
                <el-input v-model="feedbackForm.contact" placeholder="邮箱或微信（选填）" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="submitting" @click="submitFeedback">
                  提交反馈
                </el-button>
              </el-form-item>
            </el-form>
          </el-card>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import request from '@/utils/request'

const activeMenu = ref('guide')
const activeFaq = ref(['faq1'])

const feedbackForm = reactive({
  type: 'bug',
  title: '',
  content: '',
  contact: ''
})

const submitting = ref(false)

const apiList = ref([
  { method: 'POST', path: '/api/v1/agents/:id/chat', desc: '智能体对话' },
  { method: 'GET', path: '/api/v1/agents', desc: '获取智能体列表' },
  { method: 'POST', path: '/api/v1/knowledge', desc: '创建知识库' },
  { method: 'POST', path: '/api/v1/knowledge/:id/documents', desc: '上传文档' },
  { method: 'GET', path: '/api/v1/monitor/stats', desc: '获取监控统计' }
])

const handleMenuSelect = (index) => {
  activeMenu.value = index
}

const submitFeedback = async () => {
  if (!feedbackForm.title || !feedbackForm.content) {
    ElMessage.warning('请填写标题和内容')
    return
  }

  submitting.value = true
  try {
    await request.post('/system/feedback', feedbackForm)
    ElMessage.success('反馈已提交，感谢您的建议')
    feedbackForm.title = ''
    feedbackForm.content = ''
    feedbackForm.contact = ''
  } catch (e) {
    ElMessage.error('提交失败: ' + (e.message || e))
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.help-center-container {
  padding: 20px;
}

.nav-card {
  position: sticky;
  top: 20px;
}

h4 {
  margin: 0 0 8px;
  font-size: 14px;
  color: #303133;
}

ul {
  margin: 0;
  padding-left: 20px;
  color: #606266;
  font-size: 13px;
}

li {
  margin-bottom: 4px;
}
</style>
