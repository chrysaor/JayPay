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
    Note over money: the saga adds the amount<br/>to the balance in PostgreSQL
    alt the balance update fails
        money->>banking: RollbackFirmBankingRequestCommand
        banking->>money: RollbackFirmBankingFinishedEvent
    end
    query->>membership: GET /memberships/{membershipId}
    membership-->>query: member address
    query->>dynamodb: add the amount to the total of that address
```

`POST /money/increase-async` reaches the same end through Kafka. money-service writes a task to `jaypay.task.topic` and blocks the request. task-consumer marks every subtask done and writes the result to `jaypay.task.result.topic`. When money-service reads the result, it adds the amount to the balance and answers.

`make smoke` runs both top-ups. It checks that the saga trace spans money-service and banking-service, and that the Kafka trace spans money-service and task-consumer.

## Admin console request through auth-service

Only the sign-in screen and the JavaScript bundle load without a staff account. Every `/api/<service>/` request of the admin console passes nginx `auth_request` first: nginx sends the original method, path and headers, without the body, to `/authorize/<original path>` on auth-service and forwards the request only when auth-service answers 204.

```mermaid
sequenceDiagram
    autonumber
    participant browser as browser
    participant admin as frontend-admin, nginx
    participant auth as auth-service
    participant membership as membership-service

    browser->>admin: POST /api/auth/staff/sign-in
    admin->>auth: POST /staff/sign-in
    auth-->>browser: 200, staff token for 8 hours and the account
    browser->>admin: PUT /api/membership/memberships/7, Bearer token
    admin->>auth: PUT /authorize/api/membership/memberships/7, no body
    Note over auth: checks the token signature, audience and expiry,<br/>loads the account, and looks the request up in the table
    alt viewer, or the account is inactive, or the path is not in the table
        auth-->>admin: 401 or 403
        admin-->>browser: 401 or 403 with a JSON error
    else operator or admin
        auth-->>admin: 204
        admin->>membership: PUT /memberships/7
        membership-->>browser: 200, the edited member
    end
```

- `StaffSecurityConfig` in auth-service holds the only decision table, as Spring Security `authorizeHttpRequests` rules: one line per method and path the admin console calls, the lowest role that may call it, and `denyAll` for everything else. `AdminConsoleGateTest` pins the table for no token and for each role.
- The roles form the hierarchy viewer < operator < admin through a Spring Security `RoleHierarchy`.
- auth-service checks `$request_uri` as the browser sent it. Spring Security's firewall refuses an encoded slash, backslash or dot, `;`, `//`, a dot segment and an encoded `%` with 403, so the path auth-service decides on is the path the upstream service receives. Tomcat in auth-service passes encoded slashes and backslashes through to the firewall instead of answering 400, because nginx `auth_request` turns any answer other than 2xx, 401 and 403 into 500. `AdminConsoleGateOverTomcatTest` sends these paths through a real Tomcat.
- auth-service loads the account on every check, so a deactivation or a role change applies at once to tokens issued before it.
- Staff tokens are HS256 JWTs signed with `STAFF_JWT_SECRET`, with the audience `jaypay-admin-console`. Customer tokens from membership-service use `JWT_SECRET` and carry no audience, so neither token opens the other site.
- The token carries the factors the sign-in proved, today `FACTOR_PASSWORD`, as Spring Security 7 `FactorGrantedAuthority` values, and the sign-in answer has a `status` of `SIGNED_IN`. A second factor for accounts with the MFA flag can add another status and a `requireFactors` rule without changing the shape.

Spring Security stays in the adapters of auth-service. `domain` holds `StaffAccount`, `StaffRole` and `PasswordPolicy`, the list of password rules. `adapter.in.web` holds the decision table, the sign-in through `DaoAuthenticationProvider` and the token check. `adapter.out.password` and `adapter.out.token` wrap `PasswordEncoder` and `JwtEncoder` behind the `PasswordHashing` and `StaffTokenIssuer` ports. `SecurityStaysInAdaptersTest` fails when `domain` or `application` imports Spring Security.

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
