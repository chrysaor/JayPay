import { useId, type ReactNode } from 'react'

export function Panel({ title, endpoint, children }: { title: string; endpoint: string; children: ReactNode }) {
  const headingId = useId()
  return (
    <section aria-labelledby={headingId}>
      <h2 id={headingId}>{title}</h2>
      <p>
        <code>{endpoint}</code>
      </p>
      {children}
    </section>
  )
}
