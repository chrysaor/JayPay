import { ErrorNotice } from '@jaypay/ui/ErrorNotice'
import { formatFeeRate } from '@jaypay/api/payment'
import { formatDateTime, formatWon } from '@jaypay/ui/format'
import { useQuery } from '@tanstack/react-query'
import { findAwaitingSettlement } from '../api/settlement'

export function Settlement() {
  const queue = useQuery({ queryKey: ['settlement-queue'], queryFn: findAwaitingSettlement })
  const payments = queue.data ?? []
  const total = payments.reduce((sum, payment) => sum + payment.requestPrice, 0)
  const payout = payments.reduce((sum, payment) => sum + payment.payout, 0)
  return (
    <>
      <div className="heading-row">
        <h1>Settlement</h1>
        <button
          type="button"
          onClick={() => {
            void queue.refetch()
          }}
          disabled={queue.isFetching}
        >
          Refresh
        </button>
      </div>
      <p className="muted">
        The settlement batch pays each merchant. A payment leaves this list once the bank confirms its transfer.
      </p>
      <ErrorNotice error={queue.error} />
      {queue.data?.length === 0 ? <p className="empty">No payments are awaiting settlement.</p> : null}
      {payments.length > 0 ? (
        <>
          <p className="summary">
            {payments.length} {payments.length === 1 ? 'payment' : 'payments'} awaiting settlement, {formatWon(total)}{' '}
            in total, {formatWon(payout)} to pay out
          </p>
          <table>
            <thead>
              <tr>
                <th>Payment</th>
                <th>Member</th>
                <th>Merchant</th>
                <th>Amount</th>
                <th>Fee rate</th>
                <th>Fee</th>
                <th>Payout</th>
                <th>Approved at</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {payments.map((payment) => (
                <tr key={payment.paymentId}>
                  <td>#{payment.paymentId}</td>
                  <td>{payment.requestMembershipId}</td>
                  <td>{payment.franchiseId}</td>
                  <td>{formatWon(payment.requestPrice)}</td>
                  <td>{formatFeeRate(payment.feeRateBasisPoints)}</td>
                  <td>{formatWon(payment.fee)}</td>
                  <td>{formatWon(payment.payout)}</td>
                  <td>{formatDateTime(payment.approvedAt)}</td>
                  <td>
                    <span className="badge badge-warn">Awaiting payout</span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      ) : null}
    </>
  )
}
