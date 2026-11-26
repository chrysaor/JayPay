import { call, parse, postJson, request, ServerError } from '@jaypay/api/client'
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
  try {
    return parse(await request('banking', `/banking/account/${encodeURIComponent(membershipId)}`), registeredBankAccountSchema)
  } catch (error) {
    if (error instanceof ServerError && error.status === 404) return null
    throw error
  }
}
