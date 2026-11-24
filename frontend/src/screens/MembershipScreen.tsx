import { useMutation } from '@tanstack/react-query'
import { findMembership, registerMembership } from '../api/membership'
import { Outcome } from '../components/Outcome'
import { Panel } from '../components/Panel'
import { readForm } from '../components/form'

function RegisterMembership() {
  const register = useMutation({ mutationFn: registerMembership })
  return (
    <Panel title="회원 가입" endpoint="POST /memberships">
      <form
        onSubmit={(event) => {
          const form = readForm(event)
          register.mutate({
            name: form.text('name'),
            email: form.text('email'),
            address: form.text('address'),
            corp: form.checked('corp'),
          })
        }}
      >
        <label>
          이름
          <input name="name" required />
        </label>
        <label>
          이메일
          <input name="email" type="email" required />
        </label>
        <label>
          주소
          <input name="address" required />
        </label>
        <label className="inline">
          <input name="corp" type="checkbox" />
          법인 회원
        </label>
        <button disabled={register.isPending}>가입하기</button>
      </form>
      <Outcome state={register} />
    </Panel>
  )
}

function FindMembership() {
  const find = useMutation({ mutationFn: findMembership })
  return (
    <Panel title="회원 조회" endpoint="GET /memberships/{membershipId}">
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

export function MembershipScreen() {
  return (
    <>
      <h1>회원</h1>
      <RegisterMembership />
      <FindMembership />
    </>
  )
}
