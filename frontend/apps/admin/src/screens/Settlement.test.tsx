import { fakeRoutes } from '@jaypay/api/testing'
import { screen, within } from '@testing-library/react'
import { describe, expect, test } from 'vitest'
import { renderApp, signedInAs, type TestRole } from '../test/render'

const listRoute = 'GET /api/payment/payment/normal-status'

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
  test('lists the payments awaiting payout with their total', async () => {
    fakeRoutes({ ...signedInAs(), [listRoute]: { body: [payment, another] } })
    renderApp('/settlement')

    const row = within(await screen.findByRole('row', { name: /#10/ }))
    expect(row.getByText('#10')).toBeInTheDocument()
    expect(row.getByText('₩12,000')).toBeInTheDocument()
    expect(row.getByText('3.5%')).toBeInTheDocument()
    expect(row.getByText('₩420')).toBeInTheDocument()
    expect(row.getByText('₩11,580')).toBeInTheDocument()
    expect(row.getByText('Awaiting payout')).toBeInTheDocument()
    expect(screen.getByText(/2 payments awaiting settlement/)).toHaveTextContent(
      '2 payments awaiting settlement, ₩20,000 in total, ₩19,300 to pay out',
    )
  })

  test('says that the batch pays out and the bank confirmation clears the list', async () => {
    fakeRoutes({ ...signedInAs(), [listRoute]: { body: [payment] } })
    renderApp('/settlement')

    expect(
      await screen.findByText('The settlement batch pays each merchant. A payment leaves this list once the bank confirms its transfer.'),
    ).toBeInTheDocument()
  })

  test.each<TestRole>(['ADMIN', 'OPERATOR', 'VIEWER'])('%s can only read the queue', async (role) => {
    const requests = fakeRoutes({ ...signedInAs(role), [listRoute]: { body: [payment] } })
    renderApp('/settlement')

    const row = within(await screen.findByRole('row', { name: /#10/ }))
    expect(row.queryByRole('button')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Mark settled' })).not.toBeInTheDocument()
    expect(requests.some((r) => r.url.endsWith('/finish-settlement'))).toBe(false)
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
})
