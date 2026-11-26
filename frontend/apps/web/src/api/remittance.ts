import { call, postJson } from '@jaypay/api/client'
import { z } from 'zod'

const remittanceSchema = z.object({
  remittanceId: z.string(),
  requestId: z.string(),
  fromMembershipId: z.string(),
  remittanceType: z.number(),
  toMembershipId: z.string().nullish(),
  toBankName: z.string().nullish(),
  toBankAccountNumber: z.string().nullish(),
  amount: z.number(),
  remittanceStatus: z.enum(['REQUESTED', 'DEBITED', 'TRANSFERRING', 'COMPLETED', 'FAILED', 'REFUNDING', 'REFUNDED']),
})

export type Remittance = z.output<typeof remittanceSchema>

export const toMembership = 0
export const toBankAccount = 1

export type NewRemittance = { requestId: string; fromMembershipId: string; amount: number } & (
  | { remittanceType: typeof toMembership; toMembershipId: string }
  | { remittanceType: typeof toBankAccount; toBankName: string; toBankAccountNumber: string }
)

export function requestRemittance(input: NewRemittance) {
  return call('remittance', '/remittance/request', remittanceSchema, postJson(input))
}
