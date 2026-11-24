import { Card } from '@jaypay/ui/Card'
import { ErrorNotice } from '@jaypay/ui/ErrorNotice'
import { readForm } from '@jaypay/ui/form'
import { formatWon } from '@jaypay/ui/format'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { findBalance, increaseMoney } from '../api/money'
import { useMember } from '../session'

export function Wallet() {
  const { membershipId } = useMember()
  const queryClient = useQueryClient()
  const balanceKey = ['balance', membershipId]
  const balance = useQuery({ queryKey: balanceKey, queryFn: () => findBalance(membershipId) })
  const charge = useMutation({
    mutationFn: (amount: number) => increaseMoney({ targetMembershipId: membershipId, amount }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: balanceKey }),
  })
  return (
    <>
      <h1>Wallet</h1>
      <div className="grid">
        <Card title="Balance">
          {balance.data === undefined ? null : <p className="amount">{formatWon(balance.data)}</p>}
          {balance.isPending ? <p className="muted">Loading...</p> : null}
          <ErrorNotice error={balance.error} />
        </Card>
        <Card title="Add money">
          <form
            className="stack"
            onSubmit={(event) => {
              charge.mutate(readForm(event).number('amount'))
              event.currentTarget.reset()
            }}
          >
            <label>
              Amount
              <input name="amount" type="number" min="1" step="1" required />
            </label>
            <button className="primary" disabled={charge.isPending}>
              Add money
            </button>
          </form>
          {charge.data ? (
            <p className="notice notice-success" role="status">
              Added {formatWon(charge.data.amount)} to your wallet.
            </p>
          ) : null}
          <ErrorNotice error={charge.error} />
        </Card>
      </div>
    </>
  )
}
