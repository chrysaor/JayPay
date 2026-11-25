import { fakeRoutes, type Reply } from '@jaypay/api/testing'
import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, test, vi } from 'vitest'
import { leaveFor } from './leave'
import {
  member,
  pendingKey,
  pendingSignIn,
  renderApp,
  signedInAs,
  staffToken,
  testCode,
  testVerifier,
  tokenReply,
  tokenRoute,
} from './test/render'

const memberRoute = 'GET /api/membership/memberships/1'
const queueRoute = 'GET /api/payment/payment/normal-status'
const endpoints = '/realms/staff/protocol/openid-connect'

function menuLabels() {
  const menu = screen.getByRole('navigation', { name: 'Admin menu' })
  return within(menu)
    .getAllByRole('link')
    .map((link) => link.textContent)
}

function lastExit() {
  const url = vi.mocked(leaveFor).mock.lastCall?.[0] ?? ''
  const [path, query = ''] = url.split('?')
  return { path, params: new URLSearchParams(query) }
}

function pending() {
  return JSON.parse(sessionStorage.getItem(pendingKey) ?? 'null') as {
    state: string
    verifier: string
    returnTo: string
  } | null
}

async function challengeOf(verifier: string) {
  const digest = new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier)))
  return btoa(String.fromCharCode(...digest)).replaceAll('+', '-').replaceAll('/', '_').replace(/=+$/, '')
}

function formOf(body: string | null | undefined) {
  return Object.fromEntries(new URLSearchParams(body ?? ''))
}

describe('signing in', () => {
  test('a visitor who is not signed in is sent to Keycloak with a PKCE challenge and nothing else is called', async () => {
    const requests = fakeRoutes({})
    renderApp('/settlement?status=open')

    await waitFor(() => {
      expect(leaveFor).toHaveBeenCalledOnce()
    })
    const { path, params } = lastExit()
    const started = pending()
    expect(path).toBe(`${endpoints}/auth`)
    expect(params.get('client_id')).toBe('jaypay-admin-console')
    expect(params.get('response_type')).toBe('code')
    expect(params.get('scope')).toBe('openid')
    expect(params.get('redirect_uri')).toBe(`${window.location.origin}/callback`)
    expect(params.get('code_challenge_method')).toBe('S256')
    expect(started?.returnTo).toBe('/settlement?status=open')
    expect(params.get('state')).toBe(started?.state)
    expect(params.get('code_challenge')).toBe(await challengeOf(started?.verifier ?? ''))
    expect(started?.state).not.toBe(started?.verifier)
    expect(screen.queryByRole('navigation', { name: 'Admin menu' })).not.toBeInTheDocument()
    expect(requests).toEqual([])
  })

  test('the callback trades the code for tokens and opens the screen the visitor asked for', async () => {
    const requests = fakeRoutes({ ...signedInAs(), [queueRoute]: { body: [] } })
    renderApp('/settlement')

    expect(await screen.findByRole('heading', { name: 'Settlement' })).toBeInTheDocument()
    const exchange = requests.find((r) => r.url === `${endpoints}/token`)
    expect(exchange?.contentType).toBe('application/x-www-form-urlencoded')
    expect(exchange?.authorization).toBeUndefined()
    expect(formOf(exchange?.body)).toEqual({
      client_id: 'jaypay-admin-console',
      grant_type: 'authorization_code',
      code: testCode,
      redirect_uri: `${window.location.origin}/callback`,
      code_verifier: testVerifier,
    })
    expect(requests.find((r) => r.url.endsWith('/normal-status'))?.authorization).toBe(`Bearer ${staffToken}`)
    expect(pending()).toBeNull()
    expect(localStorage.length).toBe(0)
    expect(JSON.stringify(sessionStorage)).not.toContain(staffToken)
  })

  test('signing in from the start opens the member search', async () => {
    fakeRoutes(signedInAs())
    renderApp('/')

    expect(await screen.findByRole('heading', { name: 'Members' })).toBeInTheDocument()
  })

  test('a callback whose state does not match this tab is refused without calling Keycloak', async () => {
    pendingSignIn('/members', 'state-from-this-tab')
    const requests = fakeRoutes({ [tokenRoute]: tokenReply(['admin']) })
    renderApp('/callback?code=stolen&state=state-from-elsewhere')

    expect(await screen.findByRole('alert')).toHaveTextContent('This sign-in did not start in this tab. Sign in again.')
    expect(requests).toEqual([])
    expect(pending()).toBeNull()
    expect(screen.queryByRole('navigation', { name: 'Admin menu' })).not.toBeInTheDocument()
  })

  test('a callback this tab never started is refused', async () => {
    const requests = fakeRoutes({ [tokenRoute]: tokenReply(['admin']) })
    renderApp('/callback?code=stolen&state=anything')

    expect(await screen.findByRole('alert')).toHaveTextContent('This sign-in did not start in this tab. Sign in again.')
    expect(requests).toEqual([])
  })

  test('a callback replayed after it was used is refused', async () => {
    const requests = fakeRoutes(signedInAs())
    renderApp('/members')
    await screen.findByRole('heading', { name: 'Members' })

    renderApp(`/callback?code=${testCode}&state=state-from-this-tab`)

    expect(await screen.findByRole('alert')).toHaveTextContent('This sign-in did not start in this tab. Sign in again.')
    expect(requests.filter((r) => r.url === `${endpoints}/token`)).toHaveLength(1)
  })

  test('an error Keycloak sends back is shown', async () => {
    pendingSignIn()
    fakeRoutes({})
    renderApp('/callback?error=access_denied&error_description=Account%20is%20disabled&state=state-from-this-tab')

    expect(await screen.findByRole('alert')).toHaveTextContent('Account is disabled')
  })

  test('a code Keycloak refuses to trade shows a message and offers to sign in again', async () => {
    const requests = fakeRoutes({ [tokenRoute]: { status: 400, body: { error: 'invalid_grant' } } })
    pendingSignIn()
    const user = renderApp(`/callback?code=${testCode}&state=state-from-this-tab`)

    expect(await screen.findByRole('alert')).toHaveTextContent('The sign-in server refused the request. Sign in again.')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(lastExit().path).toBe(`${endpoints}/auth`)
    expect(requests).toHaveLength(1)
  })
})

describe('the signed-in console', () => {
  test('every screen is in the menu, with no staff accounts screen', async () => {
    fakeRoutes(signedInAs('ADMIN'))
    renderApp('/members')

    await screen.findByRole('heading', { name: 'Members' })
    expect(menuLabels()).toEqual(['Members', 'Regional money', 'Settlement', 'My account'])
  })

  test('the staff accounts address is gone', async () => {
    fakeRoutes(signedInAs('ADMIN'))
    renderApp('/staff')

    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument()
  })

  test('an unknown address shows a not-found page', async () => {
    fakeRoutes(signedInAs())
    renderApp('/nowhere')

    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument()
  })

  test('the sidebar shows the username and the highest realm role in the token', async () => {
    pendingSignIn()
    fakeRoutes({ [tokenRoute]: tokenReply(['default-roles-staff', 'viewer', 'admin'], 120, 'olivia') })
    renderApp('/members')

    const sidebar = within(await screen.findByRole('complementary'))
    expect(sidebar.getByText('olivia')).toBeInTheDocument()
    expect(sidebar.getByText('Admin', { selector: '.staff-role' })).toBeInTheDocument()
  })

  test('an account without a console role sees why and can sign out', async () => {
    pendingSignIn()
    const requests = fakeRoutes({ [tokenRoute]: tokenReply(['default-roles-staff'], 120, 'newcomer') })
    const user = renderApp('/members')

    expect(await screen.findByRole('heading', { name: 'No access to JayPay Admin' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('newcomer has no admin console role.')
    expect(requests.filter((r) => r.url.startsWith('/api/'))).toEqual([])

    await user.click(screen.getByRole('button', { name: 'Sign out' }))
    expect(lastExit().path).toBe(`${endpoints}/logout`)
  })

  test('every request after sign-in carries the access token', async () => {
    const requests = fakeRoutes({ ...signedInAs(), [memberRoute]: { body: member } })
    renderApp('/members?by=id&q=1')

    await screen.findByRole('row', { name: /Hong Gildong/ })
    expect(requests.find((r) => r.url.endsWith('/memberships/1'))?.authorization).toBe(`Bearer ${staffToken}`)
  })
})

describe('signing out', () => {
  test('signing out ends the Keycloak session and forgets the tokens', async () => {
    const requests = fakeRoutes({ ...signedInAs(), [memberRoute]: { body: member } })
    const user = renderApp('/members')

    await user.click(await screen.findByRole('button', { name: 'Sign out' }))

    const { path, params } = lastExit()
    expect(path).toBe(`${endpoints}/logout`)
    expect(params.get('client_id')).toBe('jaypay-admin-console')
    expect(params.get('id_token_hint')).toBe('id-token-1')
    expect(params.get('post_logout_redirect_uri')).toBe(`${window.location.origin}/signed-out`)
    expect(await screen.findByText('You signed out.')).toBeInTheDocument()
    expect(screen.queryByRole('navigation', { name: 'Admin menu' })).not.toBeInTheDocument()
    expect(requests.filter((r) => r.url.startsWith('/api/'))).toEqual([])
  })

  test('the page Keycloak returns to after sign-out offers to sign in again', async () => {
    fakeRoutes({})
    const user = renderApp('/signed-out')

    expect(await screen.findByText('You signed out.')).toBeInTheDocument()
    expect(leaveFor).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(lastExit().path).toBe(`${endpoints}/auth`)
    expect(pending()?.returnTo).toBe('/members')
  })
})

describe('the end of a session', () => {
  test('a request the server no longer accepts ends the session without sending the visitor away', async () => {
    fakeRoutes({
      ...signedInAs(),
      [queueRoute]: { status: 401, body: { error: 'UNAUTHENTICATED', message: 'Sign in again.' } },
    })
    const user = renderApp('/settlement')

    expect(await screen.findByText('Your session has ended. Sign in again.')).toBeInTheDocument()
    expect(leaveFor).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(lastExit().path).toBe(`${endpoints}/auth`)
    expect(pending()?.returnTo).toBe('/settlement')
  })

  test('the tokens are refreshed before the access token expires', async () => {
    const requests = fakeRoutes({ ...signedInAs('OPERATOR', 31), [memberRoute]: { body: member } })
    renderApp('/members')
    await screen.findByRole('heading', { name: 'Members' })

    await waitFor(
      () => {
        expect(requests.filter((r) => r.url === `${endpoints}/token`)).toHaveLength(2)
      },
      { timeout: 3000 },
    )
    const refresh = requests.filter((r) => r.url === `${endpoints}/token`)[1]
    expect(formOf(refresh?.body)).toEqual({
      client_id: 'jaypay-admin-console',
      grant_type: 'refresh_token',
      refresh_token: 'refresh-operator-offline_access',
    })
    expect(screen.getByRole('heading', { name: 'Members' })).toBeInTheDocument()
  })

  test('a refresh Keycloak refuses ends the session', async () => {
    const routes: Record<string, Reply> = { ...signedInAs('OPERATOR', 31) }
    fakeRoutes(routes)
    renderApp('/members')
    await screen.findByRole('heading', { name: 'Members' })
    routes[tokenRoute] = { status: 400, body: { error: 'invalid_grant' } }

    expect(await screen.findByText('Your session has ended. Sign in again.', {}, { timeout: 3000 })).toBeInTheDocument()
    expect(leaveFor).not.toHaveBeenCalled()
  })
})
