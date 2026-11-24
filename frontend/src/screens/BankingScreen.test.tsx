import { describe, expect, test } from 'vitest'
import { fakeServer, renderApp } from '../test/harness'
import { section, shownValue } from '../test/queries'

const account = {
  registeredBankAccountId: '7',
  membershipId: '1',
  bankName: '국민은행',
  bankAccountNumber: '123-456',
  linkedStatusIsValid: true,
  aggregateIdentifier: '',
}

describe('계좌 화면', () => {
  test('회원의 계좌를 등록하고 등록 ID를 보여 준다', async () => {
    const requests = fakeServer({ body: account })
    const user = renderApp('/banking')

    const form = section('계좌 등록')
    await user.type(form.getByLabelText('회원 ID'), '1')
    await user.type(form.getByLabelText('은행 이름'), '국민은행')
    await user.type(form.getByLabelText('계좌 번호'), '123-456')
    await user.click(form.getByRole('button', { name: '등록하기' }))

    expect(await shownValue('계좌 등록', 'registeredBankAccountId')).toBe('7')
    expect(requests).toEqual([
      {
        url: '/api/banking/banking/account/register',
        method: 'POST',
        contentType: 'application/json',
        // Lombok turns `boolean isValid` into the JSON property `valid`.
        body: JSON.stringify({ membershipId: '1', bankName: '국민은행', bankAccountNumber: '123-456', valid: true }),
      },
    ])
  })

  test('회원 ID로 등록된 계좌를 조회한다', async () => {
    const requests = fakeServer({ body: account })
    const user = renderApp('/banking')

    const form = section('등록 계좌 조회')
    await user.type(form.getByLabelText('회원 ID'), '1')
    await user.click(form.getByRole('button', { name: '조회하기' }))

    expect(await shownValue('등록 계좌 조회', 'bankAccountNumber')).toBe('123-456')
    expect(requests.map((r) => `${r.method} ${r.url}`)).toEqual(['GET /api/banking/banking/account/1'])
  })

  test('등록이 거절되면 오류를 보여 준다', async () => {
    fakeServer({ status: 500, body: { error: 'Internal Server Error' } })
    const user = renderApp('/banking')

    const form = section('계좌 등록')
    await user.type(form.getByLabelText('회원 ID'), '1')
    await user.type(form.getByLabelText('은행 이름'), '국민은행')
    await user.type(form.getByLabelText('계좌 번호'), '123-456')
    await user.click(form.getByRole('button', { name: '등록하기' }))

    expect(await form.findByRole('alert')).toHaveTextContent('HTTP 500')
  })
})
