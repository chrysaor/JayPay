const won = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'KRW' })

export function formatWon(amount: number) {
  return won.format(amount)
}
