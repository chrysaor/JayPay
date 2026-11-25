import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { App } from '../App'

export const member = {
  membershipId: '1',
  name: 'Hong Gildong',
  email: 'hong@example.com',
  address: 'Seoul',
  valid: true,
  corp: false,
  refreshToken: 'refresh-1',
}

export type TestRole = 'ADMIN' | 'OPERATOR' | 'VIEWER'

export const storageKey = 'jaypay.admin.session'
export const staffToken = 'staff-token-1'
export const meRoute = 'GET /api/auth/staff/me'

export function staffAccount(role: TestRole = 'OPERATOR', username = 'jay') {
  return { username, role, active: true, mfaEnabled: false }
}

export function hoursFromNow(hours: number) {
  return new Date(Date.now() + hours * 3_600_000).toISOString()
}

export function signedInAs(role: TestRole = 'OPERATOR', expiresAt = hoursFromNow(8)) {
  const account = staffAccount(role)
  localStorage.setItem(storageKey, JSON.stringify({ token: staffToken, expiresAt, account }))
  return { [meRoute]: { body: account } }
}

export function storedSession(): unknown {
  return JSON.parse(localStorage.getItem(storageKey) ?? 'null')
}

export function renderApp(path: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const user = userEvent.setup()
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <App />
      </MemoryRouter>
    </QueryClientProvider>,
  )
  return user
}

export async function region(name: string) {
  return within(await screen.findByRole('region', { name }))
}
