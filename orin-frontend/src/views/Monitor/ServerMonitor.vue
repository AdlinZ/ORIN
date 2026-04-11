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

      <div v-if="isOfflineMode" class="offline-banner">
        <el-icon><Warning /></el-icon>
        <span>离线模式 — 显示最近保存的数据，部分节点数据可能不是最新的</span>
      </div>

      <div class="messages-container" v-loading="loading">
        <div class="dashboard-content">
          <div class="panel-section margin-bottom-lg">

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
                <div class="metric-value">{{ formatBytesOrNA(memoryInfo.used) }}</div>
                <div class="metric-sub">
                  <span>已用</span>
                  <span class="divider">/</span>
                  <span>{{ formatBytesOrNA(memoryInfo.total) }}</span>
                </div>
                <div class="metric-gauge">
                  <el-progress :percentage="memoryInfo.percent" :stroke-width="6" :show-text="false" :color="getUsageColor(memoryInfo.percent)" />
                </div>
                <div class="metric-label">可用: {{ memoryInfo.total > 0 ? `${formatBytes(memoryInfo.available)} (${formatPercent(100 - memoryInfo.percent)})` : 'N/A' }}</div>
              </el-card>
            </el-col>

            <el-col :xs="24" :sm="12" :lg="6">
              <el-card shadow="never" class="metric-card disk-card">
                <div class="metric-header">
                  <el-icon><Folder /></el-icon>
                  <span>磁盘使用</span>
                </div>
                <div class="metric-value">{{ formatBytesOrNA(diskInfo.used) }}</div>
                <div class="metric-sub">
                  <span>已用</span>
                  <span class="divider">/</span>
                  <span>{{ formatBytesOrNA(diskInfo.total) }}</span>
                </div>
                <div class="metric-gauge">
                  <el-progress :percentage="diskInfo.percent" :stroke-width="6" :show-text="false" :color="getUsageColor(diskInfo.percent)" />
                </div>
                <div class="metric-label">可用: {{ diskInfo.total > 0 ? formatBytes(diskInfo.available) : 'N/A' }}</div>
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
                  <span>显存: {{ selectedSnapshot.gpuMemory || `${formatBytesOrNA(gpuInfo.memoryUsed)} / ${formatBytesOrNA(gpuInfo.memoryTotal)}` }}</span>
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
                    <span class="info-value">{{ formatLogicalCores(selectedServerInfo) }} 线程</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">总内存</span>
                    <span class="info-value">{{ formatBytesOrNA(memoryInfo.total) }}</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">进程数</span>
                    <span class="info-value">{{ formatCount(selectedServerInfo.processCount) }}</span>
                  </div>
                  <div class="info-item wide">
                    <span class="info-label">GPU</span>
                    <span class="info-value">{{ selectedServerInfo.gpuModel || 'N/A' }}</span>
                  </div>
                  <div class="info-item wide">
                    <span class="info-label">磁盘</span>
                    <span class="info-value">{{ diskInfo.devices || 'N/A' }}</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">实例 ID</span>
                    <span class="info-value">{{ currentServerId || 'N/A' }}</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">实例名称</span>
                    <span class="info-value">{{ currentServerName || 'N/A' }}</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">运行状态</span>
                    <span class="info-value">{{ serverOnline ? '运行中' : '离线' }}</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">数据源</span>
                    <span class="info-value">{{ selectedNode?.prometheusUrl || '全局默认' }}</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">公网 IP</span>
                    <span class="info-value">{{ selectedNode?.publicIp || selectedNode?.publicIP || 'N/A' }}</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">私网 IP</span>
                    <span class="info-value">{{ selectedNode?.privateIp || selectedNode?.privateIP || 'N/A' }}</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">地域</span>
                    <span class="info-value">{{ selectedNode?.region || 'N/A' }}</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">规格族</span>
                    <span class="info-value">{{ selectedNode?.instanceFamily || selectedNode?.flavor || 'N/A' }}</span>
                  </div>
                  <div class="info-item wide">
                    <span class="info-label">镜像</span>
                    <span class="info-value">{{ selectedNode?.image || selectedNode?.imageName || 'N/A' }}</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">到期时间</span>
                    <span class="info-value">{{ formatDateTime(selectedNode?.expireAt || selectedNode?.expiredAt || selectedNode?.expireTime) }}</span>
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
                    :max-points="200"
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
                    :max-points="200"
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

    <el-dialog v-model="nodeDialogVisible" width="640px" append-to-body class="node-dialog">
      <template #header>
        <div class="node-dialog-header">
          <div class="node-dialog-title">{{ isEditingNode ? '编辑节点' : '添加节点' }}</div>
          <div class="node-dialog-subtitle">配置节点标识、展示名称和可选 Prometheus 地址。</div>
        </div>
      </template>
      <el-form :model="nodeForm" label-position="top" ref="nodeFormRef" class="node-form">
        <div class="node-form-section">
          <div class="section-title">基础信息</div>
          <div class="section-grid">
            <el-form-item label="节点 ID" prop="serverId" required>
              <el-input
                v-model="nodeForm.serverId"
                placeholder="自动生成，如: ORIN-20260409153000-A1B2"
                :disabled="isEditingNode"
                readonly
                maxlength="128"
                show-word-limit
              >
                <template #append>
                  <el-button :disabled="isEditingNode" @click="regenerateNodeId">重新生成</el-button>
                </template>
              </el-input>
              <div class="field-tip">保存后作为唯一标识，建议保持稳定且可读。</div>
            </el-form-item>
            <el-form-item label="节点名称" prop="serverName">
              <el-input
                v-model="nodeForm.serverName"
                placeholder="如: 北京 GPU 节点"
                maxlength="64"
                show-word-limit
              />
            </el-form-item>
          </div>
        </div>

        <div class="node-form-section">
          <div class="section-title">数据源配置</div>
          <el-form-item label="Prometheus URL（可选）" prop="prometheusUrl">
            <el-input
              v-model="nodeForm.prometheusUrl"
              placeholder="如: http://192.168.1.100:9090"
              clearable
            />
            <div class="field-tip">填写后该节点优先使用此地址；留空则走全局默认配置。</div>
          </el-form-item>
          <el-alert
            v-if="nodeForm.prometheusUrl.trim() && !nodePrometheusUrlValid"
            title="Prometheus URL 格式不正确，请使用 http:// 或 https:// 开头的完整地址。"
            type="warning"
            :closable="false"
            show-icon
          />
        </div>

        <div class="node-form-section">
          <div class="section-title">备注信息</div>
          <el-form-item label="备注" prop="remark">
            <el-input
              v-model="nodeForm.remark"
              type="textarea"
              :rows="3"
              maxlength="200"
              show-word-limit
              placeholder="可选：用途、机房位置、负责人等"
            />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="nodeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveNode" :loading="nodeDialogLoading" :disabled="!canSubmitNodeForm">保存</el-button>
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

        <el-form-item label="自动采集任务">
          <div class="config-switch-row">
            <div>
              <div class="config-switch-title">后台定时自动采集硬件数据</div>
              <div class="config-switch-desc">
                默认开启，每分钟采集一次。修改后需要重启后端服务才会生效。
              </div>
            </div>
            <el-switch v-model="hardwareAutoCollectEnabled" />
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
  TrendCharts,
  Warning
} from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import LineChart from '@/components/LineChart.vue';
import {
  collectServerHardware,
  createServerInfo,
  deleteServerInfo,
  getPrometheusConfig,
  getPrometheusServerStatus,
  getServerHardwareHistory,
  getServerHardwareTrend,
  getServerInfoList,
  getServerNodes,
  getSystemProperties,
  testPrometheusConnection,
  updatePrometheusConfig,
  updateServerInfo,
  updateSystemProperties
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
const hardwareAutoCollectEnabled = ref(true);

let refreshTimer = null;

const isHttpUrl = (value = '') => {
  const text = String(value || '').trim();
  if (!text) return true;
  try {
    const parsed = new URL(text);
    return parsed.protocol === 'http:' || parsed.protocol === 'https:';
  } catch {
    return false;
  }
};

const nodePrometheusUrlValid = computed(() => isHttpUrl(nodeForm.value.prometheusUrl));
const canSubmitNodeForm = computed(() => Boolean((nodeForm.value.serverId || '').trim()) && nodePrometheusUrlValid.value);
const NODE_ID_PREFIX = 'ORIN-';

const generateNodeId = () => {
  const now = new Date();
  const pad = (num) => String(num).padStart(2, '0');
  const ts = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`;
  const rand = Math.random().toString(36).slice(2, 6).toUpperCase();
  return `${NODE_ID_PREFIX}${ts}-${rand}`;
};

const normalizeNodeId = (value = '') => {
  const text = String(value || '').trim();
  if (!text) return generateNodeId();
  if (text.startsWith(NODE_ID_PREFIX)) return text;
  return `${NODE_ID_PREFIX}${text}`;
};

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
  gpuMemory: '',
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
  id: node.id || node.serverId || '',
  name: node.name || node.serverName || node.id || node.serverId || 'Unnamed Node'
});

const normalizedNodes = computed(() => {
  const nodes = (serverNodes.value || []).map(normalizeNode);
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

const isOfflineMode = computed(() =>
  overviewNodes.value.some((node) => node.snapshot.online === false && (node.snapshot.recordedAt || node.snapshot.timestamp))
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
  snapshot.gpuMemory = metric.gpuMemory || '';
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
      // Preserve previously fetched snapshot when offline, only mark as offline
      const existing = nodeSnapshots.value[node.id];
      return [node.id, existing ? { ...existing, online: false, errorMessage: error.message || '请求失败' } : { ...createEmptySnapshot(node), online: false, errorMessage: error.message || '请求失败' }];
    }
  }));

  nodeSnapshots.value = Object.fromEntries(results);
  await hydrateCurrentNodeSnapshot();
};

const hydrateCurrentNodeSnapshot = async () => {
  const serverId = currentServerId.value;
  if (!serverId) return;
  const snapshot = nodeSnapshots.value[serverId];
  if (!snapshot) return;

  const needsHydration = (Number(snapshot.memoryTotal) || 0) <= 0 || (Number(snapshot.diskTotal) || 0) <= 0;
  if (!needsHydration) return;

  try {
    const realtime = await getPrometheusServerStatus(serverId);
    if (!realtime || realtime.online === false) return;

    const next = { ...snapshot };
    const memoryTotal = Number(realtime.memoryTotal) || 0;
    const diskTotal = Number(realtime.diskTotal) || 0;
    const memoryUsage = Number(realtime.memoryUsage) || 0;
    const diskUsage = Number(realtime.diskUsage) || 0;
    const gpuMemoryTotal = Number(realtime.gpuMemoryTotal) || 0;
    const gpuMemoryUsed = Number(realtime.gpuMemoryUsed) || 0;

    if ((Number(next.memoryTotal) || 0) <= 0 && memoryTotal > 0) next.memoryTotal = memoryTotal;
    if ((Number(next.diskTotal) || 0) <= 0 && diskTotal > 0) next.diskTotal = diskTotal;
    if ((Number(next.memoryUsed) || 0) <= 0 && (Number(next.memoryTotal) || 0) > 0 && memoryUsage >= 0) {
      next.memoryUsed = Math.round((Number(next.memoryTotal) || 0) * (memoryUsage / 100));
    }
    if ((Number(next.diskUsed) || 0) <= 0 && (Number(next.diskTotal) || 0) > 0 && diskUsage >= 0) {
      next.diskUsed = Math.round((Number(next.diskTotal) || 0) * (diskUsage / 100));
    }

    if ((Number(next.gpuMemoryTotal) || 0) <= 0 && gpuMemoryTotal > 0) next.gpuMemoryTotal = gpuMemoryTotal;
    if ((Number(next.gpuMemoryUsed) || 0) <= 0 && gpuMemoryUsed > 0) next.gpuMemoryUsed = gpuMemoryUsed;
    const currentGpuMemoryText = String(next.gpuMemory || '').trim().toLowerCase();
    if ((!currentGpuMemoryText || currentGpuMemoryText === 'n/a' || currentGpuMemoryText === 'unknown') && gpuMemoryTotal > 0) {
      const used = gpuMemoryUsed > 0 ? gpuMemoryUsed : 0;
      next.gpuMemory = `${formatBytes(used)} / ${formatBytes(gpuMemoryTotal)}`;
    }

    next.cpuModel = next.cpuModel || realtime.cpuModel || '';
    const currentGpuModel = String(next.gpuModel || '').trim().toLowerCase();
    if (!currentGpuModel || currentGpuModel === 'unknown' || currentGpuModel === 'n/a') {
      next.gpuModel = realtime.gpuModel || next.gpuModel || '';
    }
    next.os = next.os || realtime.os || '';
    next.online = typeof next.online === 'boolean' ? next.online : Boolean(realtime.online);
    nodeSnapshots.value = { ...nodeSnapshots.value, [serverId]: next };
  } catch {
    // Ignore hydration failures; keep historical snapshot as-is.
  }
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
    const properties = await getSystemProperties();
    const rawEnabled = properties?.['orin.hardware.monitor.enabled'];
    hardwareAutoCollectEnabled.value = rawEnabled === undefined || rawEnabled === null || rawEnabled === ''
      ? true
      : String(rawEnabled).toLowerCase() === 'true';
    prometheusStatus.value.connected = !!(config && config.enabled);
  } catch (error) {
    prometheusStatus.value.connected = false;
    hardwareAutoCollectEnabled.value = true;
  }
};

const savePrometheusConfig = async () => {
  configSaving.value = true;
  try {
    await updatePrometheusConfig(prometheusConfig.value);
    await updateSystemProperties({
      'orin.hardware.monitor.enabled': hardwareAutoCollectEnabled.value ? 'true' : 'false'
    });
    ElMessage.success('硬件监控数据源配置已保存（自动采集开关需重启后端生效）');
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
      serverId: generateNodeId(),
      serverName: '',
      prometheusUrl: '',
      remark: ''
    };
  }
  nodeDialogVisible.value = true;
};

const regenerateNodeId = () => {
  if (isEditingNode.value) return;
  nodeForm.value.serverId = generateNodeId();
};

const saveNode = async () => {
  const serverId = normalizeNodeId(nodeForm.value.serverId);
  const serverName = String(nodeForm.value.serverName || '').trim();
  const prometheusUrl = String(nodeForm.value.prometheusUrl || '').trim();
  const remark = String(nodeForm.value.remark || '').trim();

  if (!serverId) {
    ElMessage.warning('请输入节点ID');
    return;
  }
  if (!isHttpUrl(prometheusUrl)) {
    ElMessage.warning('Prometheus URL 格式不正确');
    return;
  }

  nodeDialogLoading.value = true;
  try {
    if (isEditingNode.value) {
      const existingInfo = await getServerInfoList();
      const existing = existingInfo.find((item) => item.serverId === serverId);
      if (existing) {
        await updateServerInfo({
          ...existing,
          serverName,
          prometheusUrl,
          remark
        });
      }
      ElMessage.success('节点更新成功');
    } else {
      await createServerInfo({
        serverId,
        serverName,
        prometheusUrl,
        remark,
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

const formatCount = (value) => {
  const n = Number(value);
  return Number.isFinite(n) && n > 0 ? n : 'N/A';
};

const formatLogicalCores = (snapshot = {}) => {
  const logical = Number(snapshot.cpuLogicalCores);
  if (Number.isFinite(logical) && logical > 0) return logical;
  const physical = Number(snapshot.cpuCores);
  return Number.isFinite(physical) && physical > 0 ? physical : 'N/A';
};

const formatBytesOrNA = (bytes) => {
  const value = Number(bytes);
  return Number.isFinite(value) && value > 0 ? formatBytes(value) : 'N/A';
};

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

watch(configDialogVisible, async (val) => {
  if (val) {
    await fetchPrometheusStatus();
  }
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
@import './server-monitor-shared.css';

.offline-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background-color: #fef3cd;
  border-bottom: 1px solid #ffc107;
  color: #856404;
  font-size: 13px;
}

.node-dialog-header {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.node-dialog-title {
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
}

.node-dialog-subtitle {
  font-size: 13px;
  color: #6b7280;
}

.node-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.node-form-section {
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 14px;
  background: linear-gradient(180deg, #ffffff 0%, #fafafa 100%);
}

.section-title {
  font-size: 13px;
  font-weight: 600;
  color: #374151;
  margin-bottom: 8px;
}

.section-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.field-tip {
  margin-top: 4px;
  font-size: 12px;
  color: #6b7280;
}

@media (max-width: 768px) {
  .section-grid {
    grid-template-columns: 1fr;
  }
}

:deep(.node-dialog .el-dialog__body) {
  padding-top: 8px;
}
</style>
