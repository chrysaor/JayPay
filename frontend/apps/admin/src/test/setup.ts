import '@testing-library/jest-dom/vitest'
import { authorize } from '@jaypay/api/client'
import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'
import { leaveFor } from '../leave'

vi.mock('../leave', () => ({ leaveFor: vi.fn() }))

afterEach(() => {
  cleanup()
  authorize(null)
  localStorage.clear()
  sessionStorage.clear()
  vi.mocked(leaveFor).mockClear()
})
