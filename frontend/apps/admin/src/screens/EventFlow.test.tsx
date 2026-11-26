import { fakeRoutes, type ReceivedRequest, type Reply } from '@jaypay/api/testing'
import { screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { renderApp, signedInAs, type TestRole } from '../test/render'

const listRoute = 'GET /api/event-flow/flows'
const detailPath = '/api/event-flow/flows/RECHARGE/saga-2'
const detailRoute = `GET ${detailPath}`

const started = '2026-11-26T01:02:03.000Z'

const running = {
  flowType: 'RECHARGE',
  flowId: 'saga-1',
  status: 'IN_PROGRESS',
  membershipId: '12',
  amount: 10000,
  startedAt: started,
  updatedAt: '2026-11-26T01:02:04.500Z',
  lastStep: 'BANK_CHARGED',
  failedStep: null,
  stepCount: 4,
}
const failed = {
  ...running,
  flowId: 'saga-2',
  status: 'FAILED',
  membershipId: '13',
  amount: 3000,
  lastStep: 'RECHARGE_FAILED',
  failedStep: 'BANK_CHARGED',
  stepCount: 5,
}
const succeeded = { ...running, flowId: 'saga-3', status: 'SUCCEEDED', membershipId: '14', lastStep: 'WALLET_INCREASED' }

function step(name: string, service: string, outcome: string, occurredAt: string, reason: string | null = null) {
  return { step: name, service, outcome, reason, occurredAt }
}

const failedSteps = [
  step('RECHARGE_REQUESTED', 'money-service', 'SUCCEEDED', '2026-11-26T01:02:03.000Z'),
  step('MEMBERSHIP_CHECKED', 'membership-service', 'SUCCEEDED', '2026-11-26T01:02:03.120Z'),
  step('BANK_CHARGED', 'banking-service', 'FAILED', '2026-11-26T01:02:04.520Z', 'The bank declined the charge.'),
]

function detail(flow: object, steps: ReturnType<typeof step>[]) {
  return { ...flow, flowId: 'saga-2', stepCount: steps.length, steps }
}

function detailRequests(requests: ReceivedRequest[]) {
  return requests.filter((r) => r.url === detailPath)
}

function timeline() {
  return within(screen.getByRole('list', { name: 'Steps' }))
}

function stepAt(index: number) {
  const item = timeline().getAllByRole('listitem')[index]
  if (!item) throw new Error(`The timeline has no step ${String(index + 1)}.`)
  return within(item)
}

describe('event flow', () => {
  test('lists recent flows and tells each status apart in words', async () => {
    fakeRoutes({ ...signedInAs(), [listRoute]: { body: [running, failed, succeeded] } })
    renderApp('/event-flow')

    const runningRow = within(await screen.findByRole('row', { name: /saga-1/ }))
    expect(runningRow.getByText('Top-up')).toBeInTheDocument()
    expect(runningRow.getByText('12')).toBeInTheDocument()
    expect(runningRow.getByText('₩10,000')).toBeInTheDocument()
    expect(runningRow.getByText('In progress')).toBeInTheDocument()
    expect(runningRow.getByText('Bank charged')).toBeInTheDocument()
    const failedRow = within(screen.getByRole('row', { name: /saga-2/ }))
    expect(failedRow.getByText('Failed')).toBeInTheDocument()
    expect(failedRow.getByText('Bank charged')).toBeInTheDocument()
    expect(failedRow.queryByText('Recharge failed')).not.toBeInTheDocument()
    const succeededRow = within(screen.getByRole('row', { name: /saga-3/ }))
    expect(succeededRow.getByText('Succeeded')).toBeInTheDocument()
    expect(succeededRow.getByText('Wallet increased')).toBeInTheDocument()
  })

  test('the status filter asks the server for that status only', async () => {
    const requests = fakeRoutes({
      ...signedInAs(),
      [listRoute]: { body: [running, failed] },
      [`${listRoute}?status=FAILED`]: { body: [failed] },
    })
    const user = renderApp('/event-flow')

    await screen.findByRole('row', { name: /saga-1/ })
    await user.selectOptions(screen.getByLabelText('Status'), 'Failed')

    await vi.waitFor(() => {
      expect(screen.queryByRole('row', { name: /saga-1/ })).not.toBeInTheDocument()
    })
    expect(screen.getByRole('row', { name: /saga-2/ })).toBeInTheDocument()
    expect(requests.some((r) => r.url === '/api/event-flow/flows?status=FAILED')).toBe(true)
  })

  test('the list tells top-ups, remittances, payments and settlements apart', async () => {
    fakeRoutes({
      ...signedInAs(),
      [listRoute]: {
        body: [
          running,
          { ...failed, flowType: 'REMITTANCE', flowId: 'remit-1', lastStep: 'SENDER_REFUNDED', failedStep: 'BANK_TRANSFERRED' },
          { ...succeeded, flowType: 'PAYMENT', flowId: 'pay-1', lastStep: 'PAYER_DEBITED' },
          { ...running, flowType: 'SETTLEMENT', flowId: 'settlement-7', lastStep: 'MERCHANT_PAID_OUT' },
        ],
      },
    })
    renderApp('/event-flow')

    const topUp = within(await screen.findByRole('row', { name: /saga-1/ }))
    expect(topUp.getByText('Top-up')).toBeInTheDocument()
    const remittance = within(screen.getByRole('row', { name: /remit-1/ }))
    expect(remittance.getByText('Remittance')).toBeInTheDocument()
    expect(remittance.getByText('Bank transfer')).toBeInTheDocument()
    const payment = within(screen.getByRole('row', { name: /pay-1/ }))
    expect(payment.getByText('Payment')).toBeInTheDocument()
    expect(payment.getByText('Payer debited')).toBeInTheDocument()
    const settlement = within(screen.getByRole('row', { name: /settlement-7/ }))
    expect(settlement.getByText('Settlement')).toBeInTheDocument()
    expect(settlement.getByText('Merchant paid out')).toBeInTheDocument()
  })

  test('the type filter asks the server for that type and keeps the status', async () => {
    const payment = { ...failed, flowType: 'PAYMENT', flowId: 'pay-2', lastStep: 'PAYMENT_REQUESTED', failedStep: 'PAYMENT_REQUESTED' }
    const requests = fakeRoutes({
      ...signedInAs(),
      [listRoute]: { body: [running, failed, payment] },
      [`${listRoute}?status=FAILED`]: { body: [failed, payment] },
      [`${listRoute}?flowType=PAYMENT&status=FAILED`]: { body: [payment] },
    })
    const user = renderApp('/event-flow')

    await screen.findByRole('row', { name: /saga-1/ })
    await user.selectOptions(screen.getByLabelText('Status'), 'Failed')
    await user.selectOptions(screen.getByLabelText('Type'), 'Payment')

    await vi.waitFor(() => {
      expect(screen.queryByRole('row', { name: /saga-2/ })).not.toBeInTheDocument()
    })
    expect(screen.getByRole('row', { name: /pay-2/ })).toBeInTheDocument()
    expect(requests.some((r) => r.url === '/api/event-flow/flows?flowType=PAYMENT&status=FAILED')).toBe(true)
  })

  test('a filter that matches nothing says so', async () => {
    fakeRoutes({ ...signedInAs(), [listRoute]: { body: [] }, [`${listRoute}?flowType=SETTLEMENT`]: { body: [] } })
    renderApp('/event-flow?type=SETTLEMENT')

    expect(await screen.findByText('No flows match these filters.')).toBeInTheDocument()
    expect(screen.getByLabelText('Type')).toHaveValue('SETTLEMENT')
  })

  test('a remittance the bank refused shows the refund after the failed transfer', async () => {
    const remittancePath = '/api/event-flow/flows/REMITTANCE/remit-3'
    fakeRoutes({
      ...signedInAs(),
      [listRoute]: { body: [] },
      [`GET ${remittancePath}`]: {
        body: {
          ...failed,
          flowType: 'REMITTANCE',
          flowId: 'remit-3',
          lastStep: 'SENDER_REFUNDED',
          failedStep: 'BANK_TRANSFERRED',
          stepCount: 4,
          steps: [
            step('REMITTANCE_REQUESTED', 'remittance-service', 'SUCCEEDED', '2026-11-26T01:02:03.000Z'),
            step('SENDER_DEBITED', 'remittance-service', 'SUCCEEDED', '2026-11-26T01:02:03.050Z'),
            step('BANK_TRANSFERRED', 'remittance-service', 'FAILED', '2026-11-26T01:02:03.250Z', 'The bank refused the transfer'),
            step('SENDER_REFUNDED', 'remittance-service', 'SUCCEEDED', '2026-11-26T01:02:03.300Z'),
          ],
        },
      },
    })
    renderApp('/event-flow?flowType=REMITTANCE&flowId=remit-3')

    await screen.findByRole('list', { name: 'Steps' })
    expect(stepAt(0).getByText('Remittance requested')).toBeInTheDocument()
    expect(stepAt(1).getByText('Sender debited')).toBeInTheDocument()
    expect(stepAt(2).getByText('Bank transfer')).toBeInTheDocument()
    expect(stepAt(2).getByText('The bank refused the transfer')).toBeInTheDocument()
    expect(stepAt(3).getByText('Sender refunded')).toBeInTheDocument()
    expect(screen.getByText('Remittance', { selector: 'dd' })).toBeInTheDocument()
  })

  test('choosing a flow shows its steps with the service, the gap and why it failed', async () => {
    fakeRoutes({
      ...signedInAs(),
      [listRoute]: { body: [running, failed] },
      [detailRoute]: { body: detail(failed, failedSteps) },
    })
    const user = renderApp('/event-flow')

    await user.click(await screen.findByRole('link', { name: 'saga-2' }))

    await screen.findByRole('list', { name: 'Steps' })
    expect(timeline().getAllByRole('listitem')).toHaveLength(3)
    const requested = stepAt(0)
    expect(requested.getByText('Recharge requested')).toBeInTheDocument()
    expect(requested.getByText('money-service')).toBeInTheDocument()
    const checked = stepAt(1)
    expect(checked.getByText('Member checked')).toBeInTheDocument()
    expect(checked.getByText('membership-service')).toBeInTheDocument()
    expect(checked.getByText('+120 ms')).toBeInTheDocument()
    expect(checked.getByText('Succeeded')).toBeInTheDocument()
    const charged = stepAt(2)
    expect(charged.getByText('Bank charged')).toBeInTheDocument()
    expect(charged.getByText('banking-service')).toBeInTheDocument()
    expect(charged.getByText('+1.4 s')).toBeInTheDocument()
    expect(charged.getByText('Failed')).toBeInTheDocument()
    expect(charged.getByText('The bank declined the charge.')).toBeInTheDocument()
  })

  test('a step the console does not know is shown by its code', async () => {
    fakeRoutes({
      ...signedInAs(),
      [listRoute]: { body: [] },
      [detailRoute]: {
        body: detail(succeeded, [step('POINTS_GRANTED', 'reward-service', 'SUCCEEDED', started)]),
      },
    })
    renderApp('/event-flow?flowType=RECHARGE&flowId=saga-2')

    expect(await screen.findByText('POINTS_GRANTED')).toBeInTheDocument()
  })

  test('a flow the server does not know shows its message', async () => {
    fakeRoutes({
      ...signedInAs(),
      [listRoute]: { body: [] },
      [detailRoute]: { status: 404, body: { error: 'FLOW_NOT_FOUND', message: 'No flow saga-2 of type RECHARGE.' } },
    })
    renderApp('/event-flow?flowType=RECHARGE&flowId=saga-2')

    expect(await screen.findByRole('alert')).toHaveTextContent('No flow saga-2 of type RECHARGE.')
  })

  test('an empty list says no flow has started', async () => {
    fakeRoutes({ ...signedInAs(), [listRoute]: { body: [] } })
    renderApp('/event-flow')

    expect(await screen.findByText('No flows have started yet.')).toBeInTheDocument()
  })

  test.each<TestRole>(['ADMIN', 'OPERATOR', 'VIEWER'])('%s finds the screen in the menu and can only read it', async (role) => {
    const requests = fakeRoutes({
      ...signedInAs(role),
      [listRoute]: { body: [failed] },
      [detailRoute]: { body: detail(failed, failedSteps) },
    })
    const user = renderApp('/members')

    const menu = within(await screen.findByRole('navigation', { name: 'Admin menu' }))
    await user.click(menu.getByRole('link', { name: 'Event flow' }))
    await user.click(await screen.findByRole('link', { name: 'saga-2' }))
    await screen.findByRole('list', { name: 'Steps' })

    expect(within(screen.getByRole('main')).queryByRole('button')).not.toBeInTheDocument()
    expect(requests.filter((r) => r.url.startsWith('/api/')).every((r) => r.method === 'GET')).toBe(true)
  })
})

describe('event flow polling', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  test('the list refreshes on its own', async () => {
    const routes: Record<string, Reply> = { ...signedInAs(), [listRoute]: { body: [running] } }
    fakeRoutes(routes)
    renderApp('/event-flow')
    await screen.findByRole('row', { name: /saga-1/ })

    routes[listRoute] = { body: [failed, running] }
    await vi.advanceTimersByTimeAsync(5000)

    expect(await screen.findByRole('row', { name: /saga-2/ })).toBeInTheDocument()
  })

  test('a flow in progress shows a new step without a reload', async () => {
    const inProgress = detail(running, failedSteps.slice(0, 2))
    const routes: Record<string, Reply> = {
      ...signedInAs(),
      [listRoute]: { body: [] },
      [detailRoute]: { body: inProgress },
    }
    fakeRoutes(routes)
    renderApp('/event-flow?flowType=RECHARGE&flowId=saga-2')
    await screen.findByText('Member checked')
    expect(timeline().getAllByRole('listitem')).toHaveLength(2)

    routes[detailRoute] = {
      body: { ...inProgress, steps: [...inProgress.steps, step('BANK_CHARGED', 'banking-service', 'SUCCEEDED', '2026-11-26T01:02:04.520Z')] },
    }
    await vi.advanceTimersByTimeAsync(2000)

    expect(await timeline().findByText('Bank charged')).toBeInTheDocument()
    expect(timeline().getAllByRole('listitem')).toHaveLength(3)
  })

  test('a finished flow is not asked for again', async () => {
    const requests = fakeRoutes({
      ...signedInAs(),
      [listRoute]: { body: [] },
      [detailRoute]: { body: detail(failed, failedSteps) },
    })
    renderApp('/event-flow?flowType=RECHARGE&flowId=saga-2')
    await screen.findByText('The bank declined the charge.')

    await vi.advanceTimersByTimeAsync(10000)

    expect(detailRequests(requests)).toHaveLength(1)
  })
})
