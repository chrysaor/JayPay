import { ErrorNotice } from '@jaypay/ui/ErrorNotice'
import { readForm } from '@jaypay/ui/form'
import { useMutation } from '@tanstack/react-query'
import { Navigate, useLocation } from 'react-router'
import { z } from 'zod'
import { signIn } from '../api/staff'
import { useSession, useStartSession } from '../session'

const returnSchema = z.object({ from: z.string() })

export function SignIn() {
  const session = useSession()
  const startSession = useStartSession()
  const location = useLocation()
  const attempt = useMutation({ mutationFn: signIn, onSuccess: startSession })
  if (session.isPending) return <p className="muted loading">Loading...</p>
  if (session.data?.signedIn) {
    const back = returnSchema.safeParse(location.state)
    return <Navigate to={back.success ? back.data.from : '/members'} replace />
  }
  return (
    <main className="auth">
      <div className="auth-card">
        <p className="brand">
          JayPay <span>Admin</span>
        </p>
        <h1>Sign in to JayPay Admin</h1>
        {session.data?.notice ? (
          <p className="notice notice-info" role="status">
            {session.data.notice}
          </p>
        ) : null}
        <form
          className="stack"
          onSubmit={(event) => {
            const form = readForm(event)
            attempt.mutate({ username: form.text('username'), password: form.raw('password') })
          }}
        >
          <label>
            Username
            <input name="username" autoComplete="username" autoCapitalize="none" required />
          </label>
          <label>
            Password
            <input name="password" type="password" autoComplete="current-password" required />
          </label>
          <button className="primary" disabled={attempt.isPending}>
            Sign in
          </button>
          <ErrorNotice error={attempt.error} />
        </form>
      </div>
    </main>
  )
}
