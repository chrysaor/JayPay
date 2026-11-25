import { call, postJson, request } from '@jaypay/api/client'
import { z } from 'zod'

export const passwordHint = '12 to 72 characters with upper and lower case letters, a digit and a symbol. Not the username, not a common password.'

export const roleSchema = z.enum(['ADMIN', 'OPERATOR', 'VIEWER'])

export type Role = z.output<typeof roleSchema>

export const accountSchema = z.object({
  username: z.string(),
  role: roleSchema,
  active: z.boolean(),
  mfaEnabled: z.boolean(),
})

export type Account = z.output<typeof accountSchema>

export const staffSessionSchema = z.object({
  token: z.string(),
  expiresAt: z.iso.datetime({ offset: true }),
  account: accountSchema,
})

export type StaffSession = z.output<typeof staffSessionSchema>

const signInSchema = z.union([
  staffSessionSchema.extend({ status: z.literal('SIGNED_IN') }),
  z.object({ status: z.string() }),
])

export async function signIn(credentials: { username: string; password: string }): Promise<StaffSession> {
  const reply = await call('auth', '/staff/sign-in', signInSchema, postJson(credentials))
  if (!('token' in reply)) throw new Error('This account needs a sign-in step this console does not support yet.')
  return { token: reply.token, expiresAt: reply.expiresAt, account: reply.account }
}

export function findMe() {
  return call('auth', '/staff/me', accountSchema)
}

export async function changeOwnPassword(change: { currentPassword: string; newPassword: string }) {
  await request('auth', '/staff/me/password', { ...postJson(change), method: 'PUT' })
}

export function findAccounts() {
  return call('auth', '/staff/accounts', z.array(accountSchema))
}

export function createAccount(account: { username: string; password: string; role: Role; mfaEnabled: boolean }) {
  return call('auth', '/staff/accounts', accountSchema, postJson(account))
}

export type AccessChange = Pick<Account, 'role' | 'active' | 'mfaEnabled'>

export function updateAccount(username: string, change: AccessChange) {
  return call('auth', `/staff/accounts/${encodeURIComponent(username)}`, accountSchema, {
    ...postJson(change),
    method: 'PUT',
  })
}
