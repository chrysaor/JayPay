import { Link, Navigate, NavLink, Outlet, Route, Routes, useLocation } from 'react-router'
import type { StaffSession } from './api/staff'
import { roles, useAccess, type Permission } from './permissions'
import { MemberDetail } from './screens/MemberDetail'
import { Members } from './screens/Members'
import { MyAccount } from './screens/MyAccount'
import { RegionalMoney } from './screens/RegionalMoney'
import { Settlement } from './screens/Settlement'
import { SignIn } from './screens/SignIn'
import { StaffAccounts } from './screens/StaffAccounts'
import { useEndOnExpiry, useEndSession, useSession } from './session'

const menu: { to: string; label: string; needs?: Permission }[] = [
  { to: '/members', label: 'Members' },
  { to: '/money', label: 'Regional money' },
  { to: '/settlement', label: 'Settlement' },
  { to: '/staff', label: 'Staff accounts', needs: 'manageStaff' },
  { to: '/account', label: 'My account' },
]

function Console({ session }: { session: StaffSession }) {
  const endSession = useEndSession()
  useEndOnExpiry(session)
  const access = roles[session.account.role]
  return (
    <div className="console">
      <aside className="sidebar">
        <Link to="/" className="brand">
          JayPay <span>Admin</span>
        </Link>
        <nav aria-label="Admin menu">
          {menu
            .filter((item) => !item.needs || access[item.needs])
            .map((item) => (
              <NavLink key={item.to} to={item.to}>
                {item.label}
              </NavLink>
            ))}
        </nav>
        <div className="staff">
          <span className="staff-name">{session.account.username}</span>
          <span className="staff-role">{access.label}</span>
          <button type="button" className="link-button" onClick={endSession}>
            Sign out
          </button>
        </div>
      </aside>
      <main className="content">
        <Outlet context={session} />
      </main>
    </div>
  )
}

function SignedIn() {
  const session = useSession()
  const location = useLocation()
  if (session.isPending) return <p className="muted loading">Loading...</p>
  if (!session.data?.signedIn) return <Navigate to="/sign-in" replace state={{ from: location.pathname + location.search }} />
  return <Console session={session.data.session} />
}

function NotFound() {
  return (
    <>
      <h1>Page not found</h1>
      <Link to="/">Back to the console</Link>
    </>
  )
}

function StaffOnly() {
  return useAccess().manageStaff ? <StaffAccounts /> : <NotFound />
}

export function App() {
  return (
    <Routes>
      <Route path="/sign-in" element={<SignIn />} />
      <Route element={<SignedIn />}>
        <Route index element={<Navigate to="/members" replace />} />
        <Route path="members" element={<Members />} />
        <Route path="members/:membershipId" element={<MemberDetail />} />
        <Route path="money" element={<RegionalMoney />} />
        <Route path="settlement" element={<Settlement />} />
        <Route path="staff" element={<StaffOnly />} />
        <Route path="account" element={<MyAccount />} />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  )
}
