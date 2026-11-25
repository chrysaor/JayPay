import { call, postJson } from '@jaypay/api/client'
import { paymentSchema } from '@jaypay/api/payment'
import { z } from 'zod'

const settlementResultSchema = z.object({
  paymentId: z.number(),
  paymentStatus: z.literal('SETTLED'),
  alreadySettled: z.boolean(),
})

export function findAwaitingSettlement() {
  return call('payment', '/payment/normal-status', z.array(paymentSchema))
}

export function finishSettlement(paymentId: number) {
  return call('payment', '/payment/finish-settlement', settlementResultSchema, postJson({ paymentId: String(paymentId) }))
}
