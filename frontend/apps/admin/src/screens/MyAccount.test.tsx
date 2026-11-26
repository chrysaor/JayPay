import { fakeRoutes } from '@jaypay/api/testing'
import { describe, expect, test } from 'vitest'
import { region, renderApp, signedInAs } from '../test/render'

describe('my account', () => {
  test('shows who is signed in and with which role', async () => {
    fakeRoutes(signedInAs('VIEWER'))
    renderApp('/account')

    const account = await region('Account')
    expect(account.getByText('jay')).toBeInTheDocument()
    expect(account.getByText('Viewer')).toBeInTheDocument()
  })

  test('sends the password and two-factor sign-in to the Keycloak account console', async () => {
    const requests = fakeRoutes(signedInAs('VIEWER'))
    renderApp('/account')

    const security = await region('Password and two-factor sign-in')
    expect(security.getByRole('link', { name: 'Open my Keycloak account' })).toHaveAttribute(
      'href',
      '/realms/staff/account',
    )
    expect(security.queryByLabelText('Current password')).not.toBeInTheDocument()
    expect(requests.filter((r) => r.url.startsWith('/api/'))).toEqual([])
  })
})
