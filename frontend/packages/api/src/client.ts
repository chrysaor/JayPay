import { z } from 'zod'

export type Service =
  | 'membership'
  | 'banking'
  | 'money'
  | 'remittance'
  | 'payment'
  | 'money-aggregation'
  | 'money-query'

export function postJson(value: unknown): RequestInit {
  return { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(value) }
}

export async function request(service: Service, path: string, init: RequestInit = { method: 'GET' }): Promise<string> {
  let response: Response
  try {
    response = await fetch(`/api/${service}${path}`, init)
  } catch {
    throw new Error('Could not reach the server.')
  }
  const text = await response.text()
  if (!response.ok) throw new Error(`The server returned an error (HTTP ${String(response.status)}). ${text}`.trim())
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
