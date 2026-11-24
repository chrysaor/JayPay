import { z } from 'zod'

export const paymentSchema = z.object({
  paymentId: z.number(),
  requestMembershipId: z.string().nullish(),
  requestPrice: z.number(),
  franchiseId: z.string().nullish(),
  franchiseFeeRate: z.string().nullish(),
  paymentStatus: z.number(),
  approvedAt: z.union([z.string(), z.number()]).nullish(),
})

export type Payment = z.output<typeof paymentSchema>
