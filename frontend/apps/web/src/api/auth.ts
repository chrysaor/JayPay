import { call, parse, postJson, request } from '@jaypay/api/client'
import { membershipSchema } from '@jaypay/api/membership'
import { z } from 'zod'

export const sessionSchema = z.object({
  membershipId: z.string(),
  jwtToken: z.string(),
  refreshToken: z.string(),
})

export type Session = z.output<typeof sessionSchema>

export type NewMembership = { name: string; email: string; address: string; corp: boolean }

export function registerMembership(input: NewMembership) {
  return call('membership', '/memberships', membershipSchema, postJson(input))
}

export async function logIn(membershipId: string): Promise<Session> {
  const text = await request('membership', '/memberships/login', postJson({ membershipId }))
  if (text === '') throw new Error('This membership cannot sign in.')
  return parse(text, sessionSchema)
}

export function validateToken(jwtToken: string) {
  return call('membership', '/memberships/token-validate', z.boolean(), postJson({ jwtToken }))
}

export function refreshSession(refreshToken: string) {
  return call('membership', '/memberships/refresh-token', sessionSchema, postJson({ refreshToken }))
}
