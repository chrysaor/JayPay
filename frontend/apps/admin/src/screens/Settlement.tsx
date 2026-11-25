import { ErrorNotice } from '@jaypay/ui/ErrorNotice'
import { formatFeeRate } from '@jaypay/api/payment'
import { formatDateTime, formatWon } from '@jaypay/ui/format'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { findAwaitingSettlement, finishSettlement } from '../api/settlement'
import { useAccess } from '../permissions'

const queueKey = ['settlement-queue']

export function Settlement() {
  const { settlePayments } = useAccess()
  const queryClient = useQueryClient()
  const queue = useQuery({ queryKey: queueKey, queryFn: findAwaitingSettlement })
  const settle = useMutation({
    mutationFn: finishSettlement,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queueKey }),
  })
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
      <ErrorNotice error={queue.error ?? settle.error} />
      {settle.data ? (
        <p className="notice notice-success" role="status">
          {settle.data.alreadySettled
            ? `Payment #${String(settle.data.paymentId)} was already settled. Nothing changed.`
            : `Payment #${String(settle.data.paymentId)} is marked settled.`}
        </p>
      ) : null}
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
                {settlePayments ? <th /> : null}
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
                    <span className="badge badge-warn">Awaiting settlement</span>
                  </td>
                  {settlePayments ? (
                    <td>
                      <button
                        type="button"
                        disabled={settle.isPending}
                        onClick={() => {
                          settle.mutate(payment.paymentId)
                        }}
                      >
                        Mark settled
                      </button>
                    </td>
                  ) : null}
                </tr>
              ))}
            </tbody>
          </table>
        </>
      ) : null}
    </>
  )
}
