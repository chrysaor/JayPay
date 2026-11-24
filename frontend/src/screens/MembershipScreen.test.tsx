import { describe, expect, test } from 'vitest'
import { fakeServer, renderApp, unreachableServer } from '../test/harness'
import { section, shownValue } from '../test/queries'

const member = {
  membershipId: '1',
  name: '홍길동',
  email: 'hong@example.com',
  address: '서울',
  valid: true,
  corp: true,
  refreshToken: null,
}

describe('회원 화면', () => {
  test('가입 정보를 보내고 새 회원 ID를 보여 준다', async () => {
    const requests = fakeServer({ body: member })
    const user = renderApp('/membership')

    const form = section('회원 가입')
    await user.type(form.getByLabelText('이름'), '홍길동')
    await user.type(form.getByLabelText('이메일'), 'hong@example.com')
    await user.type(form.getByLabelText('주소'), '서울')
    await user.click(form.getByLabelText('법인 회원'))
    await user.click(form.getByRole('button', { name: '가입하기' }))

    expect(await shownValue('회원 가입', 'membershipId')).toBe('1')
    expect(requests).toEqual([
      {
        url: '/api/membership/memberships',
        method: 'POST',
        contentType: 'application/json',
        // Lombok turns `boolean isCorp` into the JSON property `corp`.
        body: JSON.stringify({ name: '홍길동', email: 'hong@example.com', address: '서울', corp: true }),
      },
    ])
  })

  test('회원 ID로 조회해 회원 정보를 보여 준다', async () => {
    const requests = fakeServer({ body: member })
    const user = renderApp('/membership')

    const form = section('회원 조회')
    await user.type(form.getByLabelText('회원 ID'), '1')
    await user.click(form.getByRole('button', { name: '조회하기' }))

    expect(await shownValue('회원 조회', 'name')).toBe('홍길동')
    expect(await shownValue('회원 조회', 'corp')).toBe('true')
    expect(requests.map((r) => `${r.method} ${r.url}`)).toEqual(['GET /api/membership/memberships/1'])
  })

  test('서버 오류는 상태 코드와 함께 보여 준다', async () => {
    fakeServer({ status: 502, body: { message: 'Bad Gateway' } })
    const user = renderApp('/membership')

    const form = section('회원 조회')
    await user.type(form.getByLabelText('회원 ID'), '1')
    await user.click(form.getByRole('button', { name: '조회하기' }))

    expect(await form.findByRole('alert')).toHaveTextContent('HTTP 502')
  })

  test('서버에 닿지 못하면 연결 실패를 알린다', async () => {
    unreachableServer()
    const user = renderApp('/membership')

    const form = section('회원 조회')
    await user.type(form.getByLabelText('회원 ID'), '1')
    await user.click(form.getByRole('button', { name: '조회하기' }))

    expect(await form.findByRole('alert')).toHaveTextContent('서버에 연결하지 못했습니다')
  })

  test('빈 응답은 요청이 처리되지 않은 것으로 알린다', async () => {
    fakeServer({ body: undefined })
    const user = renderApp('/membership')

    const form = section('회원 조회')
    await user.type(form.getByLabelText('회원 ID'), '1')
    await user.click(form.getByRole('button', { name: '조회하기' }))

    expect(await form.findByRole('alert')).toHaveTextContent('빈 응답')
  })

  test('예상과 다른 응답 형식은 화면에 그리지 않고 알린다', async () => {
    fakeServer({ body: { membershipId: 1 } })
    const user = renderApp('/membership')

    const form = section('회원 조회')
    await user.type(form.getByLabelText('회원 ID'), '1')
    await user.click(form.getByRole('button', { name: '조회하기' }))

    expect(await form.findByRole('alert')).toHaveTextContent('응답 형식이 예상과 다릅니다')
    expect(form.queryByRole('status')).not.toBeInTheDocument()
  })
})
