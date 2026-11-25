import { useEffect, useRef, type ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { beginSignIn, type Tokens } from '../keycloak'
import { useFinishSignIn, useSignOut } from '../session'

function AuthCard({ title, children }: { title: string; children: ReactNode }) {
  return (
    <main className="auth">
      <div className="auth-card stack">
        <p className="brand">
          JayPay <span>Admin</span>
        </p>
        <h1>{title}</h1>
        {children}
      </div>
    </main>
  )
}

function SignInButton({ returnTo }: { returnTo: string }) {
  return (
    <button type="button" className="primary" onClick={() => void beginSignIn(returnTo)}>
      Sign in
    </button>
  )
}

export function SignInRedirect({ returnTo }: { returnTo: string }) {
  const started = useRef(false)
  useEffect(() => {
    if (started.current) return
    started.current = true
    void beginSignIn(returnTo)
  }, [returnTo])
  return <p className="muted loading">Opening the sign-in page...</p>
}

export function SignInAgain({ notice, returnTo }: { notice: string; returnTo: string }) {
  return (
    <AuthCard title="Sign in to JayPay Admin">
      <p className="notice notice-info" role="status">
        {notice}
      </p>
      <SignInButton returnTo={returnTo} />
    </AuthCard>
  )
}

export function SignedOut() {
  return <SignInAgain notice="You signed out." returnTo="/members" />
}

export function NoRole({ tokens, username }: { tokens: Tokens; username: string }) {
  const signOut = useSignOut(tokens)
  return (
    <AuthCard title="No access to JayPay Admin">
      <p className="notice notice-info" role="status">
        {username} has no admin console role. Ask an admin to give the account the viewer, operator or admin role.
      </p>
      <button type="button" className="primary" onClick={signOut}>
        Sign out
      </button>
    </AuthCard>
  )
}

export function Callback() {
  const { search } = useLocation()
  const signIn = useFinishSignIn(search)
  if (signIn.isPending) return <p className="muted loading">Signing in...</p>
  if (signIn.isError) {
    return (
      <AuthCard title="Sign in to JayPay Admin">
        <p className="notice notice-error" role="alert">
          {signIn.error.message}
        </p>
        <SignInButton returnTo="/members" />
      </AuthCard>
    )
  }
  return <Navigate to={signIn.data} replace />
}
