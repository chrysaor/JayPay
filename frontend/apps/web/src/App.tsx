import { Link, Route, Routes } from 'react-router'
import { MemberLayout } from './MemberLayout'
import { BankAccount } from './screens/BankAccount'
import { Pay } from './screens/Pay'
import { Send } from './screens/Send'
import { SignIn } from './screens/SignIn'
import { SignUp } from './screens/SignUp'
import { Wallet } from './screens/Wallet'

function NotFound() {
  return (
    <main className="centered">
      <h1>Page not found</h1>
      <Link to="/">Back to JayPay</Link>
    </main>
  )
}

export function App() {
  return (
    <Routes>
      <Route path="/sign-in" element={<SignIn />} />
      <Route path="/sign-up" element={<SignUp />} />
      <Route element={<MemberLayout />}>
        <Route index element={<Wallet />} />
        <Route path="account" element={<BankAccount />} />
        <Route path="send" element={<Send />} />
        <Route path="pay" element={<Pay />} />
      </Route>
      <Route path="*" element={<NotFound />} />
    </Routes>
  )
}
