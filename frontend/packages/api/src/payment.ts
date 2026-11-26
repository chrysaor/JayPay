import { z } from 'zod'

export const paymentSchema = z.object({
  paymentId: z.number(),
  requestId: z.string(),
  requestMembershipId: z.string(),
  franchiseId: z.string(),
  requestPrice: z.number(),
  feeRateBasisPoints: z.number(),
  fee: z.number(),
  payout: z.number(),
  paymentStatus: z.enum(['REQUESTED', 'PAID', 'FAILED', 'SETTLED']),
  approvedAt: z.union([z.string(), z.number()]).nullish(),
})

export type Payment = z.output<typeof paymentSchema>

export function formatFeeRate(basisPoints: number) {
  return `${String(basisPoints / 100)}%`
}
