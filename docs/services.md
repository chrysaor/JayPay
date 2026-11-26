# Services

This page describes each of the eleven Spring Boot services in `backend/services` and the two nginx sites in `frontend/apps`. The values in each table come from `infra/docker/compose.yaml`, `deploy/envs/local/network-policy/calls.yaml`, the service's `application.yml` and its code. `make verify` compares the host port, the memory limit and the HTTP calls of every table with those files. Every service also listens on the management port 9080, set once in `backend/libs/common/src/main/resources/jaypay-common.yml`, for health, the liveness and readiness probes and the Prometheus metrics. No host port publishes it, and in the cluster only Prometheus reaches it. The host port serves neither Actuator nor the OpenAPI pages, which springdoc serves only under the Spring profile `local`.

## membership-service

Members of the customer site: sign up, look-up, edits and the customer tokens.

| | |
| --- | --- |
| Host port | `8081` |
| Memory limit | `512m` |
| Database | PostgreSQL `membership`, table `membership` |
| Calls | none |
| Called by | `banking-service`, `money-service`, `remittance-service`, `payment-service`, `money-aggregation-service`, `money-query-service`, `frontend-web`, `frontend-admin` |
| Kafka | Produces `jaypay.logging.out.stdout`, one record for each controller call, and `jaypay.flow.events`, one record for each step of a saga top-up |
| Axon | Command handler `CheckMembershipHandler` for `CheckMembershipCommand`, publishing `CheckedMembershipEvent` |
| Other stores | Vault, KV version 2 mount `kv-v1/data/encrypt`, secret `dbkey`, field `key`, read once at startup |
| Configuration | `KAFKA_BOOTSTRAP_SERVERS`, `AXON_AXONSERVER_SERVERS`, `OTEL_EXPORTER_OTLP_ENDPOINT`, `LOG_FORMAT`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_JPA_HIBERNATE_DDL_AUTO`, `SPRING_CLOUD_VAULT_TOKEN`, `SPRING_CLOUD_VAULT_SCHEME`, `SPRING_CLOUD_VAULT_HOST`, `SPRING_CLOUD_VAULT_PORT`, `JWT_SECRET`. Secrets are `SPRING_DATASOURCE_PASSWORD`, `SPRING_CLOUD_VAULT_TOKEN`, which is `VAULT_MEMBERSHIP_TOKEN` and may only read `dbkey`, and `JWT_SECRET`. |

The other services call `GET /memberships/{membershipId}` to check that a member exists and is valid. money-query-service uses the same call to read the member's address. money-aggregation-service calls `GET /memberships/address/{addressName}` to list the members at one address.

### What it does

- Sign-up stores a member with a name, an email, an address and a corporate flag. A new member is always valid.
- The email is encrypted with AES-GCM before it is stored and decrypted on every read. The key comes from Vault. When Vault does not answer, refuses the token or holds no `key` field at startup, the service fails to start.
- Login takes a member id and returns an access token and a refresh token, both HS512 JWTs signed with `JWT_SECRET`. The access token lives 20 seconds and the refresh token 60 seconds. The service stores the refresh token on the member.
- An edit replaces the name, email, address, valid flag and corporate flag, and clears the stored refresh token.
- For the first step of a saga top-up, `CheckMembershipCommand` asks whether a member exists and is valid. The answer is `CheckedMembershipEvent` with `valid` false for a member that does not exist or is not valid.

### Endpoints

- `POST /memberships` signs a member up.
- `GET /memberships/{membershipId}` returns one member, or answers 404 when the id names no member.
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
| Database | PostgreSQL `banking`, tables `registered_bank_account` and `bank_transfer` |
| Calls | `membership-service` |
| Called by | `money-service`, `remittance-service`, `settlement-service`, `frontend-web` |
| Kafka | Produces `jaypay.logging.out.stdout`, one record for each controller call, and `jaypay.flow.events`, one record for each step of a saga top-up |
| Axon | Aggregates `RegisteredBankAccountAggregate` and `FirmBankingRequestAggregate`. Command handlers for `CreateRegisteredBankAccountCommand`, `CheckRegisteredBankAccountCommand`, `RequestFirmBankingCommand` and `RollbackFirmBankingRequestCommand`. Applies `CreateRegisteredBankAccountEvent`, `CheckedRegisteredBankAccountEvent`, `RequestFirmBankingFinishedEvent` and `RollbackFirmBankingFinishedEvent`. |
| Other stores | none |
| Configuration | `KAFKA_BOOTSTRAP_SERVERS`, `AXON_AXONSERVER_SERVERS`, `OTEL_EXPORTER_OTLP_ENDPOINT`, `LOG_FORMAT`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_JPA_HIBERNATE_DDL_AUTO`, `SERVICE_MEMBERSHIP_URL`. The secret is `SPRING_DATASOURCE_PASSWORD`. |

banking-service calls membership-service for the member check before it registers an account.

### What it does

- Registering an account checks the member, asks the fake bank whether the account exists, creates a `RegisteredBankAccountAggregate` through Axon, and stores the account with the aggregate id. The `-eda` variant skips the two checks and stores the account when the command completes.
- A firm banking request moves money from one account to another once per `requestId`. `POST /banking/firmbanking/request` takes `requestId`, `fromBankName`, `fromBankAccountNumber`, `toBankName`, `toBankAccountNumber` and `moneyAmount`. `requestId` is 1 to 64 letters, digits, `-` or `_`, and `moneyAmount` is a whole number from 1 to `banking.amount.max`, 10000000. A bad id, amount or account answers 400 with `INVALID_REQUEST_ID`, `INVALID_AMOUNT` or `INVALID_BANK_ACCOUNT`.
- The first request with an id stores a `bank_transfer` row as `PENDING` through `INSERT ... ON CONFLICT (request_id) DO NOTHING`, locks it, and sends the transfer to the bank under that id. The bank's answer moves the row to `SUCCEEDED` or `FAILED` once. A repeated request with the same content answers with the stored result and sends nothing, and concurrent copies wait for the first. A request that reuses an id for another transfer answers 409 with `REQUEST_ID_REUSED`.
- When the bank does not answer, the row stays `PENDING` and the request answers 503 with `TRANSFER_NOT_CONFIRMED`. Sending the same request again asks the bank again under the same id. This relies on the bank deduplicating a transfer by its request id, the way a firm banking transaction number works. The fake bank keeps that memory in the process.
- The answer carries the request with `transferStatus` `SUCCEEDED` or `FAILED`.
- In the money top-up saga, `CheckRegisteredBankAccountCommand` checks the account and answers with `CheckedRegisteredBankAccountEvent`. `RequestFirmBankingCommand` sends the transfer to the company account `jaypay-company` under the request id `recharge-<rechargingRequestId>` and answers with `RequestFirmBankingFinishedEvent`, whose status is `0` when the bank confirmed it, `1` when the bank refused it and `2` when the bank did not answer. `RollbackFirmBankingRequestCommand` sends the amount back under `recharge-<rechargingRequestId>-refund` only when the charge landed, and first records the charge id as cancelled, so a charge the bank never confirmed cannot land later. It answers with `RollbackFirmBankingFinishedEvent`. [docs/architecture.md](architecture.md) shows the whole saga.
- `BankAccountAdapter` is the fake bank. It reports every account as valid, refuses a transfer from or to an account number that ends in `-refused`, and accepts every other transfer.

### Endpoints

- `POST /banking/account/register` registers a bank account for a member.
- `POST /banking/account/register-eda` registers a bank account through an Axon command and returns no body.
- `GET /banking/account/{membershipId}` returns the registered account of a member.
- `POST /banking/info` asks the fake bank whether an account is valid.
- `POST /banking/firmbanking/request` sends a bank transfer once per request id and returns its result.

[docs/api-contract.md](api-contract.md) lists every endpoint of every service.

### Known limitations

- The fake bank accepts every account and every transfer to an account number that does not end in `-refused`. It forgets the transfers it answered when banking-service restarts.
- A transfer left `PENDING` stays that way until the same request is sent again. Nothing retries it in the background.
- Firm banking requests from before the `bank_transfer` table stay in the old `request_firm_banking` table, which banking-service no longer reads.
- `POST /banking/account/register` throws a `NullPointerException` when the member is not valid, because the membership adapter returns no status for such a member.
- `GET /banking/account/{membershipId}` throws `NoSuchElementException` when the member has no registered account.

## money-service

Wallet balances and the two ways to top a wallet up.

| | |
| --- | --- |
| Host port | `8083` |
| Memory limit | `512m` |
| Database | PostgreSQL `money`, tables `wallet` and `wallet_change` |
| Calls | `membership-service`, `banking-service` |
| Called by | `remittance-service`, `payment-service`, `money-aggregation-service`, `frontend-web` |
| Kafka | Produces `jaypay.logging.out.stdout`, one record for each controller call, and `jaypay.flow.events`, one record for each step of a saga top-up |
| Axon | Aggregate `MemberMoneyAggregate`, with command handlers for `MemberMoneyCreatedCommand` and `RechargingMoneyRequestCreateCommand`, applying `MemberMoneyCreatedEvent` and `RechargingRequestCreatedEvent`. Saga `MoneyRechargeSaga`, which handles `RechargingRequestCreatedEvent`, `CheckedMembershipEvent`, `CheckedRegisteredBankAccountEvent`, `RequestFirmBankingFinishedEvent` and `RollbackFirmBankingFinishedEvent`, and sends `CheckMembershipCommand`, `CheckRegisteredBankAccountCommand`, `RequestFirmBankingCommand` and `RollbackFirmBankingRequestCommand`. |
| Other stores | none |
| Configuration | `KAFKA_BOOTSTRAP_SERVERS`, `AXON_AXONSERVER_SERVERS`, `OTEL_EXPORTER_OTLP_ENDPOINT`, `LOG_FORMAT`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_JPA_HIBERNATE_DDL_AUTO`, `SERVICE_MEMBERSHIP_URL`, `SERVICE_BANKING_URL`, `MONEY_AMOUNT_MAX`. The secret is `SPRING_DATASOURCE_PASSWORD`. |

money-service calls membership-service for the member check of a synchronous top-up. It calls `GET /banking/account/{membershipId}` on banking-service to find the registered account before a saga top-up.

### What it does

Every change of a balance takes a JSON body with `requestId`, `targetMembershipId` and `amount`.

- `amount` is a whole number from 1 to `money.amount.max`, 10000000 unless `MONEY_AMOUNT_MAX` sets another limit. Any other amount answers 400 with `{"error":"INVALID_AMOUNT"}` and writes nothing.
- `requestId` is 1 to 64 letters, digits, `-` or `_`, chosen by the caller. The first request with an id records a `wallet_change` row under it. A repeated request answers with the result of the first and changes nothing. A request that reuses an id for another member, direction or amount answers 409 with `{"error":"REQUEST_ID_REUSED"}`. A missing or malformed id answers 400 with `{"error":"INVALID_REQUEST_ID"}`.
- `wallet` holds one row per member, a unique `membership_id`, and a `bigint` balance that a check constraint keeps at or above 0. An increase is one `INSERT ... ON CONFLICT DO UPDATE` that adds the amount, so concurrent top-ups all count and a first top-up creates the only row. A decrease is one `UPDATE ... WHERE balance >= amount`, so it fails instead of going below 0.
- The answer carries `moneyChangingRequestId`, the request id, `moneyChangingType` `1` for an increase or `2` for a decrease, `moneyChangingResultStatus` `1` for success, `2` for failure or `0` while a saga top-up is still running, and `amount`.

- `POST /money/increase` checks the member and adds the amount to the balance. A member without a wallet gets one. A member that does not exist or is not valid answers with the status `2`.
- `POST /money/decrease` checks the member and takes the amount from the balance. A balance lower than the amount answers with the status `2` and stays as it was.
- When membership-service does not answer, or answers with neither a member nor 404, `POST /money/increase` and `POST /money/decrease` answer 503 with `{"error":"MEMBERSHIP_NOT_CONFIRMED"}` and record nothing under the request id. Sending the same request again checks the member again and applies the change once.
- `POST /money/increase-eda` records the request as running and sends `RechargingMoneyRequestCreateCommand` with the request id as `rechargingRequestId`, and answers at once with the wallet change. A repeated request id sends nothing and answers with the current status. A member without a registered bank account fails at once with the status `2`.
- The saga first sends `CheckMembershipCommand` to membership-service, then checks the bank account, requests the firm banking, and adds the amount to the balance once, only when the bank confirmed the charge. A member that does not exist or is not valid, an account that fails the check, or a charge the bank refuses ends the wallet change as `2` and moves no money. When membership-service does not answer, or the command cannot reach it, the deadline ends the saga. A saga that is not finished within `money.recharge.deadline`, 60 seconds, ends the wallet change as `2` and sends `RollbackFirmBankingRequestCommand`, which refunds the charge if it landed. When the wallet change is no longer running as the charge lands, the saga rolls the charge back too.
- `POST /money/create-member-money` creates a `MemberMoneyAggregate` and a wallet with the balance 0, or links the aggregate to the wallet that a top-up already created. It waits for the command, so a failure answers as an error. A wallet keeps the first aggregate it was linked to. A saga top-up for a member without an aggregate creates one first.
- `POST /money/member-money` returns the wallets of a list of member ids. money-aggregation-service uses it to read balances.

### Endpoints

- `POST /money/increase` tops a wallet up synchronously.
- `POST /money/increase-eda` tops a wallet up through the Axon saga and returns the wallet change.
- `POST /money/decrease` lowers a wallet synchronously.
- `POST /money/create-member-money` creates a wallet.
- `POST /money/member-money` returns the wallets of the given members.

[docs/api-contract.md](api-contract.md) lists every endpoint of every service.

### Known limitations

- `POST /money/increase` is open to the customer site and tops up any member's wallet without a bank withdrawal. Moving the wallet page to the saga needs the member from the token ([#91](https://github.com/chrysaor/JayPay/issues/91)) first.
- The saga deadline lives in the memory of the money-service instance, so a restart drops it and a saga that was waiting on the bank stays at the status `0`. A rollback the bank does not confirm is logged and not sent again.
- Two concurrent first top-ups of a member without an aggregate can each create an aggregate. The wallet keeps one, and the other stays unused.
- Wallets from before the `wallet` table stay in the old `member_money` table, which money-service no longer reads.

## remittance-service

Sends money from one member's wallet to another member or to a bank account.

| | |
| --- | --- |
| Host port | `8084` |
| Memory limit | `512m` |
| Database | PostgreSQL `remittance`, table `remittance` |
| Calls | `membership-service`, `banking-service`, `money-service` |
| Called by | `frontend-web` |
| Kafka | Produces `jaypay.logging.out.stdout`, one record for each controller call, and `jaypay.flow.events`, one record for each step of a remittance |
| Axon | none |
| Other stores | none |
| Configuration | `KAFKA_BOOTSTRAP_SERVERS`, `AXON_AXONSERVER_SERVERS`, `OTEL_EXPORTER_OTLP_ENDPOINT`, `LOG_FORMAT`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_JPA_HIBERNATE_DDL_AUTO`, `SERVICE_MEMBERSHIP_URL`, `SERVICE_BANKING_URL`, `SERVICE_MONEY_URL`, `REMITTANCE_AMOUNT_MAX`. The secret is `SPRING_DATASOURCE_PASSWORD`. |

remittance-service calls `GET /memberships/{membershipId}` on membership-service to check the sender and the receiving member. It calls `POST /money/decrease` and `POST /money/increase` on money-service to take the amount from the sender, to give it to the receiving member, and to refund it. It calls `POST /banking/info` and `POST /banking/firmbanking/request` on banking-service to check the target account and to send the transfer.

### What it does

`POST /remittance/request` takes a JSON body with `requestId`, `fromMembershipId`, `remittanceType` and `amount`. `remittanceType` `0` sends to the member `toMembershipId`, and `1` sends to the bank account `toBankName` and `toBankAccountNumber`.

- `amount` is a whole number of won from 1 to `remittance.amount.max`, 10000000 unless `REMITTANCE_AMOUNT_MAX` sets another limit, the same limit as money-service. Any other amount answers 400 with `{"error":"INVALID_AMOUNT"}` and writes nothing.
- The sender must be a valid member, or the request answers 400 with `{"error":"INVALID_SENDER"}`. A receiving member must be a valid member other than the sender, or the request answers 400 with `{"error":"INVALID_RECEIVER"}`. A bank account needs a name and a number of 1 to 64 characters that banking-service accepts, or the request answers 400 with `{"error":"INVALID_BANK_ACCOUNT"}`. Another `remittanceType` answers 400 with `{"error":"INVALID_REMITTANCE_TYPE"}`.
- `requestId` is 1 to 64 letters, digits, `-` or `_`, chosen by the caller. The first request with an id checks the sender and the receiver, then stores a `remittance` row with a new UUID as `remittanceId` and the status `REQUESTED` through `INSERT ... ON CONFLICT (request_id) DO NOTHING`, and commits it before any money moves.
- A repeated request with the same id and the same sender, receiver and amount answers with the stored remittance, or carries an unfinished one on from where it stopped. A request that reuses an id for another remittance answers 409 with `{"error":"REQUEST_ID_REUSED"}`.

Every move of money goes through money-service with a request id made from the remittance, so money-service applies it once however often it is repeated:

| Move | money-service call | Request id |
| --- | --- | --- |
| Take the amount from the sender | `POST /money/decrease` | `remittance-<remittanceId>-decrease` |
| Give it to the receiving member | `POST /money/increase` | `remittance-<remittanceId>-increase` |
| Give it back to the sender | `POST /money/increase` | `remittance-<remittanceId>-refund` |

The statuses move only this way, and each move is one `UPDATE ... WHERE remittance_id = ? AND remittance_status = ?`, so of two concurrent copies only one makes it:

| From | To | When |
| --- | --- | --- |
| `REQUESTED` | `DEBITED` | money-service took the amount from the sender |
| `REQUESTED` | `FAILED` | the sender's balance is lower than the amount. No money moved. |
| `DEBITED` | `COMPLETED` | money-service gave the amount to the receiving member |
| `DEBITED` | `REFUNDING` | money-service refused to give the amount to the receiving member |
| `DEBITED` | `TRANSFERRING` | a bank remittance is about to send the firm banking transfer |
| `TRANSFERRING` | `COMPLETED` | the bank reported the transfer as done |
| `TRANSFERRING` | `REFUNDING` | the bank reported the transfer as failed |
| `REFUNDING` | `REFUNDED` | money-service gave the amount back to the sender |

`COMPLETED`, `FAILED` and `REFUNDED` are final and answer 200 with the remittance. When money-service gives no answer, the request answers 503 with `{"error":"REMITTANCE_NOT_CONFIRMED"}` and the remittance stays `REQUESTED`, `DEBITED` or `REFUNDING`. Nothing retries it in the background. Sending the same request again repeats the unanswered call with the same request id and finishes the remittance, so a remittance whose credit failed ends `REFUNDED` with the sender's balance as before.

A bank remittance sends its transfer with the request id `remittance-<remittanceId>-transfer`, after the sender was debited, so the company account never pays out money the sender still holds. banking-service sends a transfer once per request id. When the bank gives no answer, or the service stops between the status change and the call, the remittance stays `TRANSFERRING` and the request answers 503 with `REMITTANCE_NOT_CONFIRMED`. Sending the same request again asks banking-service again under the same id, so the transfer lands once, and a refused transfer ends `REFUNDED`.

- `GET /remittance/{membershipId}` returns the remittances the member sent or received, newest first.

Every answer with a remittance carries `remittanceId`, `requestId`, `fromMembershipId`, `remittanceType`, `toMembershipId`, `toBankName`, `toBankAccountNumber`, `amount` and `remittanceStatus`. `make smoke` sends money between two members, repeats the remittance, reuses its id for another amount, sends more than the balance, a bad amount and a remittance to the sender, sends money to a bank account and repeats it, and checks that the two wallets always hold the same total.

### Endpoints

- `POST /remittance/request` sends money to a member or to a bank account. It reads a JSON body and answers 415 to a form.
- `GET /remittance/{membershipId}` returns the remittances of a member.

[docs/api-contract.md](api-contract.md) lists every endpoint of every service.

### Known limitations

- The sender comes from the request body, not from a token, so a caller can send money from any member's wallet ([#91](https://github.com/chrysaor/JayPay/issues/91)). `GET /remittance/{membershipId}` answers for any member.
- A remittance left `REQUESTED`, `DEBITED`, `TRANSFERRING` or `REFUNDING` after money-service did not answer stays that way until the same request is sent again. A refund that money-service refuses, because the sender is no longer a valid member, keeps the remittance `REFUNDING`.
- remittance-service trusts any caller and calls money-service and banking-service without service-to-service authentication ([#100](https://github.com/chrysaor/JayPay/issues/100)).
- Remittances from before the `remittance` table stay in the old `request_remittance` table, which remittance-service no longer reads.

## payment-service

Takes payments from member wallets to merchants and tracks which ones are settled.

| | |
| --- | --- |
| Host port | `8085` |
| Memory limit | `512m` |
| Database | PostgreSQL `payment`, table `payment` |
| Calls | `membership-service`, `money-service` |
| Called by | `settlement-service`, `frontend-web`, `frontend-admin` |
| Kafka | Produces `jaypay.logging.out.stdout`, one record for each controller call, and `jaypay.flow.events`, one record for each step of a payment |
| Axon | none |
| Other stores | none |
| Configuration | `KAFKA_BOOTSTRAP_SERVERS`, `AXON_AXONSERVER_SERVERS`, `OTEL_EXPORTER_OTLP_ENDPOINT`, `LOG_FORMAT`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_JPA_HIBERNATE_DDL_AUTO`, `SERVICE_MEMBERSHIP_URL`, `SERVICE_MONEY_URL`, `PAYMENT_FEE_RATE_BASIS_POINTS`, `PAYMENT_AMOUNT_MAX`. The secret is `SPRING_DATASOURCE_PASSWORD`. |

payment-service calls `GET /memberships/{membershipId}` on membership-service to check the paying member and the merchant. It calls `POST /money/decrease` on money-service to take the price from the paying member's wallet.

### What it does

`POST /payment/request` takes a JSON body with `requestId`, `requestMembershipId`, `franchiseId` and `requestPrice`.

- `requestPrice` is a whole number of won from 1 to `payment.amount.max`, 10000000 unless `PAYMENT_AMOUNT_MAX` sets another limit, the same limit as money-service. Any other price answers 400 with `{"error":"INVALID_AMOUNT"}` and writes nothing.
- The paying member must be a valid member, or the request answers 400 with `{"error":"INVALID_PAYER"}`. The merchant, `franchiseId`, must be a valid corporate member, or the request answers 400 with `{"error":"INVALID_MERCHANT"}`. payment-service checks both only on the first request with an id, the way remittance-service checks the sender and the receiver.
- The server sets the fee. `payment.fee-rate-basis-points`, 300 or 3% unless `PAYMENT_FEE_RATE_BASIS_POINTS` sets another rate, is stored on the payment. A fee rate in the body is ignored.
- The fee is `floor(requestPrice * basis points / 10000)` won, so any fraction of a won goes to the merchant. The payout is `requestPrice - fee`. Both are stored on the payment when it is taken, and a check constraint keeps `fee + payout = request_price`. `FeeRateTest` pins the rounding at the edges: 33 won at 3% has the fee 0, 34 won has the fee 1, and 10000000 won has the fee 300000.
- `requestId` is 1 to 64 letters, digits, `-` or `_`, chosen by the caller. The first request with an id stores a `payment` row with the status `REQUESTED` through `INSERT ... ON CONFLICT (request_id) DO NOTHING`, and commits it before anything else. Then payment-service calls `POST /money/decrease` with the request id `payment-<paymentId>-decrease`, so money-service takes the price once however often the call is repeated.
- A debit that succeeds moves the payment to `PAID`, and a balance lower than the price moves it to `FAILED`. Both answer 200 with the payment. Each move is one `UPDATE ... WHERE payment_status = 'REQUESTED'`.
- A repeated request with the same id and the same payer, merchant and price answers with the stored payment. A payment that is still `REQUESTED` calls money-service again with the same debit id, even when the payer or the merchant changed since the first request, so a payment whose debit landed ends `PAID` and one whose debit was refused ends `FAILED`. A request that reuses an id for another payment answers 409 with `{"error":"REQUEST_ID_REUSED"}`.
- When money-service gives no answer, the request answers 503 with `{"error":"PAYMENT_NOT_CONFIRMED"}` and the payment stays `REQUESTED` until the same request is sent again.

The statuses move only this way: `REQUESTED` to `PAID` or `FAILED`, and `PAID` to `SETTLED`.

- `GET /payment/normal-status` returns the `PAID` payments, the ones that wait for settlement.
- `POST /payment/finish-settlement` takes `{"paymentId":"<id>"}` and runs one `UPDATE ... SET payment_status = 'SETTLED' WHERE payment_id = ? AND payment_status = 'PAID'`. The call that changes the row answers `{"paymentId":<id>,"paymentStatus":"SETTLED","alreadySettled":false}`. A payment that is already settled answers the same with `"alreadySettled":true` and changes nothing. A `REQUESTED` or `FAILED` payment answers 409 with `{"error":"PAYMENT_NOT_PAID"}`, and an unknown id answers 404 with `{"error":"PAYMENT_NOT_FOUND"}`. settlement-service calls it after the bank confirmed the payout. The admin console has no button for it, and auth-service refuses it to every staff role.

Every answer with a payment carries `paymentId`, `requestId`, `requestMembershipId`, `franchiseId`, `requestPrice`, `feeRateBasisPoints`, `fee`, `payout`, `paymentStatus` and `approvedAt`. `make smoke` pays a merchant, repeats the payment, pays above the balance, checks that no staff role may settle a payment by hand, and checks that the settlement job settles the payment once the bank holds its payout.

### Endpoints

- `POST /payment/request` takes a payment from the paying member's wallet. It reads a JSON body and answers 415 to a form.
- `GET /payment/normal-status` returns the paid payments that wait for settlement.
- `POST /payment/finish-settlement` marks one paid payment as settled. Only settlement-service calls it.

[docs/api-contract.md](api-contract.md) lists every endpoint of every service.

### Known limitations

- The paying member comes from the request body, not from a token, so a caller can pay from any member's wallet ([#91](https://github.com/chrysaor/JayPay/issues/91)).
- Payments from before the `payment` table stay in the old `payment_request` table, which payment-service no longer reads, so settlement-service does not pay them out.
- Nothing returns the money of a `PAID` payment to the wallet. There is no refund or cancellation.

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

- For each `RequestFirmBankingFinishedEvent` with the status `0`, a charge the bank confirmed, the service reads the member's address from `GET /memberships/{membershipId}`. It writes one item for the top-up under the key `<address>#<yyyyMMdd>`, and adds the amount to two totals: the day total under `<address>#<yyyyMMdd>#summary` and the overall total under `<address>`. Both totals use the sort key `-1`.
- The HTTP endpoint reads the overall total of an address straight from DynamoDB and returns 0 when there is none.
- `AWS_REGION` defaults to `ap-northeast-2`. Compose points the client at DynamoDB Local through `AWS_ENDPOINT_URL_DYNAMODB`. [infra/terraform/README.md](../infra/terraform/README.md) points it at the AWS table.

### Endpoints

- `GET /money/query/get-money-sum-by-address/{address}` returns the total that saga top-ups added for one address.

[docs/api-contract.md](api-contract.md) lists every endpoint of every service.

### Known limitations

- Only top-ups through the Axon saga reach the read model. `POST /money/increase` publishes no event, so the total can differ from the sum that money-aggregation-service returns.
- Two top-ups of the same amount on the same day at the same address write the same per-top-up item, so the second overwrites the first. The totals still count both.

## settlement-service

Spring Batch job that pays merchants out for their paid payments every 30 seconds.

| | |
| --- | --- |
| Host port | `8088` |
| Memory limit | `512m` |
| Database | PostgreSQL `settlement`, the table `payout_attempt` and the Spring Batch tables, which Spring Batch creates because `SPRING_BATCH_JDBC_INITIALIZE_SCHEMA` defaults to `always` |
| Calls | `banking-service`, `payment-service` |
| Called by | none |
| Kafka | Produces `jaypay.flow.events`, one record for each step of the settlement of a payment |
| Axon | none |
| Other stores | none |
| Configuration | `KAFKA_BOOTSTRAP_SERVERS`, `AXON_AXONSERVER_SERVERS`, `OTEL_EXPORTER_OTLP_ENDPOINT`, `LOG_FORMAT`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_JPA_HIBERNATE_DDL_AUTO`, `SPRING_BATCH_JOB_ENABLED`, `SPRING_BATCH_JDBC_INITIALIZE_SCHEMA`, `SERVICE_BANKING_URL`, `SERVICE_PAYMENT_URL`. The secret is `SPRING_DATASOURCE_PASSWORD`. |

settlement-service calls payment-service for the paid payments and to finish each settlement. It calls banking-service for the merchant account and the firm banking transfer.

### What it does

`SettlementBatchScheduler` starts the job `settlement` every 30 seconds. Spring Boot does not start it on its own, because `SPRING_BATCH_JOB_ENABLED` defaults to `false`. The one step runs `SettlementTasklet`:

1. It reads the paid payments from `GET /payment/normal-status`.
2. It reads each merchant's account from `GET /banking/account/{franchiseId}`, once per merchant. A payment whose merchant has no registered bank account stays `PAID` for a later run.
3. It sends one firm banking request for that payment's `payout`, from the account `jaypay` `111-222-333`, for the payment's current payout attempt. The first run stores attempt 1 in `payout_attempt` with the merchant's account of that moment, and every later run sends the stored attempt with the stored account again, under the request id `settlement-<paymentId>` for attempt 1 and `settlement-<paymentId>-<attempt>` after it.
4. When the bank refused the stored attempt and the merchant has registered another account since, it stores the next attempt with the new account and sends it in the same run. An unanswered or confirmed attempt is never replaced, even after the merchant changes the account.
5. Only when the bank confirmed the transfer does it call `POST /payment/finish-settlement`. A refused transfer is logged as an error and an unanswered one as a warning, and both leave the payment `PAID`.

The amounts are whole won. payment-service computes the fee and the payout when it takes the payment, so settlement-service does no arithmetic and pays out exactly the stored `payout`. Every run sends the same request id and the same account for an attempt, and banking-service sends a transfer once per request id, so a second run, a run on another replica or a run after an unanswered transfer never pays the same payment twice. A new attempt starts only after banking-service answered that the one before it failed, so at most one attempt of a payment can still land. `SettlementJobTest` lets the bank stay silent for the first run, then runs the settlement four times at once and the job twice more, and checks that each payment lands at the bank once. `SettlementAfterAccountChangeTest` changes the merchants' accounts after a refused payout, a payout whose settlement answer was lost and an unanswered payout, and checks that only the refused one is sent again, to the new account, once.

The service has no HTTP endpoints of its own. Actuator answers on the management port.

### Known limitations

- A payout the bank refuses keeps its payment `PAID` until the merchant registers another account. A refusal for a reason that passes, with the same account, is never sent again.
- An unanswered payout is sent to the account it was first sent to, even after the merchant changes the account, until the bank answers.

## event-flow-service

Read model of the flows that move money: it collects the step records of each saga top-up, remittance, payment and settlement and serves the flows to the admin console.

| | |
| --- | --- |
| Host port | `8093` |
| Memory limit | `512m` |
| Database | PostgreSQL `event_flow`, tables `flow_step` and `flow` |
| Calls | none |
| Called by | `frontend-admin` |
| Kafka | Consumes `jaypay.flow.events` in the group `event-flow-service`. Produces `jaypay.logging.out.stdout`, one record for each controller call |
| Axon | none |
| Other stores | none |
| Configuration | `KAFKA_BOOTSTRAP_SERVERS`, `AXON_AXONSERVER_SERVERS`, `OTEL_EXPORTER_OTLP_ENDPOINT`, `LOG_FORMAT`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_JPA_HIBERNATE_DDL_AUTO`, `FLOW_RETENTION_PERIOD`. The topic is the key `flow.topic` in `backend/libs/common/src/main/resources/jaypay-common.yml`. The secret is `SPRING_DATASOURCE_PASSWORD`. |

### What it does

`FlowEventPublisher` in `backend/libs/common` writes one JSON record to `jaypay.flow.events` for each step, keyed by the flow id, so the steps of one flow stay in order on one partition. A record holds `flowType`, `flowId`, `step`, `service`, `outcome`, `reason`, `occurredAt`, `membershipId` and `amount`, and nothing else: no name, email, address or account number. The flow id of a top-up, a remittance and a payment is its request id, and the flow id of a settlement is `settlement-<paymentId>`, the request id of its payout. The member id of a remittance is the sender, of a payment the payer and of a settlement the merchant. The publisher sends after the step's transaction commits, waits at most two seconds for Kafka and never throws, so a Kafka outage loses flow records but never stops or changes the money.

| Step | Service | Outcome |
| --- | --- | --- |
| `RECHARGE_REQUESTED` | money-service | succeeded, or failed when the member has no registered bank account |
| `MEMBERSHIP_CHECKED` | membership-service | succeeded, or failed for a member that does not exist or is not valid |
| `BANK_ACCOUNT_CHECKED` | banking-service | succeeded, or failed when the bank does not confirm the account |
| `BANK_CHARGED` | banking-service | succeeded, in progress while the bank has not answered, or failed when it refused |
| `WALLET_INCREASED` | money-service | succeeded, which ends the flow |
| `RECHARGE_FAILED` | money-service | failed, with the reason the saga gave up |
| `CHARGE_ROLLED_BACK` | banking-service | succeeded, in progress or failed, for the refund of a charge |

| Remittance step | Outcome |
| --- | --- |
| `REMITTANCE_REQUESTED` | succeeded, or failed when the sender, the receiver or the receiving bank account is not valid |
| `SENDER_DEBITED` | succeeded, or failed when money-service refused to take the amount from the sender's wallet |
| `RECEIVER_CREDITED` | succeeded, which ends a remittance to a member, or failed when money-service refused to add the amount |
| `BANK_TRANSFERRED` | succeeded, which ends a remittance to a bank account, or failed when the bank refused the transfer |
| `SENDER_REFUNDED` | succeeded once the amount is back in the sender's wallet |

| Payment step | Outcome |
| --- | --- |
| `PAYMENT_REQUESTED` | succeeded, or failed when the payer or the merchant is not valid |
| `PAYER_DEBITED` | succeeded, which ends the payment, or failed when money-service refused to take the price |

| Settlement step | Outcome |
| --- | --- |
| `SETTLEMENT_STARTED` | succeeded, or in progress while the merchant has no registered bank account |
| `MERCHANT_PAID_OUT` | succeeded, or failed when the bank refused the payout |
| `PAYMENT_SETTLED` | succeeded once payment-service marked the payment settled, which ends the flow |

remittance-service publishes every remittance step, payment-service every payment step and settlement-service every settlement step. A step whose call got no answer publishes nothing until the retry gets one. settlement-service runs each job in one transaction, so it sends that run's records when the run commits.

The service stores each step once per flow and step. A record that Kafka delivers again keeps the first copy, and a record that is not a well formed step is logged and skipped. A record whose step belongs to another type of flow is skipped too. After each new step it recomputes the flow: failed once any step failed, succeeded once its last step succeeded (`WALLET_INCREASED`, `RECEIVER_CREDITED`, `BANK_TRANSFERRED`, `PAYER_DEBITED` or `PAYMENT_SETTLED`), in progress otherwise.

Every hour it deletes each flow, with its steps, whose newest step is older than `flow.retention.period`. The period is `FLOW_RETENTION_PERIOD` and defaults to `30d`. The test profile turns the schedule off with `flow.retention.schedule.enabled: false`.

### Endpoints

- `GET /flows` returns the 50 newest flows by start time, each with its status, member id, amount, start and last time, last step, failed step and step count. `?flowType=RECHARGE`, `REMITTANCE`, `PAYMENT` or `SETTLEMENT` keeps one type, and any other type answers 400 `INVALID_FLOW_TYPE`. `?status=IN_PROGRESS`, `SUCCEEDED` or `FAILED` keeps one status, and any other status answers 400 `INVALID_STATUS`. The two filters combine.
- `GET /flows/{flowType}/{flowId}` returns one flow with its steps in the order they happened, or 404 `FLOW_NOT_FOUND`.

The admin console reaches both through `/api/event-flow/` for every staff role.

[docs/api-contract.md](api-contract.md) lists every endpoint of every service.

### Known limitations

- `POST /money/increase` and the settlement an admin finishes by hand with `POST /payment/finish-settlement` publish no steps.
- A request that is refused and then sent again under the same request id keeps the first, failed copy of its first step.
- A flow whose records were lost while Kafka was down shows fewer steps or stays in progress.
- A settlement waiting for a merchant account sends its first step again on every run until the account appears. The service keeps the first copy, so the flow shows when the wait began.

## auth-service

The decision on every admin console request, made from the staff token Keycloak issued. Keycloak holds the staff accounts and signs them in.

| | |
| --- | --- |
| Host port | `8089` |
| Memory limit | `512m` |
| Database | none |
| Calls | none |
| Called by | `frontend-admin` |
| Kafka | none |
| Axon | none |
| Other stores | none |
| Configuration | `KAFKA_BOOTSTRAP_SERVERS`, `AXON_AXONSERVER_SERVERS`, `OTEL_EXPORTER_OTLP_ENDPOINT`, `LOG_FORMAT`, `STAFF_ISSUER_URI`, `STAFF_JWK_SET_URI`. No secrets. |

nginx in frontend-admin sends an `auth_request` to `/authorize` followed by the original path before it forwards an `/api/` request. auth-service fetches the public keys of the realm `staff` from Keycloak at `STAFF_JWK_SET_URI` and caches them.

### What it does

- `/authorize/**` answers 204 when the decision table in `StaffSecurityConfig` lets the token's role make the original request, 401 without a valid token, and 403 otherwise.
- A valid token is signed by a key of the realm, has the issuer `STAFF_ISSUER_URI` and the audience `jaypay-admin-console`, and has not expired, with 60 seconds of clock skew.
- The realm roles `viewer`, `operator` and `admin` in `realm_access.roles` are the roles. They form the hierarchy viewer, operator, admin, and the highest one counts.
- A disabled account or a removed role stops new sign-ins and refreshes at once. An access token issued before keeps working until it expires, at most 3 minutes later.

[docs/architecture.md](architecture.md) shows the sign-in and an admin console request step by step.

### Endpoints

- `/authorize/**`, any method, is the decision for nginx `auth_request`.

[docs/api-contract.md](api-contract.md) lists every endpoint of every service.

### Known limitations

- auth-service does not ask Keycloak about each token, so revoking an account takes up to the lifetime of its access token plus the clock skew.

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

The record value is logged at level INFO, so it reaches ClickHouse like any other log line. The records come from `LoggingAspect` in `backend/libs/common`, which writes one record before each method call on a web controller. The aspect runs in every service that scans `com.jaypay.common` and has web controllers: membership-service, banking-service, money-service, remittance-service, payment-service, money-aggregation-service, money-query-service and event-flow-service.

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

`frontend/apps/web/nginx.conf.template` forwards only the method and path pairs that the screens send, each to a fixed path on the matching `JAYPAY_*_URL` with `/api/<service>` stripped. nginx matches the path after it decodes and normalizes it, a member id must be digits, and the forwarded path is built from the table, not copied from the request. Any other `/api/` request answers 404, internal endpoints, Actuator and the OpenAPI pages included, and every other path serves the single-page app. `src/api/nginx.test.ts` runs every API call of the screens and fails when one has no entry in the table, when an entry forwards to another path, or when an entry is not used. `frontend/Dockerfile` sets the `JAYPAY_*_URL` defaults to the Compose service names. `deploy/envs/local/frontend/frontend-web.yaml` sets them for the local cluster.

The site has these pages:

- `/sign-up` signs a member up and signs the member in.
- `/sign-in` signs a member in by member id.
- `/` shows the wallet balance and tops it up through `POST /money/increase`. It keeps the request id of a failed top-up and sends it again when the member retries the same amount, so a retry never charges twice.
- `/account` shows or registers the bank account.
- `/send` sends money to a member or to a bank account.
- `/pay` pays a merchant.

The site checks the stored access token on load with `POST /memberships/token-validate` and renews it with `POST /memberships/refresh-token`.

### Known limitations

- nginx forwards the screens' requests without checking a token. A member id is enough to sign in, and the top-up, remittance and payment requests name the member in the body.

## frontend-admin

The admin console, served by nginx, with a proxy to Keycloak for the staff sign-in and to five services behind the auth-service gate.

| | |
| --- | --- |
| Host port | `8091` |
| Memory limit | `64m` |
| Database | none |
| Calls | `auth-service`, `membership-service`, `payment-service`, `money-aggregation-service`, `money-query-service`, `event-flow-service` |
| Called by | the browser, through the nginx server on port 8080 of the container |
| Kafka | none |
| Axon | none |
| Other stores | The staff tokens in memory only. The state, PKCE verifier and return path of a sign-in in progress in the tab's `sessionStorage` under `jaypay.admin.sign-in` |
| Configuration | `JAYPAY_AUTH_URL`, `JAYPAY_KEYCLOAK_URL`, `JAYPAY_MEMBERSHIP_URL`, `JAYPAY_PAYMENT_URL`, `JAYPAY_MONEY_AGGREGATION_URL`, `JAYPAY_MONEY_QUERY_URL`, `JAYPAY_EVENT_FLOW_URL`, `NGINX_ENTRYPOINT_LOCAL_RESOLVERS` |

### What it does

`frontend/apps/admin/nginx.conf.template` proxies `/realms/staff/` and `/resources/` to Keycloak at `JAYPAY_KEYCLOAK_URL`, so the browser signs in on the admin site's own origin, and answers 404 to any other `/realms/` path. It proxies `/api/membership/`, `/api/payment/`, `/api/money-aggregation/`, `/api/money-query/` and `/api/event-flow/` to the matching `JAYPAY_*_URL`. Before it forwards one of them, it sends an `auth_request` to `/authorize` followed by the original path on auth-service with the method and headers and without the body. A 401 or 403 from auth-service becomes a JSON error for the browser. Any other `/api/` path answers 404, including the old `/api/auth/staff/` sign-in. `frontend/Dockerfile` sets the `JAYPAY_*_URL` defaults to the Compose service names. `deploy/envs/local/frontend/frontend-admin.yaml` sets them for the local cluster.

The console has these pages:

- `/callback` finishes a Keycloak sign-in. It refuses a `state` that this tab did not start, trades the code and the PKCE verifier for tokens, and opens the page the visitor asked for.
- `/signed-out` is where Keycloak returns after sign-out.
- `/members` finds members by id or by address.
- `/members/:membershipId` shows a member and lets an operator or admin edit it.
- `/money` shows the sum of balances at an address from money-aggregation-service and the total from money-query-service.
- `/event-flow` lists the 50 newest flows, newest first, filtered by type and status, with a column that names each type, and refreshes the list every 5 seconds. Selecting a flow shows its steps in order with the service, the outcome, the time since the previous step and the reason of a failure. The timeline refreshes every 2 seconds while the flow is in progress and stops once it succeeded or failed. On a screen narrower than 1500 pixels the timeline shows above the list. Members appear by id only. Every staff role may open it.
- `/settlement` lists the paid payments with their fee and payout, and lets an operator or admin mark one settled. Marking a payment that is settled already says so and changes nothing.
- `/account` shows the username and role and links to the Keycloak account console at `/realms/staff/account`, where staff change their password and set up an authenticator app.

Staff accounts are managed in the Keycloak admin console, which replaced the staff accounts page. [infra/docker/README.md](../infra/docker/README.md#staff-accounts) describes how to open it.
