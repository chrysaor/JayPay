import { z } from 'zod'

/** Backend services the browser reaches through `/api/<service>/...` on the same origin. */
export type Service = 'membership' | 'banking' | 'money' | 'remittance' | 'payment'

/** Every way a call can fail. Screens show the message; nothing else needs the details. */
export type ApiFailure =
  | { kind: 'network' }
  | { kind: 'http'; status: number; body: string }
  | { kind: 'empty' }
  | { kind: 'invalid'; issues: string }

function describe(failure: ApiFailure): string {
  switch (failure.kind) {
    case 'network':
      return '서버에 연결하지 못했습니다.'
    case 'http':
      return `서버가 오류를 돌려줬습니다 (HTTP ${String(failure.status)}). ${failure.body}`.trim()
    case 'empty':
      return '서버가 빈 응답을 돌려줬습니다. 백엔드가 요청을 처리하지 않았을 수 있습니다.'
    case 'invalid':
      return `응답 형식이 예상과 다릅니다.\n${failure.issues}`
  }
}

export class ApiError extends Error {
  readonly failure: ApiFailure

  constructor(failure: ApiFailure) {
    super(describe(failure))
    this.name = 'ApiError'
    this.failure = failure
  }
}

type RequestBody =
  | { json: unknown }
  // Spring binds a parameter without @RequestBody from form fields, not from JSON.
  | { form: Record<string, string> }

type CallOptions = { method?: 'GET' | 'POST'; body?: RequestBody }

function toInit({ method = 'GET', body }: CallOptions): RequestInit {
  if (!body) return { method }
  if ('json' in body) {
    return { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body.json) }
  }
  return {
    method,
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams(body.form),
  }
}

/** Calls a backend endpoint and parses the response with `schema`, or throws an ApiError. */
export async function call<S extends z.ZodType>(
  service: Service,
  path: string,
  schema: S,
  options: CallOptions = {},
): Promise<z.output<S>> {
  let response: Response
  try {
    response = await fetch(`/api/${service}${path}`, toInit(options))
  } catch {
    throw new ApiError({ kind: 'network' })
  }

  const text = await response.text()
  if (!response.ok) throw new ApiError({ kind: 'http', status: response.status, body: text })
  // Spring answers 200 with no body when a controller returns null.
  if (text === '') throw new ApiError({ kind: 'empty' })

  let json: unknown
  try {
    json = JSON.parse(text)
  } catch {
    throw new ApiError({ kind: 'invalid', issues: text })
  }
  const parsed = schema.safeParse(json)
  if (!parsed.success) throw new ApiError({ kind: 'invalid', issues: z.prettifyError(parsed.error) })
  return parsed.data
}

/** Encodes one path segment taken from user input. */
export function segment(value: string): string {
  return encodeURIComponent(value)
}
