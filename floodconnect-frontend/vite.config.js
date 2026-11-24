import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
export default defineConfig({
  plugins: [react()],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },   // import '@/utils' = src/utils
  server: { port: 5173, open: true },
  test: { environment: 'node', include: ['src/**/*.test.{js,jsx}'] },   // vitest
});
