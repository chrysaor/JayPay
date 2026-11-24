import { useMutation } from '@tanstack/react-query'
import { findMemberMoney, increaseMoney } from '../api/money'
import { Outcome } from '../components/Outcome'
import { Panel } from '../components/Panel'
import { readForm } from '../components/form'

function IncreaseMoney() {
  const increase = useMutation({ mutationFn: increaseMoney })
  return (
    <Panel title="머니 충전" endpoint="POST /money/increase">
      <form
        onSubmit={(event) => {
          const form = readForm(event)
          increase.mutate({ targetMembershipId: form.text('targetMembershipId'), amount: form.number('amount') })
        }}
      >
        <label>
          회원 ID
          <input name="targetMembershipId" required />
        </label>
        <label>
          충전 금액
          <input name="amount" type="number" min="1" step="1" required />
        </label>
        <button disabled={increase.isPending}>충전하기</button>
      </form>
      <Outcome state={increase} />
    </Panel>
  )
}

function FindMemberMoney() {
  const find = useMutation({ mutationFn: findMemberMoney })
  return (
    <Panel title="잔액 조회" endpoint="POST /money/member-money">
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

export function MoneyScreen() {
  return (
    <>
      <h1>머니</h1>
      <IncreaseMoney />
      <FindMemberMoney />
    </>
  )
}
