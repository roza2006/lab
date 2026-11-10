import { createApp } from 'vue'
import ElementPlus from 'element-plus'

import App from './App.vue'
import router from './router'
import { pinia } from './stores'
import { useAuthStore } from './stores/auth'

import 'element-plus/dist/index.css'
import './styles/main.css'

const app = createApp(App)

app.use(pinia)
app.use(router)
app.use(ElementPlus)

// 任一请求返回 401：清理登录态并回到登录页（保留来源路径）。
window.addEventListener('mp:unauthorized', () => {
  const auth = useAuthStore(pinia)
  auth.logout()
  const current = router.currentRoute.value
  if (current.name !== 'login') {
    router.replace({ name: 'login', query: { redirect: current.fullPath } })
  }
})

app.mount('#app')
