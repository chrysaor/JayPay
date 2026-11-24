import { fakeRoutes, jsonBody } from '@jaypay/api/testing'
import { describe, expect, test } from 'vitest'
import { region, renderApp, signedInRoutes } from '../test/render'

const remitRoute = 'POST /api/remittance/remittance/request'

const remittance = {
  remittanceRequestId: '5',
  remittanceFromMembershipId: '1',
  toBankName: null,
  toBankAccountNumber: null,
  remittanceType: 0,
  amount: 5000,
  remittanceStatus: 'success',
}

describe('send money', () => {
  test('sends money to another member from the signed-in member', async () => {
    const requests = fakeRoutes({ ...signedInRoutes, [remitRoute]: { body: remittance } })
    const user = renderApp('/send', { signedIn: true })

    const form = await region('Send money')
    await user.type(form.getByLabelText('Recipient membership ID'), '2')
    await user.type(form.getByLabelText('Amount'), '5000')
    await user.click(form.getByRole('button', { name: 'Send money' }))

    expect(await form.findByRole('status')).toHaveTextContent('Sent ₩5,000. Status: success')
    const sent = requests.find((r) => r.url.endsWith('/remittance/request'))
    expect(sent?.contentType).toBe('application/json')
    expect(jsonBody(sent)).toEqual({ fromMembershipId: '1', amount: 5000, remittanceType: 0, toMembershipId: '2' })
  })

  test('sends money to a bank account', async () => {
    const requests = fakeRoutes({
      ...signedInRoutes,
      [remitRoute]: { body: { ...remittance, remittanceType: 1, toBankName: 'Shinhan Bank' } },
    })
    const user = renderApp('/send', { signedIn: true })

    const form = await region('Send money')
    await user.click(form.getByLabelText('Bank account'))
    expect(form.queryByLabelText('Recipient membership ID')).not.toBeInTheDocument()
    await user.type(form.getByLabelText('Bank name'), 'Shinhan Bank')
    await user.type(form.getByLabelText('Account number'), '987-654')
    await user.type(form.getByLabelText('Amount'), '5000')
    await user.click(form.getByRole('button', { name: 'Send money' }))

    expect(await form.findByRole('status')).toHaveTextContent('Sent ₩5,000')
    expect(jsonBody(requests.find((r) => r.url.endsWith('/remittance/request')))).toEqual({
      fromMembershipId: '1',
      amount: 5000,
      remittanceType: 1,
      toBankName: 'Shinhan Bank',
      toBankAccountNumber: '987-654',
    })
  })

  test('a transfer the backend refuses is reported', async () => {
    fakeRoutes({ ...signedInRoutes, [remitRoute]: { body: undefined } })
    const user = renderApp('/send', { signedIn: true })

    const form = await region('Send money')
    await user.type(form.getByLabelText('Recipient membership ID'), '2')
    await user.type(form.getByLabelText('Amount'), '5000')
    await user.click(form.getByRole('button', { name: 'Send money' }))

    expect(await form.findByRole('alert')).toHaveTextContent('empty response')
  })
})
