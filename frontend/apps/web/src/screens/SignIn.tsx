import { ErrorNotice } from '@jaypay/ui/ErrorNotice'
import { readForm } from '@jaypay/ui/form'
import { useMutation } from '@tanstack/react-query'
import { Link, Navigate, useNavigate } from 'react-router'
import { logIn } from '../api/auth'
import { useSession, useStartSession } from '../session'

export function SignIn() {
  const session = useSession()
  const startSession = useStartSession()
  const navigate = useNavigate()
  const signIn = useMutation({
    mutationFn: logIn,
    onSuccess: (issued) => {
      startSession(issued)
      void navigate('/')
    },
  })
  if (session.data) return <Navigate to="/" replace />
  return (
    <main className="auth">
      <div className="auth-card">
        <p className="brand">JayPay</p>
        <h1>Sign in to JayPay</h1>
        <form
          className="stack"
          onSubmit={(event) => {
            signIn.mutate(readForm(event).text('membershipId'))
          }}
        >
          <label>
            Membership ID
            <input name="membershipId" inputMode="numeric" autoComplete="username" required />
          </label>
          <button className="primary" disabled={signIn.isPending}>
            Sign in
          </button>
          <ErrorNotice error={signIn.error} />
        </form>
        <p className="muted">
          New to JayPay? <Link to="/sign-up">Create an account</Link>
        </p>
      </div>
    </main>
  )
}
