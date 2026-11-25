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
    remittance.requestRemittance({ fromMembershipId: '1', toMembershipId: '2', remittanceType: remittance.toMembership, amount: 1 }),
  requestPayment: () => payment.requestPayment({ requestMembershipId: '1', requestPrice: '1', franchiseId: '1', franchiseFeeRate: '1' }),
}

type Route = { key: string; matches: (request: string) => string | null; upstream: string }

function routes(): Route[] {
  const template = readFileSync(resolve(import.meta.dirname, '../../nginx.conf.template'), 'utf8')
  const table = /map "\$request_method \$uri" \$jaypay_upstream \{\n([\s\S]*?)\n\}/.exec(template)?.[1] ?? ''
  return table
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line.startsWith('"'))
    .map((line) => {
      const [, key = '', upstream = ''] = /^"([^"]+)"\s+(\S+);$/.exec(line) ?? []
      if (!key.startsWith('~')) return { key, upstream, matches: (request) => (request === key ? upstream : null) }
      const pattern = new RegExp(key.slice(1))
      return {
        key,
        upstream,
        matches: (request) => {
          const groups = pattern.exec(request)?.groups
          return groups ? upstream.replace(/\$(\w+)/g, (_, name: string) => groups[name] ?? '') : null
        },
      }
    })
}

function functionsOf(...modules: object[]) {
  return modules.flatMap((module) => Object.entries(module).filter(([, value]) => typeof value === 'function').map(([name]) => name))
}

async function screenRequests() {
  const received: ReceivedRequest[] = fakeServer({ body: {} })
  for (const call of Object.values(screenCalls)) await call().catch(() => undefined)
  return received.map((request) => `${request.method} ${request.url}`)
}

function expectedUpstream(request: string) {
  const [, service = '', path = ''] = /^\w+ \/api\/([a-z]+)(\/.*)$/.exec(request) ?? []
  return `\${JAYPAY_${service.toUpperCase()}_URL}${path}`
}

describe('the customer site nginx', () => {
  it('lists every API call the screens make', () => {
    expect(Object.keys(screenCalls).sort()).toEqual(functionsOf(auth, banking, membership, money, payment, remittance).sort())
  })

  it('forwards each screen request to its service, and only to that path', async () => {
    const table = routes()
    for (const request of await screenRequests()) {
      const forwarded = table.map((route) => route.matches(request)).filter((target) => target !== null)
      expect(forwarded, request).toEqual([expectedUpstream(request)])
    }
  })

  it('forwards nothing the screens do not request', async () => {
    const requests = await screenRequests()
    const unused = routes().filter((route) => !requests.some((request) => route.matches(request) !== null))
    expect(unused.map((route) => route.key)).toEqual([])
  })

  it('anchors every pattern at both ends and captures only digits', () => {
    const patterns = routes().filter((route) => route.key.startsWith('~'))
    expect(patterns.length).toBeGreaterThan(0)
    for (const { key } of patterns) expect(key, key).toMatch(/^~\^[A-Z]+ (\/[a-z-]+|\/\(\?<\w+>\[0-9\]\+\))+\$$/)
  })
})
