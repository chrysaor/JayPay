import { fakeRoutes, jsonBody } from '@jaypay/api/testing'
import { screen } from '@testing-library/react'
import { describe, expect, test } from 'vitest'
import { region, renderApp, signedInRoutes } from '../test/render'

const accountRoute = 'GET /api/banking/banking/account/1'
const registerRoute = 'POST /api/banking/banking/account/register'

const account = {
  registeredBankAccountId: '7',
  membershipId: '1',
  bankName: 'Kookmin Bank',
  bankAccountNumber: '123-456',
  linkedStatusIsValid: true,
  aggregateIdentifier: '',
}

describe('bank account', () => {
  test('shows the linked bank account', async () => {
    fakeRoutes({ ...signedInRoutes, [accountRoute]: { body: account } })
    renderApp('/account', { signedIn: true })

    const linked = await region('Linked account')
    expect(await linked.findByText('Kookmin Bank')).toBeInTheDocument()
    expect(linked.getByText('123-456')).toBeInTheDocument()
    expect(linked.getByText('Linked')).toBeInTheDocument()
  })

  test('a member without an account links one', async () => {
    const routes = { ...signedInRoutes, [accountRoute]: { status: 404 }, [registerRoute]: { body: account } }
    const requests = fakeRoutes(routes)
    const user = renderApp('/account', { signedIn: true })

    const form = await region('Link a bank account')
    await user.type(form.getByLabelText('Bank name'), 'Kookmin Bank')
    await user.type(form.getByLabelText('Account number'), '123-456')
    await user.click(form.getByRole('button', { name: 'Link account' }))

    expect(await (await region('Linked account')).findByText('123-456')).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Link a bank account' })).not.toBeInTheDocument()
    expect(jsonBody(requests.find((r) => r.url.endsWith('/register')))).toEqual({
      membershipId: '1',
      bankName: 'Kookmin Bank',
      bankAccountNumber: '123-456',
      valid: true,
    })
  })

  test('a rejected link shows the server error', async () => {
    fakeRoutes({ ...signedInRoutes, [accountRoute]: { status: 404 }, [registerRoute]: { status: 500 } })
    const user = renderApp('/account', { signedIn: true })

    const form = await region('Link a bank account')
    await user.type(form.getByLabelText('Bank name'), 'Kookmin Bank')
    await user.type(form.getByLabelText('Account number'), '123-456')
    await user.click(form.getByRole('button', { name: 'Link account' }))

    expect(await form.findByRole('alert')).toHaveTextContent('HTTP 500')
  })

  test('a failed lookup shows the error and still offers to link an account', async () => {
    fakeRoutes({ ...signedInRoutes, [accountRoute]: { status: 500 } })
    renderApp('/account', { signedIn: true })

    expect(await screen.findByRole('alert')).toHaveTextContent('HTTP 500')
    expect(await region('Link a bank account')).toBeTruthy()
  })
})
