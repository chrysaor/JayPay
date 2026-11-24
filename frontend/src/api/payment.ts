import { z } from 'zod'
import { call } from './client'

const paymentSchema = z.object({
  paymentId: z.number(),
  requestMembershipId: z.string().nullish(),
  requestPrice: z.number(),
  franchiseId: z.string().nullish(),
  franchiseFeeRate: z.string().nullish(),
  paymentStatus: z.number(),
  approvedAt: z.union([z.string(), z.number()]).nullish(),
})

export type NewPayment = {
  requestMembershipId: string
  requestPrice: string
  franchiseId: string
  franchiseFeeRate: string
}

export function requestPayment(input: NewPayment) {
  return call('payment', '/payment/request', paymentSchema, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams(input),
  })
}

export function findNormalStatusPayments() {
  return call('payment', '/payment/normal-status', z.array(paymentSchema))
}
