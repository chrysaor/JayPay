import { screen } from '@testing-library/react'
import { expect, test } from 'vitest'
import { renderApp } from './test/harness'

test('홈 화면이 앱 이름과 메뉴를 보여 준다', () => {
  renderApp('/')

  expect(screen.getByRole('heading', { name: 'JayPay' })).toBeInTheDocument()
  expect(screen.getByRole('navigation', { name: '주 메뉴' })).toBeInTheDocument()
})

test('없는 경로는 안내 문구를 보여 준다', () => {
  renderApp('/nowhere')

  expect(screen.getByText('없는 화면입니다.')).toBeInTheDocument()
})
