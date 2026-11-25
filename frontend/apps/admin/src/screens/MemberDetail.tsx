import { findMembership, type Membership } from '@jaypay/api/membership'
import { Card } from '@jaypay/ui/Card'
import { ErrorNotice } from '@jaypay/ui/ErrorNotice'
import { readForm } from '@jaypay/ui/form'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { modifyMembership } from '../api/membership'

function ProfileForm({ member }: { member: Membership }) {
  const queryClient = useQueryClient()
  const save = useMutation({
    mutationFn: modifyMembership,
    onSuccess: (saved) => {
      queryClient.setQueryData(['membership', saved.membershipId], saved)
    },
  })
  return (
    <Card title="Profile">
      <form
        className="stack"
        onSubmit={(event) => {
          const form = readForm(event)
          save.mutate({
            membershipId: member.membershipId,
            name: form.text('name'),
            email: form.text('email'),
            address: form.text('address'),
            valid: form.checked('valid'),
            corp: form.checked('corp'),
          })
        }}
      >
        <label>
          Name
          <input name="name" defaultValue={member.name ?? ''} required />
        </label>
        <label>
          Email
          <input name="email" type="email" defaultValue={member.email ?? ''} required />
        </label>
        <label>
          Address
          <input name="address" defaultValue={member.address ?? ''} required />
        </label>
        <label className="check">
          <input name="valid" type="checkbox" defaultChecked={member.valid} />
          Active
        </label>
        <label className="check">
          <input name="corp" type="checkbox" defaultChecked={member.corp} />
          Corporate account
        </label>
        <button className="primary" disabled={save.isPending}>
          Save changes
        </button>
      </form>
      {save.isSuccess ? (
        <p className="notice notice-success" role="status">
          Saved.
        </p>
      ) : null}
      <ErrorNotice error={save.error} />
    </Card>
  )
}

export function MemberDetail() {
  const { membershipId = '' } = useParams()
  const member = useQuery({ queryKey: ['membership', membershipId], queryFn: () => findMembership(membershipId) })
  return (
    <>
      <Link to="/members" className="back">
        Back to members
      </Link>
      <h1>Member #{membershipId}</h1>
      {member.isPending ? <p className="muted">Loading...</p> : null}
      <ErrorNotice error={member.error} />
      {member.data ? <ProfileForm key={membershipId} member={member.data} /> : null}
    </>
  )
}
