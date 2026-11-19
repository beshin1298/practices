import { fileURLToPath, URL } from 'node:url';

import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 5173,
    // The repo lives on a Windows drive mounted in WSL2 (/mnt/c), where file-change events do not
    // reach Linux: without polling, Vite keeps serving stale modules after an edit.
    watch: { usePolling: true, interval: 300 },
    // Same-origin in dev: the browser calls /api on :5173 and Vite forwards to Spring Boot.
    proxy: { '/api': 'http://localhost:8080' },
  },
});
