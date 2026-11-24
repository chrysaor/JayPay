import react from '@vitejs/plugin-react'
import { resolve } from 'node:path'
import { loadEnv } from 'vite'
import { defineConfig } from 'vitest/config'

const upstreams = {
  membership: ['JAYPAY_MEMBERSHIP_URL', 'http://localhost:8081'],
  banking: ['JAYPAY_BANKING_URL', 'http://localhost:8082'],
  money: ['JAYPAY_MONEY_URL', 'http://localhost:8083'],
  remittance: ['JAYPAY_REMITTANCE_URL', 'http://localhost:8084'],
  payment: ['JAYPAY_PAYMENT_URL', 'http://localhost:8085'],
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, resolve(import.meta.dirname, '../..'), 'JAYPAY_')
  return {
    plugins: [react()],
    server: {
      proxy: Object.fromEntries(
        Object.entries(upstreams).map(([service, [variable, fallback]]) => [
          `/api/${service}`,
          {
            target: env[variable] ?? fallback,
            changeOrigin: true,
            rewrite: (path: string) => path.replace(`/api/${service}`, ''),
          },
        ]),
      ),
    },
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      restoreMocks: true,
      unstubGlobals: true,
    },
  }
})
