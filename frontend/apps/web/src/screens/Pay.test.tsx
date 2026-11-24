import { fakeRoutes } from '@jaypay/api/testing'
import { describe, expect, test } from 'vitest'
import { region, renderApp, signedInRoutes } from '../test/render'

const payRoute = 'POST /api/payment/payment/request'

const payment = {
  paymentId: 10,
  requestMembershipId: '1',
  requestPrice: 12000,
  franchiseId: 'f-1',
  franchiseFeeRate: '3.5',
  paymentStatus: 0,
  approvedAt: '2026-11-24T10:00:00.000+00:00',
}

async function pay(user: ReturnType<typeof renderApp>) {
  const form = await region('Pay a merchant')
  await user.type(form.getByLabelText('Merchant ID'), 'f-1')
  await user.type(form.getByLabelText('Amount'), '12000')
  await user.type(form.getByLabelText('Merchant fee rate (%)'), '3.5')
  await user.click(form.getByRole('button', { name: 'Pay' }))
  return form
}

describe('pay', () => {
  test('pays a merchant as the signed-in member with form fields and shows the receipt', async () => {
    const requests = fakeRoutes({ ...signedInRoutes, [payRoute]: { body: payment } })
    const user = renderApp('/pay', { signedIn: true })

    const form = await pay(user)

    const receipt = await form.findByRole('status')
    expect(receipt).toHaveTextContent('Payment #10 approved')
    expect(receipt).toHaveTextContent('₩12,000')
    expect(receipt).toHaveTextContent('f-1')
    expect(requests.filter((r) => r.url.endsWith('/payment/request'))).toEqual([
      {
        url: '/api/payment/payment/request',
        method: 'POST',
        contentType: 'application/x-www-form-urlencoded',
        body: 'requestMembershipId=1&requestPrice=12000&franchiseId=f-1&franchiseFeeRate=3.5',
      },
    ])
  })

  test('a failed payment shows the server error', async () => {
    fakeRoutes({ ...signedInRoutes, [payRoute]: { status: 500 } })
    const user = renderApp('/pay', { signedIn: true })

    const form = await pay(user)

    expect(await form.findByRole('alert')).toHaveTextContent('HTTP 500')
  })
})
