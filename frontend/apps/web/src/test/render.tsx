import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { App } from '../App'

export const storedSession = { membershipId: '1', jwtToken: 'jwt-1', refreshToken: 'refresh-1' }

export const member = {
  membershipId: '1',
  name: 'Hong Gildong',
  email: 'hong@example.com',
  address: 'Seoul',
  valid: true,
  corp: false,
  refreshToken: 'refresh-1',
}

export const signedInRoutes = {
  'POST /api/membership/memberships/token-validate': { body: true },
  'GET /api/membership/memberships/1': { body: member },
}

export function renderApp(path: string, { signedIn = false } = {}) {
  if (signedIn) localStorage.setItem('jaypay.session', JSON.stringify(storedSession))
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

export function storedSessionValue(): unknown {
  return JSON.parse(localStorage.getItem('jaypay.session') ?? 'null')
}
