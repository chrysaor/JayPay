import { describe, expect, test } from 'vitest'
import { fakeServer, renderApp } from '../test/harness'
import { section, shownValue } from '../test/queries'

describe('머니 화면', () => {
  test('충전 금액을 보내고 충전 결과를 보여 준다', async () => {
    const requests = fakeServer({
      body: { moneyChangingRequestId: '42', moneyChangingType: 1, moneyChangingResultStatus: 1, amount: 10000 },
    })
    const user = renderApp('/money')

    const form = section('머니 충전')
    await user.type(form.getByLabelText('회원 ID'), '1')
    await user.type(form.getByLabelText('충전 금액'), '10000')
    await user.click(form.getByRole('button', { name: '충전하기' }))

    expect(await shownValue('머니 충전', 'moneyChangingRequestId')).toBe('42')
    expect(requests).toEqual([
      {
        url: '/api/money/money/increase',
        method: 'POST',
        contentType: 'application/json',
        body: JSON.stringify({ targetMembershipId: '1', amount: 10000 }),
      },
    ])
  })

  test('회원의 머니 잔액을 조회한다', async () => {
    const requests = fakeServer({ body: [{ memberMoneyId: '3', membershipId: '1', balance: 25000 }] })
    const user = renderApp('/money')

    const form = section('잔액 조회')
    await user.type(form.getByLabelText('회원 ID'), '1')
    await user.click(form.getByRole('button', { name: '조회하기' }))

    expect(await shownValue('잔액 조회', 'balance')).toBe('25000')
    expect(requests).toEqual([
      {
        url: '/api/money/money/member-money',
        method: 'POST',
        contentType: 'application/json',
        body: JSON.stringify({ targetMembershipIds: ['1'] }),
      },
    ])
  })

  test('잔액이 없는 회원이면 결과가 없다고 알린다', async () => {
    fakeServer({ body: [] })
    const user = renderApp('/money')

    const form = section('잔액 조회')
    await user.type(form.getByLabelText('회원 ID'), '9')
    await user.click(form.getByRole('button', { name: '조회하기' }))

    expect(await form.findByRole('status')).toHaveTextContent('결과가 없습니다.')
  })

  test('충전이 실패하면 오류를 보여 준다', async () => {
    fakeServer({ status: 500 })
    const user = renderApp('/money')

    const form = section('머니 충전')
    await user.type(form.getByLabelText('회원 ID'), '1')
    await user.type(form.getByLabelText('충전 금액'), '10000')
    await user.click(form.getByRole('button', { name: '충전하기' }))

    expect(await form.findByRole('alert')).toHaveTextContent('HTTP 500')
  })
})
