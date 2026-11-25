export function ErrorNotice({ error }: { error: (Error & { violations?: readonly string[] }) | null }) {
  if (!error) return null
  return (
    <div className="notice notice-error" role="alert">
      {error.message}
      {error.violations?.length ? (
        <ul>
          {error.violations.map((violation) => (
            <li key={violation}>{violation}</li>
          ))}
        </ul>
      ) : null}
    </div>
  )
}
