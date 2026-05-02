<template>
  <div class="agent-workspace server-workspace" :class="{ 'is-collapsed': sessionPaneCollapsed }">
    <div v-if="!sessionPaneCollapsed" class="d-overlay" @click="sessionPaneCollapsed = true"></div>

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
            <button
              type="button"
              class="session-item session-overview-entry"
              @click="openOverviewFromList"
            >
              <div class="node-icon">
                <el-icon><Menu /></el-icon>
              </div>

              <div class="session-main">
                <div class="session-title">全节点面板</div>
                <div class="session-meta">
                  <span>返回总览</span>
                  <span class="meta-separator">·</span>
                  <span>离开当前单节点</span>
                </div>
              </div>
            </button>

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
                    class="configured-tag"
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
          <el-button
            class="node-list-trigger"
            :class="{ 'is-active': !sessionPaneCollapsed }"
            type="primary"
            @click="sessionPaneCollapsed = !sessionPaneCollapsed"
          >
            <el-icon><Menu /></el-icon>
            <span>节点列表</span>
            <span class="node-list-count">{{ filteredNodes.length }}</span>
          </el-button>
          <div>
            <h2 class="header-title">{{ currentServerName }} - 单节点面板</h2>
            <div class="header-subtitle">围绕当前选中的节点展开详细监控、趋势分析和历史记录</div>
          </div>
        </div>

        <div style="display: flex; align-items: center; gap: 8px">
          <el-button plain size="small" @click="configDialogVisible = true">数据源配置</el-button>
          <el-button :icon="Refresh" :loading="loading" type="primary" @click="fetchAllData" size="small">刷新数据</el-button>
        </div>
      </div>

      <el-alert
        v-if="serverOnline === false"
        type="warning"
        :closable="false"
        show-icon
        style="margin: 0 24px 16px;"
      >
        <template #title>
          <span>
            节点 <strong>{{ currentServerName }}</strong> 当前离线，自动刷新已暂停。正在展示最近一次采集数据
            <template v-if="lastSnapshotTime">（{{ lastSnapshotTime }}）</template>。
          </span>
          <el-button link type="primary" style="margin-left: 8px;" :loading="loading" @click="fetchAllData">重新检测</el-button>
        </template>
      </el-alert>

      <div class="messages-container" v-loading="contentLoading">
        <div class="dashboard-content">
          <div class="panel-section">
            <el-row :gutter="20" class="margin-bottom-lg">
              <el-col :xs="24" :sm="12" :lg="6">
                <el-card shadow="never" class="metric-card cpu-card">
                  <div class="metric-header"><el-icon><Cpu /></el-icon><span>CPU 使用率</span></div>
                  <div class="metric-value">{{ formatPercent(cpuUsagePercent) }}</div>
                  <div class="metric-sub"><span>核心数: {{ selectedServerInfo.cpuCores || 0 }}</span></div>
                  <div class="metric-gauge">
                    <el-progress :percentage="cpuUsagePercent" :stroke-width="6" :show-text="false" :color="getUsageColor(cpuUsagePercent)" />
                  </div>
                  <div class="metric-label">型号: {{ selectedServerInfo.cpuModel || 'Unknown' }}</div>
                </el-card>
              </el-col>

              <el-col :xs="24" :sm="12" :lg="6">
                <el-card shadow="never" class="metric-card memory-card">
                  <div class="metric-header"><el-icon><Coin /></el-icon><span>内存使用</span></div>
                  <div class="metric-value">{{ formatBytesOrNA(memoryInfo.used) }}</div>
                  <div class="metric-sub"><span>已用</span><span class="divider">/</span><span>{{ formatBytesOrNA(memoryInfo.total) }}</span></div>
                  <div class="metric-gauge">
                    <el-progress :percentage="memoryInfo.percent" :stroke-width="6" :show-text="false" :color="getUsageColor(memoryInfo.percent)" />
                  </div>
                  <div class="metric-label">可用: {{ memoryInfo.total > 0 ? `${formatBytes(memoryInfo.available)} (${formatPercent(100 - memoryInfo.percent)})` : 'N/A' }}</div>
                </el-card>
              </el-col>

              <el-col :xs="24" :sm="12" :lg="6">
                <el-card shadow="never" class="metric-card disk-card">
                  <div class="metric-header"><el-icon><Folder /></el-icon><span>磁盘使用</span></div>
                  <div class="metric-value">{{ formatBytesOrNA(diskInfo.used) }}</div>
                  <div class="metric-sub"><span>已用</span><span class="divider">/</span><span>{{ formatBytesOrNA(diskInfo.total) }}</span></div>
                  <div class="metric-gauge">
                    <el-progress :percentage="diskInfo.percent" :stroke-width="6" :show-text="false" :color="getUsageColor(diskInfo.percent)" />
                  </div>
                  <div class="metric-label">可用: {{ diskInfo.total > 0 ? formatBytes(diskInfo.available) : 'N/A' }}</div>
                </el-card>
              </el-col>

              <el-col :xs="24" :sm="12" :lg="6">
                <el-card shadow="never" class="metric-card gpu-card">
                  <div class="metric-header"><el-icon><Star /></el-icon><span>GPU 使用</span></div>
                  <div class="metric-value">{{ formatPercent(gpuInfo.used) }}</div>
                  <div class="metric-sub"><span>显存: {{ gpuMemoryDisplay }}</span></div>
                  <div class="metric-gauge">
                    <el-progress :percentage="gpuInfo.used || 0" :stroke-width="6" :show-text="false" :color="getUsageColor(gpuInfo.used)" />
                  </div>
                  <div class="metric-label">型号: {{ gpuModelDisplay }}</div>
                </el-card>
              </el-col>
            </el-row>

            <el-row :gutter="20" class="margin-bottom-lg">
              <el-col :span="24">
                <el-card shadow="never" class="info-card">
                  <template #header>
                    <div class="card-header"><el-icon><Monitor /></el-icon><span>服务器信息</span></div>
                  </template>
                  <div class="info-groups">
                    <section class="info-group">
                      <div class="info-group-title">基础硬件</div>
                      <div class="server-info-grid">
                        <div class="info-item"><span class="info-label">操作系统</span><span class="info-value">{{ selectedServerInfo.os || 'Unknown' }}</span></div>
                        <div class="info-item"><span class="info-label">CPU 型号</span><span class="info-value">{{ selectedServerInfo.cpuModel || 'Unknown' }}</span></div>
                        <div class="info-item"><span class="info-label">CPU 物理核心</span><span class="info-value">{{ selectedServerInfo.cpuCores || 0 }} 核</span></div>
                        <div class="info-item"><span class="info-label">CPU 逻辑核心</span><span class="info-value">{{ formatLogicalCores(selectedServerInfo) }} 线程</span></div>
                        <div class="info-item"><span class="info-label">总内存</span><span class="info-value">{{ formatBytesOrNA(memoryInfo.total) }}</span></div>
                        <div class="info-item"><span class="info-label">进程数</span><span class="info-value">{{ formatCount(selectedServerInfo.processCount) }}</span></div>
                        <div class="info-item"><span class="info-label">GPU</span><span class="info-value">{{ selectedServerInfo.gpuModel || 'N/A' }}</span></div>
                        <div class="info-item"><span class="info-label">磁盘</span><span class="info-value">{{ diskInfo.devices || 'N/A' }}</span></div>
                        <div class="info-item"><span class="info-label">CPU 温度</span><span class="info-value">{{ formatTemperature(selectedServerInfo.cpuTemperature) }}</span></div>
                        <div class="info-item"><span class="info-label">GPU 温度</span><span class="info-value">{{ formatTemperature(selectedServerInfo.gpuTemperature) }}</span></div>
                        <div class="info-item"><span class="info-label">GPU 功耗</span><span class="info-value">{{ formatPower(selectedServerInfo.gpuPower) }}</span></div>
                      </div>
                    </section>

                    <section class="info-group">
                      <div class="info-group-title">节点实例</div>
                      <div class="server-info-grid">
                        <div class="info-item"><span class="info-label">实例 ID</span><span class="info-value">{{ currentServerId || 'N/A' }}</span></div>
                        <div class="info-item"><span class="info-label">实例名称</span><span class="info-value">{{ currentServerName || 'N/A' }}</span></div>
                        <div class="info-item"><span class="info-label">运行状态</span><span class="info-value">{{ serverOnline ? '运行中' : '离线' }}</span></div>
                        <div class="info-item"><span class="info-label">数据源</span><span class="info-value">{{ selectedServerMeta.prometheusUrl || selectedNode?.prometheusUrl || '全局默认' }}</span></div>
                        <div class="info-item"><span class="info-label">创建时间</span><span class="info-value">{{ formatDateTime(selectedServerMeta.createdAt) }}</span></div>
                        <div class="info-item"><span class="info-label">最近在线</span><span class="info-value">{{ formatDateTime(selectedServerMeta.lastOnlineTime || selectedSnapshot.recordedAt || selectedSnapshot.timestamp) }}</span></div>
                        <div class="info-item wide"><span class="info-label">备注</span><span class="info-value">{{ selectedServerMeta.remark || 'N/A' }}</span></div>
                      </div>
                    </section>

                    <section class="info-group">
                      <div class="info-group-title">云资源扩展</div>
                      <div class="server-info-grid">
                        <div class="info-item"><span class="info-label">公网 IP</span><span class="info-value">{{ selectedServerMeta.publicIp || selectedServerMeta.publicIP || 'N/A' }}</span></div>
                        <div class="info-item"><span class="info-label">私网 IP</span><span class="info-value">{{ selectedServerMeta.privateIp || selectedServerMeta.privateIP || 'N/A' }}</span></div>
                        <div class="info-item"><span class="info-label">地域</span><span class="info-value">{{ selectedServerMeta.region || 'N/A' }}</span></div>
                        <div class="info-item"><span class="info-label">规格族</span><span class="info-value">{{ selectedServerMeta.instanceFamily || selectedServerMeta.flavor || 'N/A' }}</span></div>
                        <div class="info-item wide"><span class="info-label">镜像</span><span class="info-value">{{ selectedServerMeta.image || selectedServerMeta.imageName || 'N/A' }}</span></div>
                        <div class="info-item"><span class="info-label">到期时间</span><span class="info-value">{{ formatDateTime(selectedServerMeta.expireAt || selectedServerMeta.expiredAt || selectedServerMeta.expireTime) }}</span></div>
                      </div>
                    </section>
                  </div>
                </el-card>
              </el-col>
            </el-row>

            <el-row :gutter="20" class="margin-bottom-lg">
              <el-col :span="24">
                <el-card shadow="never" class="chart-card">
                  <template #header>
                    <div class="card-header">
                      <el-icon><TrendCharts /></el-icon>
                      <span>指标趋势</span>
                      <el-select v-model="selectedTrendMetric" size="small" style="margin-left: auto; width: 180px;">
                        <el-option
                          v-for="option in trendMetricOptions"
                          :key="option.value"
                          :label="option.label"
                          :value="option.value"
                        />
                      </el-select>
                    </div>
                  </template>
                  <div v-loading="contentLoading" style="height: 280px;">
                    <LineChart
                      v-if="trendChartData.length > 0"
                      :data="trendChartData"
                      :title="selectedTrendMeta.label"
                      :y-axis-name="selectedTrendMeta.unit"
                      height="260px"
                      :color="selectedTrendMeta.color"
                      :max-points="200"
                    />
                    <el-empty v-else description="暂无趋势数据" :image-size="80" />
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
                  <el-select v-model="period" size="small" style="margin-left: 12px; width: 100px;" @change="handlePeriodChange">
                    <el-option label="5分钟" value="5m" />
                    <el-option label="1小时" value="1h" />
                    <el-option label="24小时" value="24h" />
                    <el-option label="7天" value="7d" />
                  </el-select>
                  <el-button type="primary" size="small" style="margin-left: auto;" :loading="collecting" @click="collectNow">
                    <el-icon style="margin-right: 4px;"><Refresh /></el-icon>
                    立即采集
                  </el-button>
                </div>
              </template>

              <el-table v-loading="contentLoading" :data="historyData" style="width: 100%">
                <el-table-column label="时间" min-width="180" fixed>
                  <template #default="{ row }">
                    <div class="time-cell"><el-icon><Clock /></el-icon>{{ formatDateTime(row.recordedAt || row.timestamp) }}</div>
                  </template>
                </el-table-column>
                <el-table-column label="CPU" min-width="140" align="center">
                  <template #default="{ row }"><div class="usage-cell"><el-progress :percentage="row.cpuUsage || 0" :stroke-width="8" :show-text="false" :color="getUsageColor(row.cpuUsage)" /><span class="usage-text">{{ formatPercent(row.cpuUsage) }}</span></div></template>
                </el-table-column>
                <el-table-column label="内存" min-width="140" align="center">
                  <template #default="{ row }"><div class="usage-cell"><el-progress :percentage="row.memoryUsage || 0" :stroke-width="8" :show-text="false" :color="getUsageColor(row.memoryUsage)" /><span class="usage-text">{{ formatPercent(row.memoryUsage) }}</span></div></template>
                </el-table-column>
                <el-table-column label="磁盘" min-width="140" align="center">
                  <template #default="{ row }"><div class="usage-cell"><el-progress :percentage="row.diskUsage || 0" :stroke-width="8" :show-text="false" :color="getUsageColor(row.diskUsage)" /><span class="usage-text">{{ formatPercent(row.diskUsage) }}</span></div></template>
                </el-table-column>
                <el-table-column label="GPU" min-width="140" align="center">
                  <template #default="{ row }"><div class="usage-cell"><el-progress :percentage="row.gpuUsage || 0" :stroke-width="8" :show-text="false" :color="getUsageColor(row.gpuUsage)" /><span class="usage-text">{{ formatPercent(row.gpuUsage) }}</span></div></template>
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
              <div class="config-switch-desc">开启后，平台会从配置的 Prometheus 实例拉取节点 CPU、内存、磁盘与 GPU 指标。</div>
            </div>
            <el-switch v-model="prometheusConfig.enabled" />
          </div>
        </el-form-item>

        <el-form-item label="自动采集任务">
          <div class="config-switch-row">
            <div>
              <div class="config-switch-title">后台定时自动采集硬件数据</div>
              <div class="config-switch-desc">默认开启，每分钟采集一次。修改后需要重启后端服务才会生效。</div>
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
            <div class="item-desc">当前页面会按这个频率刷新节点快照。</div>
          </div>
        </div>
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
import { useRoute, useRouter } from 'vue-router';
import {
  ArrowLeft, ArrowRight, CircleClose, Clock, Coin, Connection, Cpu,
  Folder, List, Loading, Menu, Monitor, More, Plus, Refresh, Search, Star, TrendCharts
} from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import LineChart from '@/components/LineChart.vue';
import {
  collectServerHardware, createServerInfo, deleteServerInfo, getPrometheusConfig,
  getPrometheusServerStatus, getServerHardwareHistory, getServerHardwareTrend, getServerInfoList, getServerNodes,
  getSystemProperties, testPrometheusConnection, updatePrometheusConfig, updateServerInfo,
  updateSystemProperties
} from '@/api/monitor';
import { ROUTES } from '@/router/routes';

const route = useRoute();
const router = useRouter();

const loading = ref(false);
const collecting = ref(false);
const period = ref('24h');
const selectedTrendMetric = ref('cpuUsage');
const isMobile = ref(false);
const sessionPaneCollapsed = ref(true);
const serverNodes = ref([]);
const serverInfoMap = ref({});
const searchQuery = ref('');
const nodesLoading = ref(false);
const currentServerId = ref(String(route.params.serverId || ''));
const currentServerName = ref('');
const prometheusStatus = ref({ connected: false });
const serverOnline = ref(null);
const serverError = ref('');
const nodeSnapshots = ref({});
const trendData = ref([]);
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
const nodeForm = ref({ serverId: '', serverName: '', prometheusUrl: '', remark: '' });
const prometheusConfig = ref({ prometheusUrl: '', enabled: false, cacheTtl: 10, refreshInterval: 15 });
const hardwareAutoCollectEnabled = ref(true);
let refreshTimer = null;

const trendMetricOptions = [
  { value: 'cpuUsage', label: 'CPU 使用率', unit: '%', color: '#667eea' },
  { value: 'memoryUsage', label: '内存使用率', unit: '%', color: '#10b981' },
  { value: 'diskUsage', label: '磁盘使用率', unit: '%', color: '#f39c12' },
  { value: 'gpuUsage', label: 'GPU 使用率', unit: '%', color: '#ef4444' },
  { value: 'gpuMemoryUsage', label: 'GPU 显存使用率', unit: '%', color: '#8b5cf6' }
];

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
  serverId: node.id || '', serverName: node.name || node.id || '', timestamp: 0, recordedAt: '', online: null,
  errorMessage: '', cpuUsage: 0, memoryUsage: 0, diskUsage: 0, gpuUsage: 0, cpuCores: 0, cpuLogicalCores: 0,
  cpuModel: '', memoryTotal: 0, memoryUsed: 0, diskTotal: 0, diskUsed: 0, gpuModel: '', gpuMemory: '', gpuMemoryTotal: 0,
  gpuMemoryUsed: 0, os: '', uptime: '', processCount: 0, networkDownload: '', networkUpload: '', devices: '',
  cpuTemperature: null, gpuTemperature: null, gpuPower: null
});

const withTimeout = async (promise, timeoutMs = 6000, timeoutMessage = '请求超时') => {
  let timer = null;
  try {
    return await Promise.race([
      promise,
      new Promise((_, reject) => {
        timer = setTimeout(() => reject(new Error(timeoutMessage)), timeoutMs);
      })
    ]);
  } finally {
    if (timer) clearTimeout(timer);
  }
};

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
  return normalizedNodes.value.filter((node) => (node.name && node.name.toLowerCase().includes(q)) || (node.id && node.id.toLowerCase().includes(q)));
});
const selectedNode = computed(() => normalizedNodes.value.find((node) => node.id === currentServerId.value) || null);
const selectedSnapshot = computed(() => nodeSnapshots.value[currentServerId.value] || createEmptySnapshot(selectedNode.value || {}));
const selectedServerMeta = computed(() => serverInfoMap.value[currentServerId.value] || {});
const selectedServerInfo = computed(() => {
  const snapshot = selectedSnapshot.value || {};
  const meta = selectedServerMeta.value || {};
  const pickText = (...values) => {
    for (const value of values) {
      const text = String(value ?? '').trim();
      if (text && text.toLowerCase() !== 'unknown' && text.toLowerCase() !== 'n/a') return text;
    }
    return '';
  };
  const pickNumber = (...values) => {
    for (const value of values) {
      const n = Number(value);
      if (Number.isFinite(n) && n > 0) return n;
    }
    return 0;
  };
  return {
    ...snapshot,
    os: pickText(snapshot.os, meta.os),
    cpuModel: pickText(snapshot.cpuModel, meta.cpuModel),
    gpuModel: pickText(snapshot.gpuModel, meta.gpuModel),
    cpuCores: pickNumber(snapshot.cpuCores, meta.cpuCores),
    memoryTotal: pickNumber(snapshot.memoryTotal, meta.memoryTotal),
    diskTotal: pickNumber(snapshot.diskTotal, meta.diskTotal),
    gpuMemoryTotal: pickNumber(snapshot.gpuMemoryTotal, meta.gpuMemoryTotal)
  };
});
const memoryInfo = computed(() => {
  const total = Number(selectedServerInfo.value.memoryTotal) || 0;
  const used = Number(selectedServerInfo.value.memoryUsed) || 0;
  const percent = clampPercent(selectedServerInfo.value.memoryUsage);
  return { total, used, available: Math.max(total - used, 0), percent };
});
const diskInfo = computed(() => {
  const total = Number(selectedServerInfo.value.diskTotal) || 0;
  const used = Number(selectedServerInfo.value.diskUsed) || 0;
  const percent = clampPercent(selectedServerInfo.value.diskUsage);
  return { total, used, available: Math.max(total - used, 0), percent, devices: selectedServerInfo.value.devices || 'Root Disk' };
});
const gpuInfo = computed(() => {
  const memoryUsed = Number(selectedServerInfo.value.gpuMemoryUsed) || 0;
  const memoryTotal = Number(selectedServerInfo.value.gpuMemoryTotal) || 0;
  const rawUsage = Number(selectedServerInfo.value.gpuUsage);
  const hasUsageMetric = Number.isFinite(rawUsage) && rawUsage > 0;
  const estimatedUsage = memoryTotal > 0 ? (memoryUsed / memoryTotal) * 100 : 0;
  return {
    used: clampPercent(hasUsageMetric ? rawUsage : estimatedUsage),
    memoryUsed,
    memoryTotal,
    isEstimated: !hasUsageMetric && memoryTotal > 0
  };
});
const gpuModelDisplay = computed(() => {
  const value = String(selectedServerInfo.value.gpuModel || '').trim();
  if (!value || value.toLowerCase() === 'unknown' || value.toLowerCase() === 'n/a') return '未检测到 GPU';
  return value;
});
const gpuMemoryDisplay = computed(() => {
  const raw = String(selectedServerInfo.value.gpuMemory || '').trim();
  if (raw && raw.toLowerCase() !== 'unknown' && raw.toLowerCase() !== 'n/a') return raw;
  if (gpuInfo.value.memoryTotal > 0) {
    return `${formatBytes(gpuInfo.value.memoryUsed)} / ${formatBytes(gpuInfo.value.memoryTotal)}`;
  }
  return '无显存数据';
});
const selectedTrendMeta = computed(
  () => trendMetricOptions.find((item) => item.value === selectedTrendMetric.value) || trendMetricOptions[0]
);
const trendChartData = computed(() => {
  const key = selectedTrendMeta.value.value;
  return (trendData.value || []).map((item) => ({
    timestamp: item.timestamp,
    value: clampPercent(item?.[key])
  }));
});
const cpuUsagePercent = computed(() => clampPercent(selectedSnapshot.value.cpuUsage));
const hasCachedData = computed(() => {
  const snapshotTimestamp = Number(selectedSnapshot.value.timestamp) || 0;
  return snapshotTimestamp > 0 || trendData.value.length > 0 || historyData.value.length > 0;
});
const contentLoading = computed(() => loading.value && !(serverOnline.value === false && hasCachedData.value));
const lastSnapshotTime = computed(() => {
  const value = selectedSnapshot.value.recordedAt || selectedSnapshot.value.timestamp;
  return value ? formatDateTime(value, true) : '';
});
const periodToMs = {
  '5m': 5 * 60 * 1000,
  '1h': 60 * 60 * 1000,
  '24h': 24 * 60 * 60 * 1000,
  '7d': 7 * 24 * 60 * 60 * 1000
};
const getTimeWindowByPeriod = (value) => {
  const now = Date.now();
  const range = periodToMs[value] || periodToMs['24h'];
  return {
    startTime: now - range,
    endTime: now
  };
};

const fetchNodes = async () => {
  nodesLoading.value = true;
  try {
    const data = await withTimeout(
      getServerNodes(),
      4000,
      '节点列表请求超时'
    );
    serverNodes.value = (data || []).map(normalizeNode);
    if (!serverNodes.value.some((node) => node.id === currentServerId.value)) {
      router.replace(ROUTES.MONITOR.SERVER);
    }
  } catch (error) {
    console.warn('fetchNodes failed, keep previous nodes:', error);
  } finally {
    nodesLoading.value = false;
  }
};

const fetchServerMeta = async () => {
  try {
    const list = await withTimeout(
      getServerInfoList(),
      4000,
      '节点元数据请求超时'
    );
    const map = {};
    (list || []).forEach((item) => {
      if (item?.serverId) map[item.serverId] = item;
    });
    serverInfoMap.value = map;
  } catch (error) {
    console.warn('fetchServerMeta failed, keep previous metadata:', error);
  }
};

const buildSnapshotFromMetric = (metric = {}, node = {}) => {
  const pick = (...keys) => {
    for (const key of keys) {
      const value = metric?.[key];
      if (value !== undefined && value !== null && value !== '') return value;
    }
    return undefined;
  };

  const snapshot = createEmptySnapshot(node);
  snapshot.serverId = pick('serverId', 'server_id') || node.id || snapshot.serverId;
  snapshot.serverName = pick('serverName', 'server_name') || node.name || snapshot.serverName;
  snapshot.timestamp = Number(pick('timestamp')) || 0;
  snapshot.recordedAt = pick('recordedAt', 'recorded_at') || '';
  snapshot.online = typeof metric.online === 'boolean' ? metric.online : null;
  snapshot.errorMessage = pick('errorMessage', 'error_message', 'error') || '';
  snapshot.cpuUsage = Number(pick('cpuUsage', 'cpu_usage')) || 0;
  snapshot.memoryUsage = Number(pick('memoryUsage', 'memory_usage')) || 0;
  snapshot.diskUsage = Number(pick('diskUsage', 'disk_usage')) || 0;
  snapshot.gpuUsage = Number(pick('gpuUsage', 'gpu_usage')) || 0;
  snapshot.cpuCores = Number(pick('cpuCores', 'cpu_cores')) || 0;
  snapshot.cpuLogicalCores = Number(pick('cpuLogicalCores', 'cpu_logical_cores')) || 0;
  snapshot.cpuModel = pick('cpuModel', 'cpu_model') || '';
  snapshot.memoryTotal = Number(pick('memoryTotal', 'memory_total')) || 0;
  snapshot.memoryUsed = Number(pick('memoryUsed', 'memory_used')) || 0;
  snapshot.diskTotal = Number(pick('diskTotal', 'disk_total')) || 0;
  snapshot.diskUsed = Number(pick('diskUsed', 'disk_used')) || 0;
  snapshot.gpuModel = pick('gpuModel', 'gpu_model', 'gpuName', 'gpu_name') || '';
  snapshot.gpuMemory = pick('gpuMemory', 'gpu_memory') || '';
  snapshot.gpuMemoryTotal = Number(pick('gpuMemoryTotal', 'gpu_memory_total', 'gpuVramTotal', 'gpu_vram_total')) || 0;
  snapshot.gpuMemoryUsed = Number(pick('gpuMemoryUsed', 'gpu_memory_used', 'gpuVramUsed', 'gpu_vram_used')) || 0;
  snapshot.os = pick('os') || '';
  snapshot.uptime = pick('uptime') || '';
  snapshot.processCount = Number(pick('processCount', 'process_count')) || 0;
  snapshot.cpuTemperature = Number(pick('cpuTemperature', 'cpu_temperature')) || null;
  snapshot.gpuTemperature = Number(pick('gpuTemperature', 'gpu_temperature')) || null;
  snapshot.gpuPower = Number(pick('gpuPower', 'gpu_power', 'gpuPowerWatts', 'gpu_power_watts')) || null;
  snapshot.networkDownload = pick('networkDownload', 'network_download', 'networkReceiveRate', 'network_receive_rate') || '';
  snapshot.networkUpload = pick('networkUpload', 'network_upload', 'networkTransmitRate', 'network_transmit_rate') || '';
  snapshot.devices = pick('devices') || '';
  return snapshot;
};

const fetchNodeSnapshots = async (options = {}) => {
  const onlyCurrent = Boolean(options.onlyCurrent);
  const nodesToFetch = onlyCurrent
    ? normalizedNodes.value.filter((node) => node.id === currentServerId.value)
    : normalizedNodes.value;
  const previousSnapshots = nodeSnapshots.value || {};

  const results = await Promise.all(nodesToFetch.map(async (node) => {
    const previous = previousSnapshots[node.id];
    try {
      const data = await withTimeout(
        getServerHardwareHistory({ serverId: node.id, page: 0, size: 1 }),
        onlyCurrent ? 7000 : 3500,
        `节点 ${node.id} 采集超时`
      );
      const latest = data?.content?.[0];
      if (latest) {
        return [node.id, buildSnapshotFromMetric(latest, node)];
      }
      // No fresh data from backend: keep last snapshot to avoid blanking UI.
      return [node.id, previous || createEmptySnapshot(node)];
    } catch (error) {
      // Preserve previous snapshot for offline/timeout nodes, only annotate state.
      if (previous) {
        return [node.id, { ...previous, online: false, errorMessage: error.message || '请求失败' }];
      }
      return [node.id, { ...createEmptySnapshot(node), online: false, errorMessage: error.message || '请求失败' }];
    }
  }));
  nodeSnapshots.value = {
    ...previousSnapshots,
    ...Object.fromEntries(results)
  };
  await hydrateCurrentNodeSnapshot();
};

const hydrateCurrentNodeSnapshot = async () => {
  const serverId = currentServerId.value;
  if (!serverId) return;
  const snapshot = nodeSnapshots.value[serverId];
  if (!snapshot) return;

  const gpuModelText = String(snapshot.gpuModel || '').trim().toLowerCase();
  const gpuMemoryText = String(snapshot.gpuMemory || '').trim().toLowerCase();
  const cpuTemp = Number(snapshot.cpuTemperature);
  const gpuTemp = Number(snapshot.gpuTemperature);
  const gpuPower = Number(snapshot.gpuPower);
  const needsHydration = (Number(snapshot.memoryTotal) || 0) <= 0
    || (Number(snapshot.diskTotal) || 0) <= 0
    || !gpuModelText
    || gpuModelText === 'unknown'
    || gpuModelText === 'n/a'
    || ((Number(snapshot.gpuMemoryTotal) || 0) <= 0 && (!gpuMemoryText || gpuMemoryText === 'unknown' || gpuMemoryText === 'n/a'))
    || !Number.isFinite(cpuTemp)
    || !Number.isFinite(gpuTemp)
    || !Number.isFinite(gpuPower);
  if (!needsHydration) return;

  try {
    let realtime = await getPrometheusServerStatus(serverId);

    // Fallback: some environments bind status by datasource URL rather than node id.
    if (!realtime || realtime.online === false) {
      const fallbackServerId = String(selectedServerMeta.value?.prometheusUrl || '').trim();
      if (fallbackServerId && fallbackServerId !== serverId) {
        realtime = await getPrometheusServerStatus(fallbackServerId);
      }
    }

    if (!realtime || realtime.online === false) return;

    const next = { ...snapshot };
    const memoryTotal = Number(realtime.memoryTotal ?? realtime.memory_total) || 0;
    const diskTotal = Number(realtime.diskTotal ?? realtime.disk_total) || 0;
    const memoryUsage = Number(realtime.memoryUsage ?? realtime.memory_usage ?? realtime.memoryUsagePercent) || 0;
    const diskUsage = Number(realtime.diskUsage ?? realtime.disk_usage) || 0;
    const gpuMemoryTotal = Number(realtime.gpuMemoryTotal ?? realtime.gpu_memory_total ?? realtime.gpuVramTotal) || 0;
    const gpuMemoryUsed = Number(realtime.gpuMemoryUsed ?? realtime.gpu_memory_used ?? realtime.gpuVramUsed) || 0;
    const realtimeGpuUsage = Number(realtime.gpuUsage ?? realtime.gpu_usage) || 0;

    if ((Number(next.memoryTotal) || 0) <= 0 && memoryTotal > 0) next.memoryTotal = memoryTotal;
    if ((Number(next.diskTotal) || 0) <= 0 && diskTotal > 0) next.diskTotal = diskTotal;
    if ((Number(next.memoryUsed) || 0) <= 0 && (Number(next.memoryTotal) || 0) > 0 && memoryUsage >= 0) {
      next.memoryUsed = Math.round((Number(next.memoryTotal) || 0) * (memoryUsage / 100));
    }
    if ((Number(next.diskUsed) || 0) <= 0 && (Number(next.diskTotal) || 0) > 0 && diskUsage >= 0) {
      next.diskUsed = Math.round((Number(next.diskTotal) || 0) * (diskUsage / 100));
    }
    if ((Number(next.cpuCores) || 0) <= 0 && Number(realtime.cpuCores ?? realtime.cpu_cores) > 0) {
      next.cpuCores = Number(realtime.cpuCores ?? realtime.cpu_cores) || 0;
    }
    if ((Number(next.cpuLogicalCores) || 0) <= 0 && Number(realtime.cpuLogicalCores ?? realtime.cpu_logical_cores ?? realtime.threadCount) > 0) {
      next.cpuLogicalCores = Number(realtime.cpuLogicalCores ?? realtime.cpu_logical_cores ?? realtime.threadCount) || 0;
    }

    if ((Number(next.gpuMemoryTotal) || 0) <= 0 && gpuMemoryTotal > 0) next.gpuMemoryTotal = gpuMemoryTotal;
    if ((Number(next.gpuMemoryUsed) || 0) <= 0 && gpuMemoryUsed > 0) next.gpuMemoryUsed = gpuMemoryUsed;
    const currentGpuMemoryText = String(next.gpuMemory || '').trim().toLowerCase();
    if ((!currentGpuMemoryText || currentGpuMemoryText === 'n/a' || currentGpuMemoryText === 'unknown') && gpuMemoryTotal > 0) {
      const used = gpuMemoryUsed > 0 ? gpuMemoryUsed : 0;
      next.gpuMemory = `${formatBytes(used)} / ${formatBytes(gpuMemoryTotal)}`;
    }

    next.cpuModel = next.cpuModel || realtime.cpuModel || realtime.cpu_model || '';
    next.os = next.os || realtime.os || realtime.osName || realtime.os_name || '';
    if (realtime.processCount !== undefined || realtime.process_count !== undefined) {
      next.processCount = Number(realtime.processCount ?? realtime.process_count) || 0;
    }
    if (realtime.cpuTemperature !== undefined || realtime.cpu_temperature !== undefined) {
      const v = Number(realtime.cpuTemperature ?? realtime.cpu_temperature);
      next.cpuTemperature = Number.isFinite(v) && v > 0 ? v : null;
    }
    if (realtime.gpuTemperature !== undefined || realtime.gpu_temperature !== undefined) {
      const v = Number(realtime.gpuTemperature ?? realtime.gpu_temperature);
      next.gpuTemperature = Number.isFinite(v) && v > 0 ? v : null;
    }
    if (realtime.gpuPower !== undefined || realtime.gpu_power !== undefined || realtime.gpuPowerWatts !== undefined) {
      const v = Number(realtime.gpuPower ?? realtime.gpu_power ?? realtime.gpuPowerWatts);
      next.gpuPower = Number.isFinite(v) && v >= 0 ? v : null;
    }
    if (!next.devices) {
      next.devices = realtime.devices || realtime.diskDevices || realtime.disk_devices || next.devices || '';
    }
    const currentGpuModel = String(next.gpuModel || '').trim().toLowerCase();
    if (!currentGpuModel || currentGpuModel === 'unknown' || currentGpuModel === 'n/a') {
      next.gpuModel = realtime.gpuModel || realtime.gpu_model || realtime.gpuName || next.gpuModel || '';
    }
    if ((Number(next.gpuUsage) || 0) <= 0 && realtimeGpuUsage > 0) {
      next.gpuUsage = realtimeGpuUsage;
    }
    next.online = typeof next.online === 'boolean' ? next.online : Boolean(realtime.online);
    nodeSnapshots.value = { ...nodeSnapshots.value, [serverId]: next };
  } catch {
    // Ignore hydration failures; keep historical snapshot as-is.
  }
};

const fetchNodesAndSnapshots = async () => {
  await Promise.allSettled([fetchNodes(), fetchServerMeta()]);
  // Single node panel: only fetch selected node snapshot for faster refresh.
  await fetchNodeSnapshots({ onlyCurrent: true });
};

const fetchPrometheusStatus = async () => {
  try {
    const config = await withTimeout(
      getPrometheusConfig(),
      3500,
      'Prometheus 配置请求超时'
    );
    if (config) {
      prometheusConfig.value = { prometheusUrl: config.prometheusUrl || '', enabled: !!config.enabled, cacheTtl: config.cacheTtl || 10, refreshInterval: config.refreshInterval || 15 };
    }
    const properties = await withTimeout(
      getSystemProperties(),
      3500,
      '系统属性请求超时'
    );
    const rawEnabled = properties?.['orin.hardware.monitor.enabled'];
    hardwareAutoCollectEnabled.value = rawEnabled === undefined || rawEnabled === null || rawEnabled === ''
      ? true
      : String(rawEnabled).toLowerCase() === 'true';
    prometheusStatus.value.connected = !!(config && config.enabled);
  } catch {
    prometheusStatus.value.connected = false;
    hardwareAutoCollectEnabled.value = true;
  }
};

const syncSelectedServerState = () => {
  const snapshot = selectedSnapshot.value;
  const wasOnline = serverOnline.value;
  serverOnline.value = snapshot.online;
  serverError.value = snapshot.errorMessage || (snapshot.online === false ? '暂无数据' : '');
  if (snapshot.online === false && wasOnline !== false) {
    if (refreshTimer) { clearInterval(refreshTimer); refreshTimer = null; }
  } else if (snapshot.online === true && !refreshTimer) {
    restartRefreshTimer();
  }
};

const fetchTrendData = async () => {
  try {
    let points = await getServerHardwareTrend(period.value, currentServerId.value);
    if (!Array.isArray(points) || points.length === 0) {
      const fallback = await getServerHardwareHistory({ serverId: currentServerId.value, page: 0, size: 40 });
      points = (fallback?.content || []).slice().reverse();
    }
    trendData.value = (points || []).map((item) => ({
      timestamp: item.timestamp,
      cpuUsage: Number(item.cpuUsage) || 0,
      memoryUsage: Number(item.memoryUsage) || 0,
      diskUsage: Number(item.diskUsage) || 0,
      gpuUsage: Number(item.gpuUsage) || 0,
      gpuMemoryUsage: Number(item.gpuMemoryUsage) || 0
    }));
  } catch (error) {
    console.warn('fetchTrendData failed, keep previous trend data:', error);
  }
};

const fetchHistoryData = async () => {
  try {
    const window = getTimeWindowByPeriod(period.value);
    const data = await getServerHardwareHistory({
      serverId: currentServerId.value,
      page: page.value - 1,
      size: pageSize.value,
      startTime: window.startTime,
      endTime: window.endTime
    });
    historyData.value = data?.content || [];
    historyTotal.value = data?.totalElements || 0;
  } catch (error) {
    console.warn('fetchHistoryData failed, keep previous history data:', error);
  }
};

const handlePeriodChange = async () => {
  page.value = 1;
  await Promise.allSettled([fetchTrendData(), fetchHistoryData()]);
};

const fetchAllData = async () => {
  loading.value = true;
  try {
    await withTimeout(fetchNodesAndSnapshots(), 9000, '节点快照刷新超时');
    syncSelectedServerState();
    await withTimeout(fetchPrometheusStatus(), 5000, '监控配置刷新超时');
    if (serverOnline.value === false) {
      return;
    }
    await Promise.allSettled([
      withTimeout(fetchTrendData(), 6000, '趋势数据刷新超时'),
      withTimeout(fetchHistoryData(), 6000, '历史数据刷新超时')
    ]);
  } catch (error) {
    console.error('fetchAllData failed:', error);
  } finally {
    loading.value = false;
  }
};

const refreshSnapshotsOnly = async () => {
  if (serverOnline.value === false) return;
  await fetchNodeSnapshots({ onlyCurrent: true });
  syncSelectedServerState();
};

const openNodeDialog = (node = null) => {
  if (node) {
    isEditingNode.value = true;
    nodeForm.value = { serverId: node.id, serverName: node.name || '', prometheusUrl: node.prometheusUrl || '', remark: node.remark || '' };
  } else {
    isEditingNode.value = false;
    nodeForm.value = { serverId: generateNodeId(), serverName: '', prometheusUrl: '', remark: '' };
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

  if (!serverId) return ElMessage.warning('请输入节点ID');
  if (!isHttpUrl(prometheusUrl)) return ElMessage.warning('Prometheus URL 格式不正确');

  nodeDialogLoading.value = true;
  try {
    if (isEditingNode.value) {
      const existingInfo = await getServerInfoList();
      const existing = existingInfo.find((item) => item.serverId === serverId);
      if (existing) {
        await updateServerInfo({ ...existing, serverName, prometheusUrl, remark });
      }
      ElMessage.success('节点更新成功');
    } else {
      await createServerInfo({ serverId, serverName, prometheusUrl, remark, online: false });
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
  if (command === 'edit') return openNodeDialog(node);
  if (command === 'delete') {
    ElMessageBox.confirm(`确定要删除节点 "${node.name || node.id}" 吗?`, '确认删除', { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' })
      .then(async () => {
        await deleteServerInfo(node.id);
        ElMessage.success('节点已删除');
        if (currentServerId.value === node.id) router.replace(ROUTES.MONITOR.SERVER);
        await fetchAllData();
      })
      .catch(() => {});
  }
};

const changeServer = (node) => {
  if (!node) return;
  sessionPaneCollapsed.value = true;
  router.push(ROUTES.MONITOR.SERVER_NODE.replace(':serverId', encodeURIComponent(node.id)));
};

const goBackToOverview = () => router.push(ROUTES.MONITOR.SERVER);

const openOverviewFromList = () => {
  sessionPaneCollapsed.value = true;
  goBackToOverview();
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
  } finally {
    configSaving.value = false;
  }
};

const testPrometheusSource = async () => {
  configTesting.value = true;
  try {
    const result = await testPrometheusConnection();
    if (result.online) ElMessage.success('连接成功，Prometheus 响应正常');
    else ElMessage.warning(`连接失败: ${result.error || '无法获取指标'}`);
  } finally {
    configTesting.value = false;
  }
};

const clampPercent = (value) => Math.max(0, Math.min(100, Number((Number(value) || 0).toFixed(1))));
const formatPercent = (value) => `${clampPercent(value).toFixed(1)}%`;
const formatCount = (value) => {
  const n = Number(value);
  return Number.isFinite(n) && n >= 0 ? n : 'N/A';
};
const formatLogicalCores = (snapshot = {}) => {
  const logical = Number(snapshot.cpuLogicalCores);
  if (Number.isFinite(logical) && logical > 0) return logical;
  const physical = Number(snapshot.cpuCores);
  return Number.isFinite(physical) && physical > 0 ? physical : 'N/A';
};
const formatTemperature = (value) => {
  const n = Number(value);
  return Number.isFinite(n) && n > 0 ? `${n.toFixed(1)} °C` : 'N/A';
};
const formatPower = (value) => {
  const n = Number(value);
  return Number.isFinite(n) && n >= 0 ? `${n.toFixed(1)} W` : 'N/A';
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
  const date = typeof value === 'number' || /^\d+$/.test(String(value)) ? new Date(Number(value)) : new Date(String(value).replace('T', ' '));
  if (Number.isNaN(date.getTime())) return String(value);
  return compact ? date.toLocaleString('zh-CN', { hour12: false }) : date.toLocaleString('zh-CN');
};
const getUsageColor = (value) => {
  const val = Number(value) || 0;
  if (val > 80) return '#e74c3c';
  if (val > 60) return '#f39c12';
  return '#27ae60';
};
const getNodeStatusText = (snapshot) => (!snapshot || snapshot.online === null ? '待采集' : snapshot.online ? '在线' : '离线');
const getNodeStatusType = (snapshot) => (!snapshot || snapshot.online === null ? 'info' : snapshot.online ? 'success' : 'danger');
const getNodeStatusClass = (snapshot) => (!snapshot || snapshot.online === null ? 'pending' : snapshot.online ? 'online' : 'offline');

const updateIsMobile = () => {
  if (typeof window === 'undefined') return;
  isMobile.value = window.innerWidth <= 768;
};
const restartRefreshTimer = () => {
  if (refreshTimer) clearInterval(refreshTimer);
  const intervalSeconds = Number(prometheusConfig.value.refreshInterval) || 30;
  refreshTimer = window.setInterval(async () => { await refreshSnapshotsOnly(); }, Math.max(intervalSeconds, 5) * 1000);
};

watch(() => route.params.serverId, async (serverId) => {
  currentServerId.value = String(serverId || '');
  page.value = 1;
  await Promise.all([fetchTrendData(), fetchHistoryData()]);
  syncSelectedServerState();
}, { immediate: false });

watch(selectedNode, (node) => {
  currentServerName.value = node?.name || currentServerId.value;
}, { immediate: true });

onMounted(async () => {
  updateIsMobile();
  window.addEventListener('resize', updateIsMobile);
  await fetchAllData();
  restartRefreshTimer();
});

onUnmounted(() => {
  window.removeEventListener('resize', updateIsMobile);
  if (refreshTimer) clearInterval(refreshTimer);
});
</script>

<style scoped>
@import './server-monitor-shared.css';

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

.info-groups {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.info-group {
  border: 1px solid #eef2f7;
  border-radius: 12px;
  padding: 12px;
  background: #fcfdff;
}

.info-group-title {
  font-size: 13px;
  font-weight: 600;
  color: #475569;
  margin-bottom: 10px;
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
