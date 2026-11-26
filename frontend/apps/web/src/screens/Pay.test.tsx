import { fakeRoutes, jsonBody, type ReceivedRequest } from '@jaypay/api/testing'
import { waitFor } from '@testing-library/react'
import { describe, expect, test } from 'vitest'
import { region, renderApp, signedInRoutes } from '../test/render'

const payRoute = 'POST /api/payment/payment/request'

const payment = {
  paymentId: 10,
  requestId: 'r-10',
  requestMembershipId: '1',
  franchiseId: '7',
  requestPrice: 12000,
  feeRateBasisPoints: 300,
  fee: 360,
  payout: 11640,
  paymentStatus: 'PAID',
  approvedAt: '2026-11-24T10:00:00.000Z',
}

async function pay(user: ReturnType<typeof renderApp>, amount = '12000') {
  const form = await region('Pay a merchant')
  await user.clear(form.getByLabelText('Merchant ID'))
  await user.type(form.getByLabelText('Merchant ID'), '7')
  await user.clear(form.getByLabelText('Amount'))
  await user.type(form.getByLabelText('Amount'), amount)
  await user.click(form.getByRole('button', { name: 'Pay' }))
  return form
}

function paymentRequests(requests: ReceivedRequest[]) {
  return requests.filter((r) => r.url.endsWith('/payment/request'))
}

describe('pay', () => {
  test('pays a merchant as the signed-in member with a JSON body and shows the receipt', async () => {
    const requests = fakeRoutes({ ...signedInRoutes, [payRoute]: { body: payment } })
    const user = renderApp('/pay', { signedIn: true })

    const form = await pay(user)

    const receipt = await form.findByRole('status')
    expect(receipt).toHaveTextContent('Payment #10 approved')
    expect(receipt).toHaveTextContent('₩12,000')
    expect(receipt).toHaveTextContent('7')
    const [sent] = paymentRequests(requests)
    expect(sent?.contentType).toBe('application/json')
    const { requestId, ...body } = jsonBody(sent) as Record<string, unknown>
    expect(requestId).toMatch(/^[0-9a-f-]{36}$/)
    expect(body).toEqual({ requestMembershipId: '1', franchiseId: '7', requestPrice: 12000 })
  })

  test('asks for no fee rate, because the server sets it', async () => {
    fakeRoutes({ ...signedInRoutes })
    renderApp('/pay', { signedIn: true })

    const form = await region('Pay a merchant')
    expect(form.queryByLabelText(/fee/i)).not.toBeInTheDocument()
  })

  test('a payment above the balance shows that it was declined', async () => {
    fakeRoutes({ ...signedInRoutes, [payRoute]: { body: { ...payment, paymentStatus: 'FAILED', approvedAt: null } } })
    const user = renderApp('/pay', { signedIn: true })

    const form = await pay(user)

    expect(await form.findByRole('alert')).toHaveTextContent('Payment #10 was declined: your wallet holds less than ₩12,000.')
    expect(form.queryByRole('status')).not.toBeInTheDocument()
  })

  test('an amount the server refuses shows its message', async () => {
    fakeRoutes({
      ...signedInRoutes,
      [payRoute]: {
        status: 400,
        body: { error: 'INVALID_AMOUNT', message: 'Send a requestPrice that is a whole number from 1 to 10000000.' },
      },
    })
    const user = renderApp('/pay', { signedIn: true })

    const form = await pay(user, '20000000')

    expect(await form.findByRole('alert')).toHaveTextContent('Send a requestPrice that is a whole number from 1 to 10000000.')
  })

  test('retrying a payment that got no answer reuses its request id so the wallet is charged once', async () => {
    const requests = fakeRoutes({ ...signedInRoutes, [payRoute]: { status: 503 } })
    const user = renderApp('/pay', { signedIn: true })

    for (let attempt = 0; attempt < 2; attempt++) {
      const form = await pay(user)
      await form.findByRole('alert')
    }
    await pay(user, '5000')

    await waitFor(() => {
      expect(paymentRequests(requests)).toHaveLength(3)
    })
    const ids = paymentRequests(requests).map((r) => (jsonBody(r) as { requestId: string }).requestId)
    expect(ids[1]).toBe(ids[0])
    expect(ids[2]).not.toBe(ids[0])
  })
})
