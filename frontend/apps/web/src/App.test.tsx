import { fakeRoutes, jsonBody, unreachableServer } from '@jaypay/api/testing'
import { screen } from '@testing-library/react'
import { describe, expect, test } from 'vitest'
import { member, renderApp, signedInRoutes, storedSessionValue } from './test/render'

const issuedToken = { membershipId: '1', jwtToken: 'jwt-1', refreshToken: 'refresh-1' }

describe('signing in', () => {
  test('a visitor who is not signed in lands on the sign-in page', async () => {
    fakeRoutes({})
    renderApp('/')

    expect(await screen.findByRole('heading', { name: 'Sign in to JayPay' })).toBeInTheDocument()
  })

  test('signing in with a membership ID opens the wallet and remembers the session', async () => {
    const requests = fakeRoutes({
      'POST /api/membership/memberships/login': { body: issuedToken },
      'GET /api/membership/memberships/1': { body: member },
      'POST /api/money/money/member-money': { body: [] },
    })
    const user = renderApp('/sign-in')

    await user.type(await screen.findByLabelText('Membership ID'), '1')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('heading', { name: 'Wallet' })).toBeInTheDocument()
    expect(await screen.findByText('Hong Gildong')).toBeInTheDocument()
    expect(jsonBody(requests.find((r) => r.url.endsWith('/login')))).toEqual({ membershipId: '1' })
    expect(storedSessionValue()).toEqual(issuedToken)
  })

  test('a membership that cannot sign in is told so', async () => {
    fakeRoutes({ 'POST /api/membership/memberships/login': { body: undefined } })
    const user = renderApp('/sign-in')

    await user.type(await screen.findByLabelText('Membership ID'), '9')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('This membership cannot sign in.')
  })

  test('a sign-in the server refuses shows the reason the server gave', async () => {
    fakeRoutes({
      'POST /api/membership/memberships/login': {
        status: 401,
        body: { error: 'MEMBERSHIP_NOT_VALID', message: 'The membership 9 is not valid, so it cannot sign in.' },
      },
    })
    const user = renderApp('/sign-in')

    await user.type(await screen.findByLabelText('Membership ID'), '9')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('The membership 9 is not valid, so it cannot sign in.')
  })

  test('an unreachable server is reported on sign in', async () => {
    unreachableServer()
    const user = renderApp('/sign-in')

    await user.type(await screen.findByLabelText('Membership ID'), '1')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not reach the server.')
  })
})

describe('returning members', () => {
  test('a stored session with a valid token goes straight to the wallet', async () => {
    const requests = fakeRoutes({ ...signedInRoutes, 'POST /api/money/money/member-money': { body: [] } })
    renderApp('/', { signedIn: true })

    expect(await screen.findByRole('heading', { name: 'Wallet' })).toBeInTheDocument()
    expect(jsonBody(requests.find((r) => r.url.endsWith('/token-validate')))).toEqual({ jwtToken: 'jwt-1' })
  })

  test('an expired token is refreshed and the new token is stored', async () => {
    const refreshed = { membershipId: '1', jwtToken: 'jwt-2', refreshToken: 'refresh-1' }
    const requests = fakeRoutes({
      ...signedInRoutes,
      'POST /api/membership/memberships/token-validate': { body: false },
      'POST /api/membership/memberships/refresh-token': { body: refreshed },
      'POST /api/money/money/member-money': { body: [] },
    })
    renderApp('/', { signedIn: true })

    expect(await screen.findByRole('heading', { name: 'Wallet' })).toBeInTheDocument()
    expect(jsonBody(requests.find((r) => r.url.endsWith('/refresh-token')))).toEqual({ refreshToken: 'refresh-1' })
    expect(storedSessionValue()).toEqual(refreshed)
  })

  test('a session that cannot be refreshed is cleared and the member signs in again', async () => {
    fakeRoutes({
      'POST /api/membership/memberships/token-validate': { body: false },
      'POST /api/membership/memberships/refresh-token': { body: undefined },
    })
    renderApp('/', { signedIn: true })

    expect(await screen.findByRole('heading', { name: 'Sign in to JayPay' })).toBeInTheDocument()
    expect(storedSessionValue()).toBeNull()
  })

  test('signing out forgets the session', async () => {
    fakeRoutes({ ...signedInRoutes, 'POST /api/money/money/member-money': { body: [] } })
    const user = renderApp('/', { signedIn: true })

    await user.click(await screen.findByRole('button', { name: 'Sign out' }))

    expect(await screen.findByRole('heading', { name: 'Sign in to JayPay' })).toBeInTheDocument()
    expect(storedSessionValue()).toBeNull()
  })

  test('the main menu links every screen', async () => {
    fakeRoutes({ ...signedInRoutes, 'POST /api/money/money/member-money': { body: [] } })
    renderApp('/', { signedIn: true })

    const menu = await screen.findByRole('navigation', { name: 'Main menu' })
    expect([...menu.querySelectorAll('a')].map((link) => link.textContent)).toEqual([
      'Wallet',
      'Bank account',
      'Send',
      'Pay',
    ])
  })
})

test('an unknown address shows a not-found page', async () => {
  fakeRoutes({})
  renderApp('/nowhere')

  expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument()
})
