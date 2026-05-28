import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      resolvers: [ElementPlusResolver()],
      imports: ['vue', 'vue-router', 'pinia'],
      dts: 'src/auto-imports.d.ts'
    }),
    Components({
      resolvers: [ElementPlusResolver()],
      dts: 'src/components.d.ts'
    })
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    port: 3000,
    host: true,
    proxy: {
      /** raccoon-drone-sim 嵌入页代理（独立启动仍用 http://localhost:3010） */
      '/sim-drone': {
        target: 'http://localhost:3010',
        changeOrigin: true,
        ws: true,
        rewrite: (path) => path.replace(/^\/sim-drone/, '')
      },
      '/api/drone': {
        target: 'http://localhost:8091',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/drone/, '')
      },
      '/api/iot-data': {
        target: 'http://localhost:8092',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/iot-data/, '')
      },
      '/api/agent': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/agent/, '')
      },
      '/api': {
        target: 'http://localhost:8087',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      }
    }
  }
})