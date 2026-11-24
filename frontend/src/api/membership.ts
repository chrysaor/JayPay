import { z } from 'zod'
import { call, segment } from './client'

// membership-service: RegisterMembershipController, FindMembershipController.
// Lombok names the getter of `boolean isValid` `isValid()`, so Jackson writes and reads `valid`, not `isValid`.
const membershipSchema = z.object({
  membershipId: z.string(),
  name: z.string().nullish(),
  email: z.string().nullish(),
  address: z.string().nullish(),
  valid: z.boolean(),
  corp: z.boolean(),
  refreshToken: z.string().nullish(),
})

export type Membership = z.output<typeof membershipSchema>

export type NewMembership = { name: string; email: string; address: string; corp: boolean }

export function registerMembership(input: NewMembership) {
  return call('membership', '/memberships', membershipSchema, { method: 'POST', body: { json: input } })
}

export function findMembership(membershipId: string) {
  return call('membership', `/memberships/${segment(membershipId)}`, membershipSchema)
}
