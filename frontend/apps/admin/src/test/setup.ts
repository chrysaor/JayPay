import '@testing-library/jest-dom/vitest'
import { authorize } from '@jaypay/api/client'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

afterEach(() => {
  cleanup()
  authorize(null)
  localStorage.clear()
})
