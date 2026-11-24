import { Card } from '@jaypay/ui/Card'
import { ErrorNotice } from '@jaypay/ui/ErrorNotice'
import { readForm } from '@jaypay/ui/form'
import { formatWon } from '@jaypay/ui/format'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { requestRemittance, toBankAccount, toMembership } from '../api/remittance'
import { useMember } from '../session'

export function Send() {
  const { membershipId } = useMember()
  const queryClient = useQueryClient()
  const [target, setTarget] = useState<typeof toMembership | typeof toBankAccount>(toMembership)
  const remit = useMutation({
    mutationFn: requestRemittance,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['balance', membershipId] }),
  })
  return (
    <>
      <h1>Send</h1>
      <Card title="Send money">
        <form
          className="stack"
          onSubmit={(event) => {
            const form = readForm(event)
            const common = { fromMembershipId: membershipId, amount: form.number('amount') }
            remit.mutate(
              target === toMembership
                ? { ...common, remittanceType: toMembership, toMembershipId: form.text('toMembershipId') }
                : {
                    ...common,
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
              <input name="toMembershipId" inputMode="numeric" required />
            </label>
          ) : (
            <>
              <label>
                Bank name
                <input name="toBankName" required />
              </label>
              <label>
                Account number
                <input name="toBankAccountNumber" inputMode="numeric" required />
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
        {remit.data ? (
          <p className="notice notice-success" role="status">
            Sent {formatWon(remit.data.amount)}.
            {remit.data.remittanceStatus ? ` Status: ${remit.data.remittanceStatus}` : ''}
          </p>
        ) : null}
        <ErrorNotice error={remit.error} />
      </Card>
    </>
  )
}
