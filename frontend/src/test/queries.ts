import { screen, within } from '@testing-library/react'

export function section(name: string) {
  return within(screen.getByRole('region', { name }))
}

export async function shownValue(sectionName: string, field: string) {
  const result = await section(sectionName).findByRole('status')
  return within(result).getByText(field, { selector: 'dt' }).nextElementSibling?.textContent
}
