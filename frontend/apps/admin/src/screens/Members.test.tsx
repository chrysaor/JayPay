import { fakeRoutes } from '@jaypay/api/testing'
import { screen, within } from '@testing-library/react'
import { describe, expect, test } from 'vitest'
import { member, renderApp } from '../test/render'

const corporateMember = { ...member, membershipId: '2', name: 'Jaypay Corp', corp: true, valid: false }

function rows() {
  return screen.getAllByRole('row').slice(1)
}

describe('member search', () => {
  test('finds a member by membership ID', async () => {
    const requests = fakeRoutes({ 'GET /api/membership/memberships/1': { body: member } })
    const user = renderApp('/members')

    await user.type(await screen.findByLabelText('Membership ID or address'), '1')
    await user.click(screen.getByRole('button', { name: 'Search' }))

    const row = within(await screen.findByRole('row', { name: /Hong Gildong/ }))
    expect(row.getByText('hong@example.com')).toBeInTheDocument()
    expect(row.getByText('Personal')).toBeInTheDocument()
    expect(row.getByText('Active')).toBeInTheDocument()
    expect(row.getByRole('link', { name: 'Edit' })).toHaveAttribute('href', '/members/1')
    expect(requests.map((r) => `${r.method} ${r.url}`)).toEqual(['GET /api/membership/memberships/1'])
  })

  test('lists every member at an address', async () => {
    const requests = fakeRoutes({
      'GET /api/membership/memberships/address/Seoul': { body: [member, corporateMember] },
    })
    const user = renderApp('/members')

    await user.selectOptions(await screen.findByLabelText('Search by'), 'Address')
    await user.type(screen.getByLabelText('Membership ID or address'), 'Seoul')
    await user.click(screen.getByRole('button', { name: 'Search' }))

    await screen.findByRole('row', { name: /Jaypay Corp/ })
    expect(rows()).toHaveLength(2)
    const corporate = within(screen.getByRole('row', { name: /Jaypay Corp/ }))
    expect(corporate.getByText('Corporate')).toBeInTheDocument()
    expect(corporate.getByText('Inactive')).toBeInTheDocument()
    expect(requests.map((r) => `${r.method} ${r.url}`)).toEqual(['GET /api/membership/memberships/address/Seoul'])
  })

  test('a search link opens with its results', async () => {
    fakeRoutes({ 'GET /api/membership/memberships/address/Busan': { body: [corporateMember] } })
    renderApp('/members?by=address&q=Busan')

    expect(await screen.findByRole('row', { name: /Jaypay Corp/ })).toBeInTheDocument()
    expect(screen.getByLabelText('Membership ID or address')).toHaveValue('Busan')
  })

  test('an address with no members says so', async () => {
    fakeRoutes({ 'GET /api/membership/memberships/address/Jeju': { body: [] } })
    renderApp('/members?by=address&q=Jeju')

    expect(await screen.findByText('No members found.')).toBeInTheDocument()
  })

  test('a failed search shows the server error', async () => {
    fakeRoutes({ 'GET /api/membership/memberships/9': { status: 500 } })
    renderApp('/members?by=id&q=9')

    expect(await screen.findByRole('alert')).toHaveTextContent('HTTP 500')
  })
})
