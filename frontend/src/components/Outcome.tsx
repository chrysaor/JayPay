type Fields = Readonly<Record<string, string | number | boolean | null | undefined>>

type RequestState = {
  isPending: boolean
  error: Error | null
  data: Fields | Fields[] | undefined
}

export function Outcome({ state }: { state: RequestState }) {
  if (state.error) {
    return (
      <div className="result error" role="alert">
        {state.error.message}
      </div>
    )
  }
  if (state.data === undefined) {
    return state.isPending ? <p>요청 중...</p> : null
  }
  const records = Array.isArray(state.data) ? state.data : [state.data]
  return (
    <div className="result" role="status">
      {records.length === 0 ? <p>결과가 없습니다.</p> : null}
      {records.map((fields, index) => (
        <dl key={index}>
          {Object.entries(fields).map(([key, value]) => (
            <div key={key}>
              <dt>{key}</dt>
              <dd>{String(value)}</dd>
            </div>
          ))}
        </dl>
      ))}
    </div>
  )
}
