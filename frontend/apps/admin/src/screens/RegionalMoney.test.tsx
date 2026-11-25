import { fakeRoutes, jsonBody } from '@jaypay/api/testing'
import { screen } from '@testing-library/react'
import { describe, expect, test } from 'vitest'
import { region, renderApp, signedInAs } from '../test/render'

const aggregationRoute = 'POST /api/money-aggregation/money/aggregation/get-money-sum-by-address'
const queryRoute = 'GET /api/money-query/money/query/get-money-sum-by-address/Seoul'

describe('regional money', () => {
  test('shows the live aggregation and the query model total for a region', async () => {
    const requests = fakeRoutes({ ...signedInAs(), [aggregationRoute]: { body: 150000 }, [queryRoute]: { body: 149000 } })
    const user = renderApp('/money')

    await user.type(await screen.findByLabelText('Region'), 'Seoul')
    await user.click(screen.getByRole('button', { name: 'Look up' }))

    expect(await (await region('Live aggregation')).findByText('₩150,000')).toBeInTheDocument()
    expect(await (await region('Query model')).findByText('₩149,000')).toBeInTheDocument()
    const aggregation = requests.find((r) => r.url.includes('/money-aggregation/'))
    expect(aggregation?.contentType).toBe('application/json')
    expect(jsonBody(aggregation)).toEqual({ address: 'Seoul' })
  })

  test('a region link opens with its totals', async () => {
    fakeRoutes({ ...signedInAs(), [aggregationRoute]: { body: 0 }, [queryRoute]: { body: 0 } })
    renderApp('/money?region=Seoul')

    expect(await (await region('Live aggregation')).findByText('₩0')).toBeInTheDocument()
    expect(screen.getByLabelText('Region')).toHaveValue('Seoul')
  })

  test('one failing source does not hide the other', async () => {
    fakeRoutes({ ...signedInAs(), [aggregationRoute]: { status: 502 }, [queryRoute]: { body: 149000 } })
    renderApp('/money?region=Seoul')

    expect(await (await region('Live aggregation')).findByRole('alert')).toHaveTextContent('HTTP 502')
    expect(await (await region('Query model')).findByText('₩149,000')).toBeInTheDocument()
  })
})
