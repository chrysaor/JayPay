import { z } from 'zod'
import { call, postJson } from './client'

const membershipSchema = z.object({
  membershipId: z.string(),
  name: z.string().nullish(),
  email: z.string().nullish(),
  address: z.string().nullish(),
  valid: z.boolean(),
  corp: z.boolean(),
  refreshToken: z.string().nullish(),
})

export type NewMembership = { name: string; email: string; address: string; corp: boolean }

export function registerMembership(input: NewMembership) {
  return call('membership', '/memberships', membershipSchema, postJson(input))
}

export function findMembership(membershipId: string) {
  return call('membership', `/memberships/${encodeURIComponent(membershipId)}`, membershipSchema)
}
