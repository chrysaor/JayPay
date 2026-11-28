# 0037. The sign-in endpoints refuse with 401, and an access token names its member

## Release

0.6.0

## Status

accepted

## Context

The audit of [#149](https://github.com/chrysaor/JayPay/issues/149) wrote the first tests of the sign-in endpoints of membership-service and could not pin two answers ([#224](https://github.com/chrysaor/JayPay/issues/224)):

- `POST /memberships/auth` answered the member only for the refresh token of the last sign-in. An access token answered an empty body.
- A sign-in of a member that is not valid, and a token that `POST /memberships/refresh-token` or `POST /memberships/auth` refused, answered 200 with an empty body.

## Options

- A: `POST /memberships/auth` answers the member of an access token, and every refusal answers 401 with an error.
- B: keep the answers and pin them with tests.

## Decision

A. The owner chose it.

- `POST /memberships/auth` answers the member of any token that validates. The two kinds of token carry the same claims, so a refresh token is answered too.
- A sign-in or a refresh of a member that is not valid answers 401 `MEMBERSHIP_NOT_VALID`.
- A token that does not validate, a token whose member does not exist, and on `POST /memberships/refresh-token` a token that is not the refresh token of the last sign-in answer 401 `TOKEN_REFUSED`.
- The customer site keeps its check for an empty answer to a sign-in, so it works with a membership-service of 0.5.0 as well ([0030](0030-releases-do-not-depend-on-the-order-of-the-services.md)). With the new service it shows the message of the 401.

## What we gave up

- A caller that read the empty 200 as "refused" now gets an error status. The customer site is the only caller in the repository, and no caller of `POST /memberships/auth` exists in it.
- An access token still cannot be told from a refresh token, and sign-in still takes a member id without a credential.

## When to decide again

When customer sign-in gets a credential, or when the gateway decision ([#115](https://github.com/chrysaor/JayPay/issues/115)) moves token checks out of membership-service.

## Links

- Issue [#224](https://github.com/chrysaor/JayPay/issues/224)
