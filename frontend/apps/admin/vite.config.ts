import react from '@vitejs/plugin-react'
import { resolve } from 'node:path'
import { loadEnv } from 'vite'
import { defineConfig } from 'vitest/config'

const upstreams = {
  membership: ['JAYPAY_MEMBERSHIP_URL', 'http://localhost:8081'],
  payment: ['JAYPAY_PAYMENT_URL', 'http://localhost:8085'],
  'money-aggregation': ['JAYPAY_MONEY_AGGREGATION_URL', 'http://localhost:8086'],
  'money-query': ['JAYPAY_MONEY_QUERY_URL', 'http://localhost:8087'],
  'event-flow': ['JAYPAY_EVENT_FLOW_URL', 'http://localhost:8093'],
} as const

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, resolve(import.meta.dirname, '../..'), 'JAYPAY_')
  return {
    plugins: [react()],
    server: {
      port: 5174,
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
      coverage: {
        enabled: true,
        provider: 'v8',
        include: ['src/**/*.{ts,tsx}'],
        exclude: ['src/test/**'],
        reporter: ['json-summary'],
      },
    },
  }
})
