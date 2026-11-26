import { Card } from '@jaypay/ui/Card'
import { ErrorNotice } from '@jaypay/ui/ErrorNotice'
import { readForm } from '@jaypay/ui/form'
import { formatWon } from '@jaypay/ui/format'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { type Remittance, requestRemittance, toBankAccount, toMembership } from '../api/remittance'
import { useMember } from '../session'

type Transfer = { amount: number } & (
  | { remittanceType: typeof toMembership; toMembershipId: string }
  | { remittanceType: typeof toBankAccount; toBankName: string; toBankAccountNumber: string }
)

function receiverOf(remittance: Remittance) {
  return remittance.remittanceType === toMembership
    ? `member ${remittance.toMembershipId ?? ''}`
    : `${remittance.toBankName ?? ''} ${remittance.toBankAccountNumber ?? ''}`
}

function Outcome({ remittance }: { remittance: Remittance }) {
  const amount = formatWon(remittance.amount)
  switch (remittance.remittanceStatus) {
    case 'COMPLETED':
      return (
        <p className="notice notice-success" role="status">
          Sent {amount} to {receiverOf(remittance)}.
        </p>
      )
    case 'FAILED':
      return (
        <p className="notice notice-error" role="alert">
          The remittance was declined: your wallet holds less than {amount}.
        </p>
      )
    case 'REFUNDED':
      return (
        <p className="notice notice-error" role="alert">
          The money could not be delivered to {receiverOf(remittance)}. {amount} is back in your wallet.
        </p>
      )
    case 'TRANSFERRING':
      return (
        <p className="notice" role="status">
          {amount} left your wallet, and the bank has not confirmed the transfer yet. It will not be sent twice.
        </p>
      )
    default:
      return (
        <p className="notice" role="status">
          Status: {remittance.remittanceStatus}
        </p>
      )
  }
}

export function Send() {
  const { membershipId } = useMember()
  const queryClient = useQueryClient()
  const [target, setTarget] = useState<typeof toMembership | typeof toBankAccount>(toMembership)
  const pending = useRef<{ transfer: string; requestId: string } | null>(null)
  const remit = useMutation({
    mutationFn: (transfer: Transfer) => {
      const key = JSON.stringify(transfer)
      if (pending.current?.transfer !== key) pending.current = { transfer: key, requestId: crypto.randomUUID() }
      return requestRemittance({ ...transfer, requestId: pending.current.requestId, fromMembershipId: membershipId })
    },
    onSuccess: () => {
      pending.current = null
      return queryClient.invalidateQueries({ queryKey: ['balance', membershipId] })
    },
  })
  return (
    <>
      <h1>Send</h1>
      <Card title="Send money">
        <form
          className="stack"
          onSubmit={(event) => {
            const form = readForm(event)
            const amount = form.number('amount')
            remit.mutate(
              target === toMembership
                ? { amount, remittanceType: toMembership, toMembershipId: form.text('toMembershipId') }
                : {
                    amount,
                    remittanceType: toBankAccount,
                    toBankName: form.text('toBankName'),
                    toBankAccountNumber: form.text('toBankAccountNumber'),
                  },
            )
          }}
        >
          <fieldset className="segmented">
            <legend>Send to</legend>
            <label>
              <input
                type="radio"
                name="target"
                checked={target === toMembership}
                onChange={() => {
                  setTarget(toMembership)
                }}
              />
              JayPay member
            </label>
            <label>
              <input
                type="radio"
                name="target"
                checked={target === toBankAccount}
                onChange={() => {
                  setTarget(toBankAccount)
                }}
              />
              Bank account
            </label>
          </fieldset>
          {target === toMembership ? (
            <label>
              Recipient membership ID
              <input name="toMembershipId" inputMode="numeric" pattern="[0-9]+" required />
            </label>
          ) : (
            <>
              <label>
                Bank name
                <input name="toBankName" maxLength={64} required />
              </label>
              <label>
                Account number
                <input name="toBankAccountNumber" inputMode="numeric" maxLength={64} required />
              </label>
            </>
          )}
          <label>
            Amount
            <input name="amount" type="number" min="1" step="1" required />
          </label>
          <button className="primary" disabled={remit.isPending}>
            Send money
          </button>
        </form>
        {remit.data ? <Outcome remittance={remit.data} /> : null}
        <ErrorNotice error={remit.error} />
      </Card>
    </>
  )
}
