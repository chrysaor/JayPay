# Services

This page describes each of the eleven Spring Boot services in `backend/services` and the two nginx sites in `frontend/apps`. The values in each table come from `infra/docker/compose.yaml`, `deploy/envs/local/network-policy/calls.yaml`, the service's `application.yml` and its code. `make verify` compares the host port, the memory limit and the HTTP calls of every table with those files.

## membership-service

Members of the customer site: sign up, look-up, edits and the customer tokens.

| | |
| --- | --- |
| Host port | `8081` |
| Memory limit | `512m` |
| Database | PostgreSQL `membership`, table `membership` |
| Calls | none |
| Called by | `banking-service`, `money-service`, `remittance-service`, `payment-service`, `money-aggregation-service`, `money-query-service`, `frontend-web`, `frontend-admin` |
| Kafka | Produces `jaypay.logging.out.stdout`, one record for each controller call |
| Axon | none |
| Other stores | Vault, KV version 2 mount `kv-v1/data/encrypt`, secret `dbkey`, field `key`, read once at startup |
| Configuration | `KAFKA_BOOTSTRAP_SERVERS`, `AXON_AXONSERVER_SERVERS`, `OTEL_EXPORTER_OTLP_ENDPOINT`, `LOG_FORMAT`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_JPA_HIBERNATE_DDL_AUTO`, `SPRING_CLOUD_VAULT_TOKEN`, `SPRING_CLOUD_VAULT_SCHEME`, `SPRING_CLOUD_VAULT_HOST`, `SPRING_CLOUD_VAULT_PORT`, `JWT_SECRET`, `ENCRYPTION_FALLBACK_KEY`. Secrets are `SPRING_DATASOURCE_PASSWORD`, `SPRING_CLOUD_VAULT_TOKEN`, `JWT_SECRET` and `ENCRYPTION_FALLBACK_KEY`. |

The other services call `GET /memberships/{membershipId}` to check that a member exists and is valid. money-query-service uses the same call to read the member's address. money-aggregation-service calls `GET /memberships/address/{addressName}` to list the members at one address.

### What it does

- Sign-up stores a member with a name, an email, an address and a corporate flag. A new member is always valid.
- The email is encrypted with AES-GCM before it is stored and decrypted on every read. The key comes from Vault. When Vault does not answer at startup, the service logs a warning and uses `ENCRYPTION_FALLBACK_KEY`.
- Login takes a member id and returns an access token and a refresh token, both HS512 JWTs signed with `JWT_SECRET`. The access token lives 20 seconds and the refresh token 60 seconds. The service stores the refresh token on the member.
- An edit replaces the name, email, address, valid flag and corporate flag, and clears the stored refresh token.

### Endpoints

- `POST /memberships` signs a member up.
- `GET /memberships/{membershipId}` returns one member.
- `GET /memberships/address/{addressName}` returns the members at one address.
- `PUT /memberships/{membershipId}` edits a member.
- `POST /memberships/login` issues the two tokens for a valid member.
- `POST /memberships/token-validate` answers whether a token has a valid signature and has not expired.
- `POST /memberships/refresh-token` issues a new access token for the stored refresh token.
- `POST /memberships/auth` returns the member that a token belongs to.

[docs/api-contract.md](api-contract.md) lists every endpoint of every service.

### Known limitations

- Login takes a member id only, with no password or other credential.
- `PUT /memberships/{membershipId}` edits the member named by `membershipId` in the request body and ignores the id in the path.
- `POST /memberships/auth` returns a member only when the token in the request equals the stored refresh token, so an access token never matches.
- Login answers 200 with an empty body for a member that is not valid.

## banking-service

Registered bank accounts and firm banking requests against a fake bank.

| | |
| --- | --- |
| Host port | `8082` |
| Memory limit | `512m` |
| Database | PostgreSQL `banking`, tables `registered_bank_account` and `request_firm_banking` |
| Calls | `membership-service` |
| Called by | `money-service`, `remittance-service`, `settlement-service`, `frontend-web` |
| Kafka | Produces `jaypay.logging.out.stdout`, one record for each controller call |
| Axon | Aggregates `RegisteredBankAccountAggregate` and `FirmBankingRequestAggregate`. Command handlers for `CreateRegisteredBankAccountCommand`, `CheckRegisteredBankAccountCommand`, `CreateFirmBankingRequestCommand`, `RequestFirmBankingCommand`, `RollbackFirmBankingRequestCommand` and `UpdateFirmBankingRequestCommand`. Applies `CreateRegisteredBankAccountEvent`, `CheckedRegisteredBankAccountEvent`, `FirmBankingRequestCreatedEvent`, `RequestFirmBankingFinishedEvent`, `RollbackFirmBankingFinishedEvent` and `FirmBankingRequestUpdatedEvent`. |
| Other stores | none |
| Configuration | `KAFKA_BOOTSTRAP_SERVERS`, `AXON_AXONSERVER_SERVERS`, `OTEL_EXPORTER_OTLP_ENDPOINT`, `LOG_FORMAT`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_JPA_HIBERNATE_DDL_AUTO`, `SERVICE_MEMBERSHIP_URL`. The secret is `SPRING_DATASOURCE_PASSWORD`. |

banking-service calls membership-service for the member check before it registers an account.

### What it does

- Registering an account checks the member, asks the fake bank whether the account exists, creates a `RegisteredBankAccountAggregate` through Axon, and stores the account with the aggregate id. The `-eda` variant skips the two checks and stores the account when the command completes.
- A firm banking request asks the fake bank to move money from one account to another and stores the request with its status. The status is `1` when the bank answers success and `2` otherwise.
- In the money top-up saga, `CheckRegisteredBankAccountCommand` checks the account and answers with `CheckedRegisteredBankAccountEvent`. `RequestFirmBankingCommand` records a transfer to the company account `jaypay-company` and answers with `RequestFirmBankingFinishedEvent`. `RollbackFirmBankingRequestCommand` sends the amount back and answers with `RollbackFirmBankingFinishedEvent`. [docs/architecture.md](architecture.md) shows the whole saga.
- `BankAccountAdapter` is the fake bank. It reports every account as valid and every transfer as a success.

### Endpoints

- `POST /banking/account/register` registers a bank account for a member.
- `POST /banking/account/register-eda` registers a bank account through an Axon command and returns no body.
- `GET /banking/account/{membershipId}` returns the registered account of a member.
- `POST /banking/info` asks the fake bank whether an account is valid.
- `POST /banking/firmbanking/request` runs a firm banking transfer and returns the stored request.
- `POST /banking/firmbanking/request-eda` runs a firm banking transfer through an Axon command and returns no body.
- `PUT /banking/firmbanking/update-eda` changes the status of a firm banking aggregate and of its stored request.

[docs/api-contract.md](api-contract.md) lists every endpoint of every service.

### Known limitations

- The fake bank accepts every account and every transfer.
- `POST /banking/account/register` throws a `NullPointerException` when the member is not valid, because the membership adapter returns no status for such a member.
- `GET /banking/account/{membershipId}` throws `NoSuchElementException` when the member has no registered account.

## money-service

Wallet balances and the three ways to top a wallet up.

| | |
| --- | --- |
| Host port | `8083` |
| Memory limit | `512m` |
| Database | PostgreSQL `money`, tables `member_money` and `money_changing_request` |
| Calls | `membership-service`, `banking-service` |
| Called by | `remittance-service`, `money-aggregation-service`, `frontend-web` |
| Kafka | Produces `jaypay.task.topic`. Consumes `jaypay.task.result.topic` in the group `my-group`. Produces `jaypay.logging.out.stdout`, one record for each controller call and one for each task result. |
| Axon | Aggregate `MemberMoneyAggregate`, with command handlers for `MemberMoneyCreatedCommand` and `RechargingMoneyRequestCreateCommand`, applying `MemberMoneyCreatedEvent` and `RechargingRequestCreatedEvent`. Saga `MoneyRechargeSaga`, which handles `RechargingRequestCreatedEvent`, `CheckedRegisteredBankAccountEvent`, `RequestFirmBankingFinishedEvent` and `RollbackFirmBankingFinishedEvent`, and sends `CheckRegisteredBankAccountCommand`, `RequestFirmBankingCommand` and `RollbackFirmBankingRequestCommand`. |
| Other stores | none |
| Configuration | `KAFKA_BOOTSTRAP_SERVERS`, `AXON_AXONSERVER_SERVERS`, `OTEL_EXPORTER_OTLP_ENDPOINT`, `LOG_FORMAT`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_JPA_HIBERNATE_DDL_AUTO`, `TASK_TOPIC`, `TASK_RESULT_TOPIC`, `SERVICE_MEMBERSHIP_URL`, `SERVICE_BANKING_URL`. The secret is `SPRING_DATASOURCE_PASSWORD`. |

money-service calls membership-service for the member check of a synchronous top-up. It calls `GET /banking/account/{membershipId}` on banking-service to find the registered account before a saga top-up.

### What it does

- `POST /money/increase` checks the member, adds the amount to the balance, and records a `money_changing_request` row with the status `1` for success or `2` for failure. A member without a `member_money` row gets one.
- `POST /money/increase-async` writes a task with two subtasks to `jaypay.task.topic` and blocks the request until the result arrives on `jaypay.task.result.topic`. When every subtask is done, it adds the amount and answers.
- `POST /money/increase-eda` sends `RechargingMoneyRequestCreateCommand` and answers at once. The saga then checks the bank account through banking-service, requests the firm banking, and adds the amount to the balance. When the balance update fails, it sends `RollbackFirmBankingRequestCommand`.
- `POST /money/decrease-eda` runs the same saga with the negative amount.
- `POST /money/create-member-money` creates a `MemberMoneyAggregate` and a wallet with the balance 0.
- `POST /money/member-money` returns the wallets of a list of member ids. remittance-service and money-aggregation-service use it to read balances.

### Endpoints

- `POST /money/increase` tops a wallet up synchronously.
- `POST /money/increase-async` tops a wallet up through the Kafka task.
- `POST /money/increase-eda` tops a wallet up through the Axon saga and returns no body.
- `POST /money/decrease` does nothing and returns no body.
- `POST /money/decrease-eda` lowers a wallet through the Axon saga and returns no body.
- `POST /money/create-member-money` creates a wallet.
- `POST /money/member-money` returns the wallets of the given members.

[docs/api-contract.md](api-contract.md) lists every endpoint of every service.

### Known limitations

- `POST /money/decrease` logs the request and changes nothing.
- `POST /money/increase` accepts a negative amount and does not check that the balance stays at or above 0.
- `POST /money/increase-async` waits for the task result with no timeout. A failed task returns an empty body.
- The task result goes to an in-memory latch, so the request returns only when the same money-service instance reads the result.

## remittance-service

Sends money from one member to another member or to a bank account.

| | |
| --- | --- |
| Host port | `8084` |
| Memory limit | `512m` |
| Database | PostgreSQL `remittance`, table `request_remittance` |
| Calls | `membership-service`, `banking-service`, `money-service` |
| Called by | `frontend-web` |
| Kafka | Produces `jaypay.logging.out.stdout`, one record for each controller call |
| Axon | none |
| Other stores | none |
| Configuration | `KAFKA_BOOTSTRAP_SERVERS`, `AXON_AXONSERVER_SERVERS`, `OTEL_EXPORTER_OTLP_ENDPOINT`, `LOG_FORMAT`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_JPA_HIBERNATE_DDL_AUTO`, `SERVICE_MEMBERSHIP_URL`, `SERVICE_BANKING_URL`, `SERVICE_MONEY_URL`. The secret is `SPRING_DATASOURCE_PASSWORD`. |

remittance-service calls membership-service for the member check of the sender. It calls `POST /money/member-money` and `POST /money/increase` on money-service, and `POST /banking/info` and `POST /banking/firmbanking/request` on banking-service.

### What it does

`POST /remittance/request` stores a `request_remittance` row first. Then it runs these steps:

1. It checks that the sender is a valid member.
2. It reads the sender's balance. When the balance is lower than the amount, it tops the wallet up through `POST /money/increase` by the missing amount, rounded up to a multiple of 10000.
3. For `remittanceType` `0`, it lowers the sender's wallet and raises the receiver's wallet, both through `POST /money/increase`.
4. For `remittanceType` `1`, it checks the target account through `POST /banking/info` and sends a firm banking transfer from the company account `jaypay-company` to the target account.
5. It sets the status of the row to `success` and returns it.

`make smoke` sends 5000 from one member to another and checks both balances.

### Endpoints

- `POST /remittance/request` sends money to a member or to a bank account.
- `GET /remittance/{membershipId}` is meant to return the history of a member.

[docs/api-contract.md](api-contract.md) lists every endpoint of every service.

### Known limitations

- `GET /remittance/{membershipId}` always returns an empty body. Neither `FindRemittanceService` nor the persistence adapter reads the table.
- A remittance to a bank account does not lower the sender's wallet.
- A failed step returns an empty body and leaves the row without a status. Nothing undoes a wallet change that came before the failure.
- In the returned remittance, `remittanceRequestId` holds the sender's member id instead of the row id.

## payment-service

Records payments to merchants and tracks which ones are settled.

| | |
| --- | --- |
| Host port | `8085` |
| Memory limit | `512m` |
| Database | PostgreSQL `payment`, table `payment_request` |
| Calls | `membership-service` |
| Called by | `settlement-service`, `frontend-web`, `frontend-admin` |
| Kafka | Produces `jaypay.logging.out.stdout`, one record for each controller call |
| Axon | none |
| Other stores | none |
| Configuration | `KAFKA_BOOTSTRAP_SERVERS`, `AXON_AXONSERVER_SERVERS`, `OTEL_EXPORTER_OTLP_ENDPOINT`, `LOG_FORMAT`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_JPA_HIBERNATE_DDL_AUTO`, `SERVICE_MEMBERSHIP_URL`. The secret is `SPRING_DATASOURCE_PASSWORD`. |

payment-service calls membership-service for the member check of the paying member.

### What it does

- A payment names the paying member, the price, the franchise id and the franchise fee rate. payment-service checks the member and stores the payment with the status `0`.
- The list of unsettled payments returns every payment with the status `0`.
- Finishing the settlement of a payment sets its status to `2`. settlement-service calls it for every payment it settles, and the admin console calls it from **Mark settled**.

### Endpoints

- `POST /payment/request` records a payment. It reads form fields or query parameters, not a JSON body.
- `GET /payment/normal-status` returns the unsettled payments.
- `POST /payment/finish-settlement` marks one payment as settled.

[docs/api-contract.md](api-contract.md) lists every endpoint of every service.

### Known limitations

- A payment does not lower the wallet of the paying member.
- A payment for a member that is not valid returns an empty body.

## money-aggregation-service

Sums the wallet balances of the members at one address.

| | |
| --- | --- |
| Host port | `8086` |
| Memory limit | `512m` |
| Database | PostgreSQL `money_aggregation`, no JPA entities |
| Calls | `membership-service`, `money-service` |
| Called by | `frontend-admin` |
| Kafka | Produces `jaypay.logging.out.stdout`, one record for each controller call |
| Axon | none |
| Other stores | none |
| Configuration | `KAFKA_BOOTSTRAP_SERVERS`, `AXON_AXONSERVER_SERVERS`, `OTEL_EXPORTER_OTLP_ENDPOINT`, `LOG_FORMAT`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_JPA_HIBERNATE_DDL_AUTO`, `SERVICE_MEMBERSHIP_URL`, `SERVICE_MONEY_URL`. The secret is `SPRING_DATASOURCE_PASSWORD`. |

money-aggregation-service calls membership-service to list the members at an address and money-service to read their balances.

### What it does

The service reads the member ids at the address from `GET /memberships/address/{addressName}`. It asks `POST /money/member-money` for their wallets in batches of at most 100 ids and returns the sum of the balances. The admin console shows this sum next to the one from money-query-service.

### Endpoints

- `POST /money/aggregation/get-money-sum-by-address` returns the sum of the balances at one address.

[docs/api-contract.md](api-contract.md) lists every endpoint of every service.

## money-query-service

Read model of the money that saga top-ups added per address, kept in DynamoDB.

| | |
| --- | --- |
| Host port | `8087` |
| Memory limit | `512m` |
| Database | PostgreSQL `money_query`, no JPA entities |
| Calls | `membership-service` |
| Called by | `frontend-admin` |
| Kafka | Produces `jaypay.logging.out.stdout`, one record for each controller call |
| Axon | Event handler `MoneyIncreaseEventHandler` for `RequestFirmBankingFinishedEvent`, in the processing group `com.jaypay.money.query.adapter.axon`. No query handler. |
| Other stores | DynamoDB table named by `DYNAMODB_TABLE_NAME`, `MoneyIncreaseEventByRegion` in Compose, defined in `infra/terraform/modules/dynamodb-money-sum/table.json` |
| Configuration | `KAFKA_BOOTSTRAP_SERVERS`, `AXON_AXONSERVER_SERVERS`, `OTEL_EXPORTER_OTLP_ENDPOINT`, `LOG_FORMAT`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_JPA_HIBERNATE_DDL_AUTO`, `SERVICE_MEMBERSHIP_URL`, `DYNAMODB_TABLE_NAME`, `AWS_REGION`, `AWS_ENDPOINT_URL_DYNAMODB`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`. Secrets are `SPRING_DATASOURCE_PASSWORD` and, against AWS, `AWS_SECRET_ACCESS_KEY`. |

money-query-service calls membership-service to read the address of the member in each event.

### What it does

- For each `RequestFirmBankingFinishedEvent`, the service reads the member's address from `GET /memberships/{membershipId}`. It writes one item for the top-up under the key `<address>#<yyyyMMdd>`, and adds the amount to two totals: the day total under `<address>#<yyyyMMdd>#summary` and the overall total under `<address>`. Both totals use the sort key `-1`.
- The HTTP endpoint reads the overall total of an address straight from DynamoDB and returns 0 when there is none.
- `AWS_REGION` defaults to `ap-northeast-2`. Compose points the client at DynamoDB Local through `AWS_ENDPOINT_URL_DYNAMODB`. [infra/terraform/README.md](../infra/terraform/README.md) points it at the AWS table.

### Endpoints

- `GET /money/query/get-money-sum-by-address/{address}` returns the total that saga top-ups added for one address.

[docs/api-contract.md](api-contract.md) lists every endpoint of every service.

### Known limitations

- Only top-ups through the Axon saga reach the read model. `POST /money/increase` and `POST /money/increase-async` publish no event, so the total can differ from the sum that money-aggregation-service returns.
- The handler adds the amount of every `RequestFirmBankingFinishedEvent`, whatever status the event carries.
- Two top-ups of the same amount on the same day at the same address write the same per-top-up item, so the second overwrites the first. The totals still count both.

## settlement-service

Spring Batch job that pays merchants for their unsettled payments every 30 seconds.

| | |
| --- | --- |
| Host port | `8088` |
| Memory limit | `512m` |
| Database | PostgreSQL `settlement`, the Spring Batch tables, which Spring Batch creates because `SPRING_BATCH_JDBC_INITIALIZE_SCHEMA` defaults to `always` |
| Calls | `banking-service`, `payment-service` |
| Called by | none |
| Kafka | none |
| Axon | none |
| Other stores | none |
| Configuration | `KAFKA_BOOTSTRAP_SERVERS`, `AXON_AXONSERVER_SERVERS`, `OTEL_EXPORTER_OTLP_ENDPOINT`, `LOG_FORMAT`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_JPA_HIBERNATE_DDL_AUTO`, `SPRING_BATCH_JOB_ENABLED`, `SPRING_BATCH_JDBC_INITIALIZE_SCHEMA`, `SERVICE_BANKING_URL`, `SERVICE_PAYMENT_URL`. The secret is `SPRING_DATASOURCE_PASSWORD`. |

settlement-service calls payment-service for the unsettled payments and to finish each settlement. It calls banking-service for the merchant account and the firm banking transfer.

### What it does

`SettlementBatchScheduler` starts the job `settlement` every 30 seconds. Spring Boot does not start it on its own, because `SPRING_BATCH_JOB_ENABLED` defaults to `false`. The one step runs `SettlementTasklet`:

1. It reads the unsettled payments from `GET /payment/normal-status`.
2. It groups them by franchise id and reads each franchise's account from `GET /banking/account/{franchiseId}`. The franchise id must be the id of a member with a registered bank account.
3. It sends one firm banking request per franchise, from the account `jaypay` `111-222-333`.
4. It calls `POST /payment/finish-settlement` for every payment.

The service has no HTTP endpoints of its own. The host port serves Actuator.

### Known limitations

- The amount for a payment is `(100 - fee rate) * price * 100`. A payment of 1000 at a fee rate of 10 settles 9000000, and `SettlementServiceTest` expects that number.
- The job marks every payment settled without reading the answer of the firm banking request.

## auth-service

Staff accounts of the admin console, staff sign-in, and the decision on every admin console request.

| | |
| --- | --- |
| Host port | `8089` |
| Memory limit | `512m` |
| Database | PostgreSQL `auth`, table `staff_account` |
| Calls | none |
| Called by | `frontend-admin` |
| Kafka | none |
| Axon | none |
| Other stores | none |
| Configuration | `KAFKA_BOOTSTRAP_SERVERS`, `AXON_AXONSERVER_SERVERS`, `OTEL_EXPORTER_OTLP_ENDPOINT`, `LOG_FORMAT`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_JPA_HIBERNATE_DDL_AUTO`, `STAFF_JWT_SECRET`, `STAFF_ADMIN_USERNAME`, `STAFF_ADMIN_PASSWORD`, `STAFF_OPERATOR_USERNAME`, `STAFF_OPERATOR_PASSWORD`, `STAFF_VIEWER_USERNAME`, `STAFF_VIEWER_PASSWORD`. Secrets are `SPRING_DATASOURCE_PASSWORD`, `STAFF_JWT_SECRET` and the three `STAFF_<ROLE>_PASSWORD` keys. |

frontend-admin calls auth-service twice. nginx sends an `auth_request` to `/authorize` followed by the original path before it forwards an `/api/` request, and it proxies `/api/auth/staff/` for sign-in and account management.

### What it does

- At startup, `DefaultStaffAccounts` creates an admin, an operator and a viewer from the `STAFF_<ROLE>_USERNAME` and `STAFF_<ROLE>_PASSWORD` keys, unless an account with that username exists.
- Sign-in checks the password and returns an HS256 JWT signed with `STAFF_JWT_SECRET`, with the issuer `jaypay-auth-service`, the audience `jaypay-admin-console` and a lifetime of 8 hours. The service refuses to start when the secret is shorter than 32 bytes.
- `StaffSecurityConfig` holds the decision table. `/authorize/**` answers 204 when the table lets the role make the original request, 401 without a valid token, and 403 otherwise. The roles form the hierarchy viewer, operator, admin.
- Each check loads the account again, so a deactivation or a role change applies to tokens issued before it.
- A username has 3 to 64 lowercase letters, digits, dots, hyphens or underscores. A password has at least 12 characters and at most 72 bytes, upper and lower case letters, a digit and a symbol. It must not contain the username or match a list of common passwords. `PasswordPolicy` holds these rules.
- An admin can create accounts and change the role, the active flag and the MFA flag of another account. Nobody can change their own role or deactivate themselves.

[docs/architecture.md](architecture.md) shows the admin console request step by step.

### Endpoints

- `POST /staff/sign-in` signs a staff account in.
- `GET /staff/me` returns the signed-in account.
- `PUT /staff/me/password` changes the password of the signed-in account.
- `GET /staff/accounts` lists the staff accounts. Admin only.
- `POST /staff/accounts` creates a staff account. Admin only.
- `PUT /staff/accounts/{username}` changes the role, active flag and MFA flag of an account. Admin only.
- `/authorize/**`, any method, is the decision for nginx `auth_request`.

[docs/api-contract.md](api-contract.md) lists every endpoint of every service.

### Known limitations

- The MFA flag is stored and shown, but sign-in does not use it.
- Nothing limits how often someone may try a password.
- An admin cannot reset another account's password.

## task-consumer

Kafka consumer that completes the top-up tasks of money-service.

| | |
| --- | --- |
| Host port | none |
| Memory limit | `384m` |
| Database | none |
| Calls | none |
| Called by | none |
| Kafka | Consumes `jaypay.task.topic` in the group `my-group`. Produces `jaypay.task.result.topic`. |
| Axon | none |
| Other stores | none |
| Configuration | `KAFKA_BOOTSTRAP_SERVERS`, `AXON_AXONSERVER_SERVERS`, `OTEL_EXPORTER_OTLP_ENDPOINT`, `LOG_FORMAT`, `TASK_TOPIC`, `TASK_RESULT_TOPIC` |

### What it does

For each task, task-consumer sets the status of every subtask to `success` and writes the task to `jaypay.task.result.topic` under the same key. It checks neither the member nor the bank account that the subtasks name.

## logging-consumer

Kafka consumer that writes every record of the logging topic to its own log.

| | |
| --- | --- |
| Host port | none |
| Memory limit | `384m` |
| Database | none |
| Calls | none |
| Called by | none |
| Kafka | Consumes `jaypay.logging.out.stdout` in the group `my-group` |
| Axon | none |
| Other stores | none |
| Configuration | `KAFKA_BOOTSTRAP_SERVERS`, `AXON_AXONSERVER_SERVERS`, `OTEL_EXPORTER_OTLP_ENDPOINT`, `LOG_FORMAT`. The topic is the key `logging.topic` in `backend/libs/common/src/main/resources/jaypay-common.yml`. |

### What it does

The record value is logged at level INFO, so it reaches ClickHouse like any other log line. The records come from `LoggingAspect` in `backend/libs/common`, which writes one record before each method call on a web controller. The aspect runs in every service that scans `com.jaypay.common` and has web controllers: membership-service, banking-service, money-service, remittance-service, payment-service, money-aggregation-service and money-query-service. money-service also writes one record for each task result.

## frontend-web

The customer site, served by nginx, with a proxy to five services.

| | |
| --- | --- |
| Host port | `8090` |
| Memory limit | `64m` |
| Database | none |
| Calls | `membership-service`, `banking-service`, `money-service`, `remittance-service`, `payment-service` |
| Called by | the browser, through the nginx server on port 8080 of the container |
| Kafka | none |
| Axon | none |
| Other stores | The session in the browser's `localStorage` under `jaypay.session` |
| Configuration | `JAYPAY_MEMBERSHIP_URL`, `JAYPAY_BANKING_URL`, `JAYPAY_MONEY_URL`, `JAYPAY_REMITTANCE_URL`, `JAYPAY_PAYMENT_URL`, `NGINX_ENTRYPOINT_LOCAL_RESOLVERS` |

### What it does

`frontend/apps/web/nginx.conf.template` proxies `/api/membership/`, `/api/banking/`, `/api/money/`, `/api/remittance/` and `/api/payment/` to the matching `JAYPAY_*_URL`, with `/api/<service>` stripped from the path. Any other `/api/` path answers 404, and every other path serves the single-page app. `frontend/Dockerfile` sets the `JAYPAY_*_URL` defaults to the Compose service names. `deploy/envs/local/frontend/frontend-web.yaml` sets them for the local cluster.

The site has these pages:

- `/sign-up` signs a member up and signs the member in.
- `/sign-in` signs a member in by member id.
- `/` shows the wallet balance and tops it up through `POST /money/increase`.
- `/account` shows or registers the bank account.
- `/send` sends money to a member or to a bank account.
- `/pay` pays a merchant.

The site checks the stored access token on load with `POST /memberships/token-validate` and renews it with `POST /memberships/refresh-token`.

### Known limitations

- nginx forwards every method on the five paths without checking a token, so the site also forwards member edits and `POST /payment/finish-settlement`.

## frontend-admin

The admin console, served by nginx, with a proxy to five services behind the auth-service gate.

| | |
| --- | --- |
| Host port | `8091` |
| Memory limit | `64m` |
| Database | none |
| Calls | `auth-service`, `membership-service`, `payment-service`, `money-aggregation-service`, `money-query-service` |
| Called by | the browser, through the nginx server on port 8080 of the container |
| Kafka | none |
| Axon | none |
| Other stores | The staff session in the browser's `localStorage` under `jaypay.admin.session` |
| Configuration | `JAYPAY_AUTH_URL`, `JAYPAY_MEMBERSHIP_URL`, `JAYPAY_PAYMENT_URL`, `JAYPAY_MONEY_AGGREGATION_URL`, `JAYPAY_MONEY_QUERY_URL`, `NGINX_ENTRYPOINT_LOCAL_RESOLVERS` |

### What it does

`frontend/apps/admin/nginx.conf.template` proxies `/api/membership/`, `/api/payment/`, `/api/money-aggregation/` and `/api/money-query/` to the matching `JAYPAY_*_URL`. Before it forwards one of them, it sends an `auth_request` to `/authorize` followed by the original path on auth-service with the method and headers and without the body. A 401 or 403 from auth-service becomes a JSON error for the browser. `/api/auth/staff/` goes to auth-service without the gate, because auth-service checks those requests itself. Any other `/api/` path answers 404. `frontend/Dockerfile` sets the `JAYPAY_*_URL` defaults to the Compose service names. `deploy/envs/local/frontend/frontend-admin.yaml` sets them for the local cluster.

The console has these pages:

- `/sign-in` signs a staff account in.
- `/members` finds members by id or by address.
- `/members/:membershipId` shows a member and lets an operator or admin edit it.
- `/money` shows the sum of balances at an address from money-aggregation-service and the total from money-query-service.
- `/settlement` lists the unsettled payments and lets an operator or admin mark one settled.
- `/staff` manages staff accounts. Admins only.
- `/account` changes the password of the signed-in account.
