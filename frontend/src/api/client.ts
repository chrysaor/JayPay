import { z } from 'zod'

type Service = 'membership' | 'banking' | 'money' | 'remittance' | 'payment'

export function postJson(value: unknown): RequestInit {
  return { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(value) }
}

export async function call<S extends z.ZodType>(
  service: Service,
  path: string,
  schema: S,
  init: RequestInit = { method: 'GET' },
): Promise<z.output<S>> {
  let response: Response
  try {
    response = await fetch(`/api/${service}${path}`, init)
  } catch {
    throw new Error('서버에 연결하지 못했습니다.')
  }

  const text = await response.text()
  if (!response.ok) throw new Error(`서버가 오류를 돌려줬습니다 (HTTP ${String(response.status)}). ${text}`.trim())
  if (text === '') throw new Error('서버가 빈 응답을 돌려줬습니다. 백엔드가 요청을 처리하지 않았을 수 있습니다.')

  let json: unknown
  try {
    json = JSON.parse(text)
  } catch {
    throw new Error(`응답 형식이 예상과 다릅니다.\n${text}`)
  }
  const parsed = schema.safeParse(json)
  if (!parsed.success) throw new Error(`응답 형식이 예상과 다릅니다.\n${z.prettifyError(parsed.error)}`)
  return parsed.data
}
