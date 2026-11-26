import { call } from '@jaypay/api/client'
import { z } from 'zod'

export const outcomeSchema =z.enum(['IN_PROGRESS', 'SUCCEEDED', 'FAILED'])

export type Outcome = z.output<typeof outcomeSchema>

const flowSchema = z.object({
  flowType: z.string(),
  flowId: z.string(),
  status: outcomeSchema,
  membershipId: z.string(),
  amount: z.number(),
  startedAt: z.string(),
  updatedAt: z.string(),
  lastStep: z.string(),
  failedStep: z.string().nullable(),
  stepCount: z.number(),
})

const stepSchema = z.object({
  step: z.string(),
  service: z.string(),
  outcome: outcomeSchema,
  reason: z.string().nullable(),
  occurredAt: z.string(),
})

const flowDetailSchema = flowSchema.extend({ steps: z.array(stepSchema) })

export type FlowDetail = z.output<typeof flowDetailSchema>

export const flowTypeSchema = z.enum(['RECHARGE', 'REMITTANCE', 'PAYMENT', 'SETTLEMENT'])

export type FlowType = z.output<typeof flowTypeSchema>

export function findFlows(status: Outcome | null, flowType: FlowType | null) {
  const query = new URLSearchParams({ ...(flowType ? { flowType } : {}), ...(status ? { status } : {}) }).toString()
  return call('event-flow', query ? `/flows?${query}` : '/flows', z.array(flowSchema))
}

export function findFlow(flowType: string, flowId: string) {
  return call('event-flow', `/flows/${encodeURIComponent(flowType)}/${encodeURIComponent(flowId)}`, flowDetailSchema)
}

const stepLabels: Partial<Record<string, string>> = {
  RECHARGE_REQUESTED: 'Recharge requested',
  MEMBERSHIP_CHECKED: 'Member checked',
  BANK_ACCOUNT_CHECKED: 'Bank account checked',
  BANK_CHARGED: 'Bank charged',
  WALLET_INCREASED: 'Wallet increased',
  RECHARGE_FAILED: 'Recharge failed',
  CHARGE_ROLLED_BACK: 'Charge rolled back',
  REMITTANCE_REQUESTED: 'Remittance requested',
  SENDER_DEBITED: 'Sender debited',
  RECEIVER_CREDITED: 'Receiver credited',
  BANK_TRANSFERRED: 'Bank transfer',
  SENDER_REFUNDED: 'Sender refunded',
  PAYMENT_REQUESTED: 'Payment requested',
  PAYER_DEBITED: 'Payer debited',
  SETTLEMENT_STARTED: 'Settlement started',
  MERCHANT_PAID_OUT: 'Merchant paid out',
  PAYMENT_SETTLED: 'Payment settled',
}

export const flowTypeLabels: Record<FlowType, string> = {
  RECHARGE: 'Top-up',
  REMITTANCE: 'Remittance',
  PAYMENT: 'Payment',
  SETTLEMENT: 'Settlement',
}

export function stepLabel(step: string) {
  return stepLabels[step] ?? step
}

export function flowTypeLabel(flowType: string) {
  const known = flowTypeSchema.safeParse(flowType)
  return known.success ? flowTypeLabels[known.data] : flowType
}

export function formatGap(from: string, to: string) {
  const millis = Math.max(0, new Date(to).getTime() - new Date(from).getTime())
  if (millis < 1000) return `+${String(millis)} ms`
  if (millis < 60_000) return `+${(millis / 1000).toFixed(1)} s`
  return `+${(millis / 60_000).toFixed(1)} min`
}
