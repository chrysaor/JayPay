import { Link, Navigate, NavLink, Outlet, Route, Routes } from 'react-router'
import { MemberDetail } from './screens/MemberDetail'
import { Members } from './screens/Members'
import { RegionalMoney } from './screens/RegionalMoney'
import { Settlement } from './screens/Settlement'

const menu = [
  { to: '/members', label: 'Members' },
  { to: '/money', label: 'Regional money' },
  { to: '/settlement', label: 'Settlement' },
]

function Layout() {
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
      </aside>
      <main className="content">
        <Outlet />
      </main>
    </div>
  )
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
      <Route element={<Layout />}>
        <Route index element={<Navigate to="/members" replace />} />
        <Route path="members" element={<Members />} />
        <Route path="members/:membershipId" element={<MemberDetail />} />
        <Route path="money" element={<RegionalMoney />} />
        <Route path="settlement" element={<Settlement />} />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  )
}
