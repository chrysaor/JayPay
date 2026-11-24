import { useId, type ReactNode } from 'react'

/** One request on a screen: a titled section with the endpoint it calls. */
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
