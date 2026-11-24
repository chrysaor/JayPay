import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { vi } from 'vitest'
import { App } from '../App'

export function renderApp(path: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const user = userEvent.setup()
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <App />
      </MemoryRouter>
    </QueryClientProvider>,
  )
  return user
}

type ReceivedRequest = {
  url: string
  method: string
  contentType: string | null
  body: string | null
}

type Reply = { status?: number; body?: unknown }

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

export function unreachableServer() {
  vi.stubGlobal(
    'fetch',
    vi.fn(() => Promise.reject(new TypeError('Failed to fetch'))),
  )
}
