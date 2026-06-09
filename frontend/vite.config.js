import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      '/prometheus': {
        target: 'http://prometheus:9090',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/prometheus/, '')
      },
      '/api': {
        target: 'http://api-service:8080',
        changeOrigin: true
      }
    }
  }
})
