import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useOutletContext } from 'react-router'
import { refreshSession, sessionSchema, validateToken, type Session } from './api/auth'

const storageKey = 'jaypay.session'
const sessionKey = ['session']

function readStoredSession(): Session | null {
  let stored: unknown
  try {
    stored = JSON.parse(localStorage.getItem(storageKey) ?? 'null')
  } catch {
    return null
  }
  const parsed = sessionSchema.safeParse(stored)
  return parsed.success ? parsed.data : null
}

function storeSession(session: Session | null) {
  if (session) localStorage.setItem(storageKey, JSON.stringify(session))
  else localStorage.removeItem(storageKey)
}

async function restoreSession(): Promise<Session | null> {
  const stored = readStoredSession()
  if (!stored) return null
  try {
    if (await validateToken(stored.jwtToken)) return stored
    const refreshed = await refreshSession(stored.refreshToken)
    storeSession(refreshed)
    return refreshed
  } catch {
    storeSession(null)
    return null
  }
}

export function useSession() {
  return useQuery({ queryKey: sessionKey, queryFn: restoreSession, staleTime: Infinity })
}

export function useStartSession() {
  const queryClient = useQueryClient()
  return (session: Session) => {
    storeSession(session)
    queryClient.setQueryData(sessionKey, session)
  }
}

export function useEndSession() {
  const queryClient = useQueryClient()
  return () => {
    storeSession(null)
    queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== sessionKey[0] })
    queryClient.setQueryData(sessionKey, null)
  }
}

export function useMember() {
  return useOutletContext<Session>()
}
