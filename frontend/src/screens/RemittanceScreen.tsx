import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { requestRemittance, toBankAccount, toMembership } from '../api/remittance'
import { Outcome } from '../components/Outcome'
import { Panel } from '../components/Panel'
import { readForm } from '../components/form'

export function RemittanceScreen() {
  const [target, setTarget] = useState<typeof toMembership | typeof toBankAccount>(toMembership)
  const remit = useMutation({ mutationFn: requestRemittance })
  return (
    <>
      <h1>송금</h1>
      <Panel title="송금 요청" endpoint="POST /remittance/request">
        <form
          onSubmit={(event) => {
            const form = readForm(event)
            const common = { fromMembershipId: form.text('fromMembershipId'), amount: form.number('amount') }
            remit.mutate(
              target === toMembership
                ? { ...common, remittanceType: toMembership, toMembershipId: form.text('toMembershipId') }
                : {
                    ...common,
                    remittanceType: toBankAccount,
                    toBankName: form.text('toBankName'),
                    toBankAccountNumber: form.text('toBankAccountNumber'),
                  },
            )
          }}
        >
          <label>
            송금 방식
            <select
              value={target}
              onChange={(event) => {
                setTarget(Number(event.target.value) === toBankAccount ? toBankAccount : toMembership)
              }}
            >
              <option value={toMembership}>회원에게</option>
              <option value={toBankAccount}>은행 계좌로</option>
            </select>
          </label>
          <label>
            보내는 회원 ID
            <input name="fromMembershipId" required />
          </label>
          {target === toMembership ? (
            <label>
              받는 회원 ID
              <input name="toMembershipId" required />
            </label>
          ) : (
            <>
              <label>
                받는 은행
                <input name="toBankName" required />
              </label>
              <label>
                받는 계좌 번호
                <input name="toBankAccountNumber" required />
              </label>
            </>
          )}
          <label>
            금액
            <input name="amount" type="number" min="1" step="1" required />
          </label>
          <button disabled={remit.isPending}>송금하기</button>
        </form>
        <Outcome state={remit} />
      </Panel>
    </>
  )
}
