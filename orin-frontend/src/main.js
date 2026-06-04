import { createApp } from 'vue'
import { createPinia } from 'pinia' // Import Pinia
import { ElLoading } from 'element-plus'
import 'element-plus/es/components/loading/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/notification/style/css'
import 'element-plus/theme-chalk/dark/css-vars.css' // Dark mode support
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import './assets/css/main.css' // Import Global CSS
import './assets/styles/theme.css' // Import Amber Energy Theme
import './assets/styles/global.css' // Import ORIN Global Styles
import './assets/styles/arco-orin.css'
import App from './App.vue'
import router from './router'
import permission from './utils/permission' // Import permission directive

const app = createApp(App)
const pinia = createPinia()

app.use(pinia) // Use Pinia

app.use(ElLoading)
app.use(router)

// Register permission directive
app.directive('permission', permission)

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
    app.component(key, component)
}

app.mount('#app')
