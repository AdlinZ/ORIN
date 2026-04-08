import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

// 构建时日期
const buildDate = new Date().toISOString().split('T')[0]

const toPackageChunk = (id) => {
  if (!id.includes('node_modules')) return null
  if (id.includes('/node_modules/echarts/')) return 'echarts'
  if (id.includes('/node_modules/element-plus/') || id.includes('/node_modules/@element-plus/')) return 'element-plus'
  if (id.includes('/node_modules/@vue-flow/')) return 'vue-flow'
  if (
    id.includes('/node_modules/vue/') ||
    id.includes('/node_modules/vue-router/') ||
    id.includes('/node_modules/pinia/')
  ) {
    return 'vue-core'
  }
  return 'vendor'
}

// https://vitejs.dev/config/
export default defineConfig({
  define: {
    __BUILD_DATE__: JSON.stringify(buildDate)
  },
  plugins: [
    vue(),
    // Element Plus 按需自动导入
    AutoImport({
      resolvers: [ElementPlusResolver()],
      imports: ['vue', 'vue-router', 'pinia'],
      dts: false // 如需类型支持可设为 'src/auto-imports.d.ts'
    }),
    Components({
      resolvers: [ElementPlusResolver()],
      dts: false // 如需类型支持可设为 'src/components.d.ts'
    })
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/v1': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/v3': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/swagger-ui': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    },
    // Enable SPA fallback - serve index.html for all non-file routes
    historyApiFallback: true
  },
  build: {
    // 分包策略 - 提升缓存效率
    rollupOptions: {
      output: {
        manualChunks (id) {
          return toPackageChunk(id)
        }
      }
    },
    // 生产环境移除 console 和 debugger
    minify: 'esbuild',
    // Chunk 大小警告阈值
    chunkSizeWarningLimit: 600
  },
  test: {
    environment: 'jsdom',
    globals: true,
    server: {
      deps: {
        inline: ['element-plus', '@element-plus/icons-vue']
      }
    }
  }
})
