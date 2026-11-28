# Which behaviours have a test

Line coverage says which code ran. This page says which behaviour of each use case a test checks, and where none does. It was made for [#149](https://github.com/chrysaor/JayPay/issues/149) by reading the `port.in` interfaces of every service, the names of every test and the JaCoCo report per class. A cell names the test class that checks the case, "none" where no test does, and "-" where the case does not apply.

The cases:

- Normal: the request succeeds.
- Refused: each reason the use case refuses a request.
- Repeated: the same request arrives again or twice at once.
- Lost answer: a service it calls did the work and the answer did not arrive.
- Outage: a service it calls answers 5xx or does not answer.

## membership-service

| Use case | Normal | Refused | Repeated | Lost answer | Outage |
| --- | --- | --- | --- | --- | --- |
| Register a member | RegisterMembershipControllerTest | RegisterMembershipControllerTest (blank address) | - | - | VaultAdapterTest (Vault at start) |
| Modify a member | MembershipResponseTest | ModifyMembershipControllerTest | - | - | - |
| Find a member by id or address | MembershipResponseTest, MembershipPersistenceTest | none (unknown id) | - | - | - |
| Sign in | AuthMembershipControllerTest | AuthMembershipControllerTest (unknown member, member that is not valid) | - | - | - |
| Refresh a token | AuthMembershipControllerTest | AuthMembershipControllerTest | - | - | - |
| Validate a token | AuthMembershipControllerTest, JwtTokenProviderTest | JwtTokenProviderTest | - | - | - |
| Member of a token | MembershipResponseTest | AuthMembershipControllerTest | - | - | - |
| Check a member for a recharge (Axon) | CheckMembershipTest | CheckMembershipTest | - | - | - |

## banking-service

| Use case | Normal | Refused | Repeated | Lost answer | Outage |
| --- | --- | --- | --- | --- | --- |
| Register a bank account | RegisterBankAccountControllerTest | RegisterBankAccountControllerTest (member not confirmed), RegisterBankAccountServiceTest (account not valid) | RegisterBankAccountControllerTest (another account) | - | RegisterBankAccountControllerTest, MembershipServiceAdapterTest |
| Register a bank account by event | RegisterBankAccountControllerTest | RegisterBankAccountServiceTest (Axon refuses) | none | - | none |
| Find the registered account | RegisterBankAccountControllerTest, BankingPersistenceTest | RegisterBankAccountControllerTest | - | - | - |
| Bank account information | RegisterBankAccountControllerTest | - | - | - | - |
| Transfer and reverse | BankTransferTest | BankTransferTest | BankTransferTest | BankTransferTest | BankTransferTest (bank does not answer) |

## money-service

| Use case | Normal | Refused | Repeated | Lost answer | Outage |
| --- | --- | --- | --- | --- | --- |
| Open a wallet | WalletInvariantsTest, RechargeSagaTest | MoneyPersistenceTest (second wallet) | WalletInvariantsTest | - | - |
| Increase | IncreaseMoneyTest | IncreaseMoneyTest (member that is not valid) | WalletInvariantsTest | - | WalletInvariantsTest (membership-service) |
| Decrease | WalletInvariantsTest | WalletInvariantsTest | WalletInvariantsTest | - | - |
| Refund | WalletInvariantsTest | WalletInvariantsTest | WalletInvariantsTest | - | - |
| Recharge through the saga | RechargeSagaTest, RechargeSagaFirstStartTest | RechargeSagaTest | RechargeSagaTest | RechargeSagaTest | RechargeSagaTest |
| Balances of members | MoneyPersistenceTest | - | - | - | - |

## payment-service

| Use case | Normal | Refused | Repeated | Lost answer | Outage |
| --- | --- | --- | --- | --- | --- |
| Request a payment | PaymentInvariantsTest | PaymentInvariantsTest | PaymentInvariantsTest | PaymentInvariantsTest | PaymentInvariantsTest (money-service, membership-service) |
| Resume unconfirmed payments | PaymentInvariantsTest, SchemaUpgradeTest | PaymentInvariantsTest | PaymentInvariantsTest | PaymentInvariantsTest | PaymentInvariantsTest |
| List paid payments and finish a settlement | PaymentInvariantsTest, PaymentPersistenceTest | PaymentPersistenceTest | PaymentPersistenceTest | - | - |

## remittance-service

| Use case | Normal | Refused | Repeated | Lost answer | Outage |
| --- | --- | --- | --- | --- | --- |
| Request a remittance | RemittanceInvariantsTest | RemittanceInvariantsTest, RemittancePersistenceTest | RemittanceInvariantsTest | RemittanceInvariantsTest | RemittanceInvariantsTest (money-service, banking-service, membership-service) |
| Resume unfinished remittances | RemittanceInvariantsTest, SchemaUpgradeTest | - | RemittanceInvariantsTest | RemittanceInvariantsTest | RemittanceInvariantsTest |
| Remittances of a member | RemittanceInvariantsTest | - | - | - | - |

## settlement-service

| Use case | Normal | Refused | Repeated | Lost answer | Outage |
| --- | --- | --- | --- | --- | --- |
| Settle paid payments | SettlementServiceTest | SettlementServiceTest, SettlementAfterAccountChangeTest | SettlementJobTest, SettlementServiceTest | SettlementWhenAServiceFailsTest | SettlementWhenAServiceFailsTest |

## money-query-service and money-aggregation-service

| Use case | Normal | Refused | Repeated | Lost answer | Outage |
| --- | --- | --- | --- | --- | --- |
| Add an increase to the region total | MoneySumByAddressServicesTest, RegionTotalTest | RegionTotalTest (rolled back recharge, member that does not exist) | RegionTotalTest, DynamoDBAdapterTest | - | DynamoDBAdapterTest (table), RegionTotalTest and MembershipServiceAdapterTest (membership-service) |
| Read the region total | MoneySumByAddressServicesTest | - | - | - | DynamoDBAdapterTest |
| Sum the balances at an address | GetMoneySumByAggregationServiceTest, GetMoneySumControllerTest | - | - | - | GetMoneySumControllerTest |

## event-flow-service, logging-consumer and auth-service

| Use case | Normal | Refused | Repeated | Lost answer | Outage |
| --- | --- | --- | --- | --- | --- |
| Record a flow step | EventFlowKafkaTest, FlowTest | EventFlowKafkaTest (not a flow step, another type) | EventFlowKafkaTest | - | - |
| Find flows | EventFlowKafkaTest | EventFlowKafkaTest | - | - | - |
| Forget old flows | EventFlowKafkaTest | - | - | - | - |
| Log every message of the logging topic | LoggingConsumerKafkaTest | - | - | - | - |
| Admit a staff request | AdminConsoleGateTest, AdminConsoleGateOverTomcatTest | AdminConsoleGateTest | - | - | - |

## Code no test reaches

From the JaCoCo report of all modules, 293 classes with code.

| Class | Not reached | What it means |
| --- | --- | --- |
| `GetMoneySumByAggregationService`, `GetMoneySumController` | every line | Closed here by GetMoneySumByAggregationServiceTest. The controller is covered by GetMoneySumControllerTest |
| `AuthMembershipService` | 9 of 12 branches | Closed here by AuthMembershipControllerTest, and by the answers decision [0037](decisions/0037-sign-in-refusals-answer-401.md) fixed |
| `RegisterBankAccountService`, `BankAccountAdapter` | 3 of 6 and 4 of 4 branches | The refusals of a registration are closed by [#225](https://github.com/chrysaor/JayPay/issues/225). The branches of `BankAccountAdapter` are the fake bank's refusal of a transfer, which `BankTransferTest` replaces with its own bank |
| `FlowRetentionScheduler`, `SettlementBatchScheduler` | every line, 5 each | Each only calls its use case on a schedule. The use cases are tested, the schedule is not |
| `DummyDataGenerator`, `DummyMoneyDataSimulator` in `tools/utility` | every line, 130 | Developer tools that fill a running stack. Not part of a service |
| `FlowEventListener` | 7 of 22 branches | The checks that a step names its flow, service, outcome and time. The tests send two kinds of malformed step |
| `ChangeMoneyService`, `MoneyRechargeSaga` | 7 of 40 and 5 of 25 branches | Not read branch by branch in this audit |
| `BankingServiceAdapter` in settlement-service and remittance-service | 6 of 16 and 5 of 16 branches | Not read branch by branch in this audit |

## What the audit changed

- membership-service had no test for sign-in, token refresh or the member of a token. The new tests found that a sign-in of a member that does not exist answered 500, which is now 404 `MEMBERSHIP_NOT_FOUND`.
- money-aggregation-service had no test for its one use case. It now has one for the sum over more than 100 members and for an address without members.
- remittance-service had no test for the remittances of a member. It now has one.
- [#225](https://github.com/chrysaor/JayPay/issues/225) found that a member who registered another bank account kept the first one, and fixed it.
- [#223](https://github.com/chrysaor/JayPay/issues/223) found that a payment, a remittance, a bank account registration and the sum of balances read an outage of a service they call as a refusal or failed on it, and decision [0035](decisions/0035-an-outage-of-a-checked-service-answers-503.md) fixed the answer.
- [#228](https://github.com/chrysaor/JayPay/issues/228) found that a top-up handled during an outage of membership-service never reached the region total. It is now asked again a fixed number of times.
- Three gaps are filed: [#223](https://github.com/chrysaor/JayPay/issues/223), [#224](https://github.com/chrysaor/JayPay/issues/224) and [#225](https://github.com/chrysaor/JayPay/issues/225).

Mutation testing would show which of the tested lines a test would notice changing. It needs a new build plugin, and the owner decided not to add it for now, see decision [0039](decisions/0039-no-mutation-testing-for-now.md).
