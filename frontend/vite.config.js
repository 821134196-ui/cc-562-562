import { defineConfig } from 'vite';
import { svelte } from '@sveltejs/vite-plugin-svelte';

// dev: 5173 端口，/api 代理到 Quarkus 8080
// build: 产物直接输出到后端静态资源目录，由 Quarkus 同源提供
export default defineConfig({
  plugins: [svelte()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080'
    }
  },
  build: {
    outDir: '../src/main/resources/META-INF/resources',
    emptyOutDir: true
  }
});
