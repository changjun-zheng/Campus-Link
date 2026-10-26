import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig({
  plugins: [
    vue(),
    // 样式工具层（CR-023）；设计令牌与 EP 覆写在 src/styles/tokens.css
    tailwindcss(),
    // Element Plus 按需自动引入（组件 + API），替代全量引入，显著减小构建产物
    AutoImport({ resolvers: [ElementPlusResolver()] }),
    Components({ resolvers: [ElementPlusResolver()] }),
  ],
  server: {
    port: 5173,
    proxy: {
      // 开发环境代理到本地后端；后端默认端口 8088（CR-011），可用 VITE_API_TARGET 覆盖
      '/api': {
        target: process.env.VITE_API_TARGET || 'http://localhost:8088',
        changeOrigin: true,
      },
    },
  },
  // 生产产物预览也代理 /api（CR-154）：此前 preview 无代理，首屏性能只能测到空壳，
  // 或退回 dev server（含 HMR / 未压缩模块，数字不代表线上）。
  preview: {
    port: 4173,
    proxy: {
      '/api': {
        target: process.env.VITE_API_TARGET || 'http://localhost:8088',
        changeOrigin: true,
      },
    },
  },
})
