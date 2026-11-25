import { fakeRoutes, jsonBody } from '@jaypay/api/testing'
import { screen } from '@testing-library/react'
import { describe, expect, test } from 'vitest'
import { member, renderApp, storedSessionValue } from '../test/render'

const issuedToken = { membershipId: '1', jwtToken: 'jwt-1', refreshToken: 'refresh-1' }

async function fillSignUp(user: ReturnType<typeof renderApp>) {
  await user.type(await screen.findByLabelText('Name'), 'Hong Gildong')
  await user.type(screen.getByLabelText('Email'), 'hong@example.com')
  await user.type(screen.getByLabelText('Address'), 'Seoul')
  await user.click(screen.getByLabelText('Corporate account'))
  await user.click(screen.getByRole('button', { name: 'Create account' }))
}

describe('sign up', () => {
  test('creates the membership, signs the member in and shows the new membership ID', async () => {
    const requests = fakeRoutes({
      'POST /api/membership/memberships': { body: { ...member, corp: true } },
      'POST /api/membership/memberships/login': { body: issuedToken },
    })
    const user = renderApp('/sign-up')

    await fillSignUp(user)

    expect(await screen.findByRole('status')).toHaveTextContent('Your membership ID is 1')
    expect(screen.getByRole('link', { name: 'Go to my wallet' })).toHaveAttribute('href', '/')
    expect(requests.map((r) => `${r.method} ${r.url} ${String(r.contentType)}`)).toEqual([
      'POST /api/membership/memberships application/json',
      'POST /api/membership/memberships/login application/json',
    ])
    expect(jsonBody(requests[0])).toEqual({
      name: 'Hong Gildong',
      email: 'hong@example.com',
      address: 'Seoul',
      corp: true,
    })
    expect(storedSessionValue()).toEqual(issuedToken)
  })

  test('a rejected sign up shows the server error', async () => {
    fakeRoutes({ 'POST /api/membership/memberships': { status: 500 } })
    const user = renderApp('/sign-up')

    await fillSignUp(user)

    expect(await screen.findByRole('alert')).toHaveTextContent('HTTP 500')
    expect(storedSessionValue()).toBeNull()
  })

  test('the sign-in page links to sign up', async () => {
    fakeRoutes({})
    renderApp('/sign-in')

    expect(await screen.findByRole('link', { name: 'Create an account' })).toHaveAttribute('href', '/sign-up')
  })
})
