import { call, postJson } from '@jaypay/api/client'
import { z } from 'zod'

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

export async function findBalance(membershipId: string) {
  const wallets = await call(
    'money',
    '/money/member-money',
    z.array(memberMoneySchema),
    postJson({ targetMembershipIds: [membershipId] }),
  )
  return wallets[0]?.balance ?? 0
}
