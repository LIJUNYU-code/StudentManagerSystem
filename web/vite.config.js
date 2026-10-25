import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue(), vueDevTools()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  // 开发服务器代理：前端代码一律请求 /api/xxx，
  // vite 把它转发到 http://localhost:8080/xxx 并去掉 /api 前缀。
  // 为什么需要它：浏览器同源策略禁止跨端口 AJAX（5173 → 8080），
  // 后端没配 CORS，代理是零后端改动的标准解法。
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
})
