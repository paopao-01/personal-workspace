import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import { fileURLToPath, URL } from 'node:url'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.JOBHUB_API_TARGET ?? 'http://127.0.0.1:8080',
        secure: false,
        // 不设 rewrite：后端 Controller 类注解为 @RequestMapping("/api")，
        // 路径本身已含 /api 前缀，原样转发即可。
      },
    },
  },
})
