import * as membership from '@jaypay/api/membership'
import { fakeServer, type ReceivedRequest } from '@jaypay/api/testing'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import * as auth from './auth'
import * as banking from './banking'
import * as money from './money'
import * as payment from './payment'
import * as remittance from './remittance'

const screenCalls: Record<string, () => Promise<unknown>> = {
  registerMembership: () => auth.registerMembership({ name: 'Jay', email: 'jay@example.com', address: 'Seoul', corp: false }),
  logIn: () => auth.logIn('1'),
  validateToken: () => auth.validateToken('token'),
  refreshSession: () => auth.refreshSession('refresh'),
  findMembership: () => membership.findMembership('12'),
  findBankAccount: () => banking.findBankAccount('12'),
  registerBankAccount: () => banking.registerBankAccount({ membershipId: '1', bankName: 'Jay Bank', bankAccountNumber: '1', valid: true }),
  findBalance: () => money.findBalance('1'),
  increaseMoney: () => money.increaseMoney({ requestId: 'r', targetMembershipId: '1', amount: 1 }),
  requestRemittance: () =>
    remittance.requestRemittance({
      requestId: 'r',
      fromMembershipId: '1',
      toMembershipId: '2',
      remittanceType: remittance.toMembership,
      amount: 1,
    }),
  requestPayment: () => payment.requestPayment({ requestId: 'r', requestMembershipId: '1', franchiseId: '2', requestPrice: 1 }),
}

type Route = { key: string; service: string; pattern: RegExp }

function routes(): Route[] {
  const values = readFileSync(resolve(import.meta.dirname, '../../../../../deploy/charts/gateway/values.yaml'), 'utf8')
  const site = /^customerSite:\n((?: .*\n)*)/m.exec(values)?.[1] ?? ''
  return [...site.matchAll(/^ {4}- \{ service: ([a-z-]+), method: ([A-Z]+), path: "?([^",}]+)"?[ ,]/gm)].map(([, service = '', method = '', path = '']) => ({
    key: `${method} ${path}`,
    service,
    pattern: new RegExp(`^${method} /api/${service}${path}$`),
  }))
}

function functionsOf(...modules: object[]) {
  return modules.flatMap((module) => Object.entries(module).filter(([, value]) => typeof value === 'function').map(([name]) => name))
}

async function screenRequests() {
  const received: ReceivedRequest[] = fakeServer({ body: {} })
  for (const call of Object.values(screenCalls)) await call().catch(() => undefined)
  return received.map((request) => `${request.method} ${request.url}`)
}

describe('the customer site routes of the gateway', () => {
  it('lists every API call the screens make', () => {
    expect(Object.keys(screenCalls).sort()).toEqual(functionsOf(auth, banking, membership, money, payment, remittance).sort())
  })

  it('has exactly one route for each screen request, to the service its path names', async () => {
    const table = routes()
    for (const request of await screenRequests()) {
      const matched = table.filter((route) => route.pattern.test(request))
      expect(matched.map((route) => route.service), request).toEqual([/^\w+ \/api\/([a-z-]+)\//.exec(request)?.[1]])
    }
  })

  it('has no route the screens do not request', async () => {
    const requests = await screenRequests()
    const unused = routes().filter((route) => !requests.some((request) => route.pattern.test(request)))
    expect(unused.map((route) => route.key)).toEqual([])
  })

  it('lets a path variable be digits only', () => {
    const table = routes()
    expect(table.length).toBeGreaterThan(0)
    for (const { key } of table) expect(key, key).toMatch(/^[A-Z]+ (\/[a-z-]+|\/\[0-9\]\+)+$/)
  })
})
