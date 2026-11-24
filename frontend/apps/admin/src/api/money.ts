import { call, postJson } from '@jaypay/api/client'
import { z } from 'zod'

export function aggregateMoneySum(address: string) {
  return call('money-aggregation', '/money/aggregation/get-money-sum-by-address', z.number(), postJson({ address }))
}

export function queryMoneySum(address: string) {
  return call('money-query', `/money/query/get-money-sum-by-address/${encodeURIComponent(address)}`, z.number())
}
