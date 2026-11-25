import { fakeRoutes, jsonBody } from '@jaypay/api/testing'
import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, test } from 'vitest'
import { renderApp, signedInAs } from '../test/render'

const listRoute = 'GET /api/payment/payment/normal-status'
const finishRoute = 'POST /api/payment/payment/finish-settlement'

const payment = {
  paymentId: 10,
  requestId: 'r-10',
  requestMembershipId: '1',
  franchiseId: '71',
  requestPrice: 12000,
  feeRateBasisPoints: 350,
  fee: 420,
  payout: 11580,
  paymentStatus: 'PAID',
  approvedAt: '2026-11-24T10:00:00.000+00:00',
}
const another = { ...payment, paymentId: 11, requestPrice: 8000, fee: 280, payout: 7720, franchiseId: '72' }

describe('settlement', () => {
  test('lists the payments awaiting settlement with their total', async () => {
    fakeRoutes({ ...signedInAs(), [listRoute]: { body: [payment, another] } })
    renderApp('/settlement')

    const row = within(await screen.findByRole('row', { name: /#10/ }))
    expect(row.getByText('#10')).toBeInTheDocument()
    expect(row.getByText('₩12,000')).toBeInTheDocument()
    expect(row.getByText('3.5%')).toBeInTheDocument()
    expect(row.getByText('₩420')).toBeInTheDocument()
    expect(row.getByText('₩11,580')).toBeInTheDocument()
    expect(row.getByText('Awaiting settlement')).toBeInTheDocument()
    expect(screen.getByText(/2 payments awaiting settlement/)).toHaveTextContent(
      '2 payments awaiting settlement, ₩20,000 in total, ₩19,300 to pay out',
    )
  })

  test('marking a payment settled sends its ID and reloads the list', async () => {
    const routes = { ...signedInAs(), [listRoute]: { body: [payment, another] }, [finishRoute]: { body: { paymentId: 10, paymentStatus: 'SETTLED', alreadySettled: false } } }
    const requests = fakeRoutes(routes)
    const user = renderApp('/settlement')
    const row = within(await screen.findByRole('row', { name: /#10/ }))

    routes[listRoute] = { body: [another] }
    await user.click(row.getByRole('button', { name: 'Mark settled' }))

    await waitFor(() => {
      expect(screen.queryByRole('row', { name: /#10/ })).not.toBeInTheDocument()
    })
    expect(jsonBody(requests.find((r) => r.url.endsWith('/finish-settlement')))).toEqual({ paymentId: '10' })
    expect(requests.filter((r) => r.url.endsWith('/normal-status'))).toHaveLength(2)
    expect(screen.getByRole('status')).toHaveTextContent('Payment #10 is marked settled.')
  })

  test('settling a payment that was settled already says that nothing changed', async () => {
    fakeRoutes({
      ...signedInAs(),
      [listRoute]: { body: [payment] },
      [finishRoute]: { body: { paymentId: 10, paymentStatus: 'SETTLED', alreadySettled: true } },
    })
    const user = renderApp('/settlement')

    const row = within(await screen.findByRole('row', { name: /#10/ }))
    await user.click(row.getByRole('button', { name: 'Mark settled' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Payment #10 was already settled. Nothing changed.')
  })

  test('a failed settlement shows the server error', async () => {
    fakeRoutes({ ...signedInAs(), [listRoute]: { body: [payment] }, [finishRoute]: { status: 500 } })
    const user = renderApp('/settlement')

    const row = within(await screen.findByRole('row', { name: /#10/ }))
    await user.click(row.getByRole('button', { name: 'Mark settled' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('HTTP 500')
  })

  test('an empty queue says nothing is waiting', async () => {
    fakeRoutes({ ...signedInAs(), [listRoute]: { body: [] } })
    renderApp('/settlement')

    expect(await screen.findByText('No payments are awaiting settlement.')).toBeInTheDocument()
  })

  test('a queue that cannot be loaded shows the error', async () => {
    fakeRoutes({ ...signedInAs(), [listRoute]: { status: 502 } })
    renderApp('/settlement')

    expect(await screen.findByRole('alert')).toHaveTextContent('HTTP 502')
  })

  test('a viewer sees the queue without a way to settle it', async () => {
    fakeRoutes({ ...signedInAs('VIEWER'), [listRoute]: { body: [payment] } })
    renderApp('/settlement')

    expect(await screen.findByRole('row', { name: /#10/ })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Mark settled' })).not.toBeInTheDocument()
  })
})
