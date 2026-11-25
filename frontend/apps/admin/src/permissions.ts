import type { Role } from './api/staff'
import { useStaff } from './session'

type Access = {
  label: string
  editMembers: boolean
  settlePayments: boolean
  manageStaff: boolean
}

export type Permission = keyof Omit<Access, 'label'>

export const roles: Record<Role, Access> = {
  ADMIN: { label: 'Admin', editMembers: true, settlePayments: true, manageStaff: true },
  OPERATOR: { label: 'Operator', editMembers: true, settlePayments: true, manageStaff: false },
  VIEWER: { label: 'Viewer', editMembers: false, settlePayments: false, manageStaff: false },
}

export function useAccess() {
  return roles[useStaff().account.role]
}
