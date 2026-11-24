import { vi } from 'vitest'

export type ReceivedRequest = {
  url: string
  method: string
  contentType: string | null
  body: string | null
}

export type Reply = { status?: number; body?: unknown }

export function fakeServer(reply: Reply | ((request: ReceivedRequest) => Reply)) {
  const received: ReceivedRequest[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn((url: string, init: RequestInit) => {
      const request = {
        url,
        method: init.method ?? 'GET',
        contentType: new Headers(init.headers).get('Content-Type'),
        body: init.body instanceof URLSearchParams ? init.body.toString() : typeof init.body === 'string' ? init.body : null,
      }
      received.push(request)
      const { status = 200, body } = typeof reply === 'function' ? reply(request) : reply
      return Promise.resolve(new Response(body === undefined ? '' : JSON.stringify(body), { status }))
    }),
  )
  return received
}

export function fakeRoutes(routes: Partial<Record<string, Reply>>) {
  return fakeServer((request) => routes[`${request.method} ${request.url}`] ?? { status: 404 })
}

export function unreachableServer() {
  vi.stubGlobal(
    'fetch',
    vi.fn(() => Promise.reject(new TypeError('Failed to fetch'))),
  )
}

export function jsonBody(request: ReceivedRequest | undefined): unknown {
  return JSON.parse(request?.body ?? 'null')
}
