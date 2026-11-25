import { authorize } from '@jaypay/api/client'
import { useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { useOutletContext } from 'react-router'
import { endKeycloakSession, finishSignIn, refreshTokens, staffOf, type Tokens } from './keycloak'
import type { Role } from './permissions'

export type Session = { tokens: Tokens; staff: { username: string; role: Role } }

export type SessionState =
  | { status: 'signed-in'; session: Session }
  | { status: 'no-role'; tokens: Tokens; username: string }
  | { status: 'signed-out'; notice: string | null }

const sessionKey = ['staff-session']
const endedNotice = 'Your session has ended. Sign in again.'
const signedOutNotice = 'You signed out.'
const refreshAhead = 30_000

function open(queryClient: QueryClient, tokens: Tokens): SessionState {
  const { username, role } = staffOf(tokens)
  if (!role) {
    authorize(null)
    return { status: 'no-role', tokens, username }
  }
  authorize({
    token: tokens.accessToken,
    onUnauthorized: () => {
      end(queryClient, endedNotice)
    },
  })
  return { status: 'signed-in', session: { tokens, staff: { username, role } } }
}

function end(queryClient: QueryClient, notice: string | null) {
  authorize(null)
  queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== sessionKey[0] })
  const signedOut: SessionState = { status: 'signed-out', notice }
  queryClient.setQueryData(sessionKey, signedOut)
}

export function useSession() {
  return useQuery<SessionState>({
    queryKey: sessionKey,
    queryFn: () => ({ status: 'signed-out', notice: null }),
    initialData: { status: 'signed-out', notice: null },
    staleTime: Infinity,
  }).data
}

export function useFinishSignIn(search: string) {
  const queryClient = useQueryClient()
  return useQuery({
    queryKey: ['sign-in', search],
    queryFn: async () => {
      const { tokens, returnTo } = await finishSignIn(search)
      queryClient.setQueryData(sessionKey, open(queryClient, tokens))
      return returnTo
    },
    retry: false,
    staleTime: Infinity,
  })
}

export function useSignOut(tokens: Tokens) {
  const queryClient = useQueryClient()
  return () => {
    end(queryClient, signedOutNotice)
    endKeycloakSession(tokens)
  }
}

export function useKeepFresh(tokens: Tokens) {
  const queryClient = useQueryClient()
  useEffect(() => {
    const timer = setTimeout(
      () => {
        refreshTokens(tokens).then(
          (fresh) => queryClient.setQueryData(sessionKey, open(queryClient, fresh)),
          () => {
            end(queryClient, endedNotice)
          },
        )
      },
      Math.max(0, tokens.expiresAt - Date.now() - refreshAhead),
    )
    return () => {
      clearTimeout(timer)
    }
  }, [queryClient, tokens])
}

export function useStaff() {
  return useOutletContext<Session>()
}
