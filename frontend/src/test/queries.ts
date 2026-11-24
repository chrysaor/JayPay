import { screen, within } from '@testing-library/react'

/** The section titled `name`, so a test can work inside one form of a screen. */
export function section(name: string) {
  return within(screen.getByRole('region', { name }))
}

/** The value shown next to `field` in the response a section displays. */
export async function shownValue(sectionName: string, field: string) {
  const result = await section(sectionName).findByRole('status')
  const term = within(result).getByText(field, { selector: 'dt' })
  return term.nextElementSibling?.textContent
}
