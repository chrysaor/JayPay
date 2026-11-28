# 0042. The customer API is limited to 300 requests a minute per address, and sign-in to 30

## Release

0.7.0 (unreleased)

## Status

accepted

## Context

Decision [0040](0040-gateway-api-with-envoy-gateway.md) gives the gateway a rate limit in the cluster, with the counts in Valkey, and [#239](https://github.com/chrysaor/JayPay/issues/239) moves the routes of the customer site to the gateway. Neither names the limits. The customer API takes its requests without a token until [#91](https://github.com/chrysaor/JayPay/issues/91), and a member id is all that sign-in asks for, so the only thing a limit can count by is the client address.

## Options

All count per client address and answer 429 above the limit.

- A: one limit, 300 requests a minute over the whole customer API. The simplest setting. Sign-in attempts share the limit with everything else.
- B: two limits. 300 a minute over the whole customer API, and 30 a minute of its own for sign-up, sign-in and token refresh. Repeated sign-in attempts are limited apart from the rest, and the smoke test can pass the lower limit to see a 429.
- C: B and a third limit of 60 a minute for top-up, remittance and payment. Three rules, and the smoke test has to stay below the third.

## Decision

B. The owner picked it from the three options.

- `customerSite.limits` of the chart `deploy/charts/gateway` holds both: `all` with 300 a minute and `signIn` with 30 a minute. A route carries `limit: signIn` to count toward the second.
- A limit counts one client address over all the routes it covers together, not per route and not per proxy.
- A request of a sign-in route counts toward both limits.
- The limits apply in the cluster only, as decision 0040 set. The Compose stack has none.

## What we gave up

- An address is not a person. Members behind one address share its limit, and one member with many addresses has many limits. A limit per member needs the identity that #91 brings.
- The local cluster reaches the gateway through a port-forward, so every request there has the same address. `make k8s-smoke` therefore passes the sign-in limit only as its last step.
- Behind a load balancer the gateway sees the address of the balancer until a `ClientTrafficPolicy` tells it where the client address is. No environment has a balancer yet, and the one that adds it has to add that setting.
- Top-up, remittance and payment have no limit of their own.
- The numbers are not measured against real traffic. There is none.

## When to decide again

When #91 gives the gateway a member to count by, when an environment puts a load balancer in front of the gateway, or when a load test shows the numbers are wrong.

## Links

- Issue [#239](https://github.com/chrysaor/JayPay/issues/239)
- Decision [0040](0040-gateway-api-with-envoy-gateway.md), the gateway and where its rate limit runs
