import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Backend target for the dev/preview proxy. Defaults to localhost for `npm run dev`;
// docker-compose sets VITE_API_TARGET=http://pulsegate:8080 for the containerized run.
const target = process.env.VITE_API_TARGET || 'http://localhost:8080'

const proxy = {
  '/api': { target, changeOrigin: true },
  '/actuator': { target, changeOrigin: true },
}

export default defineConfig({
  plugins: [react()],
  server: {
    host: true,
    port: 5173,
    proxy,
  },
  preview: {
    host: true,
    port: 5173,
    allowedHosts: true,
    proxy,
  },
})
