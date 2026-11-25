import { fakeRoutes, jsonBody, type ReceivedRequest } from '@jaypay/api/testing'
import { waitFor } from '@testing-library/react'
import { describe, expect, test } from 'vitest'
import { region, renderApp, signedInRoutes } from '../test/render'

const remitRoute = 'POST /api/remittance/remittance/request'

const remittance = {
  remittanceId: '7c6f2a52-1b0e-4c1f-9a51-0d3b8f0e2a11',
  requestId: 'r-5',
  fromMembershipId: '1',
  remittanceType: 0,
  toMembershipId: '2',
  toBankName: null,
  toBankAccountNumber: null,
  amount: 5000,
  remittanceStatus: 'COMPLETED',
}

async function sendToMember(user: ReturnType<typeof renderApp>, amount = '5000') {
  const form = await region('Send money')
  await user.clear(form.getByLabelText('Recipient membership ID'))
  await user.type(form.getByLabelText('Recipient membership ID'), '2')
  await user.clear(form.getByLabelText('Amount'))
  await user.type(form.getByLabelText('Amount'), amount)
  await user.click(form.getByRole('button', { name: 'Send money' }))
  return form
}

function remittanceRequests(requests: ReceivedRequest[]) {
  return requests.filter((r) => r.url.endsWith('/remittance/request'))
}

describe('send money', () => {
  test('sends money to another member from the signed-in member with a request id', async () => {
    const requests = fakeRoutes({ ...signedInRoutes, [remitRoute]: { body: remittance } })
    const user = renderApp('/send', { signedIn: true })

    const form = await sendToMember(user)

    expect(await form.findByRole('status')).toHaveTextContent('Sent ₩5,000 to member 2.')
    const [sent] = remittanceRequests(requests)
    expect(sent?.contentType).toBe('application/json')
    const { requestId, ...body } = jsonBody(sent) as Record<string, unknown>
    expect(requestId).toMatch(/^[0-9a-f-]{36}$/)
    expect(body).toEqual({ fromMembershipId: '1', amount: 5000, remittanceType: 0, toMembershipId: '2' })
  })

  test('sends money to a bank account', async () => {
    const requests = fakeRoutes({
      ...signedInRoutes,
      [remitRoute]: {
        body: { ...remittance, remittanceType: 1, toMembershipId: null, toBankName: 'Shinhan Bank', toBankAccountNumber: '987-654' },
      },
    })
    const user = renderApp('/send', { signedIn: true })

    const form = await region('Send money')
    await user.click(form.getByLabelText('Bank account'))
    expect(form.queryByLabelText('Recipient membership ID')).not.toBeInTheDocument()
    await user.type(form.getByLabelText('Bank name'), 'Shinhan Bank')
    await user.type(form.getByLabelText('Account number'), '987-654')
    await user.type(form.getByLabelText('Amount'), '5000')
    await user.click(form.getByRole('button', { name: 'Send money' }))

    expect(await form.findByRole('status')).toHaveTextContent('Sent ₩5,000 to Shinhan Bank 987-654.')
    const { requestId, ...body } = jsonBody(remittanceRequests(requests)[0]) as Record<string, unknown>
    expect(requestId).toMatch(/^[0-9a-f-]{36}$/)
    expect(body).toEqual({
      fromMembershipId: '1',
      amount: 5000,
      remittanceType: 1,
      toBankName: 'Shinhan Bank',
      toBankAccountNumber: '987-654',
    })
  })

  test('a remittance above the balance shows that it was declined', async () => {
    fakeRoutes({ ...signedInRoutes, [remitRoute]: { body: { ...remittance, remittanceStatus: 'FAILED' } } })
    const user = renderApp('/send', { signedIn: true })

    const form = await sendToMember(user)

    expect(await form.findByRole('alert')).toHaveTextContent('The remittance was declined: your wallet holds less than ₩5,000.')
    expect(form.queryByRole('status')).not.toBeInTheDocument()
  })

  test('a remittance that could not be delivered says the money is back in the wallet', async () => {
    fakeRoutes({ ...signedInRoutes, [remitRoute]: { body: { ...remittance, remittanceStatus: 'REFUNDED' } } })
    const user = renderApp('/send', { signedIn: true })

    const form = await sendToMember(user)

    expect(await form.findByRole('alert')).toHaveTextContent(
      'The money could not be delivered to member 2. ₩5,000 is back in your wallet.',
    )
  })

  test('a bank transfer the bank has not confirmed says it will not be sent twice', async () => {
    fakeRoutes({
      ...signedInRoutes,
      [remitRoute]: {
        body: {
          ...remittance,
          remittanceType: 1,
          toMembershipId: null,
          toBankName: 'Shinhan Bank',
          toBankAccountNumber: '987-654',
          remittanceStatus: 'TRANSFERRING',
        },
      },
    })
    const user = renderApp('/send', { signedIn: true })

    const form = await sendToMember(user)

    expect(await form.findByRole('status')).toHaveTextContent(
      '₩5,000 left your wallet, and the bank has not confirmed the transfer yet. It will not be sent twice.',
    )
  })

  test('a remittance the server refuses shows its message', async () => {
    fakeRoutes({
      ...signedInRoutes,
      [remitRoute]: {
        status: 400,
        body: { error: 'INVALID_RECEIVER', message: 'Send the money to a member other than the sender.' },
      },
    })
    const user = renderApp('/send', { signedIn: true })

    const form = await sendToMember(user)

    expect(await form.findByRole('alert')).toHaveTextContent('Send the money to a member other than the sender.')
  })

  test('retrying a remittance that got no answer reuses its request id so the money moves once', async () => {
    const requests = fakeRoutes({ ...signedInRoutes, [remitRoute]: { status: 503 } })
    const user = renderApp('/send', { signedIn: true })

    for (let attempt = 0; attempt < 2; attempt++) {
      const form = await sendToMember(user)
      await form.findByRole('alert')
    }
    await sendToMember(user, '6000')

    await waitFor(() => {
      expect(remittanceRequests(requests)).toHaveLength(3)
    })
    const ids = remittanceRequests(requests).map((r) => (jsonBody(r) as { requestId: string }).requestId)
    expect(ids[1]).toBe(ids[0])
    expect(ids[2]).not.toBe(ids[0])
  })
})
