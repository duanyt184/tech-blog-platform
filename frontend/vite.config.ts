import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => ({
  plugins: [vue()],
  server: {
    host: 'localhost',
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': {
        target: loadEnv(mode, '.', '').API_PROXY_TARGET || 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
}))

