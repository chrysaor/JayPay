import { Link, Navigate, NavLink, Outlet, Route, Routes, useLocation } from 'react-router'
import { roles } from './permissions'
import { MemberDetail } from './screens/MemberDetail'
import { Members } from './screens/Members'
import { MyAccount } from './screens/MyAccount'
import { RegionalMoney } from './screens/RegionalMoney'
import { Settlement } from './screens/Settlement'
import { Callback, NoRole, SignedOut, SignInAgain, SignInRedirect } from './screens/SignIn'
import { useKeepFresh, useSession, useSignOut, type Session } from './session'

const menu = [
  { to: '/members', label: 'Members' },
  { to: '/money', label: 'Regional money' },
  { to: '/settlement', label: 'Settlement' },
  { to: '/account', label: 'My account' },
]

function Console({ session }: { session: Session }) {
  const signOut = useSignOut(session.tokens)
  useKeepFresh(session.tokens)
  return (
    <div className="console">
      <aside className="sidebar">
        <Link to="/" className="brand">
          JayPay <span>Admin</span>
        </Link>
        <nav aria-label="Admin menu">
          {menu.map((item) => (
            <NavLink key={item.to} to={item.to}>
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="staff">
          <span className="staff-name">{session.staff.username}</span>
          <span className="staff-role">{roles[session.staff.role].label}</span>
          <button type="button" className="link-button" onClick={signOut}>
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
  const state = useSession()
  const location = useLocation()
  const returnTo = location.pathname + location.search
  switch (state.status) {
    case 'signed-in':
      return <Console session={state.session} />
    case 'no-role':
      return <NoRole tokens={state.tokens} username={state.username} />
    case 'signed-out':
      return state.notice ? <SignInAgain notice={state.notice} returnTo={returnTo} /> : <SignInRedirect returnTo={returnTo} />
  }
}

function NotFound() {
  return (
    <>
      <h1>Page not found</h1>
      <Link to="/">Back to the console</Link>
    </>
  )
}

export function App() {
  return (
    <Routes>
      <Route path="/callback" element={<Callback />} />
      <Route path="/signed-out" element={<SignedOut />} />
      <Route element={<SignedIn />}>
        <Route index element={<Navigate to="/members" replace />} />
        <Route path="members" element={<Members />} />
        <Route path="members/:membershipId" element={<MemberDetail />} />
        <Route path="money" element={<RegionalMoney />} />
        <Route path="settlement" element={<Settlement />} />
        <Route path="account" element={<MyAccount />} />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  )
}
