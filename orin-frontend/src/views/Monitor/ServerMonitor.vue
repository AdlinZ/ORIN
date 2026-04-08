<template>
  <div class="agent-workspace server-workspace" :class="{ 'is-collapsed': sessionPaneCollapsed }">
    <div v-if="!sessionPaneCollapsed && isMobile" class="d-overlay" @click="sessionPaneCollapsed = true"></div>

    <aside class="workspace-sidebar" :class="{ 'is-collapsed': sessionPaneCollapsed }">
      <div class="workspace-session-pane">
        <div class="session-collapse-handle">
          <el-button
            class="collapse-btn"
            circle
            :icon="sessionPaneCollapsed ? ArrowRight : ArrowLeft"
            @click="sessionPaneCollapsed = !sessionPaneCollapsed"
          />
        </div>

        <div v-if="sessionPaneCollapsed" class="collapsed-pane">
          <el-button class="collapsed-new-btn" circle :icon="Monitor" @click="sessionPaneCollapsed = false" />
        </div>

        <template v-else>
          <div class="sidebar-top">
            <div class="sidebar-profile">
              <div class="sidebar-avatar">
                <el-icon><Monitor /></el-icon>
              </div>
              <div class="sidebar-name">服务器节点</div>
            </div>
            <div class="sidebar-actions">
              <el-button link :icon="Refresh" class="refresh-btn" @click="fetchNodesAndSnapshots" :loading="nodesLoading" />
              <el-button link :icon="Plus" class="add-btn" @click="openNodeDialog()" />
            </div>
          </div>

          <div class="session-search">
            <el-input v-model="searchQuery" placeholder="搜索节点..." :prefix-icon="Search" clearable />
          </div>

          <div class="session-list">
            <div
              v-for="node in filteredNodes"
              :key="node.id"
              :class="['session-item', { active: currentServerId === node.id }]"
              @click="changeServer(node)"
            >
              <div class="node-icon">
                <el-icon><Connection /></el-icon>
              </div>

              <div class="session-main">
                <div class="session-title">
                  {{ node.name || node.id }}
                  <el-tag
                    v-if="node.configured"
                    size="small"
                    effect="plain"
                    type="success"
                    style="padding: 0 4px; height: 18px; line-height: 16px; margin-left: 4px;"
                  >
                    已配置
                  </el-tag>
                </div>

                <div class="session-meta">
                  <span>{{ node.id === 'local' ? '本地主节点' : '远程节点' }}</span>
                  <span class="meta-separator">·</span>
                  <span :class="['node-status-text', getNodeStatusClass(nodeSnapshots[node.id]) ]">
                    {{ getNodeStatusText(nodeSnapshots[node.id]) }}
                  </span>
                </div>
              </div>

              <el-dropdown trigger="click" @command="(cmd) => handleNodeAction(cmd, node)" @click.stop>
                <el-button link :icon="More" class="node-action-btn" @click.stop />
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="edit">编辑</el-dropdown-item>
                    <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>

            <el-empty v-if="filteredNodes.length === 0" :image-size="56" description="暂无节点" />
          </div>
        </template>
      </div>
    </aside>

    <main class="workspace-main custom-scrollbar">
      <div class="chat-header">
        <div style="display: flex; align-items: center; gap: 12px">
          <el-button v-if="sessionPaneCollapsed" link :icon="Menu" @click="sessionPaneCollapsed = false" />
          <div>
            <h2 class="header-title">全节点面板</h2>
            <div class="header-subtitle">
              先看全节点概览，点击左侧节点后进入单节点详情
            </div>
          </div>
        </div>

        <div style="display: flex; align-items: center; gap: 8px">
          <el-button plain size="small" @click="configDialogVisible = true">数据源配置</el-button>
          <el-button :icon="Refresh" :loading="loading" type="primary" @click="fetchAllData" size="small">刷新数据</el-button>
        </div>
      </div>

      <div class="messages-container" v-loading="loading">
        <div class="dashboard-content">
          <div class="panel-section margin-bottom-lg">
            <div class="section-heading">
              <div>
                <div class="section-kicker">Global Panel</div>
                <h3>全节点面板</h3>
              </div>
              <div class="section-desc">聚合展示所有节点的最新快照，适合快速查看整个集群的健康状态。</div>
            </div>

          <el-row :gutter="20" class="margin-bottom-lg">
            <el-col :xs="24" :sm="12" :lg="6">
              <el-card shadow="never" class="overview-card">
                <div class="overview-label">节点总数</div>
                <div class="overview-value">{{ normalizedNodes.length }}</div>
                <div class="overview-meta">已发现节点</div>
              </el-card>
            </el-col>
            <el-col :xs="24" :sm="12" :lg="6">
              <el-card shadow="never" class="overview-card success">
                <div class="overview-label">在线节点</div>
                <div class="overview-value">{{ onlineNodeCount }}</div>
                <div class="overview-meta">最近采集正常</div>
              </el-card>
            </el-col>
            <el-col :xs="24" :sm="12" :lg="6">
              <el-card shadow="never" class="overview-card">
                <div class="overview-label">已配置节点</div>
                <div class="overview-value">{{ configuredNodeCount }}</div>
                <div class="overview-meta">含 Prometheus 配置</div>
              </el-card>
            </el-col>
            <el-col :xs="24" :sm="12" :lg="6">
              <el-card shadow="never" class="overview-card warning">
                <div class="overview-label">当前选中</div>
                <div class="overview-value current-value">{{ hasSelectedNode ? currentServerName : '未选择' }}</div>
                <div class="overview-meta">{{ hasSelectedNode ? getNodeStatusText(selectedSnapshot) : '点击左侧节点进入详情' }}</div>
              </el-card>
            </el-col>
          </el-row>

          <el-card shadow="never" class="nodes-overview-card">
            <template #header>
              <div class="card-header">
                <el-icon><Monitor /></el-icon>
                <span>节点总览</span>
                <span class="record-count">共 {{ filteredNodes.length }} 个节点</span>
              </div>
            </template>

            <div v-if="overviewNodes.length" class="node-grid">
              <button
                v-for="node in overviewNodes"
                :key="node.id"
                type="button"
                class="node-summary-card"
                :class="{ active: currentServerId === node.id }"
                @click="changeServer(node)"
              >
                <div class="node-summary-head">
                  <div>
                    <div class="node-summary-title">{{ node.name || node.id }}</div>
                    <div class="node-summary-id">{{ node.id }}</div>
                  </div>
                  <el-tag :type="getNodeStatusType(node.snapshot)" effect="plain" size="small">
                    {{ getNodeStatusText(node.snapshot) }}
                  </el-tag>
                </div>

                <div class="node-summary-metrics">
                  <div class="summary-metric">
                    <span>CPU</span>
                    <strong>{{ formatPercent(node.snapshot.cpuUsage) }}</strong>
                  </div>
                  <div class="summary-metric">
                    <span>内存</span>
                    <strong>{{ formatPercent(node.snapshot.memoryUsage) }}</strong>
                  </div>
                  <div class="summary-metric">
                    <span>磁盘</span>
                    <strong>{{ formatPercent(node.snapshot.diskUsage) }}</strong>
                  </div>
                  <div class="summary-metric">
                    <span>GPU</span>
                    <strong>{{ formatPercent(node.snapshot.gpuUsage) }}</strong>
                  </div>
                </div>

                <div class="node-summary-foot">
                  <span>{{ node.id === 'local' ? '本地主节点' : '远程节点' }}</span>
                  <span>{{ formatDateTime(node.snapshot.recordedAt || node.snapshot.timestamp, true) }}</span>
                </div>
              </button>
            </div>

            <el-empty v-else description="暂无节点概览数据" :image-size="88" />
          </el-card>
          </div>

          <div v-if="hasSelectedNode" class="panel-section">
            <div class="section-heading">
              <div>
                <div class="section-kicker">Single Node Panel</div>
                <h3>单节点面板</h3>
              </div>
              <div class="section-desc">围绕当前选中的节点展开详细监控、趋势分析和历史记录。</div>
            </div>

          <el-row :gutter="20" class="margin-bottom-lg">
            <el-col :span="24">
              <el-card shadow="never" class="status-card">
                <div class="status-content">
                  <div class="status-item">
                    <div class="status-label">
                      <el-icon><Connection /></el-icon>
                      <span>Prometheus</span>
                    </div>
                    <el-tag :type="prometheusStatus.connected ? 'success' : 'danger'" effect="dark" size="small">
                      <el-icon style="margin-right: 4px;">
                        <component :is="prometheusStatus.connected ? 'CircleCheck' : 'CircleClose'" />
                      </el-icon>
                      {{ prometheusStatus.connected ? '已连接' : '未连接' }}
                    </el-tag>
                  </div>

                  <div class="status-item">
                    <div class="status-label">
                      <el-icon><Monitor /></el-icon>
                      <span>服务器状态</span>
                    </div>
                    <el-tag v-if="serverOnline === null" type="info" effect="dark" size="small">
                      <el-icon style="margin-right: 4px;"><Loading /></el-icon>
                      加载中
                    </el-tag>
                    <el-tooltip v-else-if="!serverOnline && serverError" :content="serverError" placement="bottom">
                      <el-tag type="danger" effect="dark" size="small">
                        <el-icon style="margin-right: 4px;"><CircleClose /></el-icon>
                        离线
                      </el-tag>
                    </el-tooltip>
                    <el-tag v-else :type="serverOnline ? 'success' : 'danger'" effect="dark" size="small">
                      <el-icon style="margin-right: 4px;">
                        <component :is="serverOnline ? 'CircleCheck' : 'CircleClose'" />
                      </el-icon>
                      {{ serverOnline ? '在线' : '离线' }}
                    </el-tag>
                  </div>

                  <div class="status-item">
                    <div class="status-label">
                      <el-icon><Clock /></el-icon>
                      <span>最后采集</span>
                    </div>
                    <span class="status-value">{{ formatDateTime(selectedSnapshot.recordedAt || selectedSnapshot.timestamp, true) }}</span>
                  </div>

                  <div class="status-item">
                    <div class="status-label">
                      <el-icon><Cpu /></el-icon>
                      <span>CPU 核心</span>
                    </div>
                    <span class="status-value">{{ selectedServerInfo.cpuCores || 0 }} 核 / {{ selectedServerInfo.cpuLogicalCores || 0 }} 线程</span>
                  </div>
                </div>
              </el-card>
            </el-col>
          </el-row>

          <el-row :gutter="20" class="margin-bottom-lg">
            <el-col :xs="24" :sm="12" :lg="6">
              <el-card shadow="never" class="metric-card cpu-card">
                <div class="metric-header">
                  <el-icon><Cpu /></el-icon>
                  <span>CPU 使用率</span>
                </div>
                <div class="metric-value">{{ formatPercent(cpuUsagePercent) }}</div>
                <div class="metric-sub">
                  <span>核心数: {{ selectedServerInfo.cpuCores || 0 }}</span>
                </div>
                <div class="metric-gauge">
                  <el-progress :percentage="cpuUsagePercent" :stroke-width="6" :show-text="false" :color="getUsageColor(cpuUsagePercent)" />
                </div>
                <div class="metric-label">型号: {{ selectedServerInfo.cpuModel || 'Unknown' }}</div>
              </el-card>
            </el-col>

            <el-col :xs="24" :sm="12" :lg="6">
              <el-card shadow="never" class="metric-card memory-card">
                <div class="metric-header">
                  <el-icon><Coin /></el-icon>
                  <span>内存使用</span>
                </div>
                <div class="metric-value">{{ formatBytes(memoryInfo.used) }}</div>
                <div class="metric-sub">
                  <span>已用</span>
                  <span class="divider">/</span>
                  <span>{{ formatBytes(memoryInfo.total) }}</span>
                </div>
                <div class="metric-gauge">
                  <el-progress :percentage="memoryInfo.percent" :stroke-width="6" :show-text="false" :color="getUsageColor(memoryInfo.percent)" />
                </div>
                <div class="metric-label">可用: {{ formatBytes(memoryInfo.available) }} ({{ formatPercent(100 - memoryInfo.percent) }})</div>
              </el-card>
            </el-col>

            <el-col :xs="24" :sm="12" :lg="6">
              <el-card shadow="never" class="metric-card disk-card">
                <div class="metric-header">
                  <el-icon><Folder /></el-icon>
                  <span>磁盘使用</span>
                </div>
                <div class="metric-value">{{ formatBytes(diskInfo.used) }}</div>
                <div class="metric-sub">
                  <span>已用</span>
                  <span class="divider">/</span>
                  <span>{{ formatBytes(diskInfo.total) }}</span>
                </div>
                <div class="metric-gauge">
                  <el-progress :percentage="diskInfo.percent" :stroke-width="6" :show-text="false" :color="getUsageColor(diskInfo.percent)" />
                </div>
                <div class="metric-label">可用: {{ formatBytes(diskInfo.available) }}</div>
              </el-card>
            </el-col>

            <el-col :xs="24" :sm="12" :lg="6">
              <el-card shadow="never" class="metric-card gpu-card">
                <div class="metric-header">
                  <el-icon><Star /></el-icon>
                  <span>GPU 使用</span>
                </div>
                <div class="metric-value">{{ formatPercent(gpuInfo.used) }}</div>
                <div class="metric-sub">
                  <span>显存: {{ formatBytes(gpuInfo.memoryUsed) }}</span>
                  <span class="divider">/</span>
                  <span>{{ formatBytes(gpuInfo.memoryTotal) }}</span>
                </div>
                <div class="metric-gauge">
                  <el-progress :percentage="gpuInfo.used || 0" :stroke-width="6" :show-text="false" :color="getUsageColor(gpuInfo.used)" />
                </div>
                <div class="metric-label">型号: {{ selectedServerInfo.gpuModel || 'N/A' }}</div>
              </el-card>
            </el-col>
          </el-row>

          <el-row :gutter="20" class="margin-bottom-lg">
            <el-col :span="24">
              <el-card shadow="never" class="info-card">
                <template #header>
                  <div class="card-header">
                    <el-icon><Monitor /></el-icon>
                    <span>服务器信息</span>
                  </div>
                </template>

                <div class="server-info-grid">
                  <div class="info-item">
                    <span class="info-label">操作系统</span>
                    <span class="info-value">{{ selectedServerInfo.os || 'Unknown' }}</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">CPU 型号</span>
                    <span class="info-value">{{ selectedServerInfo.cpuModel || 'Unknown' }}</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">CPU 物理核心</span>
                    <span class="info-value">{{ selectedServerInfo.cpuCores || 0 }} 核</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">CPU 逻辑核心</span>
                    <span class="info-value">{{ selectedServerInfo.cpuLogicalCores || 0 }} 线程</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">总内存</span>
                    <span class="info-value">{{ formatBytes(memoryInfo.total) }}</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">进程数</span>
                    <span class="info-value">{{ selectedServerInfo.processCount || 0 }}</span>
                  </div>
                  <div class="info-item wide">
                    <span class="info-label">GPU</span>
                    <span class="info-value">{{ selectedServerInfo.gpuModel || 'N/A' }}</span>
                  </div>
                  <div class="info-item wide">
                    <span class="info-label">磁盘</span>
                    <span class="info-value">{{ diskInfo.devices || 'N/A' }}</span>
                  </div>
                </div>
              </el-card>
            </el-col>
          </el-row>

          <el-row :gutter="20" class="margin-bottom-lg">
            <el-col :xs="24" :xl="12">
              <el-card shadow="never" class="chart-card">
                <template #header>
                  <div class="card-header">
                    <el-icon><TrendCharts /></el-icon>
                    <span>CPU & 内存使用趋势</span>
                  </div>
                </template>
                <div v-loading="loading" style="height: 280px;">
                  <LineChart
                    v-if="trendData.length > 0"
                    :data="trendData"
                    title=""
                    y-axis-name="使用率 (%)"
                    height="260px"
                    color="#667eea"
                  />
                  <el-empty v-else description="暂无趋势数据" :image-size="80" />
                </div>
              </el-card>
            </el-col>

            <el-col :xs="24" :xl="12">
              <el-card shadow="never" class="chart-card">
                <template #header>
                  <div class="card-header">
                    <el-icon><DataLine /></el-icon>
                    <span>历史记录</span>
                    <el-select v-model="period" size="small" style="margin-left: auto; width: 100px;" @change="fetchTrendData">
                      <el-option label="5分钟" value="5m" />
                      <el-option label="1小时" value="1h" />
                      <el-option label="24小时" value="24h" />
                      <el-option label="7天" value="7d" />
                    </el-select>
                  </div>
                </template>
                <div v-loading="loading" style="height: 280px;">
                  <LineChart
                    v-if="diskTrendData.length > 0"
                    :data="diskTrendData"
                    title=""
                    y-axis-name="使用率 (%)"
                    height="260px"
                    color="#f39c12"
                  />
                  <el-empty v-else description="暂无磁盘数据" :image-size="80" />
                </div>
              </el-card>
            </el-col>
          </el-row>

          <el-card shadow="never" class="history-card">
            <template #header>
              <div class="card-header">
                <el-icon><List /></el-icon>
                <span>采集历史记录</span>
                <span class="record-count">当前节点 {{ currentServerName }}，共 {{ historyTotal }} 条</span>
                <el-button type="primary" size="small" style="margin-left: auto;" :loading="collecting" @click="collectNow">
                  <el-icon style="margin-right: 4px;"><Refresh /></el-icon>
                  立即采集
                </el-button>
              </div>
            </template>

            <el-table v-loading="loading" :data="historyData" style="width: 100%">
              <el-table-column label="时间" min-width="180" fixed>
                <template #default="{ row }">
                  <div class="time-cell">
                    <el-icon><Clock /></el-icon>
                    {{ formatDateTime(row.recordedAt || row.timestamp) }}
                  </div>
                </template>
              </el-table-column>

              <el-table-column label="CPU" min-width="140" align="center">
                <template #default="{ row }">
                  <div class="usage-cell">
                    <el-progress :percentage="row.cpuUsage || 0" :stroke-width="8" :show-text="false" :color="getUsageColor(row.cpuUsage)" />
                    <span class="usage-text">{{ formatPercent(row.cpuUsage) }}</span>
                  </div>
                </template>
              </el-table-column>

              <el-table-column label="内存" min-width="140" align="center">
                <template #default="{ row }">
                  <div class="usage-cell">
                    <el-progress :percentage="row.memoryUsage || 0" :stroke-width="8" :show-text="false" :color="getUsageColor(row.memoryUsage)" />
                    <span class="usage-text">{{ formatPercent(row.memoryUsage) }}</span>
                  </div>
                </template>
              </el-table-column>

              <el-table-column label="磁盘" min-width="140" align="center">
                <template #default="{ row }">
                  <div class="usage-cell">
                    <el-progress :percentage="row.diskUsage || 0" :stroke-width="8" :show-text="false" :color="getUsageColor(row.diskUsage)" />
                    <span class="usage-text">{{ formatPercent(row.diskUsage) }}</span>
                  </div>
                </template>
              </el-table-column>

              <el-table-column label="GPU" min-width="140" align="center">
                <template #default="{ row }">
                  <div class="usage-cell">
                    <el-progress :percentage="row.gpuUsage || 0" :stroke-width="8" :show-text="false" :color="getUsageColor(row.gpuUsage)" />
                    <span class="usage-text">{{ formatPercent(row.gpuUsage) }}</span>
                  </div>
                </template>
              </el-table-column>
            </el-table>

            <div class="pagination-container">
              <el-pagination
                v-model:current-page="page"
                v-model:page-size="pageSize"
                :page-sizes="[10, 20, 50]"
                layout="total, sizes, prev, pager, next"
                :total="historyTotal"
                @size-change="fetchHistoryData"
                @current-change="fetchHistoryData"
              />
            </div>
          </el-card>
          </div>

          <el-empty
            v-else
            description="点击左侧节点后显示单节点面板"
            :image-size="96"
            class="single-node-placeholder"
          />
        </div>
      </div>
    </main>

    <el-dialog v-model="nodeDialogVisible" :title="isEditingNode ? '编辑节点' : '添加节点'" width="500px" append-to-body>
      <el-form :model="nodeForm" label-width="100px" ref="nodeFormRef">
        <el-form-item label="节点ID" prop="serverId">
          <el-input v-model="nodeForm.serverId" placeholder="如: node-01" :disabled="isEditingNode" />
        </el-form-item>
        <el-form-item label="节点名称" prop="serverName">
          <el-input v-model="nodeForm.serverName" placeholder="如: 北京服务器-1" />
        </el-form-item>
        <el-form-item label="Prometheus URL" prop="prometheusUrl">
          <el-input v-model="nodeForm.prometheusUrl" placeholder="如: http://192.168.1.100:9090" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="nodeForm.remark" type="textarea" placeholder="可选备注信息" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="nodeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveNode" :loading="nodeDialogLoading">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="configDialogVisible" title="硬件监控数据源配置" width="720px" append-to-body>
      <el-form :model="prometheusConfig" label-position="top" class="config-form">
        <el-form-item label="启用硬件监控服务">
          <div class="config-switch-row">
            <div>
              <div class="config-switch-title">Prometheus 作为全局硬件数据源</div>
              <div class="config-switch-desc">
                开启后，平台会从配置的 Prometheus 实例拉取节点 CPU、内存、磁盘与 GPU 指标。
              </div>
            </div>
            <el-switch v-model="prometheusConfig.enabled" />
          </div>
        </el-form-item>

        <el-form-item v-if="prometheusConfig.enabled" label="Prometheus 服务器地址">
          <el-input v-model="prometheusConfig.prometheusUrl" placeholder="例如: http://192.168.1.107:9090" />
          <div class="form-tip">这里配置的是全局默认 Prometheus 地址；单节点如果配置了专属 URL，会优先使用节点自己的地址。</div>
        </el-form-item>

        <div class="config-strategy-grid">
          <div class="strategy-item">
            <div class="item-title">后端缓存周期</div>
            <div class="inline-setting">
              <el-input-number v-model="prometheusConfig.cacheTtl" :min="5" :max="300" :step="5" size="small" />
              <span>秒</span>
            </div>
            <div class="item-desc">后端缓存采集结果，减少对 Prometheus 的重复请求。</div>
          </div>

          <div class="strategy-item">
            <div class="item-title">前端刷新频率</div>
            <div class="inline-setting">
              <el-input-number v-model="prometheusConfig.refreshInterval" :min="5" :max="300" :step="5" size="small" />
              <span>秒</span>
            </div>
            <div class="item-desc">当前页面会按这个频率刷新全节点快照。</div>
          </div>
        </div>

        <el-alert
          title="原来系统环境配置里的硬件监控数据源，已经迁到当前多节点监控页面统一管理。"
          type="info"
          :closable="false"
          show-icon
          style="margin-top: 16px;"
        />
      </el-form>
      <template #footer>
        <el-button @click="configDialogVisible = false">取消</el-button>
        <el-button plain :loading="configTesting" @click="testPrometheusSource">测试连接</el-button>
        <el-button type="primary" :loading="configSaving" @click="savePrometheusConfig">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import {
  ArrowLeft,
  ArrowRight,
  CircleClose,
  Clock,
  Coin,
  Connection,
  Cpu,
  DataLine,
  Folder,
  List,
  Loading,
  Menu,
  Monitor,
  More,
  Plus,
  Refresh,
  Search,
  Star,
  TrendCharts
} from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import LineChart from '@/components/LineChart.vue';
import {
  collectServerHardware,
  createServerInfo,
  deleteServerInfo,
  getPrometheusConfig,
  getServerHardwareHistory,
  getServerHardwareTrend,
  getServerInfoList,
  getServerNodes,
  testPrometheusConnection,
  updatePrometheusConfig,
  updateServerInfo
} from '@/api/monitor';
import { ROUTES } from '@/router/routes';

const router = useRouter();

const loading = ref(false);
const collecting = ref(false);
const period = ref('1h');
const isMobile = ref(false);
const sessionPaneCollapsed = ref(false);
const serverNodes = ref([]);
const searchQuery = ref('');
const nodesLoading = ref(false);
const currentServerId = ref('');
const currentServerName = ref('');
const prometheusStatus = ref({ connected: false });
const serverOnline = ref(null);
const serverError = ref('');
const nodeSnapshots = ref({});
const trendData = ref([]);
const diskTrendData = ref([]);
const historyData = ref([]);
const page = ref(1);
const pageSize = ref(10);
const historyTotal = ref(0);
const nodeDialogVisible = ref(false);
const configDialogVisible = ref(false);
const nodeDialogLoading = ref(false);
const isEditingNode = ref(false);
const nodeFormRef = ref(null);
const configSaving = ref(false);
const configTesting = ref(false);
const nodeForm = ref({
  serverId: '',
  serverName: '',
  prometheusUrl: '',
  remark: ''
});
const prometheusConfig = ref({
  prometheusUrl: '',
  enabled: false,
  cacheTtl: 10,
  refreshInterval: 15
});

let refreshTimer = null;

const createEmptySnapshot = (node = {}) => ({
  serverId: node.id || '',
  serverName: node.name || node.id || '',
  timestamp: 0,
  recordedAt: '',
  online: null,
  errorMessage: '',
  cpuUsage: 0,
  memoryUsage: 0,
  diskUsage: 0,
  gpuUsage: 0,
  cpuCores: 0,
  cpuLogicalCores: 0,
  cpuModel: '',
  memoryTotal: 0,
  memoryUsed: 0,
  diskTotal: 0,
  diskUsed: 0,
  gpuModel: '',
  gpuMemoryTotal: 0,
  gpuMemoryUsed: 0,
  os: '',
  uptime: '',
  processCount: 0,
  networkDownload: '',
  networkUpload: '',
  devices: ''
});

const normalizeNode = (node = {}) => ({
  ...node,
  id: node.id || node.serverId || 'local',
  name: node.name || node.serverName || node.id || node.serverId || 'Local Node'
});

const normalizedNodes = computed(() => {
  const nodes = (serverNodes.value || []).map(normalizeNode);
  if (!nodes.some((node) => node.id === 'local')) {
    nodes.unshift({ id: 'local', name: 'Local Node', configured: false });
  }
  return nodes;
});

const filteredNodes = computed(() => {
  const q = searchQuery.value.trim().toLowerCase();
  if (!q) return normalizedNodes.value;
  return normalizedNodes.value.filter((node) =>
    (node.name && node.name.toLowerCase().includes(q)) ||
    (node.id && node.id.toLowerCase().includes(q))
  );
});

const selectedNode = computed(() =>
  normalizedNodes.value.find((node) => node.id === currentServerId.value) || null
);

const hasSelectedNode = computed(() => !!selectedNode.value);

const selectedSnapshot = computed(() => {
  const snapshot = nodeSnapshots.value[currentServerId.value];
  return snapshot ? snapshot : createEmptySnapshot(selectedNode.value || {});
});

const selectedServerInfo = computed(() => selectedSnapshot.value);

const memoryInfo = computed(() => {
  const total = Number(selectedSnapshot.value.memoryTotal) || 0;
  const used = Number(selectedSnapshot.value.memoryUsed) || 0;
  const percent = clampPercent(selectedSnapshot.value.memoryUsage);
  return {
    total,
    used,
    available: Math.max(total - used, 0),
    percent
  };
});

const diskInfo = computed(() => {
  const total = Number(selectedSnapshot.value.diskTotal) || 0;
  const used = Number(selectedSnapshot.value.diskUsed) || 0;
  const percent = clampPercent(selectedSnapshot.value.diskUsage);
  return {
    total,
    used,
    available: Math.max(total - used, 0),
    percent,
    devices: selectedSnapshot.value.devices || 'Root Disk'
  };
});

const gpuInfo = computed(() => ({
  used: clampPercent(selectedSnapshot.value.gpuUsage),
  memoryUsed: Number(selectedSnapshot.value.gpuMemoryUsed) || 0,
  memoryTotal: Number(selectedSnapshot.value.gpuMemoryTotal) || 0
}));

const cpuUsagePercent = computed(() => clampPercent(selectedSnapshot.value.cpuUsage));

const overviewNodes = computed(() =>
  filteredNodes.value.map((node) => ({
    ...node,
    snapshot: nodeSnapshots.value[node.id] || createEmptySnapshot(node)
  }))
);

const onlineNodeCount = computed(() =>
  overviewNodes.value.filter((node) => node.snapshot.online === true).length
);

const configuredNodeCount = computed(() =>
  normalizedNodes.value.filter((node) => node.configured).length
);

const fetchNodes = async () => {
  nodesLoading.value = true;
  try {
    const data = await getServerNodes();
    serverNodes.value = (data || []).map(normalizeNode);

    if (currentServerId.value && !serverNodes.value.some((node) => node.id === currentServerId.value)) {
      currentServerId.value = '';
      currentServerName.value = '';
    }
  } catch (error) {
    console.error('Failed to fetch server nodes:', error);
  } finally {
    nodesLoading.value = false;
  }
};

const buildSnapshotFromMetric = (metric = {}, node = {}) => {
  const snapshot = createEmptySnapshot(node);
  snapshot.serverId = metric.serverId || node.id || snapshot.serverId;
  snapshot.serverName = metric.serverName || node.name || snapshot.serverName;
  snapshot.timestamp = Number(metric.timestamp) || 0;
  snapshot.recordedAt = metric.recordedAt || '';
  snapshot.online = typeof metric.online === 'boolean' ? metric.online : null;
  snapshot.errorMessage = metric.errorMessage || metric.error || '';
  snapshot.cpuUsage = Number(metric.cpuUsage) || 0;
  snapshot.memoryUsage = Number(metric.memoryUsage) || 0;
  snapshot.diskUsage = Number(metric.diskUsage) || 0;
  snapshot.gpuUsage = Number(metric.gpuUsage) || 0;
  snapshot.cpuCores = Number(metric.cpuCores) || 0;
  snapshot.cpuLogicalCores = Number(metric.cpuLogicalCores) || 0;
  snapshot.cpuModel = metric.cpuModel || '';
  snapshot.memoryTotal = Number(metric.memoryTotal) || 0;
  snapshot.memoryUsed = Number(metric.memoryUsed) || 0;
  snapshot.diskTotal = Number(metric.diskTotal) || 0;
  snapshot.diskUsed = Number(metric.diskUsed) || 0;
  snapshot.gpuModel = metric.gpuModel || '';
  snapshot.gpuMemoryTotal = Number(metric.gpuMemoryTotal) || 0;
  snapshot.gpuMemoryUsed = Number(metric.gpuMemoryUsed) || 0;
  snapshot.os = metric.os || '';
  snapshot.uptime = metric.uptime || '';
  snapshot.processCount = Number(metric.processCount) || 0;
  snapshot.networkDownload = metric.networkDownload || metric.networkReceiveRate || '';
  snapshot.networkUpload = metric.networkUpload || metric.networkTransmitRate || '';
  snapshot.devices = metric.devices || '';
  return snapshot;
};

const fetchNodeSnapshots = async () => {
  const nodes = normalizedNodes.value;
  const results = await Promise.all(nodes.map(async (node) => {
    try {
      const data = await getServerHardwareHistory({ serverId: node.id, page: 0, size: 1 });
      const latest = data?.content?.[0];
      return [node.id, latest ? buildSnapshotFromMetric(latest, node) : createEmptySnapshot(node)];
    } catch (error) {
      return [node.id, { ...createEmptySnapshot(node), online: false, errorMessage: error.message || '请求失败' }];
    }
  }));

  nodeSnapshots.value = Object.fromEntries(results);
};

const fetchNodesAndSnapshots = async () => {
  await fetchNodes();
  await fetchNodeSnapshots();
};

const fetchPrometheusStatus = async () => {
  try {
    const config = await getPrometheusConfig();
    if (config) {
      prometheusConfig.value = {
        prometheusUrl: config.prometheusUrl || '',
        enabled: !!config.enabled,
        cacheTtl: config.cacheTtl || 10,
        refreshInterval: config.refreshInterval || 15
      };
    }
    prometheusStatus.value.connected = !!(config && config.enabled);
  } catch (error) {
    prometheusStatus.value.connected = false;
  }
};

const savePrometheusConfig = async () => {
  configSaving.value = true;
  try {
    await updatePrometheusConfig(prometheusConfig.value);
    ElMessage.success('硬件监控数据源配置已保存');
    await fetchPrometheusStatus();
    restartRefreshTimer();
  } catch (error) {
    ElMessage.error(`保存失败: ${error.response?.data?.message || error.message}`);
  } finally {
    configSaving.value = false;
  }
};

const testPrometheusSource = async () => {
  configTesting.value = true;
  try {
    const result = await testPrometheusConnection();
    if (result.online) {
      ElMessage.success('连接成功，Prometheus 响应正常');
    } else {
      ElMessage.warning(`连接失败: ${result.error || '无法获取指标'}`);
    }
  } catch (error) {
    ElMessage.error(`测试失败: ${error.response?.data?.message || error.message}`);
  } finally {
    configTesting.value = false;
  }
};

const syncSelectedServerState = () => {
  const snapshot = selectedSnapshot.value;
  if (!hasSelectedNode.value) {
    serverOnline.value = null;
    serverError.value = '';
    return;
  }
  serverOnline.value = snapshot.online;
  serverError.value = snapshot.errorMessage || (snapshot.online === false ? '暂无数据' : '');
};

const fetchTrendData = async () => {
  try {
    if (!hasSelectedNode.value) {
      trendData.value = [];
      diskTrendData.value = [];
      return;
    }
    const data = await getServerHardwareTrend(period.value, currentServerId.value);
    trendData.value = (data || []).map((item) => ({
      timestamp: item.timestamp,
      value: item.cpuUsage || 0,
      memoryUsage: item.memoryUsage || 0
    }));
    diskTrendData.value = (data || []).map((item) => ({
      timestamp: item.timestamp,
      value: item.diskUsage || 0,
      gpuUsage: item.gpuUsage || 0
    }));
  } catch (error) {
    console.error('Failed to fetch trend data:', error);
    trendData.value = [];
    diskTrendData.value = [];
  }
};

const fetchHistoryData = async () => {
  try {
    if (!hasSelectedNode.value) {
      historyData.value = [];
      historyTotal.value = 0;
      return;
    }
    const data = await getServerHardwareHistory({
      serverId: currentServerId.value,
      page: page.value - 1,
      size: pageSize.value
    });
    historyData.value = data?.content || [];
    historyTotal.value = data?.totalElements || 0;
  } catch (error) {
    console.error('Failed to fetch history data:', error);
    historyData.value = [];
    historyTotal.value = 0;
  }
};

const fetchAllData = async () => {
  loading.value = true;
  try {
    await fetchNodesAndSnapshots();
    await Promise.all([
      fetchPrometheusStatus(),
      fetchTrendData(),
      fetchHistoryData()
    ]);
    syncSelectedServerState();
  } finally {
    loading.value = false;
  }
};

const refreshSnapshotsOnly = async () => {
  await fetchNodeSnapshots();
  syncSelectedServerState();
};

const openNodeDialog = (node = null) => {
  if (node) {
    isEditingNode.value = true;
    nodeForm.value = {
      serverId: node.id,
      serverName: node.name || '',
      prometheusUrl: node.prometheusUrl || '',
      remark: node.remark || ''
    };
  } else {
    isEditingNode.value = false;
    nodeForm.value = {
      serverId: '',
      serverName: '',
      prometheusUrl: '',
      remark: ''
    };
  }
  nodeDialogVisible.value = true;
};

const saveNode = async () => {
  if (!nodeForm.value.serverId) {
    ElMessage.warning('请输入节点ID');
    return;
  }

  nodeDialogLoading.value = true;
  try {
    if (isEditingNode.value) {
      const existingInfo = await getServerInfoList();
      const existing = existingInfo.find((item) => item.serverId === nodeForm.value.serverId);
      if (existing) {
        await updateServerInfo({
          ...existing,
          serverName: nodeForm.value.serverName,
          prometheusUrl: nodeForm.value.prometheusUrl,
          remark: nodeForm.value.remark
        });
      }
      ElMessage.success('节点更新成功');
    } else {
      await createServerInfo({
        serverId: nodeForm.value.serverId,
        serverName: nodeForm.value.serverName,
        prometheusUrl: nodeForm.value.prometheusUrl,
        remark: nodeForm.value.remark,
        online: false
      });
      ElMessage.success('节点添加成功');
    }

    nodeDialogVisible.value = false;
    await fetchAllData();
  } catch (error) {
    ElMessage.error(`操作失败: ${error.response?.data?.message || error.message}`);
  } finally {
    nodeDialogLoading.value = false;
  }
};

const handleNodeAction = (command, node) => {
  if (command === 'edit') {
    openNodeDialog(node);
    return;
  }

  if (command === 'delete') {
    ElMessageBox.confirm(`确定要删除节点 "${node.name || node.id}" 吗?`, '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(async () => {
      try {
        await deleteServerInfo(node.id);
        ElMessage.success('节点已删除');

        if (currentServerId.value === node.id) {
          currentServerId.value = '';
          currentServerName.value = '';
          page.value = 1;
        }

        await fetchAllData();
      } catch (error) {
        ElMessage.error(`删除失败: ${error.response?.data?.message || error.message}`);
      }
    }).catch(() => {});
  }
};

const changeServer = (node) => {
  if (!node) return;
  router.push(ROUTES.MONITOR.SERVER_NODE.replace(':serverId', encodeURIComponent(node.id)));
};

const collectNow = async () => {
  collecting.value = true;
  try {
    await collectServerHardware();
    ElMessage.success('数据采集成功');
    await fetchAllData();
  } catch (error) {
    ElMessage.error(`采集失败: ${error.response?.data?.message || error.message}`);
  } finally {
    collecting.value = false;
  }
};

const clampPercent = (value) => {
  const v = Number(value) || 0;
  return Math.max(0, Math.min(100, Number(v.toFixed(1))));
};

const formatPercent = (value) => `${clampPercent(value).toFixed(1)}%`;

const formatBytes = (bytes) => {
  const value = Number(bytes) || 0;
  if (value <= 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB', 'PB'];
  const index = Math.min(Math.floor(Math.log(value) / Math.log(1024)), units.length - 1);
  return `${(value / (1024 ** index)).toFixed(2)} ${units[index]}`;
};

const formatDateTime = (value, compact = false) => {
  if (!value) return '-';

  let date;
  if (typeof value === 'number' || /^\d+$/.test(String(value))) {
    date = new Date(Number(value));
  } else {
    date = new Date(String(value).replace('T', ' '));
  }

  if (Number.isNaN(date.getTime())) return String(value);
  return compact ? date.toLocaleString('zh-CN', { hour12: false }) : date.toLocaleString('zh-CN');
};

const getUsageColor = (value) => {
  const val = Number(value) || 0;
  if (val > 80) return '#e74c3c';
  if (val > 60) return '#f39c12';
  return '#27ae60';
};

const getNodeStatusText = (snapshot) => {
  if (!snapshot || snapshot.online === null) return '待采集';
  return snapshot.online ? '在线' : '离线';
};

const getNodeStatusType = (snapshot) => {
  if (!snapshot || snapshot.online === null) return 'info';
  return snapshot.online ? 'success' : 'danger';
};

const getNodeStatusClass = (snapshot) => {
  if (!snapshot || snapshot.online === null) return 'pending';
  return snapshot.online ? 'online' : 'offline';
};

const updateIsMobile = () => {
  if (typeof window === 'undefined') return;
  isMobile.value = window.innerWidth <= 768;
  if (!isMobile.value) {
    sessionPaneCollapsed.value = false;
  }
};

const restartRefreshTimer = () => {
  if (refreshTimer) {
    clearInterval(refreshTimer);
  }

  const intervalSeconds = Number(prometheusConfig.value.refreshInterval) || 30;
  refreshTimer = window.setInterval(async () => {
    await refreshSnapshotsOnly();
  }, Math.max(intervalSeconds, 5) * 1000);
};

watch(selectedSnapshot, syncSelectedServerState, { deep: true, immediate: true });

watch(currentServerId, async () => {
  currentServerName.value = selectedNode.value?.name || '';
  await Promise.all([fetchTrendData(), fetchHistoryData()]);
  syncSelectedServerState();
});

onMounted(async () => {
  updateIsMobile();
  window.addEventListener('resize', updateIsMobile);
  await fetchAllData();
  restartRefreshTimer();
});

onUnmounted(() => {
  window.removeEventListener('resize', updateIsMobile);
  if (refreshTimer) {
    clearInterval(refreshTimer);
  }
});
</script>

<style scoped>
.margin-bottom-lg {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.header-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
}

.header-subtitle {
  margin-top: 2px;
  font-size: 12px;
  color: #64748b;
}

.overview-card {
  border-radius: 16px !important;
  border: 1px solid rgba(148, 163, 184, 0.14) !important;
  min-height: 124px;
}

.overview-card.success {
  background: linear-gradient(180deg, rgba(16, 185, 129, 0.08) 0%, rgba(255, 255, 255, 0.96) 100%);
}

.overview-card.warning {
  background: linear-gradient(180deg, rgba(59, 130, 246, 0.08) 0%, rgba(255, 255, 255, 0.96) 100%);
}

.overview-label {
  font-size: 13px;
  color: #64748b;
}

.overview-value {
  margin-top: 10px;
  font-size: 32px;
  line-height: 1.1;
  font-weight: 700;
  color: #0f172a;
}

.overview-value.current-value {
  font-size: 24px;
}

.overview-meta {
  margin-top: 10px;
  font-size: 12px;
  color: #94a3b8;
}

.nodes-overview-card,
.info-card,
.chart-card,
.history-card,
.metric-card {
  border-radius: 16px !important;
}

.node-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
}

.node-summary-card {
  padding: 16px;
  border-radius: 16px;
  border: 1px solid #e2e8f0;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98) 0%, rgba(248, 250, 252, 0.98) 100%);
  text-align: left;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}

.node-summary-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.08);
}

.node-summary-card.active {
  border-color: #93c5fd;
  box-shadow: 0 12px 30px rgba(59, 130, 246, 0.12);
}

.node-summary-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.node-summary-title {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}

.node-summary-id {
  margin-top: 4px;
  font-size: 12px;
  color: #94a3b8;
}

.node-summary-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.summary-metric {
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(241, 245, 249, 0.85);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.summary-metric span {
  font-size: 11px;
  color: #64748b;
}

.summary-metric strong {
  font-size: 16px;
  color: #0f172a;
}

.node-summary-foot {
  margin-top: 16px;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 11px;
  color: #94a3b8;
}

.status-card {
  background: linear-gradient(135deg, #1e293b 0%, #334155 100%);
  border-radius: 16px;
  border: none !important;
}

.status-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 20px;
}

.status-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.status-label {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #94a3b8;
  font-size: 13px;
}

.status-value {
  color: #f1f5f9;
  font-weight: 500;
  font-size: 13px;
}

.metric-card {
  border: 1px solid rgba(255, 255, 255, 0.1) !important;
  transition: transform 0.2s, box-shadow 0.2s;
}

.metric-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.1);
}

.metric-header {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #64748b;
  font-size: 13px;
  margin-bottom: 12px;
}

.metric-value {
  font-size: 28px;
  font-weight: 700;
  color: #1e293b;
  margin-bottom: 4px;
}

.metric-sub {
  font-size: 12px;
  color: #94a3b8;
  margin-bottom: 12px;
}

.metric-sub .divider {
  margin: 0 6px;
  color: #cbd5e1;
}

.metric-gauge {
  margin-bottom: 8px;
}

.metric-label {
  font-size: 12px;
  color: #64748b;
}

.cpu-card { border-top: 3px solid #667eea; }
.memory-card { border-top: 3px solid #11998e; }
.disk-card { border-top: 3px solid #f39c12; }
.gpu-card { border-top: 3px solid #e74c3c; }

.server-info-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px;
  background: #f8fafc;
  border-radius: 8px;
}

.info-item.wide {
  grid-column: span 2;
}

.info-label {
  font-size: 12px;
  color: #64748b;
}

.info-value {
  font-size: 13px;
  font-weight: 500;
  color: #1e293b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.record-count {
  font-size: 12px;
  color: #94a3b8;
  font-weight: normal;
  margin-left: 8px;
}

.time-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #64748b;
  font-size: 13px;
}

.usage-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.usage-text {
  font-size: 12px;
  font-weight: 600;
  min-width: 45px;
  text-align: right;
}

.pagination-container {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.agent-workspace {
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  overflow: hidden;
  background-color: #f8fafc;
  font-family: 'Inter', -apple-system, sans-serif;
}

.agent-workspace::before {
  content: '';
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 10% 40%, rgba(37, 99, 235, 0.05) 0%, transparent 48%),
    radial-gradient(circle at 88% 18%, rgba(16, 185, 129, 0.05) 0%, transparent 40%);
  z-index: 0;
  pointer-events: none;
}

.workspace-sidebar {
  position: relative;
  z-index: 10;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: rgba(255, 255, 255, 0.78);
  backdrop-filter: blur(16px);
  border-right: 1px solid rgba(226, 232, 240, 0.8);
  width: 280px;
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  flex-shrink: 0;
}

.workspace-sidebar.is-collapsed {
  width: 64px;
}

.workspace-session-pane {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.session-collapse-handle {
  position: absolute;
  right: -14px;
  top: 50%;
  transform: translateY(-50%);
  z-index: 20;
}

.collapse-btn {
  width: 28px !important;
  height: 28px !important;
  font-size: 14px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
  color: #64748b;
  transition: 0.2s ease;
}

.collapse-btn:hover {
  color: #3b82f6;
  border-color: #bfdbfe;
  transform: scale(1.05);
}

.sidebar-top {
  padding: 20px 16px 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.sidebar-profile {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 4px;
}

.sidebar-avatar {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.04);
  background: rgba(59, 130, 246, 0.1);
  color: #3b82f6;
}

.sidebar-name {
  font-size: 15px;
  font-weight: 700;
  color: #1e293b;
  flex: 1;
  margin-top: 2px;
}

.sidebar-actions {
  position: absolute;
  top: 24px;
  right: 16px;
  display: flex;
  gap: 4px;
}

.refresh-btn,
.add-btn {
  color: #94a3b8;
}

.session-item .node-action-btn {
  opacity: 0;
  transition: opacity 0.2s;
}

.session-item:hover .node-action-btn {
  opacity: 1;
}

.session-search {
  padding: 0 16px 12px;
}

.session-search :deep(.el-input__wrapper) {
  border-radius: 10px;
  background: rgba(248, 250, 252, 0.6);
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.session-list::-webkit-scrollbar {
  width: 4px;
}

.session-list::-webkit-scrollbar-thumb {
  background: rgba(203, 213, 225, 0.6);
  border-radius: 4px;
}

.session-item {
  display: flex;
  align-items: center;
  padding: 10px 12px;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.15s ease;
  border: 1px solid transparent;
}

.session-item:hover {
  background: rgba(241, 245, 249, 0.6);
}

.session-item.active {
  background: #ffffff;
  border-color: #e2e8f0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.node-icon {
  margin-right: 10px;
  color: #64748b;
  display: flex;
  font-size: 16px;
}

.session-item.active .node-icon {
  color: #3b82f6;
}

.session-main {
  flex: 1;
  min-width: 0;
}

.session-title {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  display: flex;
  align-items: center;
  gap: 6px;
}

.session-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: #94a3b8;
}

.meta-separator {
  opacity: 0.6;
}

.node-status-text.online {
  color: #10b981;
}

.node-status-text.offline {
  color: #ef4444;
}

.node-status-text.pending {
  color: #94a3b8;
}

.collapsed-pane {
  display: flex;
  justify-content: center;
  padding-top: 24px;
}

.collapsed-new-btn {
  width: 40px !important;
  height: 40px !important;
  font-size: 18px;
  background: rgba(59, 130, 246, 0.1);
  color: #3b82f6;
  border: none;
}

.workspace-main {
  flex: 1;
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-width: 0;
  background: transparent;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 24px;
  min-height: 56px;
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid rgba(226, 232, 240, 0.5);
  z-index: 2;
}

.messages-container {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 24px;
  display: flex;
  flex-direction: column;
  scroll-behavior: smooth;
}

.messages-container::-webkit-scrollbar {
  width: 6px;
}

.messages-container::-webkit-scrollbar-thumb {
  background: rgba(203, 213, 225, 0.8);
  border-radius: 6px;
}

.dashboard-content {
  width: 100%;
  max-width: 1280px;
  margin: 0 auto;
}

.single-node-placeholder {
  margin-top: 12px;
  padding: 40px 0 20px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.55);
  border: 1px dashed rgba(148, 163, 184, 0.35);
}

.d-overlay {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.35);
  z-index: 9;
}

html.dark .header-title,
html.dark .overview-value,
html.dark .summary-metric strong,
html.dark .metric-value,
html.dark .info-value,
html.dark .node-summary-title,
html.dark .sidebar-name {
  color: #f8fafc;
}

html.dark .header-subtitle,
html.dark .overview-label,
html.dark .overview-meta,
html.dark .node-summary-id,
html.dark .node-summary-foot,
html.dark .summary-metric span,
html.dark .metric-label,
html.dark .metric-sub,
html.dark .info-label,
html.dark .time-cell,
html.dark .record-count {
  color: #94a3b8;
}

html.dark .overview-card,
html.dark .nodes-overview-card,
html.dark .metric-card,
html.dark .info-card,
html.dark .chart-card,
html.dark .history-card,
html.dark .workspace-sidebar,
html.dark .chat-header {
  background: rgba(30, 41, 59, 0.88);
  border-color: rgba(255, 255, 255, 0.08) !important;
}

html.dark .node-summary-card {
  background: linear-gradient(180deg, rgba(30, 41, 59, 0.98) 0%, rgba(15, 23, 42, 0.98) 100%);
  border-color: rgba(148, 163, 184, 0.18);
}

html.dark .summary-metric,
html.dark .info-item,
html.dark .session-item.active {
  background: rgba(51, 65, 85, 0.9);
}

html.dark .session-item:hover,
html.dark .session-search :deep(.el-input__wrapper) {
  background: rgba(51, 65, 85, 0.55);
}

html.dark .status-card {
  background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%);
}

@media (max-width: 1200px) {
  .server-info-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .info-item.wide {
    grid-column: span 2;
  }
}

@media (max-width: 900px) {
  .node-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .workspace-sidebar {
    position: absolute;
    left: 0;
    top: 0;
    bottom: 0;
    width: 280px;
  }

  .workspace-sidebar.is-collapsed {
    width: 0;
    overflow: hidden;
    border-right: none;
  }

  .chat-header,
  .messages-container {
    padding-left: 16px;
    padding-right: 16px;
  }

  .status-content {
    flex-direction: column;
    align-items: flex-start;
  }

  .server-info-grid {
    grid-template-columns: 1fr;
  }

  .info-item.wide {
    grid-column: span 1;
  }
}

/* Visual refresh aligned with ORIN design system */
.server-workspace {
  --orin-accent: var(--orin-primary, #0d9488);
  --panel-bg: linear-gradient(180deg, rgba(255, 255, 255, 0.88) 0%, rgba(248, 250, 252, 0.82) 100%);
  --panel-border: rgba(226, 232, 240, 0.95);
  --panel-shadow: 0 6px 20px rgba(15, 23, 42, 0.06);
  --text-main: var(--text-primary, #1e293b);
  --text-sub: var(--text-secondary, #64748b);
}

.server-workspace.agent-workspace {
  font-family: 'Inter', 'PingFang SC', -apple-system, sans-serif;
  background:
    radial-gradient(circle at 14% 18%, rgba(13, 148, 136, 0.08) 0%, transparent 34%),
    radial-gradient(circle at 88% 10%, rgba(14, 165, 233, 0.06) 0%, transparent 30%),
    #f8fafc;
}

.server-workspace .workspace-sidebar {
  background: rgba(255, 255, 255, 0.78);
  border-right: 1px solid var(--panel-border);
}

.server-workspace .sidebar-avatar {
  border-radius: 12px;
  background: rgba(13, 148, 136, 0.12);
  color: var(--orin-accent);
}

.server-workspace .chat-header {
  margin: 12px 16px 0;
  border-radius: var(--orin-card-radius, 12px);
  border: 1px solid var(--panel-border);
  background: var(--panel-bg);
  box-shadow: var(--panel-shadow);
}

.server-workspace .header-title,
.server-workspace .section-heading h3 {
  color: var(--text-main);
}

.server-workspace .header-title {
  font-size: 18px;
  font-weight: 700;
}

.server-workspace .header-subtitle,
.server-workspace .section-desc {
  color: var(--text-sub);
}

.server-workspace .messages-container {
  padding: 20px 16px 24px;
}

.server-workspace .dashboard-content {
  max-width: 1360px;
}

.server-workspace .section-kicker {
  color: var(--orin-accent);
  font-weight: 600;
}

.server-workspace .overview-card,
.server-workspace .nodes-overview-card,
.server-workspace .status-card,
.server-workspace .metric-card,
.server-workspace .info-card,
.server-workspace .chart-card,
.server-workspace .history-card {
  border: 1px solid var(--panel-border) !important;
  border-radius: var(--orin-card-radius, 12px) !important;
  background: var(--panel-bg);
  box-shadow: var(--panel-shadow);
}

.server-workspace .overview-card {
  min-height: 128px;
}

.server-workspace .overview-card.success {
  background: linear-gradient(180deg, rgba(16, 185, 129, 0.08) 0%, rgba(255, 255, 255, 0.94) 100%);
}

.server-workspace .overview-card.warning {
  background: linear-gradient(180deg, rgba(14, 165, 233, 0.08) 0%, rgba(255, 255, 255, 0.94) 100%);
}

.server-workspace .overview-label {
  color: var(--text-sub);
  font-weight: 600;
}

.server-workspace .overview-value {
  color: var(--text-main);
  font-size: 32px;
}

.server-workspace .card-header {
  color: var(--text-main);
}

.server-workspace .node-summary-card {
  border-radius: var(--orin-card-radius, 12px);
  border: 1px solid #e2e8f0;
  background: #fff;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.05);
}

.server-workspace .node-summary-card:hover {
  transform: translateY(-2px);
  border-color: rgba(13, 148, 136, 0.35);
  box-shadow: 0 8px 20px rgba(13, 148, 136, 0.12);
}

.server-workspace .node-summary-card.active {
  border-color: var(--orin-accent);
  box-shadow: 0 10px 24px rgba(13, 148, 136, 0.18);
}

.server-workspace .summary-metric {
  border: 1px solid #e5edf5;
  background: #f8fafc;
}

.server-workspace .status-card {
  background: linear-gradient(135deg, #1e293b 0%, #334155 100%);
}

.server-workspace .metric-card {
  border-top: 3px solid rgba(13, 148, 136, 0.6) !important;
}

.server-workspace .cpu-card {
  border-top-color: rgba(59, 130, 246, 0.75) !important;
}

.server-workspace .memory-card {
  border-top-color: rgba(13, 148, 136, 0.75) !important;
}

.server-workspace .disk-card {
  border-top-color: rgba(245, 158, 11, 0.8) !important;
}

.server-workspace .gpu-card {
  border-top-color: rgba(239, 68, 68, 0.78) !important;
}

.server-workspace .metric-value,
.server-workspace .info-value {
  color: var(--text-main);
}

.server-workspace .metric-sub,
.server-workspace .metric-label,
.server-workspace .info-label,
.server-workspace .record-count {
  color: var(--text-sub);
}

.server-workspace .history-card :deep(.el-table) {
  --el-table-header-bg-color: #f8fafc;
  --el-table-border-color: #e2e8f0;
}

.server-workspace .history-card :deep(.el-table__row:hover > td) {
  background: rgba(13, 148, 136, 0.06) !important;
}

.server-workspace .single-node-placeholder {
  border: 1px dashed #cbd5e1;
  border-radius: var(--orin-card-radius, 12px);
  background: rgba(255, 255, 255, 0.72);
}

.server-workspace .panel-section {
  animation: orin-fade-up 0.22s ease;
}

@keyframes orin-fade-up {
  from {
    opacity: 0;
    transform: translateY(4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 768px) {
  .server-workspace .chat-header {
    margin: 10px;
  }

  .server-workspace .messages-container {
    padding: 14px 10px 18px;
  }
}

html.dark .server-workspace {
  --panel-bg: linear-gradient(180deg, rgba(15, 23, 42, 0.9) 0%, rgba(15, 23, 42, 0.84) 100%);
  --panel-border: rgba(148, 163, 184, 0.22);
  --panel-shadow: 0 8px 22px rgba(2, 6, 23, 0.32);
  --text-main: #e2e8f0;
  --text-sub: #94a3b8;
}

html.dark .server-workspace .node-summary-card,
html.dark .server-workspace .summary-metric,
html.dark .server-workspace .info-item,
html.dark .server-workspace .session-item.active {
  background: rgba(15, 23, 42, 0.88);
}

html.dark .server-workspace .session-item:hover {
  background: rgba(30, 41, 59, 0.7);
}
</style>
