import { fakeRoutes, jsonBody, type Reply } from '@jaypay/api/testing'
import { screen, within } from '@testing-library/react'
import { describe, expect, test } from 'vitest'
import {
  hoursFromNow,
  meRoute,
  member,
  renderApp,
  signedInAs,
  staffAccount,
  staffToken,
  storageKey,
  storedSession,
} from './test/render'

const signInRoute = 'POST /api/auth/staff/sign-in'
const memberRoute = 'GET /api/membership/memberships/1'
const queueRoute = 'GET /api/payment/payment/normal-status'
const issued = {
  status: 'SIGNED_IN',
  token: 'issued-token',
  expiresAt: hoursFromNow(8),
  account: staffAccount('OPERATOR', 'olivia'),
}

async function signIn(user: ReturnType<typeof renderApp>, username = 'olivia', password = 'Correct-Horse-9') {
  await user.type(await screen.findByLabelText('Username'), username)
  await user.type(screen.getByLabelText('Password'), password)
  await user.click(screen.getByRole('button', { name: 'Sign in' }))
}

function menuLabels() {
  const menu = screen.getByRole('navigation', { name: 'Admin menu' })
  return within(menu)
    .getAllByRole('link')
    .map((link) => link.textContent)
}

describe('signing in', () => {
  test('a visitor who is not signed in sees only the sign-in screen', async () => {
    const requests = fakeRoutes({})
    renderApp('/settlement')

    expect(await screen.findByRole('heading', { name: 'Sign in to JayPay Admin' })).toBeInTheDocument()
    expect(screen.queryByRole('navigation', { name: 'Admin menu' })).not.toBeInTheDocument()
    expect(requests).toEqual([])
  })

  test('signing in opens the screen the visitor asked for and remembers the session', async () => {
    const requests = fakeRoutes({ [signInRoute]: { body: issued }, [queueRoute]: { body: [] } })
    const user = renderApp('/settlement')

    await signIn(user)

    expect(await screen.findByRole('heading', { name: 'Settlement' })).toBeInTheDocument()
    const sent = requests.find((r) => r.url.endsWith('/sign-in'))
    expect(jsonBody(sent)).toEqual({ username: 'olivia', password: 'Correct-Horse-9' })
    expect(sent?.authorization).toBeUndefined()
    expect(requests.find((r) => r.url.endsWith('/normal-status'))?.authorization).toBe('Bearer issued-token')
    expect(storedSession()).toEqual({ token: 'issued-token', expiresAt: issued.expiresAt, account: issued.account })
  })

  test('signing in from the start opens the member search', async () => {
    fakeRoutes({ [signInRoute]: { body: issued } })
    const user = renderApp('/')

    await signIn(user)

    expect(await screen.findByRole('heading', { name: 'Members' })).toBeInTheDocument()
  })

  test('wrong credentials show the server message and keep the visitor on the sign-in screen', async () => {
    fakeRoutes({
      [signInRoute]: {
        status: 401,
        body: {
          error: 'INVALID_CREDENTIALS',
          message: 'The username or password is wrong, or the account is inactive.',
        },
      },
    })
    const user = renderApp('/members')

    await signIn(user, 'olivia', 'wrong')

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The username or password is wrong, or the account is inactive.',
    )
    expect(screen.getByRole('heading', { name: 'Sign in to JayPay Admin' })).toBeInTheDocument()
    expect(screen.queryByText('Your session has ended. Sign in again.')).not.toBeInTheDocument()
    expect(storedSession()).toBeNull()
  })

  test('a sign-in step the console does not support is explained', async () => {
    fakeRoutes({ [signInRoute]: { body: { status: 'MFA_REQUIRED', challenge: 'c-1' } } })
    const user = renderApp('/members')

    await signIn(user)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'This account needs a sign-in step this console does not support yet.',
    )
    expect(storedSession()).toBeNull()
  })
})

describe('the signed-in console', () => {
  test('restores the stored session with the role the server reports now', async () => {
    localStorage.setItem(
      storageKey,
      JSON.stringify({ token: staffToken, expiresAt: hoursFromNow(8), account: staffAccount('ADMIN') }),
    )
    const requests = fakeRoutes({ [meRoute]: { body: staffAccount('VIEWER') } })
    renderApp('/members')

    const sidebar = within(await screen.findByRole('complementary'))
    expect(await sidebar.findByText('jay')).toBeInTheDocument()
    expect(sidebar.getByText('Viewer')).toBeInTheDocument()
    expect(menuLabels()).not.toContain('Staff accounts')
    expect(requests.find((r) => r.url.endsWith('/staff/me'))?.authorization).toBe(`Bearer ${staffToken}`)
  })

  test('every viewer and operator screen is in the menu, with no staff accounts', async () => {
    fakeRoutes(signedInAs('OPERATOR'))
    renderApp('/members')

    await screen.findByRole('heading', { name: 'Members' })
    expect(menuLabels()).toEqual(['Members', 'Regional money', 'Settlement', 'My account'])
  })

  test('an admin also sees staff accounts in the menu', async () => {
    fakeRoutes({ ...signedInAs('ADMIN'), 'GET /api/auth/staff/accounts': { body: [] } })
    renderApp('/members')

    await screen.findByRole('heading', { name: 'Members' })
    expect(menuLabels()).toEqual(['Members', 'Regional money', 'Settlement', 'Staff accounts', 'My account'])
  })

  test('an operator who opens the staff accounts address sees a not-found page', async () => {
    fakeRoutes(signedInAs('OPERATOR'))
    renderApp('/staff')

    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument()
  })

  test('an unknown address shows a not-found page', async () => {
    fakeRoutes(signedInAs())
    renderApp('/nowhere')

    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument()
  })

  test('every request after sign-in carries the staff token', async () => {
    const requests = fakeRoutes({ ...signedInAs(), [memberRoute]: { body: member } })
    renderApp('/members?by=id&q=1')

    await screen.findByRole('row', { name: /Hong Gildong/ })
    expect(requests.find((r) => r.url.endsWith('/memberships/1'))?.authorization).toBe(`Bearer ${staffToken}`)
  })

  test('signing out returns to the sign-in screen and forgets the session', async () => {
    fakeRoutes(signedInAs())
    const user = renderApp('/members')

    await user.click(await screen.findByRole('button', { name: 'Sign out' }))

    expect(await screen.findByRole('heading', { name: 'Sign in to JayPay Admin' })).toBeInTheDocument()
    expect(screen.queryByText('Your session has ended. Sign in again.')).not.toBeInTheDocument()
    expect(storedSession()).toBeNull()
  })
})

describe('the end of a session', () => {
  test('a request the server no longer accepts ends the session and signing in again returns to the screen', async () => {
    const routes: Record<string, Reply> = {
      ...signedInAs(),
      [queueRoute]: { status: 401, body: { error: 'UNAUTHENTICATED', message: 'Sign in again.' } },
      [signInRoute]: { body: issued },
    }
    fakeRoutes(routes)
    const user = renderApp('/settlement')

    expect(await screen.findByText('Your session has ended. Sign in again.')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Sign in to JayPay Admin' })).toBeInTheDocument()
    expect(storedSession()).toBeNull()

    routes[queueRoute] = { body: [] }
    await signIn(user)

    expect(await screen.findByRole('heading', { name: 'Settlement' })).toBeInTheDocument()
  })

  test('a stored session past its expiry is not restored', async () => {
    const requests = fakeRoutes(signedInAs('OPERATOR', hoursFromNow(-1)))
    renderApp('/members')

    expect(await screen.findByText('Your session has ended. Sign in again.')).toBeInTheDocument()
    expect(requests).toEqual([])
    expect(storedSession()).toBeNull()
  })

  test('a token the server rejects on restore ends the session', async () => {
    signedInAs()
    fakeRoutes({ [meRoute]: { status: 401, body: { error: 'UNAUTHENTICATED', message: 'Sign in again.' } } })
    renderApp('/members')

    expect(await screen.findByText('Your session has ended. Sign in again.')).toBeInTheDocument()
    expect(storedSession()).toBeNull()
  })

  test('the session ends on its own when it expires', async () => {
    fakeRoutes(signedInAs('OPERATOR', new Date(Date.now() + 300).toISOString()))
    renderApp('/members')

    expect(await screen.findByRole('heading', { name: 'Members' })).toBeInTheDocument()
    expect(await screen.findByText('Your session has ended. Sign in again.', {}, { timeout: 2000 })).toBeInTheDocument()
    expect(storedSession()).toBeNull()
  })
})
