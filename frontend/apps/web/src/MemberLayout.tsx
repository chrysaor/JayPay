import { findMembership } from '@jaypay/api/membership'
import { useQuery } from '@tanstack/react-query'
import { Link, Navigate, NavLink, Outlet } from 'react-router'
import { useEndSession, useSession } from './session'

const menu = [
  { to: '/', label: 'Wallet' },
  { to: '/account', label: 'Bank account' },
  { to: '/send', label: 'Send' },
  { to: '/pay', label: 'Pay' },
]

function MemberName({ membershipId }: { membershipId: string }) {
  const membership = useQuery({ queryKey: ['membership', membershipId], queryFn: () => findMembership(membershipId) })
  return <span className="member-name">{membership.data?.name ?? `Member ${membershipId}`}</span>
}

export function MemberLayout() {
  const session = useSession()
  const endSession = useEndSession()
  if (session.isPending) return <p className="centered muted">Loading...</p>
  if (!session.data) return <Navigate to="/sign-in" replace />
  return (
    <div className="shell">
      <header className="topbar">
        <Link to="/" className="brand">
          JayPay
        </Link>
        <nav aria-label="Main menu">
          {menu.map((item) => (
            <NavLink key={item.to} to={item.to} end>
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="member">
          <MemberName membershipId={session.data.membershipId} />
          <button type="button" className="link-button" onClick={endSession}>
            Sign out
          </button>
        </div>
      </header>
      <main className="content">
        <Outlet context={session.data} />
      </main>
    </div>
  )
}
