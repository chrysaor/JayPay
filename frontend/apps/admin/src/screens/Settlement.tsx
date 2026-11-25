import { ErrorNotice } from '@jaypay/ui/ErrorNotice'
import { formatDateTime, formatWon } from '@jaypay/ui/format'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { findAwaitingSettlement, finishSettlement } from '../api/settlement'

const queueKey = ['settlement-queue']

export function Settlement() {
  const queryClient = useQueryClient()
  const queue = useQuery({ queryKey: queueKey, queryFn: findAwaitingSettlement })
  const settle = useMutation({
    mutationFn: finishSettlement,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queueKey }),
  })
  const payments = queue.data ?? []
  const total = payments.reduce((sum, payment) => sum + payment.requestPrice, 0)
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
      {queue.data?.length === 0 ? <p className="empty">No payments are awaiting settlement.</p> : null}
      {payments.length > 0 ? (
        <>
          <p className="summary" role="status">
            {payments.length} {payments.length === 1 ? 'payment' : 'payments'} awaiting settlement, {formatWon(total)}{' '}
            in total
          </p>
          <table>
            <thead>
              <tr>
                <th>Payment</th>
                <th>Member</th>
                <th>Merchant</th>
                <th>Amount</th>
                <th>Fee rate</th>
                <th>Approved at</th>
                <th>Status</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {payments.map((payment) => (
                <tr key={payment.paymentId}>
                  <td>#{payment.paymentId}</td>
                  <td>{payment.requestMembershipId}</td>
                  <td>{payment.franchiseId}</td>
                  <td>{formatWon(payment.requestPrice)}</td>
                  <td>{payment.franchiseFeeRate}%</td>
                  <td>{formatDateTime(payment.approvedAt)}</td>
                  <td>
                    <span className="badge badge-warn">Awaiting settlement</span>
                  </td>
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
                </tr>
              ))}
            </tbody>
          </table>
        </>
      ) : null}
    </>
  )
}
