import { fakeRoutes, jsonBody } from '@jaypay/api/testing'
import { screen, within } from '@testing-library/react'
import { describe, expect, test } from 'vitest'
import { region, renderApp, signedInAs, staffAccount } from '../test/render'

const listRoute = 'GET /api/auth/staff/accounts'
const createRoute = 'POST /api/auth/staff/accounts'
const bobRoute = 'PUT /api/auth/staff/accounts/bob'

const jay = staffAccount('ADMIN', 'jay')
const bob = { ...staffAccount('VIEWER', 'bob'), mfaEnabled: true }
const eve = { ...staffAccount('OPERATOR', 'eve'), active: false }

async function row(username: string) {
  return within(await screen.findByRole('row', { name: new RegExp(`^${username}\\b`) }))
}

describe('staff accounts', () => {
  test('lists every account with its role, status and MFA', async () => {
    fakeRoutes({ ...signedInAs('ADMIN'), [listRoute]: { body: [bob, eve, jay] } })
    renderApp('/staff')

    const bobRow = await row('bob')
    expect(bobRow.getByLabelText('Role for bob')).toHaveValue('VIEWER')
    expect(bobRow.getByText('Active')).toBeInTheDocument()
    expect(bobRow.getByLabelText('MFA for bob')).toBeChecked()
    const eveRow = await row('eve')
    expect(eveRow.getByLabelText('Role for eve')).toHaveValue('OPERATOR')
    expect(eveRow.getByText('Inactive')).toBeInTheDocument()
    expect(eveRow.getByLabelText('MFA for eve')).not.toBeChecked()
    expect(screen.getAllByText('MFA is recorded only. Sign-in does not ask for a second step yet.')).not.toHaveLength(0)
  })

  test('the signed-in admin cannot demote or deactivate their own account', async () => {
    fakeRoutes({ ...signedInAs('ADMIN'), [listRoute]: { body: [bob, jay] } })
    renderApp('/staff')

    const own = await row('jay')
    expect(own.getByLabelText('Role for jay')).toBeDisabled()
    expect(own.getByRole('button', { name: 'Deactivate' })).toBeDisabled()
    expect(own.getByLabelText('MFA for jay')).toBeEnabled()
    expect((await row('bob')).getByRole('button', { name: 'Deactivate' })).toBeEnabled()
  })

  test('creates an account and adds it to the list', async () => {
    const carol = { ...staffAccount('OPERATOR', 'carol'), mfaEnabled: true }
    const requests = fakeRoutes({
      ...signedInAs('ADMIN'),
      [listRoute]: { body: [bob, jay] },
      [createRoute]: { body: carol },
    })
    const user = renderApp('/staff')

    const form = await region('New account')
    await user.type(form.getByLabelText('Username'), 'carol')
    await user.type(form.getByLabelText('Password'), 'Correct-Horse-9')
    await user.selectOptions(form.getByLabelText('Role'), 'Operator')
    await user.click(form.getByLabelText('MFA enabled'))
    await user.click(form.getByRole('button', { name: 'Create account' }))

    expect(await form.findByRole('status')).toHaveTextContent('Created carol.')
    expect((await row('carol')).getByLabelText('Role for carol')).toHaveValue('OPERATOR')
    expect(screen.getAllByRole('row').slice(1).map((r) => r.firstChild?.textContent)).toEqual(['bob', 'carol', 'jay (you)'])
    expect(jsonBody(requests.find((r) => r.method === 'POST'))).toEqual({
      username: 'carol',
      password: 'Correct-Horse-9',
      role: 'OPERATOR',
      mfaEnabled: true,
    })
  })

  test('a rejected account shows the message and every broken password rule', async () => {
    fakeRoutes({
      ...signedInAs('ADMIN'),
      [listRoute]: { body: [jay] },
      [createRoute]: {
        status: 400,
        body: {
          error: 'WEAK_PASSWORD',
          message: 'The password does not meet the rules.',
          violations: [{ rule: 'CONTAINS_USERNAME', message: 'Do not include the username.' }],
        },
      },
    })
    const user = renderApp('/staff')

    const form = await region('New account')
    await user.type(form.getByLabelText('Username'), 'carol')
    await user.type(form.getByLabelText('Password'), 'carol-Password-1')
    await user.click(form.getByRole('button', { name: 'Create account' }))

    const alert = await form.findByRole('alert')
    expect(alert).toHaveTextContent('The password does not meet the rules.')
    expect(within(alert).getByRole('listitem')).toHaveTextContent('Do not include the username.')
  })

  test('changing a role sends the whole access record and shows the saved account', async () => {
    const requests = fakeRoutes({
      ...signedInAs('ADMIN'),
      [listRoute]: { body: [bob, jay] },
      [bobRoute]: { body: { ...bob, role: 'OPERATOR' } },
    })
    const user = renderApp('/staff')

    await user.selectOptions((await row('bob')).getByLabelText('Role for bob'), 'Operator')

    expect(await (await row('bob')).findByDisplayValue('Operator')).toBeInTheDocument()
    expect(jsonBody(requests.find((r) => r.method === 'PUT'))).toEqual({ role: 'OPERATOR', active: true, mfaEnabled: true })
  })

  test('deactivating and turning off MFA send the change for that account', async () => {
    const requests = fakeRoutes({
      ...signedInAs('ADMIN'),
      [listRoute]: { body: [bob, jay] },
      [bobRoute]: { body: { ...bob, active: false } },
    })
    const user = renderApp('/staff')

    await user.click((await row('bob')).getByRole('button', { name: 'Deactivate' }))

    expect(await (await row('bob')).findByText('Inactive')).toBeInTheDocument()
    expect((await row('bob')).getByRole('button', { name: 'Activate' })).toBeInTheDocument()
    expect(jsonBody(requests.find((r) => r.method === 'PUT'))).toEqual({ role: 'VIEWER', active: false, mfaEnabled: true })

    await user.click((await row('bob')).getByLabelText('MFA for bob'))

    expect(jsonBody(requests.filter((r) => r.method === 'PUT')[1])).toEqual({
      role: 'VIEWER',
      active: false,
      mfaEnabled: false,
    })
  })

  test('a change the server refuses shows its message', async () => {
    fakeRoutes({
      ...signedInAs('ADMIN'),
      [listRoute]: { body: [bob, jay] },
      [bobRoute]: { status: 404, body: { error: 'NOT_FOUND', message: 'No staff account is named bob.' } },
    })
    const user = renderApp('/staff')

    await user.click((await row('bob')).getByRole('button', { name: 'Deactivate' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('No staff account is named bob.')
  })
})
