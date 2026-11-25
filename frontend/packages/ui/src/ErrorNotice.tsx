export function ErrorNotice({ error }: { error: Error | null }) {
  if (!error) return null
  return (
    <p className="notice notice-error" role="alert">
      {error.message}
    </p>
  )
}
