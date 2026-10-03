import { defineConfig } from 'vite'
import { svelte } from '@sveltejs/vite-plugin-svelte'

export default defineConfig({
  plugins: [svelte()],
  // 构建产物直接进 Quarkus 静态资源目录，后端单端口托管前端
  build: {
    outDir: '../backend/src/main/resources/META-INF/resources',
    emptyOutDir: true
  },
  server: {
    port: 5173,
    proxy: { '/api': 'http://localhost:8080' }
  }
})
