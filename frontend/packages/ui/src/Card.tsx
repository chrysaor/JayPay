import { useId, type ReactNode } from 'react'

export function Card({ title, children }: { title: string; children: ReactNode }) {
  const headingId = useId()
  return (
    <section className="card" aria-labelledby={headingId}>
      <h2 id={headingId}>{title}</h2>
      {children}
    </section>
  )
}
