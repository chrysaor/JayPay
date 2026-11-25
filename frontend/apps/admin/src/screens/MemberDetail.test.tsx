import { fakeRoutes, jsonBody } from '@jaypay/api/testing'
import { screen } from '@testing-library/react'
import { describe, expect, test } from 'vitest'
import { member, region, renderApp, signedInAs } from '../test/render'

const findRoute = 'GET /api/membership/memberships/1'
const modifyRoute = 'PUT /api/membership/memberships/1'

describe('member detail', () => {
  test('shows the member in an edit form', async () => {
    fakeRoutes({ ...signedInAs(), [findRoute]: { body: member } })
    renderApp('/members/1')

    expect(await screen.findByRole('heading', { name: 'Member #1' })).toBeInTheDocument()
    const form = await region('Profile')
    expect(form.getByLabelText('Name')).toHaveValue('Hong Gildong')
    expect(form.getByLabelText('Email')).toHaveValue('hong@example.com')
    expect(form.getByLabelText('Address')).toHaveValue('Seoul')
    expect(form.getByLabelText('Active')).toBeChecked()
    expect(form.getByLabelText('Corporate account')).not.toBeChecked()
  })

  test('saves changes with the boolean fields the backend reads', async () => {
    const requests = fakeRoutes({
      ...signedInAs(),
      [findRoute]: { body: member },
      [modifyRoute]: { body: { ...member, email: 'gildong@example.com', valid: false } },
    })
    const user = renderApp('/members/1')

    const form = await region('Profile')
    const email = form.getByLabelText('Email')
    await user.clear(email)
    await user.type(email, 'gildong@example.com')
    await user.click(form.getByLabelText('Active'))
    await user.click(form.getByRole('button', { name: 'Save changes' }))

    expect(await form.findByRole('status')).toHaveTextContent('Saved.')
    const sent = requests.find((r) => r.method === 'PUT')
    expect(sent?.contentType).toBe('application/json')
    expect(jsonBody(sent)).toEqual({
      membershipId: '1',
      name: 'Hong Gildong',
      email: 'gildong@example.com',
      address: 'Seoul',
      valid: false,
      corp: false,
    })
  })

  test('a rejected save shows the server error', async () => {
    fakeRoutes({ ...signedInAs(), [findRoute]: { body: member }, [modifyRoute]: { status: 500 } })
    const user = renderApp('/members/1')

    const form = await region('Profile')
    await user.click(form.getByRole('button', { name: 'Save changes' }))

    expect(await form.findByRole('alert')).toHaveTextContent('HTTP 500')
  })

  test('a member that cannot be loaded shows the error', async () => {
    fakeRoutes({ ...signedInAs(), [findRoute]: { body: undefined } })
    renderApp('/members/1')

    expect(await screen.findByRole('alert')).toHaveTextContent('empty response')
  })

  test('a viewer sees the profile without a way to change it', async () => {
    fakeRoutes({ ...signedInAs('VIEWER'), [findRoute]: { body: member } })
    renderApp('/members/1')

    const form = await region('Profile')
    expect(form.getByLabelText('Name')).toBeDisabled()
    expect(form.getByLabelText('Email')).toBeDisabled()
    expect(form.getByLabelText('Address')).toBeDisabled()
    expect(form.getByLabelText('Active')).toBeDisabled()
    expect(form.getByLabelText('Corporate account')).toBeDisabled()
    expect(form.queryByRole('button', { name: 'Save changes' })).not.toBeInTheDocument()
  })
})
