import type { SubmitEvent } from 'react'

export function readForm(event: SubmitEvent<HTMLFormElement>) {
  event.preventDefault()
  const data = new FormData(event.currentTarget)
  const text = (name: string) => {
    const value = data.get(name)
    return typeof value === 'string' ? value.trim() : ''
  }
  return {
    text,
    number: (name: string) => Number(text(name)),
    checked: (name: string) => data.get(name) === 'on',
  }
}
