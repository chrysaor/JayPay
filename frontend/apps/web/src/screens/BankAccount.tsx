import { Card } from '@jaypay/ui/Card'
import { ErrorNotice } from '@jaypay/ui/ErrorNotice'
import { readForm } from '@jaypay/ui/form'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { findBankAccount, registerBankAccount } from '../api/banking'
import { useMember } from '../session'

export function BankAccount() {
  const { membershipId } = useMember()
  const queryClient = useQueryClient()
  const accountKey = ['bank-account', membershipId]
  const account = useQuery({ queryKey: accountKey, queryFn: () => findBankAccount(membershipId) })
  const link = useMutation({
    mutationFn: registerBankAccount,
    onSuccess: (linked) => {
      queryClient.setQueryData(accountKey, linked)
    },
  })
  return (
    <>
      <h1>Bank account</h1>
      {account.isPending ? <p className="muted">Loading...</p> : null}
      <ErrorNotice error={account.error} />
      {account.data ? (
        <Card title="Linked account">
          <dl className="details">
            <dt>Bank</dt>
            <dd>{account.data.bankName}</dd>
            <dt>Account number</dt>
            <dd>{account.data.bankAccountNumber}</dd>
            <dt>Status</dt>
            <dd>
              <span className={account.data.linkedStatusIsValid ? 'badge badge-ok' : 'badge badge-warn'}>
                {account.data.linkedStatusIsValid ? 'Linked' : 'Not linked'}
              </span>
            </dd>
          </dl>
        </Card>
      ) : null}
      {account.data === null || account.isError ? (
        <Card title="Link a bank account">
          <p className="muted">Link an account to move money between your bank and JayPay.</p>
          <form
            className="stack"
            onSubmit={(event) => {
              const form = readForm(event)
              link.mutate({
                membershipId,
                bankName: form.text('bankName'),
                bankAccountNumber: form.text('bankAccountNumber'),
                valid: true,
              })
            }}
          >
            <label>
              Bank name
              <input name="bankName" required />
            </label>
            <label>
              Account number
              <input name="bankAccountNumber" inputMode="numeric" required />
            </label>
            <button className="primary" disabled={link.isPending}>
              Link account
            </button>
          </form>
          <ErrorNotice error={link.error} />
        </Card>
      ) : null}
    </>
  )
}
