import { Card } from '@jaypay/ui/Card'
import { ErrorNotice } from '@jaypay/ui/ErrorNotice'
import { readForm } from '@jaypay/ui/form'
import { useMutation } from '@tanstack/react-query'
import { useId } from 'react'
import { changeOwnPassword, passwordHint } from '../api/staff'
import { roles } from '../permissions'
import { useStaff } from '../session'

async function changePassword(change: { currentPassword: string; newPassword: string; confirmation: string }) {
  if (change.newPassword !== change.confirmation) throw new Error('The new passwords do not match.')
  await changeOwnPassword({ currentPassword: change.currentPassword, newPassword: change.newPassword })
}

function PasswordForm() {
  const change = useMutation({ mutationFn: changePassword })
  const hintId = useId()
  return (
    <Card title="Change password">
      <form
        className="stack"
        onSubmit={(event) => {
          const element = event.currentTarget
          const form = readForm(event)
          change.mutate(
            {
              currentPassword: form.raw('currentPassword'),
              newPassword: form.raw('newPassword'),
              confirmation: form.raw('confirmation'),
            },
            {
              onSuccess: () => {
                element.reset()
              },
            },
          )
        }}
      >
        <label>
          Current password
          <input name="currentPassword" type="password" autoComplete="current-password" required />
        </label>
        <label>
          New password
          <input name="newPassword" type="password" autoComplete="new-password" aria-describedby={hintId} required />
        </label>
        <p id={hintId} className="hint">
          {passwordHint}
        </p>
        <label>
          Confirm new password
          <input name="confirmation" type="password" autoComplete="new-password" required />
        </label>
        <button className="primary" disabled={change.isPending}>
          Change password
        </button>
      </form>
      {change.isSuccess ? (
        <p className="notice notice-success" role="status">
          Password changed.
        </p>
      ) : null}
      <ErrorNotice error={change.error} />
    </Card>
  )
}

export function MyAccount() {
  const { account } = useStaff()
  return (
    <>
      <h1>My account</h1>
      <Card title="Account">
        <dl className="facts">
          <dt>Username</dt>
          <dd>{account.username}</dd>
          <dt>Role</dt>
          <dd>{roles[account.role].label}</dd>
        </dl>
      </Card>
      <PasswordForm />
    </>
  )
}
