import { call, postJson, request } from '@jaypay/api/client'
import { paymentSchema } from '@jaypay/api/payment'
import { z } from 'zod'

export function findAwaitingSettlement() {
  return call('payment', '/payment/normal-status', z.array(paymentSchema))
}

export async function finishSettlement(paymentId: number) {
  await request('payment', '/payment/finish-settlement', postJson({ paymentId: String(paymentId) }))
}
