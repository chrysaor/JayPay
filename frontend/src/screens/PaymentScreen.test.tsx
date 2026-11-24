import { waitFor } from '@testing-library/react'
import { describe, expect, test } from 'vitest'
import { fakeServer, renderApp } from '../test/harness'
import { section, shownValue } from '../test/queries'

const payment = {
  paymentId: 10,
  requestMembershipId: '1',
  requestPrice: 12000,
  franchiseId: 'f-1',
  franchiseFeeRate: '3.5',
  paymentStatus: 0,
  approvedAt: '2026-11-24T10:00:00.000+00:00',
}

describe('결제 화면', () => {
  test('결제 정보를 폼 필드로 보내고 결제 결과를 보여 준다', async () => {
    const requests = fakeServer((request) =>
      request.url.endsWith('/normal-status') ? { body: [] } : { body: payment },
    )
    const user = renderApp('/payment')

    const form = section('결제 요청')
    await user.type(form.getByLabelText('회원 ID'), '1')
    await user.type(form.getByLabelText('결제 금액'), '12000')
    await user.type(form.getByLabelText('가맹점 ID'), 'f-1')
    await user.type(form.getByLabelText('가맹점 수수료율'), '3.5')
    await user.click(form.getByRole('button', { name: '결제하기' }))

    expect(await shownValue('결제 요청', 'paymentId')).toBe('10')
    expect(requests.filter((r) => r.method === 'POST')).toEqual([
      {
        url: '/api/payment/payment/request',
        method: 'POST',
        contentType: 'application/x-www-form-urlencoded',
        body: 'requestMembershipId=1&requestPrice=12000&franchiseId=f-1&franchiseFeeRate=3.5',
      },
    ])
  })

  test('화면에 들어오면 정산 대기 결제 목록을 보여 준다', async () => {
    const requests = fakeServer({ body: [payment] })
    renderApp('/payment')

    expect(await shownValue('정산 대기 결제', 'requestPrice')).toBe('12000')
    expect(requests.map((r) => `${r.method} ${r.url}`)).toEqual(['GET /api/payment/payment/normal-status'])
  })

  test('결제가 끝나면 정산 대기 목록을 다시 불러온다', async () => {
    const requests = fakeServer((request) =>
      request.url.endsWith('/normal-status') ? { body: [payment] } : { body: payment },
    )
    const user = renderApp('/payment')
    await shownValue('정산 대기 결제', 'paymentId')

    const form = section('결제 요청')
    await user.type(form.getByLabelText('회원 ID'), '1')
    await user.type(form.getByLabelText('결제 금액'), '12000')
    await user.type(form.getByLabelText('가맹점 ID'), 'f-1')
    await user.type(form.getByLabelText('가맹점 수수료율'), '3.5')
    await user.click(form.getByRole('button', { name: '결제하기' }))

    await waitFor(() => {
      expect(requests.filter((r) => r.url.endsWith('/normal-status'))).toHaveLength(2)
    })
  })

  test('목록을 불러오지 못하면 오류를 보여 준다', async () => {
    fakeServer({ status: 502 })
    renderApp('/payment')

    expect(await section('정산 대기 결제').findByRole('alert')).toHaveTextContent('HTTP 502')
  })
})
