# 0006. Staff sign-in written in a new auth-service

## Release

0.2.0

## Status

superseded by [0010](0010-staff-sign-in-through-keycloak.md) in 0.4.0

## Context

The admin site had no sign-in, so anyone who reached it could read and change everything. Staff needed three roles, viewer, operator and admin, and the server had to enforce the role on every request, not only hide buttons.

## Options

The issue and the PR record no other option. The one choice they name is to keep staff tokens apart from the customer tokens of membership-service.

## Decision

auth-service holds staff accounts with salted password hashes, one password policy and its own HS256 staff token, and one decision table maps each path to the role it needs, ending in deny. The admin site's nginx asks it before forwarding any `/api/<service>/` request. A separate secret and audience keep staff and customer tokens apart without touching membership-service. The first account per role comes from environment variables.

## What we gave up

- MFA was a flag only, and there was no sign-in rate limit.
- The gate loads the account on every check, one primary-key read per request, so that deactivation and role changes apply at once.
- A new database `auth`, so an existing PostgreSQL volume had to be recreated.

## When to decide again

It was decided again in 0.4.0, when Keycloak took over sign-in. See [0010](0010-staff-sign-in-through-keycloak.md).

## Links

- Issue [#61](https://github.com/chrysaor/JayPay/issues/61)
- PR [#70](https://github.com/chrysaor/JayPay/pull/70)
