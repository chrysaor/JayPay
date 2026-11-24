const won = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'KRW' })
const dateTime = new Intl.DateTimeFormat('en-US', { dateStyle: 'medium', timeStyle: 'short' })

export function formatWon(amount: number) {
  return won.format(amount)
}

export function formatDateTime(value: string | number | null | undefined) {
  if (value === null || value === undefined) return '-'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? String(value) : dateTime.format(date)
}
