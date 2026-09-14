/**
 * 本文件是 BugLoop 前端入口，负责注册状态管理和路由后挂载应用。
 */
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './app/router'
import './shared/styles/index.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')
