<template>
  <div class="mail-system-container">
    <PageHeader
      title="邮件中心"
      description="管理邮件服务配置、模板与发送记录"
      icon="Message"
    />

    <!-- 首屏骨架屏 -->
    <StateView
      v-if="pageState === 'loading'"
      state="loading"
      :loading-text="'正在加载邮件中心...'"
      :show-skeleton="true"
      :skeleton-rows="5"
      fullscreen
    />

    <!-- 错误状态 -->
    <StateView
      v-else-if="pageState === 'error'"
      state="error"
      error-title="加载失败"
      error-description="无法获取邮件配置信息，请检查网络连接"
      :show-go-back="true"
      @retry="loadMailConfig"
    />

    <!-- 正常内容 -->
    <template v-else>
      <!-- 统计卡片 -->
      <div class="stats-cards">
        <div class="stat-card stat-card-with-actions" :class="{ 'stat-success': mailConnected, 'stat-warning': !mailConnected }">
          <div class="stat-main">
            <div class="stat-icon">
              <el-icon :size="28">
                <Message />
              </el-icon>
            </div>
            <div class="stat-content">
              <div class="stat-value">
                {{ mailConnected ? '已连接' : '未配置' }}
              </div>
              <div class="stat-label">
                服务状态
              </div>
            </div>
            <div class="stat-indicator" />
          </div>
          <div class="stat-actions">
            <el-button
              type="info"
              size="small"
              :icon="Bell"
              @click="goToNotificationChannels"
            >
              告警通知
            </el-button>
            <el-button
              size="small"
              :type="mailConnected ? 'default' : 'primary'"
              :icon="Plus"
              @click="activeTab = 'config'"
            >
              {{ mailConnected ? '修改配置' : '立即配置' }}
            </el-button>
          </div>
        </div>
      
        <div class="stat-card">
          <div class="stat-icon stat-icon-blue">
            <el-icon :size="28">
              <Document />
            </el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">
              {{ templateCount }}
            </div>
            <div class="stat-label">
              邮件模板
            </div>
          </div>
        </div>
      
        <div class="stat-card">
          <div class="stat-icon stat-icon-green">
            <el-icon :size="28">
              <CircleCheck />
            </el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">
              {{ stats.successToday }}
            </div>
            <div class="stat-label">
              今日成功
            </div>
          </div>
        </div>
      
        <div class="stat-card">
          <div class="stat-icon stat-icon-orange">
            <el-icon :size="28">
              <Warning />
            </el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">
              {{ stats.failedToday }}
            </div>
            <div class="stat-label">
              今日失败
            </div>
          </div>
        </div>
      </div>

      <!-- 主要内容区 -->
      <div class="content-wrapper">
        <!-- 左侧：快速操作与状态 -->
        <div class="left-panel">
          <!-- 服务状态卡片 -->
          <el-card class="panel-card status-card" shadow="hover">
            <template #header>
              <div class="card-header">
                <span><el-icon><InfoFilled /></el-icon> 服务状态</span>
                <el-tag :type="mailConnected ? 'success' : 'warning'" size="small">
                  {{ mailConnected ? '运行中' : '待配置' }}
                </el-tag>
              </div>
            </template>
          
            <div v-if="mailConfig" class="status-content">
              <div class="status-item">
                <span class="status-label">发送方式</span>
                <span class="status-value">
                  <el-tag size="small" :type="mailConfig.mailerType === 'resend' ? 'success' : mailConfig.mailerType === 'mailersend' ? 'primary' : 'info'">
                    {{ mailConfig.mailerType === 'resend' ? 'Resend' : mailConfig.mailerType === 'mailersend' ? 'MailerSend API' : 'SMTP' }}
                  </el-tag>
                </span>
              </div>
              <div class="status-item">
                <span class="status-label">发件邮箱</span>
                <span class="status-value">{{ mailConfig.fromEmail || '-' }}</span>
              </div>
              <div class="status-item">
                <span class="status-label">发件人</span>
                <span class="status-value">{{ mailConfig.fromName || '-' }}</span>
              </div>
              <div class="status-item">
                <span class="status-label">配置时间</span>
                <span class="status-value">{{ formatDate(mailConfig.updatedAt) }}</span>
              </div>
            </div>
          
            <el-empty v-else description="暂无配置" :image-size="80" />
          
            <div class="status-actions">
              <el-button
                v-if="mailConnected"
                type="primary"
                plain
                size="small"
                @click="testConnection"
              >
                <el-icon><Promotion /></el-icon> 测试连接
              </el-button>
            </div>
          </el-card>

          <!-- 快捷操作 -->
          <el-card class="panel-card" shadow="hover">
            <template #header>
              <div class="card-header">
                <span><el-icon><Lightning /></el-icon> 快捷操作</span>
              </div>
            </template>
          
            <div class="quick-actions">
              <div class="quick-action" @click="openQuickSend">
                <el-icon :size="24">
                  <EditPen />
                </el-icon>
                <span>发送测试邮件</span>
              </div>
              <div class="quick-action" @click="activeTab = 'templates'">
                <el-icon :size="24">
                  <Document />
                </el-icon>
                <span>管理模板</span>
              </div>
              <div class="quick-action" @click="activeTab = 'logs'">
                <el-icon :size="24">
                  <List />
                </el-icon>
                <span>发送日志</span>
              </div>
              <div class="quick-action" @click="activeTab = 'config'">
                <el-icon :size="24">
                  <Setting />
                </el-icon>
                <span>系统配置</span>
              </div>
            </div>
          </el-card>
        </div>

        <!-- 右侧：标签页内容 -->
        <div class="right-panel">
          <el-tabs v-model="activeTab" class="config-tabs">
            <!-- 邮件配置 -->
            <el-tab-pane label="配置管理" name="config">
              <el-card class="config-card">
                <template #header>
                  <div class="card-header">
                    <span><el-icon><Setting /></el-icon> 邮件服务配置</span>
                  </div>
                </template>
              
                <!-- 配置步骤指示器 -->
                <div class="config-steps">
                  <div class="step-item" :class="{ active: configStep === 1, completed: configStep > 1 }">
                    <div class="step-number">
                      1
                    </div>
                    <div class="step-text">
                      选择发送方式
                    </div>
                  </div>
                  <div class="step-line" :class="{ active: configStep > 1 }" />
                  <div class="step-item" :class="{ active: configStep === 2, completed: configStep > 2 }">
                    <div class="step-number">
                      2
                    </div>
                    <div class="step-text">
                      填写配置信息
                    </div>
                  </div>
                  <div class="step-line" :class="{ active: configStep > 2 }" />
                  <div class="step-item" :class="{ active: configStep === 3 }">
                    <div class="step-number">
                      3
                    </div>
                    <div class="step-text">
                      验证配置
                    </div>
                  </div>
                </div>
              
                <!-- 步骤1: 选择发送方式 -->
                <div v-show="configStep === 1" class="config-step-content">
                  <div class="mailer-selection">
                    <div 
                      class="mailer-option" 
                      :class="{ selected: mailConfigForm.mailerType === 'mailersend' }"
                      @click="selectMailer('mailersend')"
                    >
                      <div class="mailer-icon mailersend-icon">
                        <svg viewBox="0 0 24 24" width="40" height="40">
                          <path fill="currentColor" d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z" />
                        </svg>
                      </div>
                      <div class="mailer-info">
                        <h4>MailerSend API</h4>
                        <p>推荐 • 更快速、更可靠</p>
                      </div>
                      <el-icon v-if="mailConfigForm.mailerType === 'mailersend'" class="check-icon">
                        <CircleCheck />
                      </el-icon>
                    </div>

                    <div
                      class="mailer-option"
                      :class="{ selected: mailConfigForm.mailerType === 'smtp' }"
                      @click="selectMailer('smtp')"
                    >
                      <div class="mailer-icon smtp-icon">
                        <el-icon :size="40">
                          <MessageBox />
                        </el-icon>
                      </div>
                      <div class="mailer-info">
                        <h4>SMTP</h4>
                        <p>通用 • 兼容各种邮件服务</p>
                      </div>
                      <el-icon v-if="mailConfigForm.mailerType === 'smtp'" class="check-icon">
                        <CircleCheck />
                      </el-icon>
                    </div>

                    <div
                      class="mailer-option"
                      :class="{ selected: mailConfigForm.mailerType === 'resend' }"
                      @click="selectMailer('resend')"
                    >
                      <div class="mailer-icon resend-icon">
                        <svg viewBox="0 0 24 24" width="40" height="40">
                          <path fill="currentColor" d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5" />
                        </svg>
                      </div>
                      <div class="mailer-info">
                        <h4>Resend</h4>
                        <p>现代 • 开发者友好</p>
                      </div>
                      <el-icon v-if="mailConfigForm.mailerType === 'resend'" class="check-icon">
                        <CircleCheck />
                      </el-icon>
                    </div>
                  </div>
                
                  <div class="step-actions">
                    <el-button type="primary" :disabled="!mailConfigForm.mailerType" @click="configStep = 2">
                      下一步 <el-icon><ArrowRight /></el-icon>
                    </el-button>
                  </div>
                </div>
              
                <!-- 步骤2: 填写配置 -->
                <div v-show="configStep === 2" class="config-step-content">
                  <el-form :model="mailConfigForm" label-width="140px" class="config-form">
                    <!-- MailerSend 配置 -->
                    <template v-if="mailConfigForm.mailerType === 'mailersend'">
                      <el-form-item label="API Token" required>
                        <el-input 
                          v-model="mailConfigForm.apiKey" 
                          type="password" 
                          show-password 
                          placeholder="mls_xxxxxxxxxxxx"
                        >
                          <template #prefix>
                            <el-icon><Key /></el-icon>
                          </template>
                        </el-input>
                        <div class="form-tip">
                          <el-link href="https://www.mailersend.com/dashboard/api-tokens" target="_blank">
                            获取 API Token <el-icon><Link /></el-icon>
                          </el-link>
                        </div>
                      </el-form-item>
                    
                      <el-form-item label="发件人邮箱" required>
                        <el-input v-model="mailConfigForm.fromEmail" placeholder="your-domain@trial-xxxxx.mailersend.com">
                          <template #prefix>
                            <el-icon><Message /></el-icon>
                          </template>
                        </el-input>
                        <div class="form-tip">
                          在 MailerSend 域名设置中验证的邮箱地址
                        </div>
                      </el-form-item>
                    
                      <el-form-item label="发件人名称">
                        <el-input v-model="mailConfigForm.fromName" placeholder="ORIN 系统">
                          <template #prefix>
                            <el-icon><User /></el-icon>
                          </template>
                        </el-input>
                      </el-form-item>
                    </template>
                  
                    <!-- SMTP 配置 -->
                    <template v-if="mailConfigForm.mailerType === 'smtp'">
                      <el-form-item label="SMTP 服务器" required>
                        <el-input v-model="mailConfigForm.smtpHost" placeholder="smtp.mailersend.net">
                          <template #prefix>
                            <el-icon><Monitor /></el-icon>
                          </template>
                        </el-input>
                      </el-form-item>
                    
                      <el-form-item label="端口" required>
                        <el-select v-model="mailConfigForm.smtpPort" placeholder="选择端口" style="width: 100%;">
                          <el-option label="587 (TLS)" :value="587" />
                          <el-option label="465 (SSL)" :value="465" />
                          <el-option label="25 (无加密)" :value="25" />
                        </el-select>
                      </el-form-item>
                    
                      <el-form-item label="用户名">
                        <el-input v-model="mailConfigForm.smtpUsername" placeholder="username">
                          <template #prefix>
                            <el-icon><User /></el-icon>
                          </template>
                        </el-input>
                      </el-form-item>
                    
                      <el-form-item label="密码">
                        <el-input
                          v-model="mailConfigForm.smtpPassword"
                          type="password"
                          show-password
                          placeholder="密码"
                        >
                          <template #prefix>
                            <el-icon><Lock /></el-icon>
                          </template>
                        </el-input>
                      </el-form-item>
                    
                      <el-form-item label="发件人邮箱" required>
                        <el-input v-model="mailConfigForm.fromEmail" placeholder="noreply@yourdomain.com">
                          <template #prefix>
                            <el-icon><Message /></el-icon>
                          </template>
                        </el-input>
                      </el-form-item>
                    
                      <el-form-item label="发件人名称">
                        <el-input v-model="mailConfigForm.fromName" placeholder="ORIN 系统">
                          <template #prefix>
                            <el-icon><User /></el-icon>
                          </template>
                        </el-input>
                      </el-form-item>

                      <el-form-item label="启用 SSL/TLS">
                        <el-switch v-model="mailConfigForm.sslEnabled" />
                      </el-form-item>

                      <!-- IMAP 接收配置 -->
                      <el-divider content-position="left">
                        IMAP 收件配置
                      </el-divider>

                      <el-form-item label="启用 IMAP">
                        <el-switch v-model="mailConfigForm.imapEnabled" />
                      </el-form-item>

                      <template v-if="mailConfigForm.imapEnabled">
                        <el-form-item label="IMAP 服务器">
                          <el-input v-model="mailConfigForm.imapHost" placeholder="imap.example.com">
                            <template #prefix>
                              <el-icon><Monitor /></el-icon>
                            </template>
                          </el-input>
                        </el-form-item>

                        <el-form-item label="IMAP 端口">
                          <el-input-number v-model="mailConfigForm.imapPort" :min="1" :max="65535" />
                          <div class="form-tip">
                            默认: 993 (SSL)
                          </div>
                        </el-form-item>

                        <el-form-item label="IMAP 用户名">
                          <el-input v-model="mailConfigForm.imapUsername" placeholder="your@email.com">
                            <template #prefix>
                              <el-icon><User /></el-icon>
                            </template>
                          </el-input>
                        </el-form-item>

                        <el-form-item label="IMAP 密码">
                          <el-input
                            v-model="mailConfigForm.imapPassword"
                            type="password"
                            show-password
                            placeholder="密码或应用专用密码"
                          >
                            <template #prefix>
                              <el-icon><Lock /></el-icon>
                            </template>
                          </el-input>
                          <div class="form-tip">
                            如使用 Gmail，请使用应用专用密码
                          </div>
                        </el-form-item>
                      </template>
                    </template>

                    <!-- Resend 配置 -->
                    <template v-if="mailConfigForm.mailerType === 'resend'">
                      <el-form-item label="API Token" required>
                        <el-input
                          v-model="mailConfigForm.apiKey"
                          type="password"
                          show-password
                          placeholder="re_xxxxx"
                        >
                          <template #prefix>
                            <el-icon><Key /></el-icon>
                          </template>
                        </el-input>
                        <div class="form-tip">
                          <el-link href="https://resend.com/api-keys" target="_blank">
                            获取 API Token <el-icon><Link /></el-icon>
                          </el-link>
                        </div>
                      </el-form-item>

                      <el-form-item label="发件人邮箱" required>
                        <el-input v-model="mailConfigForm.fromEmail" placeholder="noreply@yourdomain.com">
                          <template #prefix>
                            <el-icon><Message /></el-icon>
                          </template>
                        </el-input>
                        <div class="form-tip">
                          在 Resend 域名设置中验证的邮箱地址
                        </div>
                      </el-form-item>

                      <el-form-item label="发件人名称">
                        <el-input v-model="mailConfigForm.fromName" placeholder="ORIN 系统">
                          <template #prefix>
                            <el-icon><User /></el-icon>
                          </template>
                        </el-input>
                      </el-form-item>
                    </template>
                  </el-form>
                
                  <div class="step-actions">
                    <el-button @click="configStep = 1">
                      <el-icon><ArrowLeft /></el-icon> 上一步
                    </el-button>
                    <el-button type="primary" :disabled="!canProceedStep2" @click="configStep = 3">
                      下一步 <el-icon><ArrowRight /></el-icon>
                    </el-button>
                  </div>
                </div>
              
                <!-- 步骤3: 验证配置 -->
                <div v-show="configStep === 3" class="config-step-content">
                  <div class="verify-section">
                    <div class="verify-info">
                      <el-icon :size="48" color="var(--success-500)">
                        <CircleCheckFilled />
                      </el-icon>
                      <h3>配置完成</h3>
                      <p>请确认以下配置信息无误</p>
                    </div>
                  
                    <div class="verify-details">
                      <div class="verify-item">
                        <span class="verify-label">发送方式</span>
                        <span class="verify-value">{{ mailConfigForm.mailerType === 'mailersend' ? 'MailerSend API' : mailConfigForm.mailerType === 'resend' ? 'Resend' : 'SMTP' }}</span>
                      </div>
                      <div class="verify-item">
                        <span class="verify-label">发件邮箱</span>
                        <span class="verify-value">{{ mailConfigForm.fromEmail }}</span>
                      </div>
                      <div class="verify-item">
                        <span class="verify-label">发件人</span>
                        <span class="verify-value">{{ mailConfigForm.fromName || 'ORIN 系统' }}</span>
                      </div>
                    </div>
                  
                    <div class="verify-actions">
                      <el-button @click="configStep = 2">
                        修改配置
                      </el-button>
                      <el-button type="success" :loading="testingConnection" @click="saveAndTest">
                        <el-icon><Check /></el-icon> 保存配置
                      </el-button>
                    </div>
                  </div>
                </div>
              </el-card>
            </el-tab-pane>

            <!-- 发送邮件 -->
            <el-tab-pane label="发送邮件" name="send">
              <el-card class="send-card">
                <template #header>
                  <div class="card-header">
                    <span><el-icon><Promotion /></el-icon> 发送邮件</span>
                  </div>
                </template>

                <el-form :model="sendMailForm" label-width="100px" class="send-form">
                  <el-form-item label="收件人" required>
                    <el-input v-model="sendMailForm.to" placeholder="请输入收件人邮箱，多个用逗号分隔">
                      <template #prefix>
                        <el-icon><Message /></el-icon>
                      </template>
                    </el-input>
                  </el-form-item>

                  <el-form-item label="主题" required>
                    <el-input v-model="sendMailForm.subject" placeholder="请输入邮件主题">
                      <template #prefix>
                        <el-icon><Edit /></el-icon>
                      </template>
                    </el-input>
                  </el-form-item>

                  <el-form-item label="内容" required>
                    <el-input
                      v-model="sendMailForm.content"
                      type="textarea"
                      :rows="15"
                      placeholder="请输入邮件内容"
                    />
                  </el-form-item>

                  <el-form-item>
                    <el-button type="primary" :loading="sendingMail" @click="sendMail">
                      <el-icon><Promotion /></el-icon> 发送邮件
                    </el-button>
                  </el-form-item>
                </el-form>
              </el-card>
            </el-tab-pane>

            <!-- 模板管理 -->
            <el-tab-pane label="模板管理" name="templates">
              <div class="templates-header">
                <el-button type="primary" :icon="Plus" @click="openTemplateDialog()">
                  新建模板
                </el-button>
                <el-input
                  v-model="templateSearch"
                  placeholder="搜索模板..."
                  :prefix-icon="Search"
                  style="width: 240px;"
                  clearable
                />
              </div>
            
              <el-table 
                v-loading="templatesLoading" 
                :data="filteredTemplates"
                class="templates-table"
              >
                <el-table-column prop="name" label="模板名称" min-width="150">
                  <template #default="{ row }">
                    <div class="template-name">
                      <el-icon><Document /></el-icon>
                      <span>{{ row.name }}</span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column prop="code" label="模板代码" width="150">
                  <template #default="{ row }">
                    <code class="template-code">{{ row.code }}</code>
                  </template>
                </el-table-column>
                <el-table-column
                  prop="subject"
                  label="邮件主题"
                  min-width="200"
                  show-overflow-tooltip
                />
                <el-table-column
                  prop="isDefault"
                  label="默认"
                  width="80"
                  align="center"
                >
                  <template #default="{ row }">
                    <el-tag v-if="row.isDefault" type="success" size="small">
                      默认
                    </el-tag>
                    <span v-else class="text-muted">-</span>
                  </template>
                </el-table-column>
                <el-table-column
                  prop="enabled"
                  label="状态"
                  width="80"
                  align="center"
                >
                  <template #default="{ row }">
                    <el-switch 
                      v-model="row.enabled" 
                      :loading="row.switching"
                      @change="toggleTemplate(row)"
                    />
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="160" fixed="right">
                  <template #default="{ row }">
                    <el-button type="primary" link @click="openTemplateDialog(row)">
                      编辑
                    </el-button>
                    <el-button type="primary" link @click="previewTemplateFn(row)">
                      预览
                    </el-button>
                    <el-button type="danger" link @click="deleteTemplate(row)">
                      删除
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>

            <!-- 发送日志 -->
            <el-tab-pane label="发送日志" name="logs">
              <el-card class="logs-card">
                <template #header>
                  <div class="card-header">
                    <span><el-icon><List /></el-icon> 发送记录</span>
                    <div class="logs-filters">
                      <el-select
                        v-model="logFilters.status"
                        placeholder="状态筛选"
                        clearable
                        style="width: 120px;"
                      >
                        <el-option label="成功" value="SUCCESS" />
                        <el-option label="失败" value="FAILED" />
                      </el-select>
                      <el-date-picker
                        v-model="logFilters.dateRange"
                        type="daterange"
                        range-separator="至"
                        start-placeholder="开始日期"
                        end-placeholder="结束日期"
                        value-format="YYYY-MM-DD"
                        style="width: 240px;"
                      />
                      <el-button type="primary" @click="loadLogs">
                        筛选
                      </el-button>
                    </div>
                  </div>
                </template>
              
                <el-table v-loading="logsLoading" :data="mailLogs" class="logs-table">
                  <el-table-column prop="id" label="ID" width="60" />
                  <el-table-column
                    prop="subject"
                    label="主题"
                    min-width="200"
                    show-overflow-tooltip
                  />
                  <el-table-column
                    prop="recipients"
                    label="收件人"
                    min-width="150"
                    show-overflow-tooltip
                  />
                  <el-table-column prop="mailerType" label="方式" width="100">
                    <template #default="{ row }">
                      <el-tag :type="row.mailerType === 'resend' ? 'success' : row.mailerType === 'mailersend' ? 'primary' : 'info'" size="small">
                        {{ row.mailerType === 'resend' ? 'Resend' : row.mailerType === 'mailersend' ? 'MailerSend' : 'SMTP' }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column prop="status" label="状态" width="80">
                    <template #default="{ row }">
                      <div class="log-status" :class="'status-' + row.status.toLowerCase()">
                        <el-icon v-if="row.status === 'SUCCESS'">
                          <CircleCheck />
                        </el-icon>
                        <el-icon v-else-if="row.status === 'FAILED'">
                          <CircleClose />
                        </el-icon>
                        <el-icon v-else>
                          <Loading />
                        </el-icon>
                        {{ getStatusText(row.status) }}
                      </div>
                    </template>
                  </el-table-column>
                  <el-table-column
                    prop="errorMessage"
                    label="错误信息"
                    min-width="150"
                    show-overflow-tooltip
                  >
                    <template #default="{ row }">
                      <span v-if="row.errorMessage" class="error-text">{{ row.errorMessage }}</span>
                      <span v-else class="text-muted">-</span>
                    </template>
                  </el-table-column>
                  <el-table-column prop="createdAt" label="发送时间" width="160">
                    <template #default="{ row }">
                      {{ formatLogTime(row.createdAt) }}
                    </template>
                  </el-table-column>
                </el-table>
              
                <div class="pagination-wrapper">
                  <el-pagination
                    v-model:current-page="logPagination.page"
                    v-model:page-size="logPagination.size"
                    :total="logPagination.total"
                    :page-sizes="[10, 20, 50, 100]"
                    layout="total, sizes, prev, pager, next, jumper"
                    @size-change="loadLogs"
                    @current-change="loadLogs"
                  />
                </div>
              </el-card>
            </el-tab-pane>

            <!-- 收件箱 -->
            <el-tab-pane label="收件箱" name="inbox">
              <el-card class="inbox-card">
                <template #header>
                  <div class="card-header">
                    <span><el-icon><Message /></el-icon> 收件箱</span>
                    <div class="header-actions">
                      <el-button
                        type="primary"
                        size="small"
                        :icon="Refresh"
                        :loading="inboxLoading"
                        :disabled="!imapConfigured"
                        @click="fetchInboxEmails"
                      >
                        拉取邮件
                      </el-button>
                    </div>
                  </div>
                </template>

                <div v-if="!imapConfigured" class="empty-tip">
                  <el-alert type="warning" :closable="false">
                    <template #title>
                      IMAP 未配置
                    </template>
                    请在"配置管理"中启用 IMAP 并填写配置信息以接收邮件。
                  </el-alert>
                </div>

                <div v-else>
                  <div class="inbox-stats">
                    <span v-if="inboxUnreadCount > 0" class="unread-badge">
                      {{ inboxUnreadCount }} 未读
                    </span>
                  </div>

                  <el-table
                    v-loading="inboxLoading"
                    :data="inboxList"
                    style="width: 100%"
                    class="inbox-table"
                    @row-click="viewInboxMail"
                  >
                    <el-table-column width="40">
                      <template #default="{ row }">
                        <el-icon v-if="row.isStarred" color="#E6A23C">
                          <StarFilled />
                        </el-icon>
                      </template>
                    </el-table-column>
                    <el-table-column prop="fromName" label="发件人" width="180">
                      <template #default="{ row }">
                        <span :class="{ 'unread-row': !row.isRead }">
                          {{ row.fromName || row.fromEmail }}
                        </span>
                      </template>
                    </el-table-column>
                    <el-table-column prop="subject" label="主题" min-width="300">
                      <template #default="{ row }">
                        <span :class="{ 'unread-row': !row.isRead }">
                          {{ row.subject }}
                        </span>
                      </template>
                    </el-table-column>
                    <el-table-column prop="receivedAt" label="时间" width="180">
                      <template #default="{ row }">
                        {{ formatDateTime(row.receivedAt) }}
                      </template>
                    </el-table-column>
                    <el-table-column label="操作" width="100" fixed="right">
                      <template #default="{ row }">
                        <el-button
                          type="primary"
                          link
                          size="small"
                          @click.stop="viewInboxMail(row)"
                        >
                          查看
                        </el-button>
                        <el-button
                          type="danger"
                          link
                          size="small"
                          @click.stop="deleteInboxMail(row.id)"
                        >
                          删除
                        </el-button>
                      </template>
                    </el-table-column>
                  </el-table>

                  <el-pagination
                    v-model:current-page="inboxPage"
                    v-model:page-size="inboxPageSize"
                    :total="inboxTotal"
                    :page-sizes="[10, 20, 50]"
                    layout="total, sizes, prev, pager, next, jumper"
                    @size-change="loadInbox"
                    @current-change="loadInbox"
                  />
                </div>
              </el-card>

              <!-- 邮件详情对话框 -->
              <el-dialog
                v-model="inboxDetailVisible"
                :title="currentInboxMail?.subject"
                width="70%"
                top="5vh"
              >
                <div v-if="currentInboxMail" class="mail-detail">
                  <div class="mail-header">
                    <div class="mail-from">
                      <strong>发件人:</strong> {{ currentInboxMail.fromName || currentInboxMail.fromEmail }}
                      &lt;{{ currentInboxMail.fromEmail }}&gt;
                    </div>
                    <div class="mail-time">
                      <strong>时间:</strong> {{ formatDateTime(currentInboxMail.receivedAt) }}
                    </div>
                  </div>
                  <el-divider />
                  <div class="mail-content" v-html="currentInboxMail.contentHtml || currentInboxMail.content" />
                </div>
              </el-dialog>
            </el-tab-pane>

            <!-- 发件箱 -->
            <el-tab-pane label="发件箱" name="sent">
              <el-card class="sent-card">
                <template #header>
                  <div class="card-header">
                    <span><el-icon><Promotion /></el-icon> 发件箱</span>
                  </div>
                </template>

                <el-table
                  v-loading="sentLoading"
                  :data="sentList"
                  style="width: 100%"
                >
                  <el-table-column prop="subject" label="主题" min-width="300">
                    <template #default="{ row }">
                      {{ row.subject }}
                    </template>
                  </el-table-column>
                  <el-table-column prop="recipients" label="收件人" width="250">
                    <template #default="{ row }">
                      {{ row.recipients }}
                    </template>
                  </el-table-column>
                  <el-table-column prop="status" label="状态" width="100">
                    <template #default="{ row }">
                      <el-tag :type="row.status === 'SUCCESS' ? 'success' : row.status === 'FAILED' ? 'danger' : 'warning'">
                        {{ row.status === 'SUCCESS' ? '成功' : row.status === 'FAILED' ? '失败' : '待发送' }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column prop="createdAt" label="发送时间" width="180">
                    <template #default="{ row }">
                      {{ formatDateTime(row.createdAt) }}
                    </template>
                  </el-table-column>
                  <el-table-column label="操作" width="100" fixed="right">
                    <template #default="{ row }">
                      <el-button
                        type="primary"
                        link
                        size="small"
                        @click="viewSentMail(row)"
                      >
                        查看
                      </el-button>
                    </template>
                  </el-table-column>
                </el-table>

                <el-pagination
                  v-model:current-page="sentPage"
                  v-model:page-size="sentPageSize"
                  :total="sentTotal"
                  :page-sizes="[10, 20, 50]"
                  layout="total, sizes, prev, pager, next, jumper"
                  @size-change="loadSent"
                  @current-change="loadSent"
                />
              </el-card>

              <!-- 发件箱邮件详情对话框 -->
              <el-dialog
                v-model="sentDetailVisible"
                :title="currentSentMail?.subject"
                width="70%"
                top="5vh"
              >
                <div v-if="currentSentMail" class="mail-detail">
                  <div class="mail-header">
                    <div class="mail-from">
                      <strong>收件人:</strong> {{ currentSentMail.recipients }}
                    </div>
                    <div class="mail-time">
                      <strong>发送时间:</strong> {{ formatDateTime(currentSentMail.createdAt) }}
                    </div>
                    <div class="mail-status">
                      <strong>状态:</strong>
                      <el-tag :type="currentSentMail.status === 'SUCCESS' ? 'success' : currentSentMail.status === 'FAILED' ? 'danger' : 'warning'">
                        {{ currentSentMail.status === 'SUCCESS' ? '成功' : currentSentMail.status === 'FAILED' ? '失败' : '待发送' }}
                      </el-tag>
                    </div>
                  </div>
                  <el-divider />
                  <div class="mail-content">
                    {{ currentSentMail.content }}
                  </div>
                </div>
              </el-dialog>
            </el-tab-pane>
          </el-tabs>
        </div>
      </div>

      <!-- 模板编辑对话框 -->
      <el-dialog 
        v-model="templateDialogVisible" 
        :title="templateForm.id ? '编辑模板' : '新建模板'" 
        width="700px"
        destroy-on-close
      >
        <el-form :model="templateForm" label-width="100px">
          <el-form-item label="模板名称" required>
            <el-input v-model="templateForm.name" placeholder="如：系统通知" />
          </el-form-item>
          <el-form-item label="模板代码" required>
            <el-input v-model="templateForm.code" placeholder="如：system_notification" :disabled="!!templateForm.id">
              <template #prefix>
                <el-tag size="small">
                  Code
                </el-tag>
              </template>
            </el-input>
            <div class="form-tip">
              唯一标识，建议使用英文下划线
            </div>
          </el-form-item>
          <el-form-item label="邮件主题" required>
            <el-input v-model="templateForm.subject" placeholder="【{{app_name}}】{{title}}" />
            <div class="form-tip">
              支持变量：{{ variable }}
            </div>
          </el-form-item>
          <el-form-item label="邮件内容" required>
            <el-input 
              v-model="templateForm.content" 
              type="textarea" 
              :rows="10" 
              placeholder="邮件正文内容，支持变量替换"
              class="content-editor"
            />
          </el-form-item>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="设为默认">
                <el-switch v-model="templateForm.isDefault" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="启用状态">
                <el-switch v-model="templateForm.enabled" />
              </el-form-item>
            </el-col>
          </el-row>
        
          <!-- 变量说明 -->
          <el-alert
            title="可用变量说明"
            type="info"
            :closable="false"
            class="variables-tip"
          >
            <template #default>
              <div class="variables-list">
                <code>{"{{ app_name }}"}</code> - 系统名称 &nbsp;
                <code>{"{{ code }}"}</code> - 验证码 &nbsp;
                <code>{"{{ username }}"}</code> - 用户名 &nbsp;
                <code>{"{{ time }}"}</code> - 当前时间 &nbsp;
                <code>{"{{ link }}"}</code> - 链接地址
              </div>
            </template>
          </el-alert>
        </el-form>
      
        <template #footer>
          <el-button @click="templateDialogVisible = false">
            取消
          </el-button>
          <el-button type="primary" :loading="savingTemplate" @click="saveTemplate">
            保存模板
          </el-button>
        </template>
      </el-dialog>

      <!-- 发送测试邮件对话框 -->
      <el-dialog v-model="testMailDialogVisible" title="发送测试邮件" width="500px">
        <el-form :model="testMailForm" label-width="100px">
          <el-form-item label="收件人" required>
            <el-input v-model="testMailForm.to" placeholder="请输入测试收件人邮箱" />
          </el-form-item>
          <el-form-item label="邮件类型">
            <el-select v-model="testMailForm.type" style="width: 100%;">
              <el-option label="验证码" value="verification" />
              <el-option label="系统通知" value="notification" />
              <el-option label="告警通知" value="alert" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="testMailForm.type === 'verification'" label="验证码">
            <el-input v-model="testMailForm.code" placeholder="填写测试验证码" />
          </el-form-item>
        </el-form>
      
        <template #footer>
          <el-button @click="testMailDialogVisible = false">
            取消
          </el-button>
          <el-button type="primary" :loading="sendingTest" @click="sendTestMail">
            发送测试邮件
          </el-button>
        </template>
      </el-dialog>

      <!-- 模板预览对话框 -->
      <el-dialog v-model="previewDialogVisible" title="模板预览" width="600px">
        <div class="preview-content">
          <div class="preview-subject">
            <strong>主题：</strong>{{ previewTemplate?.subject }}
          </div>
          <el-divider />
          <div class="preview-body" v-html="previewTemplate?.content?.replace(/\n/g, '<br>')" />
        </div>
      </el-dialog>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus, Search, Message, Document, CircleCheck, Warning,
  InfoFilled, Lightning, EditPen, List, Setting,
  Promotion, ArrowRight, ArrowLeft, Key, Link,
  User, Lock, Monitor, MessageBox, CircleCheckFilled,
  Check, CircleClose, Loading, Refresh, StarFilled, Bell
} from '@element-plus/icons-vue'
import request from '@/utils/request'
import PageHeader from '@/components/PageHeader.vue'
import StateView from '@/components/StateView.vue'
import { ROUTES } from '@/router/routes'

const router = useRouter()

const goToNotificationChannels = () => {
  router.push(ROUTES.CONTROL.NOTIFICATION_CHANNELS)
}

// 状态 - 默认显示收件箱，已配置时进入首页，未配置时显示配置
const activeTab = ref('inbox')

// 页面状态: idle | loading | success | error | empty
// 用于控制骨架屏和状态视图显示
const pageState = ref<'idle' | 'loading' | 'success' | 'error' | 'empty'>('loading')

const mailConnected = ref(false)
const templatesLoading = ref(false)
const logsLoading = ref(false)
const testingConnection = ref(false)
const savingTemplate = ref(false)
const sendingTest = ref(false)

// 配置步骤
const configStep = ref(1)

// 统计数据
const stats = reactive({
  successToday: 0,
  failedToday: 0
})

const templateCount = ref(0)

// 邮件配置
const mailConfig = ref(null)
const mailConfigForm = reactive({
  mailerType: 'mailersend',
  apiKey: '',
  smtpHost: 'smtp.mailersend.net',
  smtpPort: 587,
  smtpUsername: '',
  smtpPassword: '',
  fromEmail: '',
  fromName: 'ORIN 系统',
  sslEnabled: true,
  // IMAP 配置
  imapEnabled: false,
  imapHost: '',
  imapPort: 993,
  imapUsername: '',
  imapPassword: ''
})

// 计算属性：步骤2是否可以继续
const canProceedStep2 = computed(() => {
  if (mailConfigForm.mailerType === 'mailersend' || mailConfigForm.mailerType === 'resend') {
    return mailConfigForm.apiKey && mailConfigForm.fromEmail
  } else {
    return mailConfigForm.smtpHost && mailConfigForm.fromEmail
  }
})

// 模板相关
const templateSearch = ref('')
const mailTemplates = ref([])
const templateDialogVisible = ref(false)
const templateForm = reactive({
  id: null,
  name: '',
  code: '',
  subject: '',
  content: '',
  isDefault: false,
  enabled: true
})

// 日志相关
const mailLogs = ref([])
const logFilters = reactive({
  status: '',
  dateRange: null
})
const logPagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

// 测试邮件
const testMailDialogVisible = ref(false)
const testMailForm = reactive({
  to: '',
  type: 'verification',
  code: '123456'
})

// 发送邮件
const sendingMail = ref(false)
const sendMailForm = reactive({
  to: '',
  subject: '',
  content: ''
})

// 预览
const previewDialogVisible = ref(false)
const previewTemplate = ref(null)

// 收件箱相关
const inboxList = ref([])
const inboxLoading = ref(false)
const inboxPage = ref(1)
const inboxPageSize = ref(20)
const inboxTotal = ref(0)
const inboxUnreadCount = ref(0)
const imapConfigured = ref(false)
const inboxDetailVisible = ref(false)
const currentInboxMail = ref(null)

// 发件箱相关
const sentList = ref([])
const sentLoading = ref(false)
const sentPage = ref(1)
const sentPageSize = ref(20)
const sentTotal = ref(0)
const sentDetailVisible = ref(false)
const currentSentMail = ref(null)

// 计算筛选后的模板
const filteredTemplates = computed(() => {
  if (!templateSearch.value) return mailTemplates.value
  const search = templateSearch.value.toLowerCase()
  return mailTemplates.value.filter(t => 
    t.name?.toLowerCase().includes(search) || 
    t.code?.toLowerCase().includes(search) ||
    t.subject?.toLowerCase().includes(search)
  )
})

// 选择邮件服务商
const selectMailer = (type) => {
  mailConfigForm.mailerType = type
}

// 加载配置
const loadMailConfig = async () => {
  try {
    pageState.value = 'loading'
    const res = await request.get('/system/mail-config')
    if (res) {
      mailConfig.value = res
      mailConnected.value = res.enabled

      // 填充表单
      mailConfigForm.mailerType = res.mailerType || 'mailersend'
      if (res.mailerType === 'mailersend' || res.mailerType === 'resend') {
        mailConfigForm.apiKey = res.apiKey || ''
      } else {
        mailConfigForm.smtpHost = res.smtpHost || 'smtp.mailersend.net'
        mailConfigForm.smtpPort = res.smtpPort || 587
        mailConfigForm.smtpUsername = res.username || ''
        mailConfigForm.smtpPassword = res.password || ''
        mailConfigForm.sslEnabled = res.sslEnabled !== false
      }
      mailConfigForm.fromEmail = res.fromEmail || ''
      mailConfigForm.fromName = res.fromName || 'ORIN 系统'

      // 加载 IMAP 配置
      mailConfigForm.imapEnabled = res.imapEnabled || false
      mailConfigForm.imapHost = res.imapHost || ''
      mailConfigForm.imapPort = res.imapPort || 993
      mailConfigForm.imapUsername = res.imapUsername || ''
      mailConfigForm.imapPassword = res.imapPassword || ''

      // 根据连接状态决定默认显示的标签页（类似 Gmail）
      // 已配置 -> 收件箱，未配置 -> 配置页面
      activeTab.value = mailConnected.value ? 'inbox' : 'config'

      pageState.value = 'success'
    } else {
      // 无配置时默认显示配置页面
      activeTab.value = 'config'
      pageState.value = 'success'
    }
  } catch (e) {
    console.error('加载配置失败:', e)
    pageState.value = 'error'
  }
}

// 保存配置
const saveAndTest = async () => {
  testingConnection.value = true
  try {
    const config = {
      id: mailConfig.value?.id,
      mailerType: mailConfigForm.mailerType,
      apiKey: (mailConfigForm.mailerType === 'mailersend' || mailConfigForm.mailerType === 'resend') ? mailConfigForm.apiKey : null,
      smtpHost: mailConfigForm.smtpHost,
      smtpPort: mailConfigForm.smtpPort,
      username: mailConfigForm.smtpUsername,
      password: mailConfigForm.smtpPassword,
      fromEmail: mailConfigForm.fromEmail,
      fromName: mailConfigForm.fromName,
      sslEnabled: mailConfigForm.sslEnabled,
      enabled: true,
      // IMAP 配置
      imapEnabled: mailConfigForm.imapEnabled,
      imapHost: mailConfigForm.imapHost,
      imapPort: mailConfigForm.imapPort,
      imapUsername: mailConfigForm.imapUsername,
      imapPassword: mailConfigForm.imapPassword
    }

    await request.post('/system/mail-config', config)

    ElMessage.success('配置保存成功')
    mailConnected.value = true
    configStep.value = 1
    loadMailConfig()
  } catch (e) {
    ElMessage.error('操作失败: ' + (e.message || e))
  } finally {
    testingConnection.value = false
  }
}

// 测试连接
const testConnection = async () => {
  testingConnection.value = true
  try {
    const res = await request.post('/system/mail-config/test', null, {
      params: { testEmail: mailConfig.value.fromEmail }
    })
    if (res.success !== false) {
      ElMessage.success('测试邮件发送成功')
    } else {
      ElMessage.error(res.message || '测试失败')
    }
  } catch (e) {
    ElMessage.error('测试失败: ' + (e.message || e))
  } finally {
    testingConnection.value = false
  }
}

// 加载模板
const loadTemplates = async () => {
  templatesLoading.value = true
  try {
    const res = await request.get('/system/mail-templates')
    mailTemplates.value = res || []
    templateCount.value = mailTemplates.value.length
  } catch (e) {
    console.error('加载模板失败:', e)
  } finally {
    templatesLoading.value = false
  }
}

// 打开模板对话框
const openTemplateDialog = (template = null) => {
  if (template) {
    templateForm.id = template.id
    templateForm.name = template.name
    templateForm.code = template.code
    templateForm.subject = template.subject
    templateForm.content = template.content
    templateForm.isDefault = template.isDefault || false
    templateForm.enabled = template.enabled !== false
  } else {
    templateForm.id = null
    templateForm.name = ''
    templateForm.code = ''
    templateForm.subject = ''
    templateForm.content = ''
    templateForm.isDefault = false
    templateForm.enabled = true
  }
  templateDialogVisible.value = true
}

// 保存模板
const saveTemplate = async () => {
  if (!templateForm.name || !templateForm.code || !templateForm.subject || !templateForm.content) {
    ElMessage.warning('请填写完整信息')
    return
  }
  
  savingTemplate.value = true
  try {
    const data = {
      name: templateForm.name,
      code: templateForm.code,
      subject: templateForm.subject,
      content: templateForm.content,
      isDefault: templateForm.isDefault,
      enabled: templateForm.enabled
    }
    
    if (templateForm.id) {
      await request.put(`/system/mail-templates/${templateForm.id}`, data)
      ElMessage.success('模板已更新')
    } else {
      await request.post('/system/mail-templates', data)
      ElMessage.success('模板已创建')
    }
    
    templateDialogVisible.value = false
    loadTemplates()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    savingTemplate.value = false
  }
}

// 切换模板状态
const toggleTemplate = async (template) => {
  template.switching = true
  try {
    await request.put(`/system/mail-templates/${template.id}`, {
      ...template,
      switching: undefined
    })
    ElMessage.success(template.enabled ? '模板已启用' : '模板已禁用')
  } catch (e) {
    template.enabled = !template.enabled
    ElMessage.error('操作失败')
  } finally {
    template.switching = false
  }
}

// 删除模板
const deleteTemplate = async (template) => {
  try {
    await ElMessageBox.confirm(`确定要删除模板「${template.name}」吗？`, '提示', {
      type: 'warning'
    })
    
    await request.delete(`/system/mail-templates/${template.id}`)
    ElMessage.success('模板已删除')
    loadTemplates()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '删除失败')
    }
  }
}

// 预览模板
const previewTemplateFn = (template) => {
  previewTemplate.value = template
  previewDialogVisible.value = true
}

// 加载日志
const loadLogs = async () => {
  logsLoading.value = true
  try {
    const params = {
      page: logPagination.page - 1,
      size: logPagination.size,
      status: logFilters.status || undefined
    }
    
    if (logFilters.dateRange && logFilters.dateRange.length === 2) {
      params.startDate = logFilters.dateRange[0]
      params.endDate = logFilters.dateRange[1]
    }
    
    const res = await request.get('/system/mail-logs', { params })
    if (res) {
      mailLogs.value = res.content || []
      logPagination.total = res.totalElements || 0
    }
  } catch (e) {
    console.error('加载日志失败:', e)
  } finally {
    logsLoading.value = false
  }
}

// 获取状态文本
const getStatusText = (status) => {
  const map = {
    'SUCCESS': '成功',
    'FAILED': '失败',
    'PENDING': '待发送'
  }
  return map[status] || status
}

// 格式化日志时间
const formatLogTime = (dateStr) => {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

// 格式化日期
const formatDate = (dateStr) => {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

// 打开快速发送
const openQuickSend = () => {
  testMailForm.to = ''
  testMailForm.type = 'verification'
  testMailForm.code = '123456'
  testMailDialogVisible.value = true
}

// 发送测试邮件
const sendTestMail = async () => {
  if (!testMailForm.to) {
    ElMessage.warning('请输入收件人')
    return
  }

  sendingTest.value = true
  try {
    // 使用验证码发送接口测试
    const res = await request.post('/auth/send-code', {
      email: testMailForm.to,
      type: testMailForm.type
    })

    if (res.success !== false) {
      ElMessage.success('测试邮件已发送')
      testMailDialogVisible.value = false
    } else {
      ElMessage.error(res.message || '发送失败')
    }
  } catch (e) {
    ElMessage.error('发送失败: ' + (e.message || e))
  } finally {
    sendingTest.value = false
  }
}

// 发送邮件
const sendMail = async () => {
  if (!sendMailForm.to) {
    ElMessage.warning('请输入收件人')
    return
  }
  if (!sendMailForm.subject) {
    ElMessage.warning('请输入邮件主题')
    return
  }
  if (!sendMailForm.content) {
    ElMessage.warning('请输入邮件内容')
    return
  }

  sendingMail.value = true
  try {
    const res = await request.post('/system/mail-config/send', {
      to: sendMailForm.to,
      subject: sendMailForm.subject,
      content: sendMailForm.content
    })

    if (res.success) {
      ElMessage.success('邮件发送成功')
      // 清空表单
      sendMailForm.to = ''
      sendMailForm.subject = ''
      sendMailForm.content = ''
    } else {
      ElMessage.error(res.message || '邮件发送失败')
    }
  } catch (e) {
    ElMessage.error('邮件发送失败: ' + (e.message || e))
  } finally {
    sendingMail.value = false
  }
}

// ==================== 收件箱相关 ====================

// 加载收件箱
const loadInbox = async () => {
  inboxLoading.value = true
  try {
    const res = await request.get('/system/mail-inbox', {
      params: {
        page: inboxPage.value - 1,
        size: inboxPageSize.value
      }
    })
    if (res) {
      inboxList.value = res.content || []
      inboxTotal.value = res.totalElements || 0
    }
  } catch (e) {
    console.error('加载收件箱失败:', e)
  } finally {
    inboxLoading.value = false
  }
}

// 获取未读数量和 IMAP 状态
const loadInboxStatus = async () => {
  try {
    const [unreadRes, imapRes] = await Promise.all([
      request.get('/system/mail-inbox/unread-count'),
      request.get('/system/mail-inbox/imap-status')
    ])
    if (unreadRes) {
      inboxUnreadCount.value = unreadRes.count || 0
    }
    if (imapRes) {
      imapConfigured.value = imapRes.configured || false
    }
  } catch (e) {
    console.error('加载收件箱状态失败:', e)
  }
}

// 拉取邮件
const fetchInboxEmails = async () => {
  inboxLoading.value = true
  try {
    const res = await request.post('/system/mail-inbox/fetch')
    if (res.success) {
      ElMessage.success(`成功拉取 ${res.fetchedCount} 封邮件`)
      loadInbox()
      loadInboxStatus()
    } else {
      ElMessage.error(res.message || '拉取失败')
    }
  } catch (e) {
    ElMessage.error('拉取失败: ' + (e.message || e))
  } finally {
    inboxLoading.value = false
  }
}

// 查看邮件详情
const viewInboxMail = async (row) => {
  try {
    // 先标记为已读
    if (!row.isRead) {
      await request.post(`/system/mail-inbox/${row.id}/read`)
      row.isRead = true
      inboxUnreadCount.value = Math.max(0, inboxUnreadCount.value - 1)
    }
    currentInboxMail.value = row
    inboxDetailVisible.value = true
  } catch (e) {
    console.error('加载邮件详情失败:', e)
  }
}

// 删除邮件
const deleteInboxMail = async (id) => {
  try {
    await ElMessageBox.confirm('确定要删除这封邮件吗?', '提示', {
      type: 'warning'
    })
    const res = await request.delete(`/system/mail-inbox/${id}`)
    if (res.success) {
      ElMessage.success('删除成功')
      loadInbox()
      loadInboxStatus()
    }
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

// ==================== 发件箱相关 ====================

// 加载发件箱（复用日志接口）
const loadSent = async () => {
  sentLoading.value = true
  try {
    const res = await request.get('/system/mail-logs', {
      params: {
        page: sentPage.value - 1,
        size: sentPageSize.value
      }
    })
    if (res) {
      sentList.value = res.content || []
      sentTotal.value = res.totalElements || 0
    }
  } catch (e) {
    console.error('加载发件箱失败:', e)
  } finally {
    sentLoading.value = false
  }
}

// 查看发件箱邮件详情
const viewSentMail = (row) => {
  currentSentMail.value = row
  sentDetailVisible.value = true
}

// 格式化日期时间
const formatDateTime = (dateStr) => {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}`
}


// 初始化
onMounted(() => {
  loadMailConfig()
  loadTemplates()
  loadLogs()
  loadInbox()
  loadInboxStatus()
  loadSent()
})
</script>

<style scoped>
.mail-system-container {
  padding: 20px;
}

/* 页面头部 */
.page-header {
  display: flex;
  justify-content: flex-end;
  align-items: flex-start;
  margin-bottom: 24px;
}

/* 统计卡片 */
.stats-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  background: var(--card-bg);
  border-radius: 8px;
  padding: 20px;
  display: flex;
  align-items: center;
  position: relative;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.stat-success {
  border-left: 4px solid var(--success-500);
}

.stat-warning {
  border-left: 4px solid var(--warning-500);
}

.stat-card-with-actions {
  flex-direction: column;
  align-items: stretch;
}

.stat-main {
  display: flex;
  align-items: center;
  width: 100%;
}

.stat-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed #ebeef5;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--info-light);
  color: var(--info-500);
  margin-right: 16px;
}

.stat-icon-blue {
  background: var(--info-light);
  color: var(--info-500);
}

.stat-icon-green {
  background: var(--success-light);
  color: var(--success-500);
}

.stat-icon-orange {
  background: var(--warning-light);
  color: var(--warning-500);
}

.stat-content {
  flex: 1;
}

.stat-value {
  font-size: 24px;
  font-weight: 600;
  color: var(--neutral-gray-900);
}

.stat-label {
  font-size: 12px;
  color: var(--neutral-gray-500);
  margin-top: 4px;
}

.stat-indicator {
  position: absolute;
  right: 0;
  top: 0;
  width: 60px;
  height: 60px;
  background: radial-gradient(circle at top right, rgba(64, 158, 255, 0.1), transparent);
}

/* 内容区域 */
.content-wrapper {
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: 20px;
}

.left-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.right-panel {
  min-width: 0;
}

/* 面板卡片 */
.panel-card {
  border-radius: 8px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 500;
}

.card-header span {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 状态卡片 */
.status-card .status-content {
  padding: 12px 0;
}

.status-item {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}

.status-item:last-child {
  border-bottom: none;
}

.status-label {
  color: var(--neutral-gray-500);
  font-size: 13px;
}

.status-value {
  color: var(--neutral-gray-900);
  font-size: 13px;
  font-weight: 500;
}

.status-actions {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
  text-align: center;
}

/* 快捷操作 */
.quick-actions {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.quick-action {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: var(--neutral-gray-100);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;
}

.quick-action:hover {
  background: var(--info-light);
  color: var(--info-500);
}

.quick-action span {
  margin-top: 8px;
  font-size: 12px;
  color: #606266;
}

.quick-action:hover span {
  color: var(--info-500);
}

/* 标签页 */
.main-tabs {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
}

/* 配置步骤 */
.config-steps {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 32px;
  padding: 20px 0;
}

.step-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
  z-index: 1;
}

.step-number {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--neutral-gray-200);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  margin-bottom: 8px;
  transition: all 0.3s;
}

.step-item.active .step-number,
.step-item.completed .step-number {
  background: var(--orin-primary);
}

.step-text {
  font-size: 13px;
  color: var(--neutral-gray-500);
}

.step-item.active .step-text {
  color: var(--orin-primary);
  font-weight: 500;
}

.step-line {
  width: 100px;
  height: 2px;
  background: var(--neutral-gray-200);
  margin: 0 8px;
  margin-bottom: 24px;
  transition: all 0.3s;
}

.step-line.active {
  background: var(--orin-primary);
}

.config-step-content {
  max-width: 600px;
  margin: 0 auto;
  padding: 20px 0;
}

/* 邮件服务商选择 */
.mailer-selection {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
  margin-bottom: 32px;
}

.mailer-option {
  border: 2px solid #ebeef5;
  border-radius: 12px;
  padding: 24px;
  cursor: pointer;
  transition: all 0.3s;
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.mailer-option:hover {
  border-color: #409EFF;
}

.mailer-option.selected {
  border-color: var(--orin-primary);
  background: var(--primary-light);
}

.mailer-icon {
  width: 64px;
  height: 64px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
}

.mailersend-icon {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
}

.smtp-icon {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  color: #fff;
}

.resend-icon {
  background: linear-gradient(135deg, #000000 0%, #333333 100%);
  color: #fff;
}

.mailer-info h4 {
  margin: 0 0 8px 0;
  color: #303133;
}

.mailer-info p {
  margin: 0;
  font-size: 13px;
  color: #909399;
}

.check-icon {
  position: absolute;
  top: 12px;
  right: 12px;
  color: var(--orin-primary);
  font-size: 20px;
}

.step-actions {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-top: 24px;
}

/* 表单 */
.config-form .form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.verify-section {
  text-align: center;
  padding: 40px 0;
}

.verify-info {
  margin-bottom: 32px;
}

.verify-info h3 {
  margin: 16px 0 8px;
  color: #303133;
}

.verify-info p {
  color: #909399;
}

.verify-details {
  background: #f5f7fa;
  border-radius: 8px;
  padding: 20px;
  max-width: 400px;
  margin: 0 auto 32px;
}

.verify-item {
  display: flex;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px solid #ebeef5;
}

.verify-item:last-child {
  border-bottom: none;
}

.verify-label {
  color: #909399;
}

.verify-value {
  color: #303133;
  font-weight: 500;
}

.verify-actions {
  display: flex;
  justify-content: center;
  gap: 16px;
}

/* 发送邮件 */
.send-card {
  max-width: 900px;
}

.send-form {
  padding: 20px;
}

/* 模板管理 */
.templates-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.template-name {
  display: flex;
  align-items: center;
  gap: 8px;
}

.template-code {
  background: #f5f7fa;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  color: #606266;
}

.text-muted {
  color: #c0c4cc;
}

/* 日志 */
.logs-filters {
  display: flex;
  gap: 12px;
}

.log-status {
  display: flex;
  align-items: center;
  gap: 4px;
}

.log-status.status-success {
  color: var(--success-500);
}

.log-status.status-failed {
  color: var(--error-500);
}

.log-status.status-pending {
  color: var(--warning-500);
}

.error-text {
  color: var(--error-500);
  font-size: 12px;
}

/* 分页 */
.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

/* 预览 */
.preview-content {
  padding: 20px;
  background: #f5f7fa;
  border-radius: 8px;
}

.preview-subject {
  font-size: 16px;
  color: #303133;
  margin-bottom: 16px;
}

.preview-body {
  color: #606266;
  line-height: 1.8;
}

/* 变量提示 */
.variables-tip {
  margin-top: 16px;
}

.variables-list {
  font-size: 12px;
}

.variables-list code {
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 4px;
  color: #409EFF;
  margin-right: 8px;
}

.content-editor {
  font-family: 'Monaco', 'Menlo', monospace;
}

@media (max-width: 1200px) {
  .stats-cards {
    grid-template-columns: repeat(2, 1fr);
  }
  
  .content-wrapper {
    grid-template-columns: 1fr;
  }
  
  .left-panel {
    flex-direction: row;
    flex-wrap: wrap;
  }
  
  .left-panel .panel-card {
    flex: 1;
    min-width: 280px;
  }
}

@media (max-width: 768px) {
  .stats-cards {
    grid-template-columns: 1fr;
  }
  
  .mailer-selection {
    grid-template-columns: 1fr;
  }

  .quick-actions {
    grid-template-columns: 1fr;
  }
}

/* 收件箱样式 */
.inbox-card,
.sent-card {
  height: 100%;
}

.inbox-stats {
  margin-bottom: 16px;
}

.unread-badge {
  background: var(--error-500);
  color: white;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 12px;
}

.unread-row {
  font-weight: 600;
  color: #303133;
}

.inbox-table .el-table__row {
  cursor: pointer;
}

.inbox-table .el-table__row:hover {
  background-color: #f5f7fa;
}

.empty-tip {
  padding: 20px 0;
}

/* 邮件详情样式 */
.mail-detail {
  padding: 10px;
}

.mail-detail .mail-header {
  margin-bottom: 16px;
}

.mail-detail .mail-from,
.mail-detail .mail-time,
.mail-detail .mail-status {
  margin-bottom: 8px;
  color: #606266;
}

.mail-detail .mail-content {
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

.header-actions {
  display: flex;
  gap: 10px;
}

html.dark .mail-system-container {
  background: transparent;
}

html.dark .main-tabs,
html.dark .panel-card,
html.dark .quick-action,
html.dark .verify-details,
html.dark .preview-content {
  background: rgba(15, 23, 42, 0.82);
  border-color: rgba(71, 85, 105, 0.48);
}

html.dark .status-item,
html.dark .status-actions,
html.dark .stat-actions,
html.dark .verify-item {
  border-color: rgba(71, 85, 105, 0.42);
}

html.dark .status-label,
html.dark .stat-label,
html.dark .mailer-info p,
html.dark .config-form .form-tip,
html.dark .verify-label,
html.dark .mail-detail .mail-from,
html.dark .mail-detail .mail-time,
html.dark .mail-detail .mail-status {
  color: #94a3b8;
}

html.dark .status-value,
html.dark .stat-value,
html.dark .mailer-info h4,
html.dark .verify-value,
html.dark .preview-subject,
html.dark .preview-body,
html.dark .mail-detail .mail-content {
  color: #e2e8f0;
}

html.dark .quick-action:hover {
  background: rgba(30, 41, 59, 0.94);
}

html.dark .mail-system-container :deep(.el-input__wrapper),
html.dark .mail-system-container :deep(.el-select__wrapper),
html.dark .mail-system-container :deep(.el-textarea__inner) {
  background: rgba(15, 23, 42, 0.76) !important;
  color: #e2e8f0 !important;
  box-shadow: inset 0 0 0 1px rgba(71, 85, 105, 0.52) !important;
}

html.dark .inbox-table .el-table__row:hover {
  background-color: rgba(30, 41, 59, 0.62);
}
</style>
