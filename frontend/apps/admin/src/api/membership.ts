import { call, postJson } from '@jaypay/api/client'
import { membershipSchema, type Membership } from '@jaypay/api/membership'
import { z } from 'zod'

export function findMembershipsByAddress(address: string) {
  return call('membership', `/memberships/address/${encodeURIComponent(address)}`, z.array(membershipSchema))
}

export function modifyMembership(membership: Membership) {
  return call('membership', `/memberships/${encodeURIComponent(membership.membershipId)}`, membershipSchema, {
    ...postJson(membership),
    method: 'PUT',
  })
}
