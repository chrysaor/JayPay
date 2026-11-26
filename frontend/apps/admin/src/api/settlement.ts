import { call } from '@jaypay/api/client'
import { paymentSchema } from '@jaypay/api/payment'
import { z } from 'zod'

export function findAwaitingSettlement() {
  return call('payment', '/payment/normal-status', z.array(paymentSchema))
}
