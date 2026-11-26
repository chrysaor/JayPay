# 0010. Staff sign in through Keycloak

## Release

0.4.0

## Status

accepted

## Context

Staff sign-in was written by hand in auth-service, see [0006](0006-staff-sign-in-in-auth-service.md). It had no lockout and MFA only as a flag, and a staff token lived 8 hours. The customer site had no sign-in at all ([#91](https://github.com/chrysaor/JayPay/issues/91)).

## Options

- Add attempt limits and lockout to the sign-in code in auth-service, as [#90](https://github.com/chrysaor/JayPay/issues/90) first asked.
- An identity provider. The owner approved Keycloak as a new component. It replaces the hand-written sign-in code and gives single sign-on.

## Decision

Keycloak runs in both local stacks with a `staff` realm kept as a file without secrets: the roles viewer, operator and admin, lockout after 5 wrong passwords, a password policy and an optional authenticator app. The admin console signs in with the Authorization Code flow and PKCE and keeps its tokens in memory. auth-service only checks the Keycloak token and applies its decision table, and its password storage, token issuing and database are gone. The plan has two more steps: a customer realm for the customer site, then removing auth-service.

## What we gave up

- Keycloak adds about 768 MiB, and the requests on the local cluster node now add up to more than its memory.
- A disabled account keeps working until its access token expires, about 3 minutes.
- The realm import skips a realm that already exists, so a change to `staff.json` does not reach an existing database.
- The Keycloak image has HIGH findings that are not scanned by the gate ([#121](https://github.com/chrysaor/JayPay/issues/121)).

## When to decide again

When the gateway in [#115](https://github.com/chrysaor/JayPay/issues/115) is chosen, because it is where Keycloak tokens would be checked and where the decision table of auth-service would move.

## Links

- Issue [#112](https://github.com/chrysaor/JayPay/issues/112)
- PR [#120](https://github.com/chrysaor/JayPay/pull/120)
