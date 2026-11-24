import { z } from 'zod'
import { call, postJson } from './client'

const remittanceRequestSchema = z.object({
  remittanceRequestId: z.string(),
  remittanceFromMembershipId: z.string().nullish(),
  toBankName: z.string().nullish(),
  toBankAccountNumber: z.string().nullish(),
  remittanceType: z.number(),
  amount: z.number(),
  remittanceStatus: z.string().nullish(),
})

export const toMembership = 0
export const toBankAccount = 1

export type NewRemittance = { fromMembershipId: string; amount: number } & (
  | { remittanceType: typeof toMembership; toMembershipId: string }
  | { remittanceType: typeof toBankAccount; toBankName: string; toBankAccountNumber: string }
)

export function requestRemittance(input: NewRemittance) {
  return call('remittance', '/remittance/request', remittanceRequestSchema, postJson(input))
}
