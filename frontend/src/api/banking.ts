import { z } from 'zod'
import { call, postJson } from './client'

const registeredBankAccountSchema = z.object({
  registeredBankAccountId: z.string(),
  membershipId: z.string().nullish(),
  bankName: z.string().nullish(),
  bankAccountNumber: z.string().nullish(),
  linkedStatusIsValid: z.boolean(),
  aggregateIdentifier: z.string().nullish(),
})

export type NewBankAccount = { membershipId: string; bankName: string; bankAccountNumber: string; valid: boolean }

export function registerBankAccount(input: NewBankAccount) {
  return call('banking', '/banking/account/register', registeredBankAccountSchema, postJson(input))
}

export function findRegisteredBankAccount(membershipId: string) {
  return call('banking', `/banking/account/${encodeURIComponent(membershipId)}`, registeredBankAccountSchema)
}
