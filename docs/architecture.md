# Architecture notes

The [root README](../README.md) shows the services, the messaging and the observability stack. This page follows one request through the saga top-up and shows the layers inside each service.

## Money top-up through the saga

`POST /money/increase-eda` on money-service tops a wallet up through Axon. `make smoke` calls it through the customer site's proxy at `/api/money/money/increase-eda`. The top-up form on the customer site calls the synchronous `POST /money/increase` instead. money-service answers `increase-eda` as soon as it sends the first command. The rest of the flow runs on Axon commands and events, and a saga in money-service drives it. Every command and event below travels through Axon Server, which the diagram leaves out.

```mermaid
sequenceDiagram
    autonumber
    participant web as frontend-web
    participant money as money-service
    participant banking as banking-service
    participant query as money-query-service
    participant membership as membership-service
    participant dynamodb as DynamoDB

    web->>money: POST /money/increase-eda
    money-->>web: 200, empty body
    Note over money: the member money aggregate handles<br/>RechargingMoneyRequestCreateCommand
    money->>banking: GET /banking/account/{membershipId}
    banking-->>money: registered bank account
    Note over money: RechargingRequestCreatedEvent<br/>starts the recharge saga
    money->>banking: CheckRegisteredBankAccountCommand
    Note over banking: the fake bank confirms the account
    banking->>money: CheckedRegisteredBankAccountEvent
    money->>banking: RequestFirmBankingCommand
    Note over banking: stores the firm banking request,<br/>the fake bank moves the money
    banking->>money: RequestFirmBankingFinishedEvent
    banking->>query: RequestFirmBankingFinishedEvent
    Note over money: the saga adds the amount<br/>to the balance in PostgreSQL once
    alt money-service has no record of the request
        money->>banking: RollbackFirmBankingRequestCommand
        banking->>money: RollbackFirmBankingFinishedEvent
    end
    query->>membership: GET /memberships/{membershipId}
    membership-->>query: member address
    query->>dynamodb: add the amount to the total of that address
```

`POST /money/increase-async` reaches the same end through Kafka. money-service writes a task to `jaypay.task.topic` and blocks the request. task-consumer marks every subtask done and writes the result to `jaypay.task.result.topic`. When money-service reads the result, it adds the amount to the balance and answers.

`make smoke` runs both top-ups. It checks that the saga trace spans money-service and banking-service, and that the Kafka trace spans money-service and task-consumer.

## Admin console sign-in through Keycloak

Keycloak holds the staff accounts in the realm `staff`. The admin console is the public client `jaypay-admin-console` and signs staff in with the Authorization Code flow and PKCE (`S256`). Only the JavaScript bundle, the callback page and the Keycloak sign-in pages load without a staff account.

```mermaid
sequenceDiagram
    autonumber
    participant browser as browser
    participant admin as frontend-admin, nginx
    participant keycloak as keycloak
    participant auth as auth-service
    participant membership as membership-service

    browser->>admin: GET /members, no tokens in memory
    admin-->>browser: the React app
    Note over browser: random state and code verifier in sessionStorage
    browser->>admin: GET /realms/staff/protocol/openid-connect/auth, S256 challenge
    admin->>keycloak: same path
    keycloak-->>browser: sign-in form, then 302 to /callback with code and state
    Note over browser: refuses a state this tab did not start
    browser->>admin: POST /realms/staff/protocol/openid-connect/token, code and verifier
    admin->>keycloak: same path
    keycloak-->>browser: access token for 2 minutes, refresh token, ID token
    browser->>admin: PUT /api/membership/memberships/7, Bearer access token
    admin->>auth: PUT /authorize/api/membership/memberships/7, no body
    Note over auth: checks the signature against the realm keys, the issuer,<br/>the audience and the expiry, and looks the request up in the table
    alt no valid token, a viewer, or a path that is not in the table
        auth-->>admin: 401 or 403
        admin-->>browser: 401 or 403 with a JSON error
    else operator or admin
        auth-->>admin: 204
        admin->>membership: PUT /memberships/7
        membership-->>browser: 200, the edited member
    end
```

### One origin for the browser and the containers

The browser reaches Keycloak only through the admin site: `frontend-admin` proxies `/realms/staff/` and `/resources/` to `JAYPAY_KEYCLOAK_URL` and answers 404 to any other `/realms/` path, so the master realm is not reachable on 8091. `KC_HOSTNAME` is the full URL of the admin site, `http://localhost:8091` in Compose and `http://admin.jaypay.localhost:8480` in the local cluster. Keycloak then writes the same issuer, `<admin site>/realms/staff`, into every token, whichever address a request came in on.

auth-service checks that issuer through `STAFF_ISSUER_URI`, but fetches the realm keys from `STAFF_JWK_SET_URI`, the address of Keycloak inside the network, `http://keycloak:8080/realms/staff/protocol/openid-connect/certs`. The issuer is never fetched, so it does not have to resolve inside a container. The callback URI `/callback` and the post sign-out URI `/signed-out` are relative to `KC_HOSTNAME`, and the browser never makes a cross-origin call, so the client needs no CORS rule.

The Keycloak admin console has its own hostname, `KC_HOSTNAME_ADMIN`, `http://localhost:8092`. Compose publishes it on `127.0.0.1:8092` only. In the cluster it has no Ingress, and `kubectl port-forward service/keycloak 8092:8080` opens it.

### Tokens and the session

- The access and refresh tokens live in memory only, in the React Query cache. A reload or a new tab starts the Authorization Code flow again, and Keycloak's session cookie signs the tab in without the form while the session lasts.
- The console refreshes the tokens 30 seconds before the access token expires. Keycloak rotates the refresh token on each use. A failed refresh or a 401 from any `/api` request ends the session in the tab and shows a button to sign in again instead of redirecting at once.
- Signing out sends the tab to Keycloak's end-session endpoint with the ID token. Keycloak ends the session, so its refresh tokens stop working, and returns to `/signed-out`.
- A Keycloak session ends after 30 idle minutes or 8 hours.

### The decision in auth-service

- `StaffSecurityConfig` in auth-service holds the only decision table, as Spring Security `authorizeHttpRequests` rules: one line per method and path the admin console calls, the lowest role that may call it, and `denyAll` for everything else. `AdminConsoleGateTest` pins the table for no token and for each role, and the refusal of a token signed by another key, an expired token, another issuer, another audience and a customer token.
- auth-service is a Spring Security resource server. The realm roles `viewer`, `operator` and `admin` in the token's `realm_access.roles` become the roles of the table, and they form the hierarchy viewer < operator < admin through a Spring Security `RoleHierarchy`. A token with none of them opens nothing.
- The audience mapper of the client puts `jaypay-admin-console` into the access token's `aud`. Customer tokens from membership-service are HS256 with `JWT_SECRET` and no audience, so neither token opens the other site.
- auth-service checks `$request_uri` as the browser sent it. Spring Security's firewall refuses an encoded slash, backslash or dot, `;`, `//`, a dot segment and an encoded `%` with 403, so the path auth-service decides on is the path the upstream service receives. Tomcat in auth-service passes encoded slashes and backslashes through to the firewall instead of answering 400, because nginx `auth_request` turns any answer other than 2xx, 401 and 403 into 500. `AdminConsoleGateOverTomcatTest` sends these paths through a real Tomcat.
- auth-service does not ask Keycloak on every request. Disabling an account or taking a role away refuses its next sign-in and its next refresh at once, but an access token issued before keeps working until it expires. With the 2-minute lifetime and Spring Security's clock skew of 60 seconds, `make smoke` and a manual measurement saw the first 401 181 seconds after the account was disabled.

Spring Security stays in the adapters of auth-service, which keeps no database: `domain` holds `StaffRole`, and `adapter.in.web` holds the decision table and the token conversion. `SecurityStaysInAdaptersTest` fails when `domain` or `application` imports Spring Security.

## Layers inside a service

Each service follows the hexagonal dependency direction in [CLAUDE.md](../CLAUDE.md): `domain` ← `application` ← `adapter`. An arrow points from the code that depends to the code it depends on.

```mermaid
flowchart RL
    subgraph adapter["adapter"]
        adapterIn["in<br/>web controllers, Kafka listeners,<br/>Axon handlers"]
        adapterOut["out<br/>persistence, HTTP clients to other services,<br/>Kafka producers, Vault, DynamoDB"]
    end

    subgraph application["application"]
        portIn["port.in<br/>use case interfaces and their commands"]
        service["service<br/>use case implementations"]
        portOut["port.out<br/>interfaces the use cases call"]
    end

    domain["domain<br/>entities and value objects"]

    adapterIn --> portIn
    adapterOut -. implements .-> portOut
    service -. implements .-> portIn
    service --> portOut
    service --> domain
    adapterIn --> domain
    adapterOut --> domain
```

- `domain` imports neither `application` nor `adapter`.
- `application` reaches the outside only through its `port.in` and `port.out` interfaces.
- `adapter` turns HTTP requests, Kafka records and Axon messages into `port.in` calls, and implements `port.out` with JPA, HTTP clients, Kafka, Vault and the AWS SDK.

## Workloads on Kubernetes

[deploy/README.md](../deploy/README.md) packages the same thirteen workloads as Helm charts, and Keycloak with the other dependencies. Nothing is deployed from this repository yet. Each workload becomes a Deployment, a Service and a ServiceAccount with its Compose name, so `http://membership-service:8080` and the other service URLs keep working inside one namespace.

```mermaid
flowchart LR
    subgraph envs["deploy/envs/local"]
        shared["spring-service.yaml<br/>frontend.yaml<br/>shared by the chart's workloads"]
        workload["spring-service/&lt;workload&gt;.yaml<br/>frontend/&lt;workload&gt;.yaml"]
    end
    secret[("Secret jaypay<br/>keys of .env.example")]
    chart["deploy/charts/&lt;chart&gt;"]
    objects["Deployment, Service, ServiceAccount<br/>named after the workload"]
    compose["infra/docker/compose.yaml"]

    shared & workload --> chart --> objects
    objects -- "secretKeyRef" --> secret
    compose -.->|"make verify compares names,<br/>secrets and memory"| workload
```

Two things differ from Compose:

- The two sites get their upstreams as full names such as `http://auth-service.jaypay.svc.cluster.local:8080`, because the nginx `resolver` ignores the search domains of the pod. The admin console's `auth_request` to auth-service uses the same name.
- Secret values come from the Secret `jaypay` instead of `infra/docker/.env`, under the same key names.
