import { call, parse, postJson, request } from '@jaypay/api/client'
import { z } from 'zod'

const registeredBankAccountSchema = z.object({
  registeredBankAccountId: z.string(),
  membershipId: z.string().nullish(),
  bankName: z.string().nullish(),
  bankAccountNumber: z.string().nullish(),
  linkedStatusIsValid: z.boolean(),
})

export type NewBankAccount = { membershipId: string; bankName: string; bankAccountNumber: string; valid: boolean }

export function registerBankAccount(input: NewBankAccount) {
  return call('banking', '/banking/account/register', registeredBankAccountSchema, postJson(input))
}

export async function findBankAccount(membershipId: string) {
  const text = await request('banking', `/banking/account/${encodeURIComponent(membershipId)}`)
  return text === '' ? null : parse(text, registeredBankAccountSchema)
}
