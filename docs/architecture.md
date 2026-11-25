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
