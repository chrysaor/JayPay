import { call } from '@jaypay/api/client'
import { paymentSchema } from '@jaypay/api/payment'

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
