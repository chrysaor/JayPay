import { fakeRoutes, jsonBody } from '@jaypay/api/testing'
import { within } from '@testing-library/react'
import { describe, expect, test } from 'vitest'
import { region, renderApp, signedInAs, staffToken } from '../test/render'

const passwordRoute = 'PUT /api/auth/staff/me/password'

async function changePassword(user: ReturnType<typeof renderApp>, next = 'Brand-New-Pass-42', confirmation = next) {
  const form = await region('Change password')
  await user.type(form.getByLabelText('Current password'), 'Old-Password-41')
  await user.type(form.getByLabelText('New password'), next)
  await user.type(form.getByLabelText('Confirm new password'), confirmation)
  await user.click(form.getByRole('button', { name: 'Change password' }))
  return form
}

describe('my account', () => {
  test('shows who is signed in and with which role', async () => {
    fakeRoutes(signedInAs('VIEWER'))
    renderApp('/account')

    const account = await region('Account')
    expect(account.getByText('jay')).toBeInTheDocument()
    expect(account.getByText('Viewer')).toBeInTheDocument()
  })

  test('changes the password of the signed-in staff member', async () => {
    const requests = fakeRoutes({ ...signedInAs('VIEWER'), [passwordRoute]: { status: 204 } })
    const user = renderApp('/account')

    const form = await changePassword(user)

    expect(await form.findByRole('status')).toHaveTextContent('Password changed.')
    const sent = requests.find((r) => r.url.endsWith('/me/password'))
    expect(sent?.method).toBe('PUT')
    expect(sent?.authorization).toBe(`Bearer ${staffToken}`)
    expect(jsonBody(sent)).toEqual({ currentPassword: 'Old-Password-41', newPassword: 'Brand-New-Pass-42' })
  })

  test('a weak password lists every rule it breaks', async () => {
    fakeRoutes({
      ...signedInAs(),
      [passwordRoute]: {
        status: 400,
        body: {
          error: 'WEAK_PASSWORD',
          message: 'The password does not meet the rules.',
          violations: [
            { rule: 'LENGTH', message: 'Use at least 12 characters.' },
            { rule: 'SYMBOL', message: 'Add a symbol.' },
          ],
        },
      },
    })
    const user = renderApp('/account')

    const form = await changePassword(user, 'short')

    const alert = await form.findByRole('alert')
    expect(alert).toHaveTextContent('The password does not meet the rules.')
    expect(within(alert).getAllByRole('listitem').map((item) => item.textContent)).toEqual([
      'Use at least 12 characters.',
      'Add a symbol.',
    ])
  })

  test('a wrong current password shows the server message', async () => {
    fakeRoutes({
      ...signedInAs(),
      [passwordRoute]: { status: 400, body: { error: 'WRONG_PASSWORD', message: 'The current password is wrong.' } },
    })
    const user = renderApp('/account')

    const form = await changePassword(user)

    expect(await form.findByRole('alert')).toHaveTextContent('The current password is wrong.')
  })

  test('a confirmation that does not match is caught before anything is sent', async () => {
    const requests = fakeRoutes(signedInAs())
    const user = renderApp('/account')

    const form = await changePassword(user, 'Brand-New-Pass-42', 'Brand-New-Pass-43')

    expect(await form.findByRole('alert')).toHaveTextContent('The new passwords do not match.')
    expect(requests.filter((r) => r.url.endsWith('/me/password'))).toEqual([])
  })
})
