import { Card } from '@jaypay/ui/Card'
import { ErrorNotice } from '@jaypay/ui/ErrorNotice'
import { readForm } from '@jaypay/ui/form'
import { formatDateTime, formatWon } from '@jaypay/ui/format'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useRef } from 'react'
import { requestPayment } from '../api/payment'
import { useMember } from '../session'

type Order = { franchiseId: string; requestPrice: number }

export function Pay() {
  const { membershipId } = useMember()
  const queryClient = useQueryClient()
  const pending = useRef<(Order & { requestId: string }) | null>(null)
  const pay = useMutation({
    mutationFn: (order: Order) => {
      if (pending.current?.franchiseId !== order.franchiseId || pending.current.requestPrice !== order.requestPrice) {
        pending.current = { ...order, requestId: crypto.randomUUID() }
      }
      return requestPayment({ requestId: pending.current.requestId, requestMembershipId: membershipId, ...order })
    },
    onSuccess: () => {
      pending.current = null
      return queryClient.invalidateQueries({ queryKey: ['balance', membershipId] })
    },
  })
  const payment = pay.data
  return (
    <>
      <h1>Pay</h1>
      <Card title="Pay a merchant">
        <form
          className="stack"
          onSubmit={(event) => {
            const form = readForm(event)
            pay.mutate({ franchiseId: form.text('franchiseId'), requestPrice: form.number('requestPrice') })
          }}
        >
          <label>
            Merchant ID
            <input name="franchiseId" inputMode="numeric" pattern="[0-9]+" required />
          </label>
          <label>
            Amount
            <input name="requestPrice" type="number" min="1" step="1" required />
          </label>
          <button className="primary" disabled={pay.isPending}>
            Pay
          </button>
        </form>
        {payment?.paymentStatus === 'PAID' ? (
          <div className="receipt" role="status">
            <p className="receipt-title">Payment #{payment.paymentId} approved</p>
            <dl className="details">
              <dt>Amount</dt>
              <dd>{formatWon(payment.requestPrice)}</dd>
              <dt>Merchant</dt>
              <dd>{payment.franchiseId}</dd>
              <dt>Approved at</dt>
              <dd>{formatDateTime(payment.approvedAt)}</dd>
            </dl>
          </div>
        ) : null}
        {payment?.paymentStatus === 'FAILED' ? (
          <div className="notice notice-error" role="alert">
            Payment #{payment.paymentId} was declined: your wallet holds less than {formatWon(payment.requestPrice)}.
          </div>
        ) : null}
        <ErrorNotice error={pay.error} />
      </Card>
    </>
  )
}
