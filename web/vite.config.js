import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
    // 按需引入（R2）：只把真正用到的 Element Plus 组件及其样式打进包里。
    // 全量引入时整个组件库（含从未使用的日期选择器、级联选择器等）都会进 bundle，
    // 主包 833KB 里绝大部分是这些死代码。
    //
    // 注意 importStyle: 'css' —— 让插件自动带上每个组件的 css，
    // 而不是把 element-plus/dist/index.css 整包引入（那样等于没省）。
    AutoImport({
      resolvers: [ElementPlusResolver()],
    }),
    Components({
      resolvers: [ElementPlusResolver({ importStyle: 'css' })],
    }),
  ],
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
