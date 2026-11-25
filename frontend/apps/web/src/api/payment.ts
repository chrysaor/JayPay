import { call, postJson } from '@jaypay/api/client'
import { paymentSchema } from '@jaypay/api/payment'

export type NewPayment = {
  requestId: string
  requestMembershipId: string
  franchiseId: string
  requestPrice: number
}

export function requestPayment(input: NewPayment) {
  return call('payment', '/payment/request', paymentSchema, postJson(input))
}
