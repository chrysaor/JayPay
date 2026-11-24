import { findMembership } from '@jaypay/api/membership'
import { ErrorNotice } from '@jaypay/ui/ErrorNotice'
import { readForm } from '@jaypay/ui/form'
import { useQuery } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router'
import { findMembershipsByAddress } from '../api/membership'

type SearchBy = 'id' | 'address'

async function searchMembers(by: SearchBy, q: string) {
  return by === 'address' ? findMembershipsByAddress(q) : [await findMembership(q)]
}

export function Members() {
  const [params, setParams] = useSearchParams()
  const by: SearchBy = params.get('by') === 'address' ? 'address' : 'id'
  const q = params.get('q') ?? ''
  const members = useQuery({ queryKey: ['members', by, q], queryFn: () => searchMembers(by, q), enabled: q !== '' })
  return (
    <>
      <h1>Members</h1>
      <form
        className="toolbar"
        onSubmit={(event) => {
          const form = readForm(event)
          setParams({ by: form.text('by'), q: form.text('q') })
        }}
      >
        <label>
          Search by
          <select name="by" defaultValue={by}>
            <option value="id">Membership ID</option>
            <option value="address">Address</option>
          </select>
        </label>
        <label className="grow">
          Membership ID or address
          <input name="q" defaultValue={q} required />
        </label>
        <button className="primary" disabled={members.isFetching}>
          Search
        </button>
      </form>
      <ErrorNotice error={members.error} />
      {members.data?.length === 0 ? <p className="empty">No members found.</p> : null}
      {members.data?.length ? (
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>Name</th>
              <th>Email</th>
              <th>Address</th>
              <th>Type</th>
              <th>Status</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {members.data.map((member) => (
              <tr key={member.membershipId}>
                <td>{member.membershipId}</td>
                <td>{member.name}</td>
                <td>{member.email}</td>
                <td>{member.address}</td>
                <td>{member.corp ? 'Corporate' : 'Personal'}</td>
                <td>
                  <span className={member.valid ? 'badge badge-ok' : 'badge badge-off'}>
                    {member.valid ? 'Active' : 'Inactive'}
                  </span>
                </td>
                <td>
                  <Link to={`/members/${member.membershipId}`}>Edit</Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : null}
    </>
  )
}
