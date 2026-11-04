import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // El 5173 ya lo usa otro proyecto
    port: 5174,
    strictPort: true,
    // Proxy hacia el backend: el frontend llama a '/api/...' y Vite lo
    // reenvia al Spring Boot en 8081. Asi NO hay CORS en desarrollo.
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
})
