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

export const pendingKey = 'jaypay.admin.sign-in'
export const tokenRoute = 'POST /realms/staff/protocol/openid-connect/token'
export const testState = 'state-from-this-tab'
export const testVerifier = 'verifier-from-this-tab'
export const testCode = 'code-from-keycloak'

function base64url(value: unknown) {
  return btoa(JSON.stringify(value)).replaceAll('+', '-').replaceAll('/', '_').replace(/=+$/, '')
}

export function accessToken(realmRoles: string[], username = 'jay') {
  return [
    base64url({ alg: 'RS256' }),
    base64url({ preferred_username: username, realm_access: { roles: realmRoles } }),
    'signature',
  ].join('.')
}

export function tokenReply(realmRoles: string[], expiresIn = 120, username = 'jay') {
  return {
    body: {
      access_token: accessToken(realmRoles, username),
      refresh_token: `refresh-${realmRoles.join('-')}`,
      id_token: 'id-token-1',
      expires_in: expiresIn,
      token_type: 'Bearer',
    },
  }
}

function realmRolesOf(role: TestRole) {
  return role === 'OPERATOR' ? ['operator', 'offline_access'] : [role.toLowerCase()]
}

export const staffToken = accessToken(realmRolesOf('OPERATOR'))

export function pendingSignIn(returnTo = '/members', state = testState) {
  sessionStorage.setItem(pendingKey, JSON.stringify({ state, verifier: testVerifier, returnTo }))
}

export function signedInAs(role: TestRole = 'OPERATOR', expiresIn = 120) {
  pendingSignIn()
  return { [tokenRoute]: tokenReply(realmRolesOf(role), expiresIn) }
}

function startOf(path: string) {
  const pending = sessionStorage.getItem(pendingKey)
  if (!pending || path.startsWith('/callback')) return path
  sessionStorage.setItem(pendingKey, JSON.stringify({ ...(JSON.parse(pending) as object), returnTo: path }))
  return `/callback?code=${testCode}&state=${testState}`
}

export function renderApp(path: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const user = userEvent.setup()
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[startOf(path)]}>
        <App />
      </MemoryRouter>
    </QueryClientProvider>,
  )
  return user
}

export async function region(name: string) {
  return within(await screen.findByRole('region', { name }))
}
