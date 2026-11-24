import { describe, expect, test } from 'vitest'
import { fakeServer, renderApp } from '../test/harness'
import { section, shownValue } from '../test/queries'

const remittance = {
  remittanceRequestId: '5',
  remittanceFromMembershipId: '1',
  toBankName: null,
  toBankAccountNumber: null,
  remittanceType: 0,
  amount: 5000,
  remittanceStatus: 'success',
}

function sentBody(requests: { body: string | null }[]): unknown {
  return JSON.parse(requests[0]?.body ?? 'null')
}

describe('송금 화면', () => {
  test('다른 회원에게 송금하고 송금 상태를 보여 준다', async () => {
    const requests = fakeServer({ body: remittance })
    const user = renderApp('/remittance')

    const form = section('송금 요청')
    await user.type(form.getByLabelText('보내는 회원 ID'), '1')
    await user.type(form.getByLabelText('받는 회원 ID'), '2')
    await user.type(form.getByLabelText('금액'), '5000')
    await user.click(form.getByRole('button', { name: '송금하기' }))

    expect(await shownValue('송금 요청', 'remittanceStatus')).toBe('success')
    expect(requests.map((r) => `${r.method} ${r.url} ${String(r.contentType)}`)).toEqual([
      'POST /api/remittance/remittance/request application/json',
    ])
    expect(sentBody(requests)).toEqual({ fromMembershipId: '1', amount: 5000, remittanceType: 0, toMembershipId: '2' })
  })

  test('은행 계좌로 송금할 때는 계좌 정보를 보낸다', async () => {
    const requests = fakeServer({ body: { ...remittance, remittanceType: 1, toBankName: '신한은행' } })
    const user = renderApp('/remittance')

    const form = section('송금 요청')
    await user.selectOptions(form.getByLabelText('송금 방식'), '은행 계좌로')
    expect(form.queryByLabelText('받는 회원 ID')).not.toBeInTheDocument()
    await user.type(form.getByLabelText('보내는 회원 ID'), '1')
    await user.type(form.getByLabelText('받는 은행'), '신한은행')
    await user.type(form.getByLabelText('받는 계좌 번호'), '987-654')
    await user.type(form.getByLabelText('금액'), '5000')
    await user.click(form.getByRole('button', { name: '송금하기' }))

    expect(await shownValue('송금 요청', 'toBankName')).toBe('신한은행')
    expect(sentBody(requests)).toEqual({
      fromMembershipId: '1',
      amount: 5000,
      remittanceType: 1,
      toBankName: '신한은행',
      toBankAccountNumber: '987-654',
    })
  })

  test('백엔드가 송금을 거절해 빈 응답을 주면 알린다', async () => {
    fakeServer({ body: undefined })
    const user = renderApp('/remittance')

    const form = section('송금 요청')
    await user.type(form.getByLabelText('보내는 회원 ID'), '1')
    await user.type(form.getByLabelText('받는 회원 ID'), '2')
    await user.type(form.getByLabelText('금액'), '5000')
    await user.click(form.getByRole('button', { name: '송금하기' }))

    expect(await form.findByRole('alert')).toHaveTextContent('빈 응답')
  })
})
