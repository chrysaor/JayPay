import { fakeRoutes, jsonBody } from '@jaypay/api/testing'
import { waitFor } from '@testing-library/react'
import { describe, expect, test } from 'vitest'
import { region, renderApp, signedInRoutes } from '../test/render'

const balanceRoute = 'POST /api/money/money/member-money'
const increaseRoute = 'POST /api/money/money/increase'

describe('wallet', () => {
  test("shows the signed-in member's balance", async () => {
    const requests = fakeRoutes({
      ...signedInRoutes,
      [balanceRoute]: { body: [{ memberMoneyId: '3', membershipId: '1', balance: 25000 }] },
    })
    renderApp('/', { signedIn: true })

    const balance = await region('Balance')
    expect(await balance.findByText('₩25,000')).toBeInTheDocument()
    expect(jsonBody(requests.find((r) => r.url.endsWith('/member-money')))).toEqual({ targetMembershipIds: ['1'] })
  })

  test('a member without money yet sees a zero balance', async () => {
    fakeRoutes({ ...signedInRoutes, [balanceRoute]: { body: [] } })
    renderApp('/', { signedIn: true })

    expect(await (await region('Balance')).findByText('₩0')).toBeInTheDocument()
  })

  test('adding money charges the wallet and refreshes the balance', async () => {
    const routes = {
      ...signedInRoutes,
      [balanceRoute]: { body: [{ memberMoneyId: '3', membershipId: '1', balance: 0 }] },
      [increaseRoute]: {
        body: { moneyChangingRequestId: '42', moneyChangingType: 1, moneyChangingResultStatus: 1, amount: 10000 },
      },
    }
    const requests = fakeRoutes(routes)
    const user = renderApp('/', { signedIn: true })
    const balance = await region('Balance')
    await balance.findByText('₩0')

    routes[balanceRoute] = { body: [{ memberMoneyId: '3', membershipId: '1', balance: 10000 }] }
    const charge = await region('Add money')
    await user.type(charge.getByLabelText('Amount'), '10000')
    await user.click(charge.getByRole('button', { name: 'Add money' }))

    expect(await charge.findByRole('status')).toHaveTextContent('Added ₩10,000 to your wallet.')
    expect(await balance.findByText('₩10,000')).toBeInTheDocument()
    expect(jsonBody(requests.find((r) => r.url.endsWith('/increase')))).toEqual({ targetMembershipId: '1', amount: 10000 })
    await waitFor(() => {
      expect(requests.filter((r) => r.url.endsWith('/member-money'))).toHaveLength(2)
    })
  })

  test('a charge the backend drops is reported', async () => {
    fakeRoutes({ ...signedInRoutes, [balanceRoute]: { body: [] }, [increaseRoute]: { body: undefined } })
    const user = renderApp('/', { signedIn: true })

    const charge = await region('Add money')
    await user.type(charge.getByLabelText('Amount'), '10000')
    await user.click(charge.getByRole('button', { name: 'Add money' }))

    expect(await charge.findByRole('alert')).toHaveTextContent('empty response')
  })

  test('a balance that cannot be loaded shows the error', async () => {
    fakeRoutes({ ...signedInRoutes, [balanceRoute]: { status: 502 } })
    renderApp('/', { signedIn: true })

    expect(await (await region('Balance')).findByRole('alert')).toHaveTextContent('HTTP 502')
  })
})
