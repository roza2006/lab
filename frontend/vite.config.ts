import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// 本地开发时把 /api 代理到 Spring Boot（默认 8080），避免跨域与 Cookie 问题。
// 生产环境由 Nginx 反向代理，见 deploy/ 目录（R5-E）。
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
})
