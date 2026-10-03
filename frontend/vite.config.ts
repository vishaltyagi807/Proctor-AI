import { fileURLToPath, URL } from 'node:url'
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv, type ProxyOptions } from 'vite'
import tailwindcss from '@tailwindcss/vite'


export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const proxy: Record<string, ProxyOptions> = {
    '/api': {
      target: env.API_URL || 'http://localhost:7050',
      changeOrigin: true,
      rewrite: (path) => path.replace(/^\/api/, ''),
      cookiePathRewrite: { '/auth': '/api/auth' },
    },
  }
  return {
    plugins: [react(), tailwindcss(),],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: { proxy },
    preview: { proxy },
  }
})
