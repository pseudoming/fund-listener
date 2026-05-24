import { createApp } from 'vue'
import App from './App.vue'
import router from './router'

// Vant 基础样式（normalize + haptics）
import 'vant/lib/index.css'

import './styles/index.css'

const app = createApp(App)
app.use(router)
app.mount('#app')
