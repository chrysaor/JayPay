import type { ReactElement } from 'react'
import { Link, NavLink, Route, Routes } from 'react-router'
import { BankingScreen } from './screens/BankingScreen'
import { MembershipScreen } from './screens/MembershipScreen'
import { MoneyScreen } from './screens/MoneyScreen'

type Screen = { path: string; label: string; element: ReactElement }

const screens: Screen[] = [
  { path: '/membership', label: '회원', element: <MembershipScreen /> },
  { path: '/banking', label: '계좌', element: <BankingScreen /> },
  { path: '/money', label: '머니', element: <MoneyScreen /> },
]

function Home() {
  return (
    <section>
      <h1>JayPay</h1>
      <p>스터디용 결제 앱 예제입니다. 메뉴에서 화면을 골라 백엔드 API를 직접 호출해 보세요.</p>
      <ul>
        {screens.map((screen) => (
          <li key={screen.path}>
            <Link to={screen.path}>{screen.label}</Link>
          </li>
        ))}
      </ul>
    </section>
  )
}

export function App() {
  return (
    <div className="layout">
      <nav aria-label="주 메뉴">
        <NavLink to="/" end>
          홈
        </NavLink>
        {screens.map((screen) => (
          <NavLink key={screen.path} to={screen.path}>
            {screen.label}
          </NavLink>
        ))}
      </nav>
      <main>
        <Routes>
          <Route path="/" element={<Home />} />
          {screens.map((screen) => (
            <Route key={screen.path} path={screen.path} element={screen.element} />
          ))}
          <Route path="*" element={<p>없는 화면입니다.</p>} />
        </Routes>
      </main>
    </div>
  )
}
