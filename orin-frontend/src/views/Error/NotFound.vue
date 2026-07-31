<template>
  <main class="bsod-container">
    <section class="not-found-card" aria-labelledby="not-found-title">
      <header class="not-found-brand">
        <BrandingLogo :height="36" />
        <span>ORIN Enterprise AI Hub</span>
      </header>

      <div class="not-found-body">
        <div class="error-code" aria-hidden="true">
          404
        </div>
        <div class="error-copy">
          <span class="error-kicker">PAGE NOT FOUND</span>
          <h1 id="not-found-title">
            这个页面不在当前工作区
          </h1>
          <p>
            链接可能已经调整、被移除，或当前账号没有对应入口。你可以返回上一页，或从 ORIN
            首页重新进入。
          </p>

          <div class="error-meta">
            <span>错误代码</span>
            <code>PAGE_NOT_FOUND_404</code>
          </div>

          <div class="actions">
            <el-button size="large" @click="$router.go(-1)">
              返回上一页
            </el-button>
            <el-button type="primary" size="large" @click="$router.push('/')">
              返回首页
            </el-button>
          </div>
        </div>
      </div>

      <footer class="not-found-footer">
        <span>需要了解接口或路由？</span>
        <router-link to="/unified-docs">
          查看统一文档中心
        </router-link>
      </footer>
    </section>
  </main>
</template>

<script setup>
import BrandingLogo from '@/components/BrandingLogo.vue'
</script>

<style scoped>
.bsod-container {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 32px;
  background: #f5f8f7;
  color: #17211f;
  font-family: Inter, "PingFang SC", "Microsoft YaHei", system-ui, sans-serif;
}

.not-found-card {
  width: min(100%, 980px);
  overflow: hidden;
  border: 1px solid #dfe6e4;
  border-radius: 16px;
  background: #ffffff;
  box-shadow: 0 18px 48px rgba(15, 35, 31, 0.08);
}

.not-found-brand,
.not-found-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 24px;
}

.not-found-brand {
  border-bottom: 1px solid #edf1f0;
}

.not-found-brand span {
  color: #71807c;
  font-size: 11px;
  font-weight: 650;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.not-found-body {
  display: grid;
  grid-template-columns: minmax(220px, 0.8fr) minmax(0, 1.2fr);
  min-height: 440px;
}

.error-code {
  display: grid;
  place-items: center;
  border-right: 1px solid #edf1f0;
  background: #f8faf9;
  color: #d4e8e3;
  font-size: clamp(92px, 14vw, 176px);
  font-weight: 750;
  line-height: 1;
  letter-spacing: -0.08em;
}

.error-copy {
  align-self: center;
  padding: 48px;
}

.error-kicker {
  color: #3f9388;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
}

h1 {
  margin: 14px 0 12px;
  color: #17211f;
  font-size: clamp(28px, 4vw, 40px);
  line-height: 1.2;
  letter-spacing: -0.025em;
}

p {
  max-width: 520px;
  margin: 0;
  color: #66736f;
  font-size: 15px;
  line-height: 1.75;
}

.error-meta {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  margin-top: 26px;
  padding: 8px 10px;
  border: 1px solid #dfe6e4;
  border-radius: 7px;
  background: #f8faf9;
  color: #71807c;
  font-size: 12px;
}

.error-meta code {
  color: #35413e;
  font-family: "JetBrains Mono", "SFMono-Regular", Consolas, monospace;
  font-size: 11px;
  font-weight: 650;
}

.actions {
  display: flex;
  gap: 10px;
  margin-top: 28px;
}

.actions :deep(.el-button) {
  min-width: 118px;
  border-radius: 8px;
  font-weight: 600;
}

.actions :deep(.el-button--primary) {
  border-color: #3f9388;
  background: #3f9388;
}

.not-found-footer {
  justify-content: flex-end;
  border-top: 1px solid #edf1f0;
  color: #8c9894;
  font-size: 12px;
}

.not-found-footer a {
  color: #347f75;
  font-weight: 650;
  text-decoration: none;
}

@media (max-width: 720px) {
  .bsod-container {
    padding: 16px;
  }

  .not-found-body {
    grid-template-columns: 1fr;
  }

  .error-code {
    min-height: 180px;
    border-right: 0;
    border-bottom: 1px solid #edf1f0;
  }

  .error-copy {
    padding: 32px 24px;
  }

  .not-found-brand span {
    display: none;
  }

  .not-found-footer {
    justify-content: flex-start;
    flex-wrap: wrap;
  }
}

html.dark .bsod-container {
  background: #0d1514;
}

html.dark .not-found-card,
html.dark .not-found-brand,
html.dark .not-found-footer {
  border-color: #293936;
  background: #131d1b;
}

html.dark .error-code,
html.dark .error-meta {
  border-color: #293936;
  background: #182421;
}

html.dark .error-code {
  color: #29413c;
}

html.dark h1 {
  color: #eef5f3;
}

html.dark p,
html.dark .not-found-brand span,
html.dark .not-found-footer {
  color: #9baca7;
}
</style>
