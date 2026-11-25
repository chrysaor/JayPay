import { z } from 'zod'
import { call } from './client'

export const membershipSchema = z.object({
  membershipId: z.string(),
  name: z.string().nullish(),
  email: z.string().nullish(),
  address: z.string().nullish(),
  valid: z.boolean(),
  corp: z.boolean(),
})

export type Membership = z.output<typeof membershipSchema>

export function findMembership(membershipId: string) {
  return call('membership', `/memberships/${encodeURIComponent(membershipId)}`, membershipSchema)
}
