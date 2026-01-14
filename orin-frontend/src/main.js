import { createApp } from 'vue'
import { createPinia } from 'pinia' // Import Pinia
import './assets/css/main.css' // Import Global CSS
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css' // Dark mode support
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import permission from './utils/permission' // Import permission directive

const app = createApp(App)
const pinia = createPinia()

app.use(pinia) // Use Pinia

app.use(ElementPlus)
app.use(router)

// Register permission directive
app.directive('permission', permission)

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
    app.component(key, component)
}

app.mount('#app')
