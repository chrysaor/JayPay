import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { vi } from 'vitest'
import { App } from '../App'

/** Renders the whole app at `path`, the way a user would land on it. */
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

export type RecordedRequest = {
  url: string
  method: string
  contentType: string | null
  body: string | null
}

/** What the fake server answers. `body: undefined` sends an empty body, as Spring does for a null return. */
export type FakeReply = { status?: number; body?: unknown }

function bodyText(body: BodyInit | null | undefined): string | null {
  if (typeof body === 'string') return body
  if (body instanceof URLSearchParams) return body.toString()
  return null
}

/** Replaces fetch with a fake server and returns the list of requests it received. */
export function fakeServer(reply: FakeReply | ((request: RecordedRequest) => FakeReply)) {
  const requests: RecordedRequest[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const headers = new Headers(init?.headers)
      const request: RecordedRequest = {
        url: input instanceof Request ? input.url : input.toString(),
        method: init?.method ?? 'GET',
        contentType: headers.get('Content-Type'),
        body: bodyText(init?.body),
      }
      requests.push(request)
      const { status = 200, body } = typeof reply === 'function' ? reply(request) : reply
      const text = body === undefined ? '' : JSON.stringify(body)
      return Promise.resolve(new Response(status === 204 ? null : text, { status }))
    }),
  )
  return requests
}

/** Makes every fetch fail the way a browser does when nothing listens on the other side. */
export function unreachableServer() {
  vi.stubGlobal(
    'fetch',
    vi.fn(() => Promise.reject(new TypeError('Failed to fetch'))),
  )
}
