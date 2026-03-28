<template>
  <div class="help-center-container">
    <PageHeader
      title="帮助中心"
      description="获取系统使用文档、常见问题解答和操作指南"
      icon="QuestionFilled"
    />

    <el-row :gutter="20">
      <!-- 左侧导航：分类 -->
      <el-col :span="6">
        <el-card class="nav-card">
          <div class="search-box">
            <el-input
              v-model="searchKeyword"
              placeholder="搜索文章..."
              prefix-icon="Search"
              clearable
              @keyup.enter="handleSearch"
            />
          </div>
          <el-menu :default-active="activeCategory" @select="handleCategorySelect">
            <el-menu-item index="">
              <el-icon><List /></el-icon>
              <span>全部文章</span>
            </el-menu-item>
            <el-menu-item v-for="cat in categories" :key="cat" :index="cat">
              <el-icon><Folder /></el-icon>
              <span>{{ cat }}</span>
            </el-menu-item>
          </el-menu>
        </el-card>
      </el-col>

      <!-- 右侧内容 -->
      <el-col :span="18">
        <!-- 文章列表 -->
        <div v-if="!selectedArticle">
          <el-card>
            <template #header>
              <div class="card-header">
                <span>{{ activeCategory ? activeCategory : '全部文章' }}</span>
                <el-tag v-if="totalCount > 0" type="info" size="small">
                  {{ totalCount }} 篇
                </el-tag>
              </div>
            </template>

            <div v-loading="loading">
              <el-table v-if="articles.length > 0" :data="articles">
                <el-table-column label="标题" min-width="200">
                  <template #default="{ row }">
                    <el-link type="primary" @click="viewArticle(row)">
                      {{ row.title }}
                    </el-link>
                  </template>
                </el-table-column>
                <el-table-column prop="category" label="分类" width="120" />
                <el-table-column prop="viewCount" label="阅读" width="80" />
                <el-table-column label="更新时间" width="180">
                  <template #default="{ row }">
                    {{ formatDate(row.updatedAt) }}
                  </template>
                </el-table-column>
              </el-table>

              <el-empty v-else description="暂无文章" />

              <div v-if="totalCount > pageSize" class="pagination">
                <el-pagination
                  v-model:current-page="currentPage"
                  :page-size="pageSize"
                  :total="totalCount"
                  layout="prev, pager, next"
                  @current-change="fetchArticles"
                />
              </div>
            </div>
          </el-card>
        </div>

        <!-- 文章详情 -->
        <div v-else>
          <el-card>
            <template #header>
              <div class="article-header">
                <el-button text @click="selectedArticle = null">
                  <el-icon><ArrowLeft /></el-icon>
                  返回列表
                </el-button>
              </div>
            </template>

            <div class="article-content">
              <h1>{{ selectedArticle.title }}</h1>
              <div class="article-meta">
                <el-tag size="small">
                  {{ selectedArticle.category }}
                </el-tag>
                <span class="meta-item">
                  <el-icon><View /></el-icon>
                  {{ selectedArticle.viewCount }} 次阅读
                </span>
                <span class="meta-item">
                  更新时间：{{ formatDate(selectedArticle.updatedAt) }}
                </span>
              </div>

              <el-divider />

              <div class="markdown-body" v-html="renderContent(selectedArticle.content)" />
            </div>
          </el-card>
        </div>

        <!-- 静态降级内容：搜索无结果时显示 -->
        <el-card v-if="showFallback && !selectedArticle" class="fallback-card">
          <template #header>
            <span>推荐阅读</span>
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
              </ul>
            </el-timeline-item>
          </el-timeline>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { List, Folder, ArrowLeft, View } from '@element-plus/icons-vue'
import { marked } from 'marked'
import PageHeader from '@/components/PageHeader.vue'
import {
  getHelpArticles,
  getHelpArticle,
  searchHelpArticles,
  getHelpCategories
} from '@/api/help'
import dayjs from 'dayjs'

const loading = ref(false)
const articles = ref([])
const categories = ref([])
const selectedArticle = ref(null)
const activeCategory = ref('')
const searchKeyword = ref('')
const showFallback = ref(false)

// 分页
const currentPage = ref(1)
const pageSize = ref(20)
const totalCount = ref(0)

onMounted(async () => {
  await fetchCategories()
  await fetchArticles()
})

const fetchCategories = async () => {
  try {
    const res = await getHelpCategories()
    categories.value = res.data || res || []
  } catch (e) {
    console.error('获取分类失败:', e)
    categories.value = ['快速开始', '智能体管理', '知识库', '工作流', 'API接口']
  }
}

const fetchArticles = async () => {
  loading.value = true
  showFallback.value = false
  try {
    const params = {
      page: currentPage.value - 1,
      size: pageSize.value,
      category: activeCategory.value || undefined
    }
    const res = await getHelpArticles(params)
    const data = res.data || res
    articles.value = data.content || []
    totalCount.value = data.totalElements || data.total || articles.value.length
  } catch (e) {
    console.error('获取文章列表失败:', e)
    articles.value = []
    showFallback.value = true
  } finally {
    loading.value = false
  }
}

const handleCategorySelect = (index) => {
  activeCategory.value = index
  currentPage.value = 1
  selectedArticle.value = null
  fetchArticles()
}

const handleSearch = async () => {
  if (!searchKeyword.value.trim()) {
    activeCategory.value = ''
    fetchArticles()
    return
  }

  loading.value = true
  try {
    const res = await searchHelpArticles(searchKeyword.value, 0, 20)
    const data = res.data || res
    articles.value = data.content || data || []
    totalCount.value = articles.value.length
    showFallback.value = articles.value.length === 0
  } catch (e) {
    console.error('搜索失败:', e)
    showFallback.value = true
  } finally {
    loading.value = false
  }
}

const viewArticle = async (article) => {
  try {
    const res = await getHelpArticle(article.id)
    selectedArticle.value = res.data || res
  } catch (e) {
    console.error('获取文章详情失败:', e)
    ElMessage.error('获取文章详情失败')
  }
}

const formatDate = (date) => {
  if (!date) return '-'
  if (Array.isArray(date)) {
    return dayjs(new Date(date[0], date[1] - 1, date[2], date[3] || 0, date[4] || 0)).format('YYYY-MM-DD HH:mm')
  }
  return dayjs(date).format('YYYY-MM-DD HH:mm')
}

const renderContent = (content) => {
  if (!content) return ''
  try {
    return marked(content)
  } catch (e) {
    return content
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

.search-box {
  margin-bottom: 12px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.article-header {
  display: flex;
  align-items: center;
}

.article-content h1 {
  margin: 0 0 16px;
  font-size: 24px;
}

.article-meta {
  display: flex;
  align-items: center;
  gap: 16px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.markdown-body {
  line-height: 1.8;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3) {
  margin-top: 24px;
  margin-bottom: 12px;
}

.markdown-body :deep(p) {
  margin-bottom: 12px;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 24px;
  margin-bottom: 12px;
}

.markdown-body :deep(code) {
  background: var(--el-fill-color-light);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
}

.markdown-body :deep(pre) {
  background: var(--el-fill-color-light);
  padding: 12px;
  border-radius: 6px;
  overflow-x: auto;
}

.fallback-card {
  margin-top: 20px;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: center;
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
