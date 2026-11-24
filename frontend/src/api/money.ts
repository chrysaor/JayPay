import { z } from 'zod'
import { call, postJson } from './client'

const moneyChangingResultSchema = z.object({
  moneyChangingRequestId: z.string(),
  moneyChangingType: z.number(),
  moneyChangingResultStatus: z.number(),
  amount: z.number(),
})

const memberMoneySchema = z.object({
  memberMoneyId: z.string(),
  membershipId: z.string(),
  balance: z.number(),
})

export function increaseMoney(input: { targetMembershipId: string; amount: number }) {
  return call('money', '/money/increase', moneyChangingResultSchema, postJson(input))
}

export function findMemberMoney(membershipId: string) {
  return call(
    'money',
    '/money/member-money',
    z.array(memberMoneySchema),
    postJson({ targetMembershipIds: [membershipId] }),
  )
}
