import { z } from 'zod'
import { leaveFor } from './leave'
import type { Role } from './permissions'

const endpoints = '/realms/staff/protocol/openid-connect'
const clientId = 'jaypay-admin-console'
const pendingKey = 'jaypay.admin.sign-in'

export const accountConsole = '/realms/staff/account'

export type Tokens = { accessToken: string; refreshToken: string; idToken: string; expiresAt: number }

export type Staff = { username: string; role: Role | null }

const pendingSchema = z.object({ state: z.string(), verifier: z.string(), returnTo: z.string() })

const tokenSchema = z.object({
  access_token: z.string(),
  refresh_token: z.string(),
  id_token: z.string(),
  expires_in: z.number(),
})

const claimsSchema = z.object({
  preferred_username: z.string(),
  realm_access: z.object({ roles: z.array(z.string()) }).optional(),
})

const realmRoles: [string, Role][] = [
  ['admin', 'ADMIN'],
  ['operator', 'OPERATOR'],
  ['viewer', 'VIEWER'],
]

function base64url(bytes: Uint8Array) {
  return btoa(String.fromCharCode(...bytes))
    .replaceAll('+', '-')
    .replaceAll('/', '_')
    .replace(/=+$/, '')
}

function randomValue() {
  return base64url(crypto.getRandomValues(new Uint8Array(32)))
}

async function challengeOf(verifier: string) {
  return base64url(new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier))))
}

function callbackUrl() {
  return `${window.location.origin}/callback`
}

function takePending() {
  const stored = sessionStorage.getItem(pendingKey)
  sessionStorage.removeItem(pendingKey)
  try {
    const parsed = pendingSchema.safeParse(JSON.parse(stored ?? 'null'))
    return parsed.success ? parsed.data : null
  } catch {
    return null
  }
}

async function requestTokens(grant: Record<string, string>): Promise<Tokens> {
  let response: Response
  try {
    response = await fetch(`${endpoints}/token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ client_id: clientId, ...grant }),
    })
  } catch {
    throw new Error('Could not reach the sign-in server.')
  }
  if (!response.ok) throw new Error('The sign-in server refused the request. Sign in again.')
  const parsed = tokenSchema.safeParse(await response.json())
  if (!parsed.success) throw new Error('The sign-in server answered in an unexpected format.')
  return {
    accessToken: parsed.data.access_token,
    refreshToken: parsed.data.refresh_token,
    idToken: parsed.data.id_token,
    expiresAt: Date.now() + parsed.data.expires_in * 1000,
  }
}

export async function beginSignIn(returnTo: string) {
  const state = randomValue()
  const verifier = randomValue()
  const challenge = await challengeOf(verifier)
  sessionStorage.setItem(pendingKey, JSON.stringify({ state, verifier, returnTo }))
  const query = new URLSearchParams({
    client_id: clientId,
    response_type: 'code',
    scope: 'openid',
    redirect_uri: callbackUrl(),
    state,
    code_challenge: challenge,
    code_challenge_method: 'S256',
  })
  leaveFor(`${endpoints}/auth?${query.toString()}`)
}

export async function finishSignIn(search: string) {
  const params = new URLSearchParams(search)
  const pending = takePending()
  const code = params.get('code')
  if (params.has('error')) throw new Error(params.get('error_description') ?? 'The sign-in server refused the sign-in.')
  if (!pending || params.get('state') !== pending.state || !code) {
    throw new Error('This sign-in did not start in this tab. Sign in again.')
  }
  const tokens = await requestTokens({
    grant_type: 'authorization_code',
    code,
    redirect_uri: callbackUrl(),
    code_verifier: pending.verifier,
  })
  return { tokens, returnTo: pending.returnTo }
}

export function refreshTokens(tokens: Tokens) {
  return requestTokens({ grant_type: 'refresh_token', refresh_token: tokens.refreshToken })
}

export function endKeycloakSession(tokens: Tokens) {
  const query = new URLSearchParams({
    client_id: clientId,
    id_token_hint: tokens.idToken,
    post_logout_redirect_uri: `${window.location.origin}/signed-out`,
  })
  leaveFor(`${endpoints}/logout?${query.toString()}`)
}

function claimsOf(token: string) {
  const payload = token.split('.')[1] ?? ''
  const bytes = Uint8Array.from(atob(payload.replaceAll('-', '+').replaceAll('_', '/')), (character) =>
    character.charCodeAt(0),
  )
  return claimsSchema.parse(JSON.parse(new TextDecoder().decode(bytes)))
}

export function staffOf(tokens: Tokens): Staff {
  const claims = claimsOf(tokens.accessToken)
  const held = claims.realm_access?.roles ?? []
  const role = realmRoles.find(([name]) => held.includes(name))?.[1] ?? null
  return { username: claims.preferred_username, role }
}
