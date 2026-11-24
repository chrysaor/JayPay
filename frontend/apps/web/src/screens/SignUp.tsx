import { ErrorNotice } from '@jaypay/ui/ErrorNotice'
import { readForm } from '@jaypay/ui/form'
import { useMutation } from '@tanstack/react-query'
import { Link } from 'react-router'
import { logIn, registerMembership, type NewMembership } from '../api/auth'
import { useStartSession } from '../session'

export function SignUp() {
  const startSession = useStartSession()
  const signUp = useMutation({
    mutationFn: async (input: NewMembership) => {
      const membership = await registerMembership(input)
      return logIn(membership.membershipId)
    },
    onSuccess: startSession,
  })
  return (
    <main className="auth">
      <div className="auth-card">
        <p className="brand">JayPay</p>
        <h1>Create your account</h1>
        {signUp.data ? (
          <div className="stack">
            <p className="notice notice-success" role="status">
              Your membership ID is <strong>{signUp.data.membershipId}</strong>. Keep it to sign in next time.
            </p>
            <Link className="button primary" to="/">
              Go to my wallet
            </Link>
          </div>
        ) : (
          <form
            className="stack"
            onSubmit={(event) => {
              const form = readForm(event)
              signUp.mutate({
                name: form.text('name'),
                email: form.text('email'),
                address: form.text('address'),
                corp: form.checked('corp'),
              })
            }}
          >
            <label>
              Name
              <input name="name" autoComplete="name" required />
            </label>
            <label>
              Email
              <input name="email" type="email" autoComplete="email" required />
            </label>
            <label>
              Address
              <input name="address" autoComplete="address-level1" required />
            </label>
            <label className="check">
              <input name="corp" type="checkbox" />
              Corporate account
            </label>
            <button className="primary" disabled={signUp.isPending}>
              Create account
            </button>
            <ErrorNotice error={signUp.error} />
          </form>
        )}
        <p className="muted">
          Already a member? <Link to="/sign-in">Sign in</Link>
        </p>
      </div>
    </main>
  )
}
