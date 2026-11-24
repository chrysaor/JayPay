import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// Each backend service is reached through /api/<service>/... on the same origin.
// The dev server proxies it here; nginx does the same in the container image.
const upstreams = {
  membership: process.env.JAYPAY_MEMBERSHIP_URL ?? 'http://localhost:8081',
  banking: process.env.JAYPAY_BANKING_URL ?? 'http://localhost:8082',
  money: process.env.JAYPAY_MONEY_URL ?? 'http://localhost:8083',
  remittance: process.env.JAYPAY_REMITTANCE_URL ?? 'http://localhost:8084',
  payment: process.env.JAYPAY_PAYMENT_URL ?? 'http://localhost:8085',
}

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: Object.fromEntries(
      Object.entries(upstreams).map(([service, target]) => [
        `/api/${service}`,
        {
          target,
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
})
