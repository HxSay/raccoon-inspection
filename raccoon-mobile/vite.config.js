import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import { fileURLToPath, URL } from 'node:url';
import Components from 'unplugin-vue-components/vite';
import { VantResolver } from '@vant/auto-import-resolver';
/**
 * 移动端构建：Vant 按需引入；开发时代理到 raccoon-cloud-system（默认 8087）
 */
export default defineConfig({
    plugins: [
        vue(),
        Components({
            resolvers: [VantResolver()]
        })
    ],
    resolve: {
        alias: {
            '@': fileURLToPath(new URL('./src', import.meta.url))
        }
    },
    server: {
        port: 5174,
        host: true,
        proxy: {
            '/api': {
                target: 'http://localhost:8087',
                changeOrigin: true,
                rewrite: function (p) { return p.replace(/^\/api/, ''); }
            }
        }
    }
});
