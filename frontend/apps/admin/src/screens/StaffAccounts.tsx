import { Card } from '@jaypay/ui/Card'
import { ErrorNotice } from '@jaypay/ui/ErrorNotice'
import { readForm } from '@jaypay/ui/form'
import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { useId } from 'react'
import {
  createAccount,
  findAccounts,
  passwordHint,
  roleSchema,
  updateAccount,
  type AccessChange,
  type Account,
} from '../api/staff'
import { roles } from '../permissions'
import { useStaff } from '../session'

const accountsKey = ['staff-accounts']
const mfaNote = 'MFA is recorded only. Sign-in does not ask for a second step yet.'

function remember(queryClient: QueryClient, saved: Account) {
  queryClient.setQueryData<Account[]>(accountsKey, (accounts = []) =>
    [...accounts.filter((account) => account.username !== saved.username), saved].sort((a, b) =>
      a.username < b.username ? -1 : 1,
    ),
  )
}

function RoleOptions() {
  return roleSchema.options.map((role) => (
    <option key={role} value={role}>
      {roles[role].label}
    </option>
  ))
}

function NewAccountForm() {
  const queryClient = useQueryClient()
  const hintId = useId()
  const create = useMutation({
    mutationFn: createAccount,
    onSuccess: (saved) => {
      remember(queryClient, saved)
    },
  })
  return (
    <Card title="New account">
      <form
        className="stack"
        onSubmit={(event) => {
          const element = event.currentTarget
          const form = readForm(event)
          create.mutate(
            {
              username: form.text('username'),
              password: form.raw('password'),
              role: roleSchema.parse(form.text('role')),
              mfaEnabled: form.checked('mfaEnabled'),
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
          Username
          <input name="username" autoComplete="off" autoCapitalize="none" required />
        </label>
        <label>
          Password
          <input name="password" type="password" autoComplete="new-password" aria-describedby={hintId} required />
        </label>
        <p id={hintId} className="hint">
          {passwordHint}
        </p>
        <label>
          Role
          <select name="role" defaultValue="VIEWER">
            <RoleOptions />
          </select>
        </label>
        <label className="check">
          <input name="mfaEnabled" type="checkbox" />
          MFA enabled
        </label>
        <p className="hint">{mfaNote}</p>
        <button className="primary" disabled={create.isPending}>
          Create account
        </button>
      </form>
      {create.isSuccess ? (
        <p className="notice notice-success" role="status">
          Created {create.data.username}.
        </p>
      ) : null}
      <ErrorNotice error={create.error} />
    </Card>
  )
}

export function StaffAccounts() {
  const { account: me } = useStaff()
  const queryClient = useQueryClient()
  const accounts = useQuery({ queryKey: accountsKey, queryFn: findAccounts })
  const update = useMutation({
    mutationFn: ({ username, change }: { username: string; change: AccessChange }) => updateAccount(username, change),
    onSuccess: (saved) => {
      remember(queryClient, saved)
    },
  })
  const change = (account: Account, next: Partial<AccessChange>) => {
    update.mutate({
      username: account.username,
      change: { role: account.role, active: account.active, mfaEnabled: account.mfaEnabled, ...next },
    })
  }
  return (
    <>
      <h1>Staff accounts</h1>
      <ErrorNotice error={accounts.error ?? update.error} />
      {accounts.isPending ? <p className="muted">Loading...</p> : null}
      {accounts.data ? (
        <>
          <p className="summary">{mfaNote}</p>
          <table>
            <thead>
              <tr>
                <th>Username</th>
                <th>Role</th>
                <th>Status</th>
                <th>MFA</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {accounts.data.map((account) => {
                const own = account.username === me.username
                return (
                  <tr key={account.username}>
                    <td>
                      {account.username}
                      {own ? <span className="muted"> (you)</span> : null}
                    </td>
                    <td>
                      <select
                        aria-label={`Role for ${account.username}`}
                        value={account.role}
                        disabled={own || update.isPending}
                        onChange={(event) => {
                          change(account, { role: roleSchema.parse(event.target.value) })
                        }}
                      >
                        <RoleOptions />
                      </select>
                    </td>
                    <td>
                      <span className={account.active ? 'badge badge-ok' : 'badge badge-off'}>
                        {account.active ? 'Active' : 'Inactive'}
                      </span>
                    </td>
                    <td>
                      <input
                        type="checkbox"
                        aria-label={`MFA for ${account.username}`}
                        checked={account.mfaEnabled}
                        disabled={update.isPending}
                        onChange={(event) => {
                          change(account, { mfaEnabled: event.target.checked })
                        }}
                      />
                    </td>
                    <td>
                      <button
                        type="button"
                        disabled={own || update.isPending}
                        onClick={() => {
                          change(account, { active: !account.active })
                        }}
                      >
                        {account.active ? 'Deactivate' : 'Activate'}
                      </button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </>
      ) : null}
      <NewAccountForm />
    </>
  )
}
