import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// 本地开发时把 /api 代理到 Spring Boot（默认 8080），避免跨域与 Cookie 问题。
// 生产环境由 Nginx 反向代理，见 deploy/ 目录（R5-E）。
// 若 8080 被占用，可用 VITE_PROXY_TARGET 指向 mock 或其他端口。
const proxyTarget = process.env.VITE_PROXY_TARGET ?? 'http://127.0.0.1:8080'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    watch: {
      // mock 服务由 node 直接运行，且会写临时文件，避免触发 Vite watcher
      ignored: ['**/mock/**'],
    },
    proxy: {
      '/api': {
        target: proxyTarget,
        changeOrigin: true,
      },
    },
  },
})
