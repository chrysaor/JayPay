# 0040. The gateway is the Kubernetes Gateway API with Envoy Gateway

## Release

0.7.0 (unreleased)

## Status

accepted

## Context

Two nginx sites each hold their own routing and their own access rule: an allowlist of method and path in the customer site, and a request to auth-service for every call of the admin console. There is no one place for authentication, rate limits, timeouts or request logging ([#115](https://github.com/chrysaor/JayPay/issues/115)). The customer site's API checks no token at all ([#91](https://github.com/chrysaor/JayPay/issues/91)).

The local cluster takes requests through the minikube ingress addon, which is ingress-nginx. Kubernetes retired ingress-nginx in March 2026 and recommends the Gateway API.

The issue asks that the same gateway and the same route table run on Compose and in the cluster.

## Options

- A: Spring Cloud Gateway as one new Spring service, in its WebMVC or its WebFlux form.
- B: the Kubernetes Gateway API with Envoy Gateway.
- C: Apache APISIX or Kong without a database.
- D: keep nginx.

For the rate limit counts: in the memory of the proxy, or shared through Redis or Valkey so that the limit holds for more than one gateway replica.

## Decision

B. The owner first chose A and changed to B, because B is the direction that fits Kubernetes and GitOps: the rules are resources that Argo CD applies without an image build, the gateway is also the cluster entry, so the Ingress and ingress-nginx go, and a service mesh can later use the same API.

- Compose runs Envoy Gateway in its standalone mode, which reads the same resources from files.
- The route table, the token check and the role each route needs move from the two nginx configurations and from auth-service into Gateway API resources. auth-service is removed.
- Rate limits apply in the cluster only, with the counts shared through Valkey. The gateway of the Compose stack has no rate limit. The owner's reason: a rate limit bounds what a customer may ask of an API, the gateway is where that is checked, and the Compose stack does not need it.
- Valkey in place of Redis. The owner chose it after a comparison that named the BSD licence of Valkey and the licence changes of Redis.
- When Valkey does not answer, the gateway lets the request through and the outage is logged as an error. The limit is a protection, and the services keep money correct without it.
- Calls between services get no rate limit for now. They do not pass through the gateway.
- Customer authentication ([#91](https://github.com/chrysaor/JayPay/issues/91)) is part of 0.7.0, in its own PR after the gateway replaces the proxies.

A trial outside the repository ran Envoy Gateway 1.10.0 in standalone mode on Docker, with tokens shaped like the ones Keycloak issues. The route table, the token check and the rule per role worked: 404 for a method or path that is not listed, 401 without a token, with a token signed by another key or after expiry, and 403 for a role that is too low. A limit kept in the proxy answered 429 from the fourth request at three a minute. A limit shared through Valkey was not applied in standalone mode, because Envoy Gateway builds the connection to its rate limit service for Kubernetes only.

## What we gave up

- The rule tests of auth-service. A route and the role it needs are checked by the smoke test against a running stack, not by `make verify`.
- A rate limit that the Compose smoke and the smoke in CI can check. Only `make k8s-smoke` reaches it.
- A supported mode on Compose. The Envoy Gateway documentation calls the standalone mode experimental and not for production. JayPay uses it for the local Compose stack only.
- Routes that point to a `Service` in the cluster. A route in standalone mode can only point to a `Backend` with a host name, so the cluster uses `Backend` resources too, with the full DNS name of each Service, and one chart renders both stacks ([#238](https://github.com/chrysaor/JayPay/issues/238)).
- A gateway image that is pulled as it is. The standalone mode downloads the Envoy binary when it starts, so the Compose stack builds `jaypay/gateway` from the Envoy Gateway image and the Envoy image, both pinned by digest. A container that runs once before the gateway writes the certificates between the controller and Envoy into a volume.
- The 403 for a staff token on a path that the table does not list. Such a request has no route to a service, falls to the admin site and gets its 404, with or without a token. A token of the staff realm for another audience gets 403, where auth-service answered 401.
- The admin console of the local cluster on the Ingress host. The issuer of the staff tokens is the address of the console, and the gateway has one listener per site on the port that Compose publishes, so the cluster serves the console on `http://localhost:8091` through a port-forward of the gateway.
- auth-service answers 403 for a path with an encoded slash or dot segments. Envoy Gateway redirects or normalises such a path and then looks for the route.
- Not checked yet: Envoy Gateway in the local cluster, the rate limit through Valkey there, a real Keycloak behind the token check, and memory under load. The gateway container used 129 MiB idle in the trial, and between 140 and 215 MiB with the routes of the admin console, so its limit on Compose is 384 MiB.

## When to decide again

When the standalone mode cannot do what the Compose stack needs, when the first step in the cluster shows that the rate limit through Valkey does not work there, or when a call between services needs a limit.

## Links

- Issue [#115](https://github.com/chrysaor/JayPay/issues/115), and [#91](https://github.com/chrysaor/JayPay/issues/91) for customer authentication
- [Envoy Gateway standalone deployment mode](https://gateway.envoyproxy.io/docs/tasks/operations/standalone-deployment-mode/)
- [Ingress NGINX retirement](https://kubernetes.io/blog/2025/11/11/ingress-nginx-retirement/)
