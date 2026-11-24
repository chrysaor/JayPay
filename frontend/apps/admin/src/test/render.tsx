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
