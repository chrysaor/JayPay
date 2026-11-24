import { fakeRoutes } from '@jaypay/api/testing'
import { screen } from '@testing-library/react'
import { expect, test } from 'vitest'
import { renderApp } from './test/render'

test('the console opens on the member search', async () => {
  fakeRoutes({})
  renderApp('/')

  expect(await screen.findByRole('heading', { name: 'Members' })).toBeInTheDocument()
})

test('the admin menu links every screen', async () => {
  fakeRoutes({})
  renderApp('/members')

  const menu = await screen.findByRole('navigation', { name: 'Admin menu' })
  expect([...menu.querySelectorAll('a')].map((link) => link.textContent)).toEqual([
    'Members',
    'Regional money',
    'Settlement',
  ])
})

test('an unknown address shows a not-found page', async () => {
  fakeRoutes({})
  renderApp('/nowhere')

  expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument()
})
