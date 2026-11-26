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

export function findFlows(status: Outcome | null) {
  return call('event-flow', status ? `/flows?status=${status}` : '/flows', z.array(flowSchema))
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
}

const flowTypeLabels: Partial<Record<string, string>> = {
  RECHARGE: 'Top-up',
}

export function stepLabel(step: string) {
  return stepLabels[step] ?? step
}

export function flowTypeLabel(flowType: string) {
  return flowTypeLabels[flowType] ?? flowType
}

export function formatGap(from: string, to: string) {
  const millis = Math.max(0, new Date(to).getTime() - new Date(from).getTime())
  if (millis < 1000) return `+${String(millis)} ms`
  if (millis < 60_000) return `+${(millis / 1000).toFixed(1)} s`
  return `+${(millis / 60_000).toFixed(1)} min`
}
