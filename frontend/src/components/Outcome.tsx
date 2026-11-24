type Scalar = string | number | boolean | null | undefined

/** A parsed response: one record of plain fields, or a list of them. */
export type Fields = Readonly<Record<string, Scalar>>

type RequestState = {
  isPending: boolean
  error: Error | null
  data: Fields | readonly Fields[] | undefined
}

function isList(data: Fields | readonly Fields[]): data is readonly Fields[] {
  return Array.isArray(data)
}

function FieldList({ fields }: { fields: Fields }) {
  return (
    <dl>
      {Object.entries(fields).map(([key, value]) => (
        <div key={key}>
          <dt>{key}</dt>
          <dd>{String(value)}</dd>
        </div>
      ))}
    </dl>
  )
}

/** Shows what came back from a request: nothing yet, in progress, an error, or the response. */
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
  const records = isList(state.data) ? state.data : [state.data]
  return (
    <div className="result" role="status">
      {records.length === 0 ? <p>결과가 없습니다.</p> : null}
      {records.map((fields, index) => (
        <FieldList key={index} fields={fields} />
      ))}
    </div>
  )
}
