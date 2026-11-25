import { useStaff } from './session'

export type Role = 'ADMIN' | 'OPERATOR' | 'VIEWER'

type Access = {
  label: string
  editMembers: boolean
  settlePayments: boolean
}

export const roles: Record<Role, Access> = {
  ADMIN: { label: 'Admin', editMembers: true, settlePayments: true },
  OPERATOR: { label: 'Operator', editMembers: true, settlePayments: true },
  VIEWER: { label: 'Viewer', editMembers: false, settlePayments: false },
}

export function useAccess() {
  return roles[useStaff().staff.role]
}
