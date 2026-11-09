import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In development the browser talks to Vite (5173), which forwards /api to the Spring Boot server.
// That means no CORS setup is needed. Set VITE_API_PROXY if your API is not on localhost:8080.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: process.env.VITE_API_PROXY || 'http://localhost:8080', changeOrigin: true },
    },
  },
});
