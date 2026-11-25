import { fakeRoutes, jsonBody } from '@jaypay/api/testing'
import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, test } from 'vitest'
import { renderApp, signedInAs } from '../test/render'

const listRoute = 'GET /api/payment/payment/normal-status'
const finishRoute = 'POST /api/payment/payment/finish-settlement'

const payment = {
  paymentId: 10,
  requestMembershipId: '1',
  requestPrice: 12000,
  franchiseId: 'f-1',
  franchiseFeeRate: '3.5',
  paymentStatus: 0,
  approvedAt: '2026-11-24T10:00:00.000+00:00',
}
const another = { ...payment, paymentId: 11, requestPrice: 8000, franchiseId: 'f-2' }

describe('settlement', () => {
  test('lists the payments awaiting settlement with their total', async () => {
    fakeRoutes({ ...signedInAs(), [listRoute]: { body: [payment, another] } })
    renderApp('/settlement')

    const row = within(await screen.findByRole('row', { name: /f-1/ }))
    expect(row.getByText('#10')).toBeInTheDocument()
    expect(row.getByText('₩12,000')).toBeInTheDocument()
    expect(row.getByText('3.5%')).toBeInTheDocument()
    expect(row.getByText('Awaiting settlement')).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('2 payments awaiting settlement, ₩20,000 in total')
  })

  test('marking a payment settled sends its ID and reloads the list', async () => {
    const routes = { ...signedInAs(), [listRoute]: { body: [payment, another] }, [finishRoute]: { body: undefined } }
    const requests = fakeRoutes(routes)
    const user = renderApp('/settlement')
    const row = within(await screen.findByRole('row', { name: /f-1/ }))

    routes[listRoute] = { body: [another] }
    await user.click(row.getByRole('button', { name: 'Mark settled' }))

    await waitFor(() => {
      expect(screen.queryByRole('row', { name: /f-1/ })).not.toBeInTheDocument()
    })
    expect(jsonBody(requests.find((r) => r.url.endsWith('/finish-settlement')))).toEqual({ paymentId: '10' })
    expect(requests.filter((r) => r.url.endsWith('/normal-status'))).toHaveLength(2)
  })

  test('a failed settlement shows the server error', async () => {
    fakeRoutes({ ...signedInAs(), [listRoute]: { body: [payment] }, [finishRoute]: { status: 500 } })
    const user = renderApp('/settlement')

    const row = within(await screen.findByRole('row', { name: /f-1/ }))
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

    expect(await screen.findByRole('row', { name: /f-1/ })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Mark settled' })).not.toBeInTheDocument()
  })
})
