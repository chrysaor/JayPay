import { Card } from '@jaypay/ui/Card'
import { accountConsole } from '../keycloak'
import { roles } from '../permissions'
import { useStaff } from '../session'

export function MyAccount() {
  const { staff } = useStaff()
  return (
    <>
      <h1>My account</h1>
      <Card title="Account">
        <dl className="facts">
          <dt>Username</dt>
          <dd>{staff.username}</dd>
          <dt>Role</dt>
          <dd>{roles[staff.role].label}</dd>
        </dl>
      </Card>
      <Card title="Password and two-factor sign-in">
        <p>Keycloak keeps your password and your authenticator app. Change them in your Keycloak account.</p>
        <a href={accountConsole}>Open my Keycloak account</a>
      </Card>
    </>
  )
}
