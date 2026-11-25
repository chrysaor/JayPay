import { useStaff } from './session'

export type Role = 'ADMIN' | 'OPERATOR' | 'VIEWER'

type Access = {
  label: string
  editMembers: boolean
}

export const roles: Record<Role, Access> = {
  ADMIN: { label: 'Admin', editMembers: true },
  OPERATOR: { label: 'Operator', editMembers: true },
  VIEWER: { label: 'Viewer', editMembers: false },
}

export function useAccess() {
  return roles[useStaff().staff.role]
}
