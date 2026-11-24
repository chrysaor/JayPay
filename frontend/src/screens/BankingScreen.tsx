import { useMutation } from '@tanstack/react-query'
import { findRegisteredBankAccount, registerBankAccount } from '../api/banking'
import { Outcome } from '../components/Outcome'
import { Panel } from '../components/Panel'
import { readForm } from '../components/form'

function RegisterBankAccount() {
  const register = useMutation({ mutationFn: registerBankAccount })
  return (
    <Panel title="계좌 등록" endpoint="POST /banking/account/register">
      <form
        onSubmit={(event) => {
          const form = readForm(event)
          register.mutate({
            membershipId: form.text('membershipId'),
            bankName: form.text('bankName'),
            bankAccountNumber: form.text('bankAccountNumber'),
            valid: form.checked('valid'),
          })
        }}
      >
        <label>
          회원 ID
          <input name="membershipId" required />
        </label>
        <label>
          은행 이름
          <input name="bankName" required />
        </label>
        <label>
          계좌 번호
          <input name="bankAccountNumber" required />
        </label>
        <label className="inline">
          <input name="valid" type="checkbox" defaultChecked />
          연동 상태를 유효로 등록
        </label>
        <button disabled={register.isPending}>등록하기</button>
      </form>
      <Outcome state={register} />
    </Panel>
  )
}

function FindRegisteredBankAccount() {
  const find = useMutation({ mutationFn: findRegisteredBankAccount })
  return (
    <Panel title="등록 계좌 조회" endpoint="GET /banking/account/{membershipId}">
      <form
        onSubmit={(event) => {
          find.mutate(readForm(event).text('membershipId'))
        }}
      >
        <label>
          회원 ID
          <input name="membershipId" required />
        </label>
        <button disabled={find.isPending}>조회하기</button>
      </form>
      <Outcome state={find} />
    </Panel>
  )
}

export function BankingScreen() {
  return (
    <>
      <h1>계좌</h1>
      <RegisterBankAccount />
      <FindRegisteredBankAccount />
    </>
  )
}
