import { z } from 'zod'

export type Service =
  | 'auth'
  | 'membership'
  | 'banking'
  | 'money'
  | 'remittance'
  | 'payment'
  | 'money-aggregation'
  | 'money-query'
  | 'event-flow'

type Credentials = { token: string; onUnauthorized: () => void }

let credentials: Credentials | null = null

export function authorize(next: Credentials | null) {
  credentials = next
}

const problemSchema = z.object({
  error: z.string(),
  message: z.string(),
  violations: z.array(z.object({ rule: z.string(), message: z.string() })).optional(),
})

function readProblem(text: string) {
  try {
    const parsed = problemSchema.safeParse(JSON.parse(text))
    return parsed.success ? parsed.data : null
  } catch {
    return null
  }
}

class ServerError extends Error {
  readonly violations: string[]

  constructor(status: number, text: string) {
    const problem = readProblem(text)
    super(problem?.message ?? `The server returned an error (HTTP ${String(status)}). ${text}`.trim())
    this.violations = problem?.violations?.map((violation) => violation.message) ?? []
  }
}

function withToken(init: RequestInit, token: string): RequestInit {
  const headers = new Headers(init.headers)
  headers.set('Authorization', `Bearer ${token}`)
  return { ...init, headers }
}

export function postJson(value: unknown): RequestInit {
  return { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(value) }
}

export async function request(service: Service, path: string, init: RequestInit = { method: 'GET' }): Promise<string> {
  const sent = credentials
  let response: Response
  try {
    response = await fetch(`/api/${service}${path}`, sent ? withToken(init, sent.token) : init)
  } catch {
    throw new Error('Could not reach the server.')
  }
  const text = await response.text()
  if (response.status === 401) sent?.onUnauthorized()
  if (!response.ok) throw new ServerError(response.status, text)
  return text
}

export function parse<S extends z.ZodType>(text: string, schema: S): z.output<S> {
  if (text === '') throw new Error('The server returned an empty response. The backend did not process the request.')
  let json: unknown
  try {
    json = JSON.parse(text)
  } catch {
    throw new Error(`The response was not in the expected format.\n${text}`)
  }
  const parsed = schema.safeParse(json)
  if (!parsed.success) throw new Error(`The response was not in the expected format.\n${z.prettifyError(parsed.error)}`)
  return parsed.data
}

export async function call<S extends z.ZodType>(
  service: Service,
  path: string,
  schema: S,
  init?: RequestInit,
): Promise<z.output<S>> {
  return parse(await request(service, path, init), schema)
}
