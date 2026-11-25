import { authorize } from '@jaypay/api/client'
import { useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { useOutletContext } from 'react-router'
import { findMe, staffSessionSchema, type StaffSession } from './api/staff'

export type SessionState = { signedIn: true; session: StaffSession } | { signedIn: false; notice: string | null }

const storageKey = 'jaypay.admin.session'
const sessionKey = ['staff-session']
const endedNotice = 'Your session has ended. Sign in again.'
const longestTimerDelay = 2 ** 31 - 1

function readStoredSession(): StaffSession | null {
  let stored: unknown
  try {
    stored = JSON.parse(localStorage.getItem(storageKey) ?? 'null')
  } catch {
    return null
  }
  const parsed = staffSessionSchema.safeParse(stored)
  return parsed.success ? parsed.data : null
}

function storeSession(session: StaffSession | null) {
  if (session) localStorage.setItem(storageKey, JSON.stringify(session))
  else localStorage.removeItem(storageKey)
}

function millisecondsLeft(session: StaffSession) {
  return Date.parse(session.expiresAt) - Date.now()
}

function open(queryClient: QueryClient, session: StaffSession): SessionState {
  storeSession(session)
  authorize({
    token: session.token,
    onUnauthorized: () => {
      close(queryClient, endedNotice)
    },
  })
  return { signedIn: true, session }
}

function forget(notice: string | null): SessionState {
  storeSession(null)
  authorize(null)
  return { signedIn: false, notice }
}

function close(queryClient: QueryClient, notice: string | null) {
  const signedOut = forget(notice)
  queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== sessionKey[0] })
  queryClient.setQueryData(sessionKey, signedOut)
}

async function restore(queryClient: QueryClient): Promise<SessionState> {
  const stored = readStoredSession()
  if (!stored) return forget(null)
  if (millisecondsLeft(stored) <= 0) return forget(endedNotice)
  open(queryClient, stored)
  try {
    return open(queryClient, { ...stored, account: await findMe() })
  } catch {
    return forget(endedNotice)
  }
}

export function useSession() {
  const queryClient = useQueryClient()
  return useQuery({ queryKey: sessionKey, queryFn: () => restore(queryClient), staleTime: Infinity })
}

export function useStartSession() {
  const queryClient = useQueryClient()
  return (session: StaffSession) => {
    queryClient.setQueryData(sessionKey, open(queryClient, session))
  }
}

export function useEndSession() {
  const queryClient = useQueryClient()
  return () => {
    close(queryClient, null)
  }
}

export function useEndOnExpiry(session: StaffSession) {
  const queryClient = useQueryClient()
  useEffect(() => {
    const delay = millisecondsLeft(session)
    if (delay > longestTimerDelay) return
    const timer = setTimeout(() => {
      close(queryClient, endedNotice)
    }, delay)
    return () => {
      clearTimeout(timer)
    }
  }, [queryClient, session])
}

export function useStaff() {
  return useOutletContext<StaffSession>()
}
