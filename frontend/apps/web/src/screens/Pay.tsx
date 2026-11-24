import { Card } from '@jaypay/ui/Card'
import { ErrorNotice } from '@jaypay/ui/ErrorNotice'
import { readForm } from '@jaypay/ui/form'
import { formatDateTime, formatWon } from '@jaypay/ui/format'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { requestPayment } from '../api/payment'
import { useMember } from '../session'

export function Pay() {
  const { membershipId } = useMember()
  const queryClient = useQueryClient()
  const pay = useMutation({
    mutationFn: requestPayment,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['balance', membershipId] }),
  })
  return (
    <>
      <h1>Pay</h1>
      <Card title="Pay a merchant">
        <form
          className="stack"
          onSubmit={(event) => {
            const form = readForm(event)
            pay.mutate({
              requestMembershipId: membershipId,
              requestPrice: form.text('requestPrice'),
              franchiseId: form.text('franchiseId'),
              franchiseFeeRate: form.text('franchiseFeeRate'),
            })
          }}
        >
          <label>
            Merchant ID
            <input name="franchiseId" required />
          </label>
          <label>
            Amount
            <input name="requestPrice" type="number" min="1" step="1" required />
          </label>
          <label>
            Merchant fee rate (%)
            <input name="franchiseFeeRate" inputMode="decimal" pattern="[0-9]+(\.[0-9]+)?" required />
          </label>
          <button className="primary" disabled={pay.isPending}>
            Pay
          </button>
        </form>
        {pay.data ? (
          <div className="receipt" role="status">
            <p className="receipt-title">Payment #{pay.data.paymentId} approved</p>
            <dl className="details">
              <dt>Amount</dt>
              <dd>{formatWon(pay.data.requestPrice)}</dd>
              <dt>Merchant</dt>
              <dd>{pay.data.franchiseId}</dd>
              <dt>Fee rate</dt>
              <dd>{pay.data.franchiseFeeRate}%</dd>
              <dt>Approved at</dt>
              <dd>{formatDateTime(pay.data.approvedAt)}</dd>
            </dl>
          </div>
        ) : null}
        <ErrorNotice error={pay.error} />
      </Card>
    </>
  )
}
