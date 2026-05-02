<template>
  <div class="bsod-container">
    <div class="bsod-content">
      <div class="sad-face">
        :(
      </div>
      <div class="message-section">
        <h2 class="main-msg">
          您的页面遇到问题，需要重新寻找。
        </h2>
        <p class="secondary-msg">
          我们已记录异常信息，请尝试返回首页或上一页继续操作。
        </p>
      </div>

      <div class="qr-section">
        <div class="qr-placeholder">
          <!-- Simplified QR SVG for aesthetic -->
          <svg
            width="120"
            height="120"
            viewBox="0 0 100 100"
            fill="currentColor"
          >
            <rect
              x="0"
              y="0"
              width="30"
              height="30"
            />
            <rect
              x="70"
              y="0"
              width="30"
              height="30"
            />
            <rect
              x="0"
              y="70"
              width="30"
              height="30"
            />
            <rect
              x="10"
              y="10"
              width="10"
              height="10"
              fill="white"
            />
            <rect
              x="80"
              y="10"
              width="10"
              height="10"
              fill="white"
            />
            <rect
              x="10"
              y="80"
              width="10"
              height="10"
              fill="white"
            />
            <rect
              x="40"
              y="40"
              width="20"
              height="20"
            />
            <rect
              x="70"
              y="70"
              width="10"
              height="10"
            />
            <rect
              x="90"
              y="90"
              width="10"
              height="10"
            />
          </svg>
        </div>
        <div class="info-details">
          <p>有关此问题的详细说明和处理建议，请访问：</p>
          <p><a href="/unified-docs" target="_blank" class="link">统一文档中心</a></p>
          <div class="stop-code">
            <span>终止代码: </span>
            <span class="bold">PAGE_NOT_FOUND_404</span>
          </div>
        </div>
      </div>

      <div class="actions">
        <el-button size="large" class="bsod-btn" @click="$router.go(-1)">
          返回上一页
        </el-button>
        <el-button size="large" class="bsod-btn" @click="$router.push('/')">
          返回首页
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
</script>

<style scoped>
.bsod-container {
  min-height: 100vh;
  background-color: var(--bg-color, var(--el-bg-color-page, #f8fafc));
  color: var(--text-primary, #1e293b);
  display: flex;
  justify-content: flex-start;
  align-items: center;
  padding: 0 10%;
  font-family: 'Inter', 'Segoe UI', system-ui, sans-serif;
  transition: background-color 0.3s, color 0.3s;
  cursor: default;
  user-select: none;
}

.bsod-content {
  max-width: 900px;
  display: flex;
  flex-direction: column;
  gap: 30px;
}

.sad-face {
  font-size: 120px;
  line-height: 1;
  margin-bottom: 10px;
  color: var(--orin-primary, #10b981);
}

.main-msg {
  font-size: 32px;
  font-weight: 500;
  line-height: 1.3;
  margin: 0 0 20px 0;
  color: var(--text-primary, #1e293b);
}

.secondary-msg {
  font-size: 20px;
  font-weight: 400;
  color: var(--text-secondary, #64748b);
  margin-bottom: 40px;
}

.qr-section {
  display: flex;
  gap: 25px;
  align-items: flex-start;
}

.qr-placeholder {
  background: white;
  padding: 10px;
  color: #0f172a; /* Keep QR dark for scanning contrast */
  flex-shrink: 0;
  border-radius: 4px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  border: 1px solid var(--el-border-color-lighter, #f1f5f9);
}

.info-details {
  font-size: 14px;
  font-weight: 400;
  line-height: 1.6;
  color: var(--text-secondary, #64748b);
}

.info-details p {
  margin: 0;
}

.info-details .link {
  margin-bottom: 10px;
  color: var(--orin-primary, #10b981);
  font-weight: 500;
  text-decoration: none;
  display: inline-block;
}

.info-details .link:hover {
  text-decoration: underline;
}

.stop-code {
  font-size: 13px;
  margin-top: 5px;
}

.stop-code .bold {
  font-weight: 600;
  color: var(--text-primary, #1e293b);
}

.actions {
  margin-top: 40px;
  display: flex;
  gap: 20px;
}

.bsod-btn {
  background: transparent !important;
  border: 1px solid var(--el-border-color, #dcdfe6) !important;
  color: var(--text-primary, #1e293b) !important;
  border-radius: 8px !important;
  padding: 12px 30px !important;
  font-family: inherit;
  font-size: 15px;
  transition: all 0.3s;
}

.bsod-btn:hover {
  background: var(--el-color-primary-light-9, #ecfdf5) !important;
  border-color: var(--orin-primary, #10b981) !important;
  color: var(--orin-primary, #10b981) !important;
  transform: translateY(-2px);
}

/* Light/Dark mode responsiveness is handled automatically by CSS variables */
html.dark .qr-placeholder {
  background: #f8fafc; /* Keep QR area bright in dark mode for scannability */
  border-color: transparent;
}

html.dark .bsod-container {
  background: var(--orin-bg, #0b1118);
  color: var(--el-text-color-primary, #f1f5f9);
}

html.dark .main-msg {
  color: var(--el-text-color-primary, #f1f5f9);
}

html.dark .secondary-msg,
html.dark .info-details {
  color: var(--el-text-color-secondary, #94a3b8);
}

html.dark .stop-code .bold {
  color: var(--el-text-color-primary, #f1f5f9);
}

html.dark .bsod-btn {
  color: var(--el-text-color-primary, #f1f5f9) !important;
  border-color: var(--el-border-color-dark, #334155) !important;
}

html.dark .bsod-btn:hover {
  background: rgba(45, 212, 191, 0.12) !important;
}
</style>
